#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ============================================================================
# 脚本名：fetch_xiaohei.py
# 用途：采集「小黑课堂」(金山 xhkt.ijinshan.com) 的计算机等级考试练习题库。
#       站点是纯静态页面，题库就是一个静态 JS 文件，无需登录、无加密、无反爬。
#       共 16 个题组 / 80 题，全部四选一，100% 带解析和难度分级。
#
# 用法：
#   python3 fetch_xiaohei.py --out xiaohei.jsonl              # 采集全部 80 题（推荐）
#   python3 fetch_xiaohei.py --out t.jsonl --from-sets        # 走原始结构 + 本地洗牌
#
# 输出：JSONL 文件，每行一题（id/level/topic/question/options/answer_index/explanation…）
# 下一步：把 jsonl 放到服务器 ~/qdata/ 下，再跑 import_questions.py --only xiaohei
# 说明：默认读源站的 PRACTICE_QUESTION_BANK（官方题库结构，选项已由站点轮换均衡、
#       还带官方题号可用于题目级链接）；加 --from-sets 则退回原始数组自己洗牌。
# ============================================================================
"""
采集 小黑课堂（金山 xhkt.ijinshan.com）在线练习题库。

站点是纯静态 SPA，题库以静态 JS 文件形式提供，无接口、无登录、无加密：
    https://xhkt.ijinshan.com/practice/question-bank/questions.js

文件里有两个结构（2026-10-23 核对）：

1) window.PRACTICE_QUESTION_SETS —— 原始数据，题目是**定长数组**
   [{level, subject, topic, prefix, questions: [[题干, [选项], 答案下标, 解析, 难度], ...]}]
   ⚠️ 原数组的选项顺序没打乱，答案 86.3% 挤在下标 0。

2) window.PRACTICE_QUESTION_BANK —— 站点由上面派生出的**完整题库结构**，共 16 个字段：
   {version, questions: [{id, level, subject, topic, type, stem, options, answer,
                          explanation, difficulty, source_type, author, reviewer,
                          review_status, reviewed_at, version}]}
   站点在生成时已按 (setIndex*5+index)%4 轮换选项并同步修正 answer 下标，
   实测答案分布 22/19/20/19（均衡），且 80 题轮换后答案指向全部正确（0 错误）。

本脚本优先使用 PRACTICE_QUESTION_BANK（数据更全、选项已被站点轮换好、带官方题号），
拿不到时回退到 PRACTICE_QUESTION_SETS + 本地 md5 固定种子洗牌。

规模：16 个题组 × 5 题 = **80 题**（L1 计算机一级 8 组、L2 二级 8 组）。
全站只有这一个题库文件：sitemap.xml 与 llms.txt 都只列 12 个页面，
其中只有 /practice/ 加载本题库；/download/ 是 3.93MB 引导安装器（PC 客户端，非网页数据）。

用法：
    python3 fetch_xiaohei.py --out xiaohei.jsonl
    python3 fetch_xiaohei.py --out t.jsonl --from-sets     # 强制走原始结构
"""
import argparse
import hashlib
import json
import os
import random
import subprocess
import sys
import tempfile
import urllib.request

BASE = "https://xhkt.ijinshan.com/practice/question-bank/questions.js"
PAGE = "https://xhkt.ijinshan.com/practice/"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0 Safari/537.36")

JS_EVAL = """
const fs=require('fs');
global.window={};
eval(fs.readFileSync(process.argv[2],'utf8'));
const w=global.window;
process.stdout.write(JSON.stringify({
  review: w.PRACTICE_BATCH_REVIEW||{},
  sets: w.PRACTICE_QUESTION_SETS||[],
  bank: w.PRACTICE_QUESTION_BANK||null
}));
"""


def fetch(url: str, retries: int = 3) -> str:
    for k in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=45) as r:
                raw = r.read()
            if raw[:2] == b"\x1f\x8b":
                import gzip
                raw = gzip.decompress(raw)
            return raw.decode("utf-8", "ignore")
        except Exception as e:
            if k == retries - 1:
                print(f"[err] 下载失败: {e}", file=sys.stderr)
                raise SystemExit(1)
    return ""


def eval_js(src: str) -> dict:
    """题库是含 window.XXX 赋值的 JS 文件，用 node 求值后转 JSON。"""
    fd, tmp_js = tempfile.mkstemp(suffix=".js")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(JS_EVAL)
    fd2, tmp_data = tempfile.mkstemp(suffix=".txt")
    with os.fdopen(fd2, "w", encoding="utf-8") as f:
        f.write(src)
    try:
        for n in ("node", "nodejs"):
            try:
                r = subprocess.run([n, tmp_js, tmp_data],
                                   capture_output=True, timeout=90)
                if r.returncode == 0 and r.stdout:
                    return json.loads(r.stdout.decode("utf-8"))
                print(f"[err] node 求值失败: {r.stderr.decode('utf-8','ignore')[:200]}",
                      file=sys.stderr)
            except FileNotFoundError:
                continue
        raise SystemExit("找不到 node，无法解析题库 JS")
    finally:
        for p in (tmp_js, tmp_data):
            try:
                os.unlink(p)
            except OSError:
                pass


