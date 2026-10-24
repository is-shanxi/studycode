# -*- coding: utf-8 -*-
# ============================================================================
# 脚本名：fetch_jyshare.py
# 用途：采集「菜鸟工具」(jyshare.com) 的计算机编程测验题库。
#       站点是 WordPress + Wp-Pro-Quiz 插件，题目走 AJAX 接口加载。
#       共 218 套测验 / 23 个编程科目 / 约 2,089 题。
#
# 用法：
#   python3 fetch_jyshare.py --out jyshare.jsonl            # 采集全部 218 套
#   python3 fetch_jyshare.py --out t.jsonl --limit 5        # 只采前 5 套（测试用）
#   python3 fetch_jyshare.py --out t.jsonl --sleep 0.5      # 每套间隔秒数，默认 0.2
#
# 输出：JSONL 文件，每行一题（subject/quiz_title/question/options/answer_index/source_url）
# 下一步：把输出的 jsonl 放到服务器 ~/qdata/ 下，再跑 import_questions.py --only jyshare
# 注意：本脚本只负责「下载+解析」，不直接写数据库
# ============================================================================
"""
采集 jyshare.com（菜鸟工具）的 Wp-Pro-Quiz 计算机测验题库。

流程：
  1. 抓 https://www.jyshare.com/quiz/ 索引页 → 218 个测验 (quizPostId, 标题)
  2. 逐个 GET /quiz/{postId}/ → 从页面里正则取插件内部 quizId
  3. POST /wp-admin/admin-ajax.php  action=wp_pro_quiz_admin_ajax&func=quizLoadData&data[quizId]=N
     返回 {json: {题id: {type, correct:[0/1]}}, content: 题目HTML}
  4. 解析 HTML 得题干+选项，用 correct 数组定位答案字母
  5. 输出 JSONL: {"subject","quiz_title","question","options":{A..D},"answer","source_url"}

用法：
  python3 fetch_jyshare.py --out /home/ubuntu/qdata/jyshare.jsonl [--limit N] [--sleep 0.25]
"""
import re
import json
import html
import time
import argparse
import subprocess

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
BASE = "https://www.jyshare.com"
AJAX = BASE + "/wp-admin/admin-ajax.php"


def curl(url, post_data=None, timeout=40):
    cmd = ["curl", "-sL", "-m", str(timeout), "-A", UA]
    if post_data is not None:
        cmd += ["-X", "POST", url, "--data", post_data]
    else:
        cmd += [url]
    r = subprocess.run(cmd, capture_output=True)
    return r.stdout.decode("utf-8", "ignore")


def clean_html(h):
    """HTML 片段转纯文本，保留换行与代码内容"""
    if not h:
        return ""
    h = re.sub(r"<br\s*/?>", "\n", h, flags=re.I)
    h = re.sub(r"</(p|div|li|h\d)>", "\n", h, flags=re.I)
    h = re.sub(r"<[^>]+>", "", h)
    h = html.unescape(h)
    h = h.replace("\xa0", " ")
    h = re.sub(r"[ \t]+", " ", h)
    h = re.sub(r"\n[ ]*\n+", "\n", h)
    return h.strip()


def parse_index():
    """返回 [(post_id, title), ...]"""
    s = curl(BASE + "/quiz/")
    pat = re.compile(r'<a[^>]+href="https://www\.jyshare\.com/quiz/(\d+)/?"[^>]*>(.*?)</a>', re.S)
    seen = {}
    for m in pat.finditer(s):
        pid = m.group(1)
        txt = re.sub(r"<[^>]+>", "", m.group(2))
        txt = re.sub(r"\s+", " ", html.unescape(txt)).strip()
        if txt and pid not in seen:
            seen[pid] = txt
    return list(seen.items())


def subject_of(title):
    """'Java 测验 3（基础）' -> 'Java'；'正则表达式 – 测验 1' -> '正则表达式'"""
    t = re.sub(r"\s*测验\s*\d+.*$", "", title).strip()
    t = t.rstrip("–—- ").strip()
    return t or title


