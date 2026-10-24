#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ============================================================================
# 脚本名：gen_ledger.py
# 用途：生成「采集网站表格.xlsx」——一份只列出采集来源信息的台账。
#       内容 = 序号 / 平台来源 / 来源网址 / 覆盖学科 / 入库题数 / 题型 / 答案 / 解析 /
#              采集状态 / 入库日期，末尾一行加粗合计。
#
# 用法：
#   python3 gen_ledger.py
#
# 输出：同目录下的 采集网站表格.xlsx（会覆盖旧文件）
# 注意：① 表格里【只放采集的网站信息】，历史演进、清理明细、采集踩坑一律不写进表格，
#          那些内容记在 .workbuddy/memory/ 里（用户明确要求过）。
#       ② 新增数据源后，记得同步修改下面 DATA 列表里的记录和合计行。
#       ③ 如果 Excel 正开着这个 xlsx，保存会失败（PermissionError），先关掉再跑。
# ============================================================================
"""生成采集网站台账 xlsx —— 只列采集的网站信息（来源、网址、学科、题数、题型、解析、状态）

历史演进、清理明细、采集踩坑等过程性说明不放进表格，改由 .workbuddy/memory/ 记录。
"""
import openpyxl
from openpyxl.styles import Font, Alignment

DATA = [
    (1, "Xiezhi", "https://github.com/MikeGu721/XiezhiBenchmark",
     "516（中国各学科门类/一级学科）", 252982, "单选", "全部有", "无", "已完成", "2026-10-23"),
    (2, "CMExam", "https://github.com/williamliujl/CMExam",
     "1（医师资格）", 60192, "单选+多选", "全部有", "51622题有(85.8%)", "已完成", "2026-10-23"),
    (3, "COIG-Exam", "https://huggingface.co/datasets/BAAI/COIG",
     "6（高中历史/政治/语文/英语/生物/地理）", 35763, "单选", "全部有", "18690题有(52.3%)", "已完成", "2026-10-23"),
    (4, "Global-MMLU", "https://huggingface.co/datasets/CohereLabs/Global-MMLU",
     "57", 13850, "单选", "全部有", "无", "已完成", "2026-10-23"),
    (5, "C-Eval", "https://huggingface.co/datasets/ceval/ceval-exam",
     "52", 13696, "单选", "全部有", "258题有", "已完成", "2026-10-22"),
    (6, "CMMLU", "https://huggingface.co/datasets/haonan-li/cmmlu",
     "67", 11742, "单选", "全部有", "无", "已完成", "2026-10-23"),
    (7, "FinanceIQ", "https://github.com/DUXiaomanAI/FinCorpus",
     "10", 7145, "单选", "全部有", "无", "已完成", "2026-10-23"),
    (8, "TAL-SCQ5K", "https://github.com/math-eval/TAL-SCQ5K",
     "1（数学竞赛）", 4667, "单选", "全部有", "4667题有(100%)", "已完成", "2026-10-23"),
    (9, "AGIEval", "https://github.com/microsoft/AGIEval",
     "11（高考8科+法考+逻辑）", 4478, "单选+多选", "全部有", "无", "已完成", "2026-10-23"),
    (10, "Ruankao", "GitHub 4仓库(awesome-ruankao/架构师/Lemon/信安)",
     "7（架构师/系分/项管师/系规/网规/信安/集成）", 3691, "单选", "全部有", "3618题有(98%)", "已完成", "2026-10-23"),
    (11, "ACLUE", "https://github.com/tyouisen/ACLUE",
     "15（古汉语）", 3154, "单选", "全部有", "无", "已完成", "2026-10-23"),    (12, "SecBench", "https://huggingface.co/datasets/RISys-Lab/Benchmarks_CyberSec_SecBench",
     "10个安全子领域", 2047, "单选", "全部有", "无", "已完成", "2026-10-23"),
    (13, "DevOps-Eval", "https://github.com/codefuse-ai/CodeFuse-DevOps-Eval",
     "8个DevOps阶段", 1391, "单选", "全部有", "98题有(7%)", "已完成", "2026-10-23"),
    (14, "FinEval", "https://huggingface.co/datasets/SUFE-AIFLM-Lab/FinEval",
     "34（金融/会计/考证）", 1282, "单选", "全部有", "无", "已完成", "2026-10-23"),
    (15, "TCM-Exam", "FreedomIntelligence药师(中药学)+CMMLU中医",
     "2", 467, "单选+多选", "全部有", "无", "已完成", "2026-10-23"),
    (16, "E-EVAL", "https://huggingface.co/datasets/E-EVAL/E-EVAL",
     "23（小学/初中/高中）", 523, "单选", "全部有", "无", "已完成", "2026-10-23"),
    (17, "M3KE", "https://github.com/tjunlp-lab/M3KE",
     "71个任务", 348, "单选", "仅dev有", "无", "已完成", "2026-10-23"),
    (18, "Jyshare（菜鸟工具）", "https://www.jyshare.com/quiz/",
     "23（C/C++/Java/Python/前端/数据库/Linux/Git等）", 2089, "单选+判断", "全部有", "无",
     "已完成", "2026-10-23"),
    (19, "CS408（王道408）", "https://github.com/zsc5725216-hub/408-quiz",
     "4（数据结构/计组/计网/操作系统）", 2084, "单选", "全部有", "2084题有(100%)",
     "已完成", "2026-10-23"),
    (20, "NCRE（一级Office）", "https://github.com/oyxning/computer-rank-exam-treasure",
     "1（计算机基础）", 476, "单选", "全部有", "415题有(87%)", "已完成", "2026-10-23"),
    (21, "RUNOOB（菜鸟教程）", "https://www.runoob.com/quiz/",
     "17（HTML/CSS/JS/Python/Java/C++/C#/Go/PHP/SQL/Linux/Git等）", 436, "单选", "全部有", "无",
     "已完成", "2026-10-23"),
    (22, "XiaoHei（小黑课堂）", "https://xhkt.ijinshan.com/practice/",
     "2（计算机一级/二级：计算机基础、MS Office、数据结构、算法、数据库）", 80, "单选", "全部有",
     "80题有(100%)", "已完成", "2026-10-23"),
]
wb = openpyxl.Workbook()
ws = wb.active
ws.title = "采集网站"

hdr = ["序号", "平台/来源", "来源网址", "覆盖学科数", "入库题数", "题型",
       "答案", "解析", "采集状态", "入库日期"]
ws.append(hdr)
for c in ws[1]:
    c.font = Font(bold=True)
    c.alignment = Alignment(horizontal="center")
for row in DATA:
    ws.append(list(row))

n = len(DATA)
ws.append([])
ws.append([None, "合计（22 个源）", None, None, sum(r[4] for r in DATA), None, None,
           "81529题带解析(19.3%)", "2961道多选", None])
for c in ws[ws.max_row]:
    c.font = Font(bold=True)

for col, w in zip("ABCDEFGHIJ", [6, 20, 55, 34, 12, 12, 10, 20, 10, 12]):
    ws.column_dimensions[col].width = w
ws.freeze_panes = "A2"

out = r"C:/Users/ASUS/Desktop/个人项目文件夹/studycode/spider/采集网站表格.xlsx"
try:
    wb.save(out)
    print("saved:", out)
except PermissionError:
    # 表格正在 Excel 里打开时 Windows 会锁住文件，去掉多余副本，只提示重跑
    print(f"[跳过] 文件被占用，保存失败：{out}")
    print("      请先在 Excel 中关闭该表格，然后重新运行本脚本。")
    import sys
    sys.exit(1)
