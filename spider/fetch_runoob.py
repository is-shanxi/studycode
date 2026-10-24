#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ============================================================================
# 脚本名：fetch_runoob.py
# 用途：采集「菜鸟教程」(runoob.com) 主站的在线测验题库。
#       共 22 套测验（其中 xhtml 那套页面没有题库数据，实际采到 21 套）/ 436 题。
#       与 jyshare（fetch_jyshare.py）的题目完全不重复，是两套不同的题。
#
# 用法：
#   python3 fetch_runoob.py --out runoob.jsonl               # 采集全部
#   python3 fetch_runoob.py --out t.jsonl --limit 3          # 只采前 3 套（测试用）
#   python3 fetch_runoob.py --out t.jsonl --no-shuffle       # 不洗牌（保留源站全 A 顺序）
#
# 输出：JSONL 文件，每行一题（subject/question/options/answer_index/url）
# 下一步：把 jsonl 放到服务器 ~/qdata/ 下，再跑 import_questions.py --only runoob
# 注意：本脚本内置「选项洗牌」——源站正确项永远排第一（A 占 62.4%），
#       不洗牌的话用户闭眼选 A 就满分。洗牌用 md5(题干) 当固定种子，保证重跑结果一致。
# ============================================================================
"""
采集 菜鸟教程 RUNOOB 在线测验（https://www.runoob.com/quiz/）全部题目。

与姊妹站 jyshare（fetch_jyshare.py，218 套 / 2,089 题）**互不重复**，本脚本采的是
runoob 主站自带的 22 套测验，共 436 题。

页面结构：题目内嵌在 <script> 里的 `quizData = {title:..., questions:[{q, options, correctIndex}]}`，
标准 JS 对象（key 无引号），所以用 node 的 Function() 求值提取，正则/ast 都处理不了。

⚠️ 关键坑：源站的选项顺序**没有打乱**，正确项永远排在第 1 位 —— 22 套里有 4 套是 20/20 全 A，
整体 A 占比 62.4%（正常应 25%）。直接入库用户闭眼选 A 就满分。
   解决办法：导入前用**固定种子**（md5(题干)）洗牌选项顺序，答案位置随之更新。
   固定种子保证脚本重跑结果一致（幂等），不会每次导入都变。

用法：
    python3 fetch_runoob.py --out runoob.jsonl            # 采全部
    python3 fetch_runoob.py --out t.jsonl --limit 3       # 只采前 3 套（测试用）
    python3 fetch_runoob.py --out t.jsonl --no-shuffle    # 不打乱（保留原始全 A 顺序）
"""
import argparse
import hashlib
import html
import json
import random
import re
import sys
import time
import urllib.request

BASE = "https://www.runoob.com/quiz/"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0 Safari/537.36")

# runoob.com/quiz 索引页上的全部测验（2026-10-23 核对，xhtml 页无 quizData，采不到）
SLUGS = [
    "asp", "cpp", "csharp", "css", "css3", "git", "go", "html", "html5",
    "java", "javascript", "jquery", "linux", "numpy", "pandas", "php",
    "python", "sql", "ts", "vue3", "xhtml", "xml",
]


def extract_quiz_data(s: str):
    """从 quizData = {...} 处用括号配对取出对象体，交给 JS 引擎求值。
    返回 None 表示本页没有题库。"""
    anchor = s.find("quizData")
    if anchor < 0:
        return None
    start = s.find("{", anchor)
    if start < 0:
        return None
    depth, i, in_str, esc = 0, start, None, False
    while i < len(s):
        c = s[i]
        if in_str:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == in_str:
                in_str = None
        elif c in "'\"":
            in_str = c
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                i += 1
                break
        i += 1
    body = s[start:i]
    js = "const fs=require('fs');\n" \
         "const s=fs.readFileSync(process.argv[2],'utf8');\n" \
         "process.stdout.write(JSON.stringify(Function('\"use strict\"; return ('+s+');')()));"
    import subprocess
    import tempfile
    import os
    fd, tmp = tempfile.mkstemp(suffix=".js")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(js)
    try:
        nodes = ["node", "nodejs"]
        last_err = None
        fd2, tmp2 = tempfile.mkstemp(suffix=".txt")
        with os.fdopen(fd2, "w", encoding="utf-8") as f:
            f.write(body)
        for n in nodes:
            try:
                r = subprocess.run([n, tmp, tmp2], capture_output=True, timeout=60)
                if r.returncode == 0 and r.stdout:
                    return json.loads(r.stdout.decode("utf-8"))
                last_err = r.stderr.decode("utf-8", "ignore")[:200]
            except FileNotFoundError:
                last_err = "no node"
                continue
        print(f"  [warn] node 解析失败: {last_err}", file=sys.stderr)
        return None
    finally:
        for p in (tmp, tmp2):
            try:
                os.unlink(p)
            except OSError:
                pass