def shuffle_options(stem: str, opts: list, ci: int):
    """回退路径用：md5(题干) 固定种子洗牌，保证重跑幂等。"""
    if len(opts) < 2:
        return opts, ci
    seed = int(hashlib.md5(stem.encode("utf-8")).hexdigest()[:8], 16)
    idx = list(range(len(opts)))
    random.Random(seed).shuffle(idx)
    return [opts[i] for i in idx], idx.index(ci)


def rows_from_bank(bank: dict):
    """官方题库结构：字段最全，选项已被站点轮换均衡。"""
    for q in bank.get("questions") or []:
        yield {
            "id": str(q.get("id") or "").strip(),
            "level": str(q.get("level") or "").strip(),
            "subject": str(q.get("subject") or "").strip(),
            "topic": str(q.get("topic") or "").strip(),
            "qtype": str(q.get("type") or "").strip(),
            "question": str(q.get("stem") or "").strip(),
            "options": [str(x).strip() for x in (q.get("options") or [])],
            "answer_index": q.get("answer"),
            "explanation": str(q.get("explanation") or "").strip(),
            "difficulty": str(q.get("difficulty") or "").strip(),
            "source_type": str(q.get("source_type") or "").strip(),
            "review_status": str(q.get("review_status") or "").strip(),
            "reviewed_at": str(q.get("reviewed_at") or "").strip(),
        }


def rows_from_sets(sets: list):
    """回退路径：原始定长数组，自己洗牌。"""
    for s in sets:
        for q in s.get("questions") or []:
            if not isinstance(q, list) or len(q) < 4:
                continue
            stem = str(q[0]).strip()
            opts = [str(x).strip() for x in (q[1] or [])]
            ci = q[2]
            if not stem or len(opts) < 2 or not isinstance(ci, int) or not (0 <= ci < len(opts)):
                continue
            opts, ci = shuffle_options(stem, opts, ci)
            yield {
                "id": "",
                "level": str(s.get("level") or "").strip(),
                "subject": str(s.get("subject") or "").strip(),
                "topic": str(s.get("topic") or "").strip(),
                "qtype": "single_choice",
                "question": stem,
                "options": opts,
                "answer_index": ci,
                "explanation": str(q[3]).strip(),
                "difficulty": str(q[4]).strip() if len(q) > 4 else "",
                "source_type": "",
                "review_status": "approved",
                "reviewed_at": "",
            }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--from-sets", action="store_true", help="强制走原始结构（自己洗牌）")
    args = ap.parse_args()

    src = fetch(BASE)
    if "PRACTICE_QUESTION_SETS" not in src:
        raise SystemExit("页面结构变了：没找到 PRACTICE_QUESTION_SETS")

    d = eval_js(src)
    bank = d.get("bank")
    review = d.get("review") or {}
    use_bank = bool(bank and bank.get("questions")) and not args.from_sets

    if use_bank:
        print(f"数据源: PRACTICE_QUESTION_BANK v{bank.get('version')}")
        rows = list(rows_from_bank(bank))
    else:
        print("数据源: PRACTICE_QUESTION_SETS（原始结构 + 本地洗牌）")
        rows = list(rows_from_sets(d.get("sets") or []))
    print(f"批次: {review.get('batch_id')} | 审核: {review.get('status')} "
          f"@ {review.get('reviewed_at')}")

    n_out = n_skip = 0
    per = {}
    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            stem = r["question"]
            opts = [x for x in r["options"] if x]
            ci = r["answer_index"]
            if not stem or len(stem) < 4 or len(opts) < 2 or len(opts) > 6:
                n_skip += 1
                continue
            if len(set(opts)) != len(opts):
                n_skip += 1
                continue
            if not isinstance(ci, int) or not (0 <= ci < len(opts)):
                n_skip += 1
                continue
            r["options"] = opts
            # 题目级 source_url：带官方题号，便于回溯到源站
            qid = r.get("id") or ""
            r["url"] = f"{PAGE}#{qid}" if qid else PAGE
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
            n_out += 1
            key = f"L{r['level']} {r['subject']} / {r['topic']}"
            per[key] = per.get(key, 0) + 1

    for k in sorted(per):
        print(f"  {k}: {per[k]} 题")
    print(f"\n完成：{n_out} 题写入 {args.out}（跳过 {n_skip} 条残缺）")


if __name__ == "__main__":
    main()