def parse_quiz_html(content, ans_map, debug=None):
    """把 quizLoadData 的 content + json 解析成题目列表

    关键：必须按 <li class="wpProQuiz_listItem"> 切分（每个块=一道题），
    不能按 questionList 切 —— 题干 div 在 questionList 之前，按后者切会整体错位一题。
    """
    out = []
    blocks = re.split(r'(?=<li class="wpProQuiz_listItem")', content)
    for blk in blocks:
        m = re.search(r'data-question_id="(\d+)"\s*(?:\n\s*)?data-type="([^"]+)"', blk)
        if not m:
            continue
        qid, qtype = m.group(1), m.group(2)

        tm = re.search(r'<div class="wpProQuiz_question_text">(.*?)</div>', blk, re.S)
        stem = clean_html(tm.group(1)) if tm else ""
        if not stem:
            if debug is not None:
                debug["no_stem"] = debug.get("no_stem", 0) + 1
            continue

        opts = []
        for lm in re.finditer(r'<li class="wpProQuiz_questionListItem"[^>]*data-pos="(\d+)"[^>]*>(.*?)</li>', blk, re.S):
            body = lm.group(2)
            lb = re.search(r"<label>(.*?)</label>", body, re.S)
            txt = clean_html(lb.group(1)) if lb else clean_html(body)
            opts.append(txt)
        if len(opts) < 2:
            if debug is not None:
                debug["few_opts"] = debug.get("few_opts", 0) + 1
            continue

        meta = ans_map.get(qid) or {}
        correct = meta.get("correct") or []
        idxs = [i for i, v in enumerate(correct) if v]
        if len(idxs) != 1:
            if debug is not None:
                debug["bad_answer"] = debug.get("bad_answer", 0) + 1
            continue
        ci = idxs[0]
        if ci >= len(opts):
            if debug is not None:
                debug["ans_oob"] = debug.get("ans_oob", 0) + 1
            continue

        out.append({
            "question": stem,
            "options": opts,
            "answer_index": ci,
            "qtype": qtype,
            "qid": qid,
        })
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--sleep", type=float, default=0.25)
    args = ap.parse_args()

    items = parse_index()
    if args.limit:
        items = items[: args.limit]
    print(f"索引页共 {len(items)} 套测验", flush=True)

    fout = open(args.out, "w", encoding="utf-8")
    total = 0
    stat = {}

    for n, (pid, title) in enumerate(items, 1):
        url = f"{BASE}/quiz/{pid}/"
        page = curl(url)
        m = re.search(r"quizId\s*:\s*(\d+)", page)
        if not m:
            print(f"  [{n}/{len(items)}] {title} -> 无 quizId，跳过", flush=True)
            continue
        quiz_id = m.group(1)

        body = (f"action=wp_pro_quiz_admin_ajax&func=quizLoadData"
                f"&data%5BquizId%5D={quiz_id}")
        raw = curl(AJAX, post_data=body)
        try:
            d = json.loads(raw)
        except Exception:
            print(f"  [{n}/{len(items)}] {title} -> AJAX 非JSON，跳过", flush=True)
            continue
        content = d.get("content") or ""
        ans_map = d.get("json") or {}
        if not content:
            print(f"  [{n}/{len(items)}] {title} -> 无 content，跳过", flush=True)
            continue

        dbg = {}
        qs = parse_quiz_html(content, ans_map, dbg)
        if dbg:
            print(f"      跳过明细 {dbg}", flush=True)
        subj = subject_of(title)
        for q in qs:
            rec = {
                "subject": subj,
                "quiz_title": title,
                "question": q["question"],
                "options": q["options"],
                "answer_index": q["answer_index"],
                "source_url": url,
            }
            fout.write(json.dumps(rec, ensure_ascii=False) + "\n")
        total += len(qs)
        stat[subj] = stat.get(subj, 0) + len(qs)
        print(f"  [{n}/{len(items)}] {title:24s} {len(qs):3d} 题  (累计 {total})", flush=True)
        time.sleep(args.sleep)

    fout.close()
    print(f"\n完成：{total} 题 -> {args.out}")
    print("科目分布：")
    for k, v in sorted(stat.items(), key=lambda x: -x[1]):
        print(f"  {k:22s} {v:4d}")


if __name__ == "__main__":
    main()