def fetch(url: str, retries: int = 3):
    for k in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read().decode("utf-8", "ignore")
        except Exception as e:
            if k == retries - 1:
                print(f"  [err] {url}: {e}", file=sys.stderr)
                return None
            time.sleep(1.5 * (k + 1))
    return None


def clean(s: str, br: bool = True) -> str:
    """反转义 HTML 实体 + 压空白。

    ⚠️ 坑 1：源站把 HTML 标签当**题目内容**存成实体（&lt;p&gt; = 答案是 <p>），
    所以 unescape 后**绝不能再删标签** —— 早期版本多做了 re.sub(</?[a-zA-Z]...>)，
    把 html/html5 套 33 道「哪个标签？」题的选项全清成空串，被去重逻辑误杀。

    ⚠️ 坑 2：<br> 在题干里是排版换行，但在选项里可能是**答案本身**
    （"ASP 中用于输出换行的标签是？" → 答案 <br>）。所以选项走 br=False 原样保留。
    """
    s = html.unescape(s or "")
    if br:
        s = re.sub(r"<br\s*/?>", "\n", s, flags=re.I)
        s = re.sub(r"<(/)?(p|div|span)\s*/?>", "\n", s, flags=re.I)
    s = re.sub(r"[ \t\u00a0]+", " ", s)
    return "\n".join(x.strip() for x in s.split("\n") if x.strip()).strip()


def shuffle_options(stem: str, opts: list, ci: int, enabled: bool = True):
    """用 md5(题干) 做固定种子洗牌选项，返回 (新选项, 新正确答案下标)。
    固定种子 => 同一题无论跑多少次结果都一样，导入脚本可安全幂等重跑。"""
    if not enabled or len(opts) < 2:
        return opts, ci
    seed = int(hashlib.md5(stem.encode("utf-8")).hexdigest()[:8], 16)
    idx = list(range(len(opts)))
    random.Random(seed).shuffle(idx)
    new_opts = [opts[i] for i in idx]
    new_ci = idx.index(ci)
    return new_opts, new_ci


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="输出 JSONL 路径")
    ap.add_argument("--limit", type=int, default=0, help="只采前 N 套（测试）")
    ap.add_argument("--sleep", type=float, default=0.4, help="每套间隔秒")
    ap.add_argument("--no-shuffle", action="store_true", help="不洗牌（保留源站全 A 顺序）")
    args = ap.parse_args()

    slugs = SLUGS[:args.limit] if args.limit else SLUGS
    n_out = n_skip = 0
    with open(args.out, "w", encoding="utf-8") as f:
        for n, slug in enumerate(slugs, 1):
            url = f"{BASE}{slug}-quiz.html"
            page = fetch(url)
            if not page:
                print(f"[{n}/{len(slugs)}] {slug}: 抓取失败")
                continue
            d = extract_quiz_data(page)
            if not d or not d.get("questions"):
                print(f"[{n}/{len(slugs)}] {slug}: 无 quizData，跳过")
                continue
            got = 0
            for q in d["questions"]:
                stem = clean(q.get("q"))
                opts = [clean(o, br=False) for o in (q.get("options") or [])]
                ci = q.get("correctIndex")
                if not stem or len(stem) < 4 or len(opts) < 2:
                    n_skip += 1
                    continue
                if ci is None or not (0 <= ci < len(opts)):
                    n_skip += 1
                    continue
                opts, ci = shuffle_options(stem, opts, ci, not args.no_shuffle)
                f.write(json.dumps({
                    "subject": slug,
                    "title": d.get("title") or slug,
                    "question": stem,
                    "options": opts,
                    "answer_index": ci,
                    "url": url,
                }, ensure_ascii=False) + "\n")
                got += 1
                n_out += 1
            print(f"[{n}/{len(slugs)}] {slug}: {got} 题")
            time.sleep(args.sleep)

    print(f"\n完成：{n_out} 题写入 {args.out}（跳过 {n_skip} 条残缺）")


if __name__ == "__main__":
    main()
