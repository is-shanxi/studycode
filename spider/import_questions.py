#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# ============================================================================
# 脚本名：import_questions.py
# 用途：把各数据源的原始题库文件解析后，批量导入 PostgreSQL 的 questions 表。
#       这是整个采集流程的【核心入库脚本】，22 个数据源全部由它统一入库。
# 特点：幂等（重复跑不会重复入库）、带答案校验闸门（答案缺失或无效的题自动丢弃）
#
# 用法：
#   python3 import_questions.py                  # 导入全部数据源
#   python3 import_questions.py --only xiaohei    # 只导入某一个源
#   python3 import_questions.py --dry-run         # 只解析不入库，用来看会导入多少条
#   python3 import_questions.py --dry-run --only runoob
#
# 依赖：服务器上的 psql（通过 docker exec pgvector 调用），不需要 psycopg2
# 数据来源：读取 ~/qdata/ 下各源的原始文件，详见下面 SOURCES 配置
# 输出：直接写入数据库表 questions；分类自动创建到 categories 表
# ============================================================================
"""
通用题目入库脚本（studycode / questions 表）

设计要点：
1. 幂等：先灌 staging 临时表，再 INSERT ... ON CONFLICT (source_platform, question_hash) DO NOTHING，
   重复跑不会产生重复数据，也不会触碰已有题目。
2. question_hash = md5(题干原文 utf-8)，与库内现有 25836 条保持同一算法（已实测校验）。
3. options 存 JSONB：{"A":"..","B":".."}，医学类可到 E，多选题 correct_answer 存 "AB"。
4. 只依赖 psql（docker exec），不需要 psycopg2。

用法：
    python3 import_questions.py             # 导入全部已下载源
    python3 import_questions.py --dry-run   # 只统计不写库
    python3 import_questions.py --only cmexam
"""

import csv
import glob
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import zipfile

DATA_DIR = os.path.expanduser("~/qdata")
CONTAINER = "pgvector"
DB = "app"

# 分类归属：(一级分类名, 二级分类名)
# 已存在的二级分类会复用，不存在则自动创建
SOURCES = {
    "cmexam": {
        "platform": "CMExam",
        "url": "https://github.com/williamliujl/CMExam",
        "category": ("考证", "医师资格"),
    },
    "financeiq": {
        "platform": "FinanceIQ",
        "url": "https://github.com/DUXiaomanAI/FinCorpus",
        "category": None,  # 每个科目单独映射，见 FINANCEIQ_MAP
    },
    "talscq": {
        "platform": "TAL-SCQ5K",
        "url": "https://github.com/math-eval/TAL-SCQ5K",
        "category": ("考研/升学", "数学竞赛"),
    },
    "aclue": {
        "platform": "ACLUE",
        "url": "https://github.com/tyouisen/ACLUE",
        "category": ("其他", "古汉语"),
    },
    "agieval": {
        "platform": "AGIEval",
        "url": "https://github.com/microsoft/AGIEval",
        "category": None,  # 每个科目单独映射，见 AGIEVAL_MAP
    },
    "tcm": {
        "platform": "TCM-Exam",
        "url": "https://huggingface.co/datasets/FreedomIntelligence/2023_Pharmacist_Licensure_Examination-TCM_track",
        "category": None,  # 按文件区分：药师资格 / 中医
    },
    "coig": {
        "platform": "COIG-Exam",
        "url": "https://huggingface.co/datasets/BAAI/COIG",
        "category": None,  # 按 subject 映射，见 COIG_MAP
    },
    "gmmlu": {
        "platform": "Global-MMLU",
        "url": "https://huggingface.co/datasets/CohereLabs/Global-MMLU",
        "category": None,  # 按学科映射，见 GMMLU_MAP
    },
    "fineval": {
        "platform": "FinEval",
        "url": "https://huggingface.co/datasets/SUFE-AIFLM-Lab/FinEval",
        "category": None,  # 按学科映射，见 FINEVAL_MAP
    },
    "eeval": {
        "platform": "E-EVAL",
        "url": "https://huggingface.co/datasets/E-EVAL/E-EVAL",
        "category": None,  # 按学科映射，见 EVAL_MAP
    },
    "devops": {
        "platform": "DevOps-Eval",
        "url": "https://github.com/codefuse-ai/CodeFuse-DevOps-Eval",
        "category": None,  # 按 DevOps 阶段映射，见 DEVOPS_MAP
    },
    "secbench": {
        "platform": "SecBench",
        "url": "https://huggingface.co/datasets/RISys-Lab/Benchmarks_CyberSec_SecBench",
        "category": None,  # 按安全子领域 domain 映射
    },
    # 以下 4 个都是软考（计算机技术与软件专业技术资格考试）题库，
    # 统一用 platform=Ruankao，靠 md5(题干) 跨仓库自动去重；
    # 二级分类用具体科目名区分。
    "ruankao": {
        "platform": "Ruankao",
        "url": "https://github.com/qingningmneg/-1",
        "category": None,  # 按科目映射，见 RUANKAO_MAP
    },
    "xiezhi": {
        "platform": "Xiezhi",
        "url": "https://github.com/MikeGu721/XiezhiBenchmark",
        "category": None,  # 按 labels 学科标签映射，见 _xiezhi_cat
    },
    # 菜鸟工具(jyshare.com，RUNOOB 同系的在线测验站) 23 个编程科目共 218 套测验，
    # 数据由 spider/fetch_jyshare.py 抓成 ~/qdata/jyshare.jsonl 后由本脚本导入。
    "jyshare": {
        "platform": "Jyshare",
        "url": "https://www.jyshare.com/quiz/",
        "category": None,  # 按科目映射，见 JYSHARE_MAP
    },
    # 408 计算机学科专业基础综合（王道 2027 教材 OCR 题库），数据结构/计组/计网/操作系统四门，
    # 99.9% 自带完整解析，是库里解析质量最高的一批计算机题。
    "cs408": {
        "platform": "CS408",
        "url": "https://github.com/zsc5725216-hub/408-quiz",
        "category": None,  # 按科目映射，见 CS408_MAP
    },
    # NCRE 全国计算机等级考试一级 MS Office 选择题，题库内嵌在单页 HTML 的 QUESTIONS 数组里
    "ncre": {
        "platform": "NCRE",
        "url": "https://github.com/oyxning/computer-rank-exam-treasure",
        "category": ("计算机/IT", "计算机基础"),
    },
    # RUNOOB 菜鸟教程主站自带测验（22 套 / 436 题），由 fetch_runoob.py 抓取。
    # 与姊妹站 jyshare 的 2,089 题零重复（已用 question_hash 全量比对确认）。
    "runoob": {
        "platform": "RUNOOB",
        "url": "https://www.runoob.com/quiz/",
        "category": None,  # 按 slug 映射，见 RUNOOB_MAP
    },
    # 小黑课堂（金山 xhkt.ijinshan.com）在线练习题库，静态 JS 文件，由 fetch_xiaohei.py 抓取。
    # 16 题组 × 5 题 = 80 题，全四选一、100% 带解析、含难度分级，站点自编并审核。
    "xiaohei": {
        "platform": "XiaoHei",
        "url": "https://xhkt.ijinshan.com/practice/",
        "category": None,  # 按 topic 映射，见 XIAOHEI_MAP
    },
}

# 408 四门课 -> (一级, 二级)，二级复用 C-Eval 已建的同名分类
CS408_MAP = {
    "数据结构": ("计算机/IT", "数据结构"),
    "计组": ("计算机/IT", "计算机组成原理"),
    "计网": ("计算机/IT", "计算机网络"),
    "操作系统": ("计算机/IT", "操作系统"),
}

# 菜鸟工具测验科目 -> (一级, 二级)，全部归入 计算机/IT
JYSHARE_MAP = {
    "C": ("计算机/IT", "C语言"),
    "C++": ("计算机/IT", "C++"),
    "C#": ("计算机/IT", "C#"),
    "Java": ("计算机/IT", "Java"),
    "Python": ("计算机/IT", "Python"),
    "JavaScript": ("计算机/IT", "JavaScript"),
    "TypeScript": ("计算机/IT", "TypeScript"),
    "Go": ("计算机/IT", "Go语言"),
    "PHP": ("计算机/IT", "PHP"),
    "Node.js": ("计算机/IT", "Node.js"),
    "HTML": ("计算机/IT", "HTML"),
    "HTML5": ("计算机/IT", "HTML"),
    "CSS": ("计算机/IT", "CSS"),
    "jQuery": ("计算机/IT", "jQuery"),
    "Vue3": ("计算机/IT", "Vue3"),
    "MySQL": ("计算机/IT", "MySQL"),
    "SQL": ("计算机/IT", "SQL"),
    "Linux": ("计算机/IT", "Linux"),
    "Git": ("计算机/IT", "Git"),
    "Markdown": ("计算机/IT", "Markdown"),
    "正则表达式": ("计算机/IT", "正则表达式"),
}

# RUNOOB 测验 slug -> (一级, 二级)。复用 JYSHARE_MAP 已建的二级分类，避免同义分类重复。
# numpy / pandas 归 Python（都是 Python 库，单独建分类只有 20 题太碎）；xhtml 归 HTML。
RUNOOB_MAP = {
    "asp": ("计算机/IT", "ASP"),
    "cpp": ("计算机/IT", "C++"),
    "csharp": ("计算机/IT", "C#"),
    "css": ("计算机/IT", "CSS"),
    "css3": ("计算机/IT", "CSS"),
    "git": ("计算机/IT", "Git"),
    "go": ("计算机/IT", "Go语言"),
    "html": ("计算机/IT", "HTML"),
    "html5": ("计算机/IT", "HTML"),
    "xhtml": ("计算机/IT", "HTML"),
    "java": ("计算机/IT", "Java"),
    "javascript": ("计算机/IT", "JavaScript"),
    "jquery": ("计算机/IT", "jQuery"),
    "linux": ("计算机/IT", "Linux"),
    "numpy": ("计算机/IT", "Python"),
    "pandas": ("计算机/IT", "Python"),
    "python": ("计算机/IT", "Python"),
    "php": ("计算机/IT", "PHP"),
    "sql": ("计算机/IT", "SQL"),
    "ts": ("计算机/IT", "TypeScript"),
    "vue3": ("计算机/IT", "Vue3"),
    "xml": ("计算机/IT", "XML"),
}

# 小黑课堂 topic -> (一级, 二级)
# 办公软件 / 算法 / 数据库 是新建二级分类（原库没有），其余复用已有分类
XIAOHEI_MAP = {
    # L1 计算机基础
    "计算机系统基础": ("计算机/IT", "计算机基础"),
    "数据与编码": ("计算机/IT", "计算机基础"),
    "网络与互联网": ("计算机/IT", "计算机基础"),
    "信息安全与文件管理": ("计算机/IT", "计算机基础"),
    # L1/L2 办公软件（MS Office 计算机二级考点）
    "Word基础编辑": ("计算机/IT", "办公软件"),
    "Excel基础操作": ("计算机/IT", "办公软件"),
    "PowerPoint基础": ("计算机/IT", "办公软件"),
    "Office通用操作": ("计算机/IT", "办公软件"),
    "Word高级应用": ("计算机/IT", "办公软件"),
    "Excel函数与引用": ("计算机/IT", "办公软件"),
    "Excel数据分析": ("计算机/IT", "办公软件"),
    "PowerPoint设计与放映": ("计算机/IT", "办公软件"),
    # L2 公共基础知识
    "数据结构基础": ("计算机/IT", "数据结构"),
    "算法基础": ("计算机/IT", "算法"),
    "程序设计基础": ("计算机/IT", "大学编程"),
    "数据库基础": ("计算机/IT", "数据库"),
}

# 软考科目 -> (一级, 二级)
RUANKAO_MAP = {
    "系统集成项目管理工程师": ("计算机/IT", "系统集成项目管理工程师"),
    "信息系统项目管理师": ("计算机/IT", "信息系统项目管理师"),
    "系统架构设计师": ("计算机/IT", "系统架构设计师"),
    "系统分析师": ("计算机/IT", "系统分析师"),
    "系统规划与管理师": ("计算机/IT", "系统规划与管理师"),
    "网络规划设计师": ("计算机/IT", "网络规划设计师"),
    "信息安全工程师": ("计算机/IT", "信息安全工程师"),
    "软件设计师": ("计算机/IT", "软件设计师"),
}

# DevOps-Eval 的 8 个阶段 -> (一级, 二级)
DEVOPS_MAP = {
    "PLAN": ("计算机/IT", "需求与规划"),
    "CODE": ("计算机/IT", "代码开发"),
    "BUILD": ("计算机/IT", "构建管理"),
    "TEST": ("计算机/IT", "测试质量"),
    "RELEASE": ("计算机/IT", "发布管理"),
    "DEPLOY": ("计算机/IT", "部署交付"),
    "OPERATE": ("计算机/IT", "运维管理"),
    "MONITOR": ("计算机/IT", "监控观测"),
}

# COIG subject -> (一级, 二级)
COIG_MAP = {
    "英语": ("考研/升学", "高中英语"),
    "语文": ("考研/升学", "高中语文"),
    "生物": ("考研/升学", "高中生物"),
    "政治": ("考研/升学", "高中政治"),
    "历史": ("考研/升学", "高中历史"),
    "地理": ("考研/升学", "高中地理"),
}

# Global-MMLU 学科 -> (一级, 二级)
GMMLU_MAP = {
    "abstract_algebra": ("考研/升学", "抽象代数"),
    "anatomy": ("考研/升学", "解剖学"),
    "astronomy": ("考研/升学", "天文学"),
    "business_ethics": ("考证", "商业伦理"),
    "clinical_knowledge": ("考证", "临床医学"),
    "college_biology": ("考研/升学", "大学生物"),
    "college_chemistry": ("考研/升学", "大学化学"),
    "college_computer_science": ("计算机/IT", "计算机科学"),
    "college_mathematics": ("考研/升学", "大学数学"),
    "college_medicine": ("考证", "医学综合"),
    "college_physics": ("考研/升学", "大学物理"),
    "computer_security": ("计算机/IT", "信息安全"),
    "conceptual_physics": ("考研/升学", "物理概念"),
    "econometrics": ("考研/升学", "计量经济学"),
    "electrical_engineering": ("考研/升学", "电气工程"),
    "elementary_mathematics": ("考研/升学", "初等数学"),
    "formal_logic": ("考研/升学", "形式逻辑"),
    "global_facts": ("其他", "世界常识"),
    "high_school_biology": ("考研/升学", "高中生物"),
    "high_school_chemistry": ("考研/升学", "高中化学"),
    "high_school_computer_science": ("计算机/IT", "高中信息技术"),
    "high_school_european_history": ("考研/升学", "欧洲史"),
    "high_school_geography": ("考研/升学", "高中地理"),
    "high_school_government_and_politics": ("考研/升学", "高中政治"),
    "high_school_macroeconomics": ("考研/升学", "宏观经济学"),
    "high_school_mathematics": ("考研/升学", "高中数学"),
    "high_school_microeconomics": ("考研/升学", "微观经济学"),
    "high_school_physics": ("考研/升学", "高中物理"),
    "high_school_psychology": ("考研/升学", "心理学"),
    "high_school_statistics": ("考研/升学", "统计学"),
    "high_school_us_history": ("考研/升学", "美国史"),
    "high_school_world_history": ("考研/升学", "世界史"),
    "human_aging": ("其他", "老年学"),
    "human_sexuality": ("其他", "性教育学"),
    "international_law": ("考证", "国际法"),
    "jurisprudence": ("考证", "法理学"),
    "logical_fallacies": ("考研/升学", "逻辑学"),
    "machine_learning": ("计算机/IT", "机器学习"),
    "management": ("考研/升学", "管理学"),
    "marketing": ("考研/升学", "市场营销"),
    "medical_genetics": ("考证", "医学遗传学"),
    "miscellaneous": ("其他", "综合常识"),
    "moral_disputes": ("其他", "伦理学"),
    "moral_scenarios": ("其他", "伦理学"),
    "nutrition": ("其他", "营养学"),
    "philosophy": ("考研/升学", "哲学"),
    "prehistory": ("考研/升学", "史前史"),
    "professional_accounting": ("考证", "注册会计师"),
    "professional_law": ("考证", "法律职业资格"),
    "professional_medicine": ("考证", "医师资格"),
    "professional_psychology": ("考证", "心理学资格"),
    "public_relations": ("考研/升学", "公共关系"),
    "security_studies": ("其他", "安全研究"),
    "sociology": ("考研/升学", "社会学"),
    "us_foreign_policy": ("其他", "国际关系"),
    "virology": ("考证", "病毒学"),
    "world_religions": ("其他", "宗教学"),
}

# FinEval 学科（文件名） -> (一级, 二级)
FINEVAL_MAP = {
    "accounting": ("考证", "会计学"),
    "advanced_financial_accounting": ("考证", "高级财务会计"),
    "auditing": ("考证", "审计学"),
    "banking_practitioner_qualification_certificate": ("考证", "银行从业资格"),
    "central_banking": ("考研/升学", "中央银行学"),
    "certified_management_accountant": ("考证", "管理会计师"),
    "certified_practising_accountant": ("考证", "注册会计师"),
    "china_actuary": ("考证", "精算师"),
    "commercial_bank_finance": ("考研/升学", "商业银行金融"),
    "corporate_finance": ("考研/升学", "公司金融"),
    "corporate_strategy_and_risk_management": ("考证", "公司战略与风险管理"),
    "cost_accounting": ("考证", "成本会计"),
    "econometrics": ("考研/升学", "计量经济学"),
    "economic_law": ("考证", "经济法"),
    "finance": ("考研/升学", "金融学"),
    "financial_engineering": ("考研/升学", "金融工程"),
    "financial_management": ("考证", "财务管理"),
    "financial_markets": ("考研/升学", "金融市场"),
    "fund_qualification_certificate": ("考证", "基金从业资格"),
    "futures_practitioner_qualification_certificate": ("考证", "期货从业资格"),
    "insurance": ("考证", "保险从业资格"),
    "intermediate_financial_accounting": ("考证", "中级财务会计"),
    "international_economics": ("考研/升学", "国际经济学"),
    "international_finance": ("考研/升学", "国际金融"),
    "investments": ("考研/升学", "投资学"),
    "macroeconomics": ("考研/升学", "宏观经济学"),
    "management_accounting": ("考证", "管理会计"),
    "microeconomics": ("考研/升学", "微观经济学"),
    "monetary_finance": ("考研/升学", "货币金融学"),
    "political_economy": ("考研/升学", "政治经济学"),
    "public_finance": ("考研/升学", "财政学"),
    "securities_practitioner_qualification_certificate": ("考证", "证券从业资格"),
    "statistics": ("考研/升学", "统计学"),
    "tax_law": ("考证", "税法"),
}

# E-EVAL 学科（Primary/Middle/High School_XXX）-> (一级, 二级)
SCHOOL_CN = {
    "Primary_School": "小学",
    "Middle_School": "初中",
    "High_School": "高中",
}
SUBJECT_CN = {
    "Chinese": "语文", "English": "英语", "Mathematics": "数学",
    "Physics": "物理", "Chemistry": "化学", "Biology": "生物",
    "Politics": "政治", "History": "历史", "Geography": "地理",
    "Science": "科学", "Ethics": "思想品德",
}

# AGIEval 科目文件名 -> (一级, 二级)
# 注：logiqa-zh 取自中国国家公务员考试行测的逻辑判断，因此归入考公
AGIEVAL_MAP = {
    "gaokao-biology": ("考研/升学", "高中生物"),
    "gaokao-chemistry": ("考研/升学", "高中化学"),
    "gaokao-chinese": ("考研/升学", "高中语文"),
    "gaokao-english": ("考研/升学", "高中英语"),
    "gaokao-geography": ("考研/升学", "高中地理"),
    "gaokao-history": ("考研/升学", "高中历史"),
    "gaokao-mathqa": ("考研/升学", "高中数学"),
    "gaokao-physics": ("考研/升学", "高中物理"),
    "jec-qa-kd": ("考证", "法律职业资格"),
    "jec-qa-ca": ("考证", "法律职业资格"),
    "logiqa-zh": ("考公", "公务员"),
}

# FinanceIQ 科目 -> (一级, 二级)
FINANCEIQ_MAP = {
    "注册会计师（CPA）": ("考证", "注册会计师"),
    "税务师": ("考证", "税务师"),
    "精算师-金融数学": ("考证", "精算师"),
    "经济师": ("考证", "经济师"),
    "证券从业资格": ("考证", "证券从业资格"),
    "基金从业资格": ("考证", "基金从业资格"),
    "期货从业资格": ("考证", "期货从业资格"),
    "银行从业资格": ("考证", "银行从业资格"),
    "理财规划师": ("考证", "理财规划师"),
    "保险从业资格CICE": ("考证", "保险从业资格"),
}


def _eeval_cat(subject):
    """E-EVAL 学科名（如 High_School_Biology）-> (一级, 二级)"""
    parts = subject.split("_", 2)
    if len(parts) < 3:
        return ("考研/升学", subject)
    school, sub = parts[0] + "_" + parts[1], parts[2]
    cn_school = SCHOOL_CN.get(school, "")
    cn_sub = SUBJECT_CN.get(sub, sub)
    return ("考研/升学", cn_school + cn_sub if cn_school else cn_sub)


def md5(s):
    return hashlib.md5(s.encode("utf-8")).hexdigest()


def psql(sql, fetch=False):
    cmd = ["sudo", "docker", "exec", "-i", CONTAINER, "psql", "-U", "postgres", "-d", DB,
           "-v", "ON_ERROR_STOP=1", "-tAc", sql]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print("SQL 失败:\n" + r.stderr, file=sys.stderr)
        sys.exit(1)
    return r.stdout.strip() if fetch else None


def _int(s):
    """psql 输出可能夹带命令标签（如 '138\\nINSERT 0 1'），提取其中的整数。"""
    m = re.search(r"-?\d+", s or "")
    return int(m.group()) if m else None


def ensure_category(top_name, sub_name, cache):
    """确保一级/二级分类存在，返回二级分类 id。"""
    key = (top_name, sub_name)
    if key in cache:
        return cache[key]
    top_id = psql("SELECT id FROM categories WHERE name = '%s' AND parent_id IS NULL"
                  % top_name.replace("'", "''"), fetch=True)
    if not top_id:
        top_id = psql("INSERT INTO categories (name) VALUES ('%s') RETURNING id"
                      % top_name.replace("'", "''"), fetch=True)
    sub_id = psql("SELECT id FROM categories WHERE name = '%s' AND parent_id = %s"
                  % (sub_name.replace("'", "''"), top_id), fetch=True)
    if not sub_id:
        sub_id = psql("INSERT INTO categories (name, parent_id) VALUES ('%s', %s) RETURNING id"
                      % (sub_name.replace("'", "''"), top_id), fetch=True)
    cache[key] = _int(sub_id)
    return _int(sub_id)


def read_cmexam():
    """CMExam: JSONL，字段 Question / Options[{key,value}] / Answer / Explanation"""
    out = []
    for f in ["cmexam_train.json", "cmexam_valid.json", "cmexam_test.json"]:
        p = os.path.join(DATA_DIR, f)
        if not os.path.exists(p):
            continue
        txt = open(p, encoding="utf-8").read()
        if txt.lstrip().startswith("["):
            rows = json.loads(txt)
        else:
            rows = []
            for line in txt.splitlines():
                if line.strip():
                    try:
                        rows.append(json.loads(line))
                    except Exception:
                        continue
        for r in rows:
            q = (r.get("Question") or "").strip()
            if not q:
                continue
            opts = {}
            for o in r.get("Options", []):
                k = str(o.get("key", "")).strip()
                v = str(o.get("value", "")).strip()
                if k:
                    opts[k] = v
            if len(opts) < 2:
                continue
            ans = "".join(sorted(set(str(r.get("Answer", "")).strip().upper())))
            if not ans or ans.strip("ABCDEFG") != "":
                continue
            out.append({
                "question": q,
                "options": opts,
                "answer": ans,
                "explanation": (r.get("Explanation") or "").strip(),
                "subject": None,
            })
    return out


def read_financeiq():
    """FinanceIQ: CSV，字段 Question / A / B / C / D / Answer"""
    out = []
    d = os.path.join(DATA_DIR, "financeiq")
    if not os.path.isdir(d):
        return out
    for fn in sorted(os.listdir(d)):
        if not fn.endswith(".csv"):
            continue
        # 文件名格式: {dev|test}_{科目}.csv
        subject = fn[:-4].split("_", 1)[1] if "_" in fn else None
        with open(os.path.join(d, fn), encoding="utf-8") as f:
            for r in csv.DictReader(f):
                q = (r.get("Question") or "").strip()
                if not q:
                    continue
                opts = {k: (r.get(k) or "").strip() for k in "ABCD" if (r.get(k) or "").strip()}
                ans = "".join(sorted(set((r.get("Answer") or "").strip().upper())))
                if len(opts) < 2 or not ans or ans.strip("ABCD") != "":
                    continue
                out.append({
                    "question": q,
                    "options": opts,
                    "answer": ans,
                    "explanation": "",
                    "subject": subject,
                })
    return out


def read_talscq():
    """TAL-SCQ5K CN：JSONL，problem / answer_option_list[[{aoVal,content}]] / answer_value"""
    out = []
    d = os.path.join(DATA_DIR, "talscq")
    for fn in ["cn_train.jsonl", "cn_test.jsonl"]:
        p = os.path.join(d, fn)
        if not os.path.exists(p):
            continue
        for line in open(p, encoding="utf-8"):
            if not line.strip():
                continue
            r = json.loads(line)
            q = (r.get("problem") or "").strip()
            if not q:
                continue
            opts = {}
            for grp in r.get("answer_option_list", []):
                if isinstance(grp, list):
                    items = grp
                else:
                    items = [grp]
                for it in items:
                    k = str(it.get("aoVal", "")).strip().upper()
                    v = str(it.get("content", "")).strip()
                    if k:
                        opts[k] = v
            if len(opts) < 2:
                continue
            ans = str(r.get("answer_value", "")).strip().upper()
            if not ans or ans.strip("ABCDEFG") != "":
                continue
            expl = r.get("answer_analysis") or []
            if isinstance(expl, list):
                expl = " ".join(str(x) for x in expl)
            out.append({"question": q, "options": opts, "answer": ans,
                        "explanation": str(expl).strip(), "subject": None})
    return out


def read_aclue():
    """ACLUE：zip 包内 dev/ 与 test/ 下的多个 CSV（Question,A,B,C,D,Answer）"""
    out = []
    zp = os.path.join(DATA_DIR, "aclue/aclue.zip")
    if not os.path.exists(zp):
        return out
    z = zipfile.ZipFile(zp)
    for n in z.namelist():
        if n.startswith("__MACOSX") or not n.endswith(".csv"):
            continue
        txt = z.read(n).decode("utf-8", errors="replace")
        for r in csv.DictReader(io.StringIO(txt)):
            q = (r.get("Question") or "").strip()
            if not q:
                continue
            opts = {k: (r.get(k) or "").strip() for k in "ABCD" if (r.get(k) or "").strip()}
            ans = "".join(sorted(set((r.get("Answer") or "").strip().upper())))
            if len(opts) < 2 or not ans or ans.strip("ABCD") != "":
                continue
            out.append({"question": q, "options": opts, "answer": ans,
                        "explanation": "", "subject": None})
    return out


def read_agieval():
    """AGIEval：parquet（query / choices / gold）。

    query 形如「问题：xxx 选项：(A).. (B)..\\n答案：从A到D, 我们应选择」
    需要把题干从 query 里剥出来，gold 是 0 基选项下标。
    """
    import pandas as pd
    out = []
    d = os.path.join(DATA_DIR, "agieval")
    if not os.path.isdir(d):
        return out
    for fn in sorted(os.listdir(d)):
        if not fn.endswith(".parquet"):
            continue
        subject = fn[:-len(".parquet")]
        try:
            df = pd.read_parquet(os.path.join(d, fn))
        except Exception:
            continue
        for _, row in df.iterrows():
            query = str(row.get("query", "") or "")
            # 剥掉尾部「\n答案：...」
            q = re.split(r"\n?答案[：:]", query)[0]
            # 剥掉「 选项：(A)...」部分
            q = re.split(r"\s*选项[：:]", q)[0]
            q = re.sub(r"^问题[：:]\s*", "", q).strip()
            if not q:
                continue
            choices = row.get("choices")
            opts = {}
            for c in (list(choices) if choices is not None else []):
                c = str(c).strip()
                m = re.match(r"^\(?([A-Z])\)?\s*(.*)$", c, re.S)
                if m and m.group(2).strip():
                    opts[m.group(1)] = m.group(2).strip()
            if len(opts) < 2:
                continue
            gold = row.get("gold")
            idxs = list(gold) if gold is not None else []
            letters = []
            for i in idxs:
                try:
                    letters.append(chr(ord("A") + int(i)))
                except Exception:
                    pass
            if not letters:
                continue
            ans = "".join(sorted(set(letters)))
            if ans.strip("ABCDEFG") != "":
                continue
            out.append({"question": q, "options": opts, "answer": ans,
                        "explanation": "", "subject": subject})
    return out


def read_tcm():
    """中医类：执业药师（中药学）jsonl(A-E) + CMMLU 中医 json(A-D, label)"""
    out = []
    d = os.path.join(DATA_DIR, "tcm")
    if not os.path.isdir(d):
        return out
    p1 = os.path.join(d, "pharmacist_tcm.jsonl")
    if os.path.exists(p1):
        for line in open(p1, encoding="utf-8"):
            if not line.strip():
                continue
            r = json.loads(line)
            q = (r.get("question") or "").strip()
            if not q:
                continue
            opts = {k: str(r.get(k, "")).strip() for k in "ABCDE" if str(r.get(k, "")).strip()}
            ans = "".join(sorted(set(str(r.get("answer", "")).strip().upper())))
            if len(opts) < 2 or not ans or ans.strip("ABCDE") != "":
                continue
            out.append({"question": q, "options": opts, "answer": ans,
                        "explanation": "", "subject": "药师资格"})
    p2 = os.path.join(d, "cmmlu_tcm.json")
    if os.path.exists(p2) and os.path.getsize(p2) > 0:
        try:
            data = json.load(open(p2, encoding="utf-8"))
        except Exception:
            data = []
        for r in data:
            q = (r.get("question") or "").strip()
            if not q:
                continue
            opts = {k: str(r.get(k, "")).strip() for k in "ABCD" if str(r.get(k, "")).strip()}
            ans = "".join(sorted(set(str(r.get("label", "")).strip().upper())))
            if len(opts) < 2 or not ans or ans.strip("ABCD") != "":
                continue
            out.append({"question": q, "options": opts, "answer": ans,
                        "explanation": "", "subject": "中医"})
    return out


def read_coig():
    """COIG exam_instructions.jsonl：高考题（史地政/语文/英语/生物）。

    字段：textbox_q_instruction / textbox_q_context / textbox_question /
          textbox_answer / textbox_answer_analysis / subject
    难点：题干和 4 个选项混排在同一个 textbox_question 里（如 "xxx? A.a B.b C.c D.d"），
         需要靠 A./B./C./D. 前缀切分。实测切分成功率 94.9%。
    另外：只保留答案是单个 A-D 字母的（另有 2.1 万条是开放问答题，无法作为选择题入库）。
    """
    p = os.path.join(DATA_DIR, "coig/exam_instructions.jsonl")
    if not os.path.exists(p):
        return []
    # 选项前缀：A./A、/A．/A)/A）/A:/A：，且前一字符不能是字母或数字
    prefix = re.compile(r"(?<![A-Za-z0-9])([ABCD])\s*[.、．\)）:：]\s*")
    out = []
    for line in open(p, encoding="utf-8"):
        if not line.strip():
            continue
        try:
            r = json.loads(line)
        except Exception:
            continue
        ans = (r.get("textbox_answer") or "").strip().upper()
        if not re.fullmatch(r"[A-D]", ans):
            continue  # 跳过开放题与多选题
        text = r.get("textbox_question") or ""
        cands = {"A": [], "B": [], "C": [], "D": []}
        for m in prefix.finditer(text):
            cands[m.group(1)].append((m.start(), m.end()))
        if not all(cands[k] for k in "ABCD"):
            continue
        try:
            d_pos = max(i for i, _ in cands["D"])
            c_pos = max(i for i, _ in cands["C"] if i < d_pos)
            b_pos = max(i for i, _ in cands["B"] if i < c_pos)
            a_pos = max(i for i, _ in cands["A"] if i < b_pos)
        except ValueError:
            continue
        a_end = next(e for i, e in cands["A"] if i == a_pos)
        b_end = next(e for i, e in cands["B"] if i == b_pos)
        c_end = next(e for i, e in cands["C"] if i == c_pos)
        d_end = next(e for i, e in cands["D"] if i == d_pos)
        stem = text[:a_pos].strip()
        opts = {
            "A": text[a_end:b_pos].strip(),
            "B": text[b_end:c_pos].strip(),
            "C": text[c_end:d_pos].strip(),
            "D": text[d_end:].strip(),
        }
        if not stem or any(not v for v in opts.values()):
            continue
        if max(len(v) for v in opts.values()) > 400:
            continue
        # 题干里带上阅读理解的上下文，否则题目不完整
        ctx = (r.get("textbox_q_context") or "").strip()
        if ctx and len(ctx) < 3000:
            stem = ctx + "\n" + stem
        out.append({"question": stem, "options": opts, "answer": ans,
                    "explanation": (r.get("textbox_answer_analysis") or "").strip(),
                    "subject": (r.get("subject") or "").strip()})
    return out


def read_gmmlu():
    """Global-MMLU 中文版（zh）：parquet，question/option_a..d/answer/subject"""
    import pandas as pd
    out = []
    d = os.path.join(DATA_DIR, "gmmlu")
    if not os.path.isdir(d):
        return out
    for fn in sorted(os.listdir(d)):
        if not fn.endswith(".parquet"):
            continue
        try:
            df = pd.read_parquet(os.path.join(d, fn))
        except Exception:
            continue
        for _, row in df.iterrows():
            q = str(row.get("question", "") or "").strip()
            if not q:
                continue
            opts = {}
            for k, col in [("A", "option_a"), ("B", "option_b"),
                           ("C", "option_c"), ("D", "option_d")]:
                v = str(row.get(col, "") or "").strip()
                if v:
                    opts[k] = v
            if len(opts) < 2:
                continue
            ans = str(row.get("answer", "") or "").strip().upper()
            if not ans or ans.strip("ABCD") != "":
                continue
            out.append({"question": q, "options": opts, "answer": ans,
                        "explanation": "", "subject": str(row.get("subject", "") or "")})
    return out


def _open_text(path):
    """按 utf-8-sig -> gbk -> latin-1 依次尝试，兼容各数据集混用的中文编码。"""
    for enc in ("utf-8-sig", "gbk", "gb18030", "latin-1"):
        try:
            f = open(path, encoding=enc)
            f.read(4096)
            f.seek(0)
            return f
        except UnicodeDecodeError:
            try:
                f.close()
            except Exception:
                pass
            continue
    return open(path, encoding="utf-8", errors="replace")


def read_fineval():
    """FinEval：dev/val 下 34 个金融学科 CSV（id/question/A/B/C/D/answer）。

    注意：test 分集（3340 题）不带 answer，无法入库，只取 dev + val。
    CSV 带 UTF-8 BOM，需用 utf-8-sig 读取。
    """
    out = []
    for split in ["dev", "val"]:
        d = os.path.join(DATA_DIR, "fineval", split)
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            if not fn.endswith(".csv"):
                continue
            subject = re.sub(r"_?(dev|val|test)\.csv$", "", fn)
            with _open_text(os.path.join(d, fn)) as f:
                for r in csv.DictReader(f):
                    q = (r.get("question") or "").strip()
                    if not q:
                        continue
                    opts = {k: (r.get(k) or "").strip() for k in "ABCD"
                            if (r.get(k) or "").strip()}
                    ans = (r.get("answer") or "").strip().upper()
                    if len(opts) < 2 or not ans or ans.strip("ABCD") != "":
                        continue
                    out.append({"question": q, "options": opts, "answer": ans,
                                "explanation": "", "subject": subject})
    return out


def read_eeval():
    """E-EVAL：dev/val 下 23 个 K12 学科 CSV（id/question/A/B/C/D/answer）。

    注意1：test 分集（3813 题）不带 answer，只取 dev + val。
    注意2：原始数据经 Excel 中转过，少量选项被自动转成日期（如 1900-01-01 01:01:17），
           无法还原原值，直接丢弃这些题（实测仅 1 条）。
    """
    out = []
    for split in ["dev", "val"]:
        d = os.path.join(DATA_DIR, "eeval/E-EVAL", split)
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            if not fn.endswith(".csv"):
                continue
            subject = re.sub(r"_?(dev|val|test)\.csv$", "", fn)
            with _open_text(os.path.join(d, fn)) as f:
                for r in csv.DictReader(f):
                    q = (r.get("question") or "").strip()
                    if not q:
                        continue
                    opts = {k: (r.get(k) or "").strip() for k in "ABCD"
                            if (r.get(k) or "").strip()}
                    ans = (r.get("answer") or "").strip().upper()
                    if len(opts) < 2 or not ans or ans.strip("ABCD") != "":
                        continue
                    if any(str(v).startswith("1900-") for v in opts.values()):
                        continue  # Excel 日期污染，丢弃
                    out.append({"question": q, "options": opts, "answer": ans,
                                "explanation": "", "subject": subject})
    return out


def read_devops():
    """DevOps-Eval 中文集：zip 内 devopseval-exam/zh/{dev,test}/{阶段}/{子类}.csv

    字段：id / question / A / B / C / D / answer / explanation / type
    全部自带答案，且**带解析**（explanation）。阶段即 DevOps 生命周期八段。
    """
    out = []
    d = os.path.join(DATA_DIR, "devops/devopseval-exam/zh")
    if not os.path.isdir(d):
        return out
    for split in ["dev", "test", "val"]:
        sd = os.path.join(d, split)
        if not os.path.isdir(sd):
            continue
        for stage in sorted(os.listdir(sd)):
            st_dir = os.path.join(sd, stage)
            if not os.path.isdir(st_dir):
                continue
            for fn in sorted(os.listdir(st_dir)):
                if not fn.endswith(".csv"):
                    continue
                with _open_text(os.path.join(st_dir, fn)) as f:
                    for r in csv.DictReader(f):
                        q = (r.get("question") or "").strip()
                        if not q:
                            continue
                        opts = {k: (r.get(k) or "").strip() for k in "ABCD"
                                if (r.get(k) or "").strip()}
                        ans = (r.get("answer") or "").strip().upper()
                        if len(opts) < 2 or not ans or ans.strip("ABCD") != "":
                            continue
                        out.append({"question": q, "options": opts, "answer": ans,
                                    "explanation": (r.get("explanation") or "").strip(),
                                    "subject": stage.upper()})
    return out


def read_ncre():
    """NCRE 全国计算机等级考试 一级 MS Office 选择题（514 题，450 带解析）

    来源：GitHub oyxning/computer-rank-exam-treasure 的 index.html，
    题库以 `const QUESTIONS = [{id, no, stem, options, answer, explain}]` 内嵌在页面里。
    """
    out = []
    p = os.path.join(DATA_DIR, "ncre", "index.html")
    if not os.path.isfile(p):
        return out
    with _open_text(p) as fh:
        s = fh.read()
    m = re.search(r'const\s+QUESTIONS\s*=\s*(\[[\s\S]*?\])\s*;', s)
    if not m:
        return out
    try:
        data = json.loads(m.group(1))
    except Exception:
        return out
    for q in data:
        stem = str(q.get("stem") or "").strip()
        opts_list = [str(x).strip() for x in (q.get("options") or [])]
        ans = str(q.get("answer") or "").strip().upper()
        expl = str(q.get("explain") or "").strip()
        if not stem or ans not in ("A", "B", "C", "D"):
            continue
        if len(opts_list) != 4 or len(set(opts_list)) < 4:
            continue
        out.append({
            "question": stem,
            "options": {chr(65 + i): v for i, v in enumerate(opts_list)},
            "answer": ans,
            "explanation": expl,
            "subject": "NCRE一级Office",
            "url": "",
        })
    return out


def read_cs408():
    """408 计算机学科专业基础综合（王道 2027 教材 OCR 题库）

    来源：GitHub zsc5725216-hub/408-quiz 的 data/{数据结构,计组,计网,操作系统}.json
    结构：{name, sections:{"1.1":{title, qs:[[序号, 答案, 题干, 解析, [选项...], 图片]]}}}
    - 99.9% 带完整解析（考研级讲解），是库里解析质量最高的计算机题
    - 选项是 OCR 文本，部分题多个选项被挤进同一元素（"A. xx B. yy"）→ 按 A./B./C./D. 标记重切
    - 图片字段不可靠（大量是排版截图而非题目配图），只按题干里的「如图/下图」判断是否剔除
    - 最终只保留：能切出 A-D 四个选项、答案在 A-D 内、题干不依赖配图
    """
    out = []
    tok = re.compile(r'(?<![A-Za-z0-9])([A-F])\s*[.、．\)）]\s*')
    img_pat = re.compile(r'(如图|下图|上图|图中|图示|图所示|见下图|见上图)')
    for subj in ("数据结构", "计组", "计网", "操作系统"):
        p = os.path.join(DATA_DIR, "cs408", subj + ".json")
        if not os.path.isfile(p):
            continue
        with _open_text(p) as fh:
            d = json.load(fh)
        for _sk, sec in (d.get("sections") or {}).items():
            for q in (sec.get("qs") or []):
                if len(q) < 5:
                    continue
                ans = str(q[1] or "").strip().upper()
                stem = str(q[2] or "").strip()
                expl = str(q[3] or "").strip()
                raw = q[4] or []
                if not stem or ans not in ("A", "B", "C", "D"):
                    continue
                if img_pat.search(stem):
                    continue
                joined = " ".join(str(x).strip() for x in raw if str(x).strip())
                marks = [(m.start(), m.end(), m.group(1)) for m in tok.finditer(joined)]
                if not marks:
                    continue
                opts = {}
                for i, (s, e, L) in enumerate(marks):
                    end = marks[i + 1][0] if i + 1 < len(marks) else len(joined)
                    t = joined[e:end].strip(" ，,;；")
                    if L not in opts and t:
                        opts[L] = t
                if len(opts) != 4 or any(k not in opts for k in "ABCD"):
                    continue
                if len(set(opts.values())) < 4:
                    continue
                out.append({
                    "question": stem,
                    "options": {k: opts[k] for k in "ABCD"},
                    "answer": ans,
                    "explanation": expl,
                    "subject": subj,
                    "url": "",
                })
    return out


def read_jyshare():
    """菜鸟工具 jyshare.com 的 Wp-Pro-Quiz 计算机测验（由 fetch_jyshare.py 抓取）

    JSONL 每行：subject / quiz_title / question / options(数组) / answer_index / source_url
    - 原始站点只给「回答正确/错误」，没有真正的解析 → explanation 留空
    - 选项数 2~4 不定：题库含大量「正确/错误」判断题，保留原样不强行补成四选一
    """
    out = []
    p = os.path.join(DATA_DIR, "jyshare.jsonl")
    if not os.path.isfile(p):
        return out
    with _open_text(p) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                r = json.loads(line)
            except Exception:
                continue
            q = (r.get("question") or "").strip()
            opts_list = [str(x).strip() for x in (r.get("options") or [])]
            opts_list = [x for x in opts_list if x]
            if not q or not (2 <= len(opts_list) <= 6):
                continue
            # 选项文本重复/为空的题丢弃
            if len(set(opts_list)) != len(opts_list):
                continue
            idx = r.get("answer_index")
            if not isinstance(idx, int) or not (0 <= idx < len(opts_list)):
                continue
            opts = {chr(65 + i): v for i, v in enumerate(opts_list)}
            out.append({
                "question": q,
                "options": opts,
                "answer": chr(65 + idx),
                "explanation": "",
                "subject": (r.get("subject") or "").strip(),
                "url": (r.get("source_url") or "").strip(),
            })
    return out


def read_runoob():
    """菜鸟教程主站 runoob.com/quiz 的 22 套测验（由 fetch_runoob.py 抓取）

    JSONL 每行：subject(slug) / title / question / options(数组) / answer_index
    - 源站没有解析，explanation 留空
    - ⚠️ 源站选项顺序没打乱、正确项永远排第一（整体 A 占比 62.4%），
      已在 fetch_runoob.py 里用 md5(题干) 固定种子洗牌，本函数直接读洗牌后的结果
    - 选项数 2~5 不定（少量判断题只有 2 个选项），保留原样
    """
    out = []
    p = os.path.join(DATA_DIR, "runoob.jsonl")
    if not os.path.isfile(p):
        return out
    with _open_text(p) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                r = json.loads(line)
            except Exception:
                continue
            q = (r.get("question") or "").strip()
            opts_list = [str(x).strip() for x in (r.get("options") or [])]
            opts_list = [x for x in opts_list if x]
            if not q or not (2 <= len(opts_list) <= 6):
                continue
            # 选项文本重复/为空的题丢弃
            if len(set(opts_list)) != len(opts_list):
                continue
            idx = r.get("answer_index")
            if not isinstance(idx, int) or not (0 <= idx < len(opts_list)):
                continue
            opts = {chr(65 + i): v for i, v in enumerate(opts_list)}
            out.append({
                "question": q,
                "options": opts,
                "answer": chr(65 + idx),
                "explanation": "",
                "subject": (r.get("subject") or "").strip(),
                "url": (r.get("url") or "").strip(),
            })
    return out


def read_xiaohei():
    """小黑课堂 xhkt.ijinshan.com 在线练习题库（由 fetch_xiaohei.py 抓取）

    JSONL 每行：level / subject / topic / prefix / difficulty / question / options / answer_index / explanation
    - 全四选一，100% 带解析（源站自编并审核，非 UGC），解析直接入库
    - ⚠️ 源站答案偏 A（80 题里 69 题在下标 0 = 86.3%），已在 fetch_xiaohei.py
      用 md5(题干) 固定种子洗牌，此处直接读洗牌后的结果
    """
    out = []
    p = os.path.join(DATA_DIR, "xiaohei.jsonl")
    if not os.path.isfile(p):
        return out
    with _open_text(p) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                r = json.loads(line)
            except Exception:
                continue
            q = (r.get("question") or "").strip()
            opts_list = [str(x).strip() for x in (r.get("options") or [])]
            opts_list = [x for x in opts_list if x]
            if not q or not (2 <= len(opts_list) <= 6):
                continue
            if len(set(opts_list)) != len(opts_list):
                continue
            idx = r.get("answer_index")
            if not isinstance(idx, int) or not (0 <= idx < len(opts_list)):
                continue
            opts = {chr(65 + i): v for i, v in enumerate(opts_list)}
            out.append({
                "question": q,
                "options": opts,
                "answer": chr(65 + idx),
                "explanation": (r.get("explanation") or "").strip(),
                "subject": (r.get("topic") or "").strip(),
                "difficulty": (r.get("difficulty") or "").strip(),
                "url": (r.get("url") or "").strip(),
            })
    return out


def read_secbench():
    """SecBench MCQs_Chinese：parquet，question / answers(选项数组) / label / domain"""
    import pandas as pd
    out = []
    d = os.path.join(DATA_DIR, "secbench")
    if not os.path.isdir(d):
        return out
    for fn in sorted(os.listdir(d)):
        if not fn.endswith(".parquet"):
            continue
        try:
            df = pd.read_parquet(os.path.join(d, fn))
        except Exception:
            continue
        for _, row in df.iterrows():
            q = str(row.get("question", "") or "").strip()
            if not q:
                continue
            raw = row.get("answers")
            # pandas 读出来可能是 ndarray，也可能是 numpy 打印风格的字符串
            items = []
            if isinstance(raw, (list, tuple)):
                items = [str(x) for x in raw]
            elif hasattr(raw, "tolist"):
                items = [str(x) for x in raw.tolist()]
            elif isinstance(raw, str):
                items = re.findall(r"'([^']*)'|\"([^\"]*)\"", raw)
                items = [a or b for a, b in items]
            items = [x.strip() for x in items if str(x).strip()]
            if len(items) < 2:
                continue
            opts = {chr(ord("A") + i): v for i, v in enumerate(items[:6])}
            ans = str(row.get("label", "") or "").strip().upper()
            if not ans or ans.strip("ABCDEF") != "" or ans not in opts:
                continue
            out.append({"question": q, "options": opts, "answer": ans,
                        "explanation": "",
                        "subject": str(row.get("domain", "") or "").strip()})
    return out


# ---------------------------------------------------------------------------
# 软考（Ruankao）系列：4 个 GitHub 仓库，格式各异，逐个适配
# ---------------------------------------------------------------------------

# 软考 Markdown 真题/模拟题的通用解析器。仓库 awesome-ruankao 里混用 4 种写法：
#   A) ### 第N题  + **选项**: + `- A. xxx`        + **答案**: C
#   B) ### 第N题  + **题目**: 题干 + `A．xxx`      + **答案**: C
#   C) **1.**题干 + 选项挤一行（A. x B. y C. z D. w）+ **答案：B**
#   D) 题目/答案分离：`综合知识_题目.md` 只有题干选项，`综合知识_答案.md` 只有答案解析
MD_RE_HEAD = re.compile(r'^#{2,4}\s*第\s*(\d+)\s*题\s*$')
MD_RE_HEAD2 = re.compile(r'^\s*\*\*(\d+)\.\*\*\s*(.*)$')          # **1.**题干
MD_RE_HEAD3 = re.compile(r'^\s*\*\*(\d+)\s*[-–]\s*(\d+)\.\*\*')   # **3-4.** 共用题干，跳过
MD_RE_OPT1 = re.compile(r'^\s*[-*]\s*([A-F])\s*[\.、．:：]\s*(.*)$')
MD_RE_OPT2 = re.compile(r'^\s*([A-F])\s*[\.、．]\s*(.*)$')
MD_RE_ANS1 = re.compile(r'^\s*\*\*答案\*\*\s*[:：]\s*(.*?)\s*\**\s*$')
MD_RE_ANS2 = re.compile(r'^\s*\*\*答案\s*[:：]\s*(.*?)\s*\*\*\s*$')
MD_RE_EXP1 = re.compile(r'^\s*\*\*解析\*\*\s*[:：]\s*(.*)$')
MD_RE_EXP2 = re.compile(r'^\s*\*\*解析\s*[:：]\*\*\s*(.*)$')
MD_RE_EXP3 = re.compile(r'^\s*解析\s*[:：]\s*(.*)$')
MD_RE_TPC1 = re.compile(r'^\s*\*\*考点\*\*\s*[:：]\s*(.*)$')
MD_RE_TPC2 = re.compile(r'^\s*\*\*考点\s*[:：]\*\*\s*(.*)$')
MD_RE_STEM = re.compile(r'^\s*\*\*题目\*\*\s*[:：]\s*(.*)$')
MD_RE_OPTLABEL = re.compile(r'^\s*\*\*选项\*\*\s*[:：]?\s*$')
MD_RE_INLINE = re.compile(r'(?<![A-Za-z0-9\u4e00-\u9fff])([A-D])\s*[\.、．]\s*')


def _md_clean_ans(s):
    """答案字段很脏：可能是 'A'，也可能是 '需人工核对（原答案：A）'、'未给出'。"""
    s = (s or "").strip().strip('*` ').upper()
    s = re.sub(r"\s+", "", s)
    if re.fullmatch(r"[A-F]{1,6}", s):
        return s
    m = re.search(r"原答案[：:]\s*([A-F])", s)     # 需人工核对（原答案：A）
    if m:
        return m.group(1)
    return ""


def _md_split_inline(s):
    """把 ' A. x B. y C. z D. w' 这种挤在一行的选项切开。

    做法与 COIG 同：从右往左依次取 D、C、B、A 的最右出现位置，保证 A<B<C<D 递增。
    """
    hits = [(m.group(1), m.start()) for m in MD_RE_INLINE.finditer(s)]
    pos = {}
    last = len(s) + 1
    for L in "DCBA":
        cand = [p for l, p in hits if l == L and p < last]
        if not cand:
            return None
        pos[L] = max(cand)
        last = pos[L]
    if not pos["A"] < pos["B"] < pos["C"] < pos["D"]:
        return None
    out = {}
    for L in "ABCD":
        end = pos[chr(ord(L) + 1)] if L != "D" else len(s)
        seg = re.sub(r"^[A-D]\s*[\.、．]\s*", "", s[pos[L]:end]).strip()
        out[L] = seg
    return s[:pos["A"]].strip(), out


def _md_parse(path):
    """解析单个 md，返回 {题号: {stem, options, answer, expl, topic}}"""
    lines = open(path, encoding="utf-8").read().split("\n")
    out, cur, state = {}, None, "head"
    for ln in lines:
        s = ln.strip()
        m = MD_RE_HEAD.match(ln)
        if m:
            cur = {"num": int(m.group(1)), "stem": [], "options": {},
                   "answer": "", "expl": "", "topic": ""}
            out[cur["num"]] = cur
            state = "stem"
            continue
        if MD_RE_HEAD3.match(ln):
            cur = None          # 3-4 共用题干，无法拆成单题，跳过
            continue
        m = MD_RE_HEAD2.match(ln)
        if m:
            cur = {"num": int(m.group(1)), "stem": [m.group(2).strip()], "options": {},
                   "answer": "", "expl": "", "topic": ""}
            out[cur["num"]] = cur
            state = "stem"
            continue
        if cur is None:
            continue
        if not s or s == "---":
            continue
        if MD_RE_OPTLABEL.match(s):
            state = "stem"
            continue
        m = MD_RE_ANS1.match(s) or MD_RE_ANS2.match(s)
        if m:
            cur["answer"] = _md_clean_ans(m.group(1))
            state = "ans"
            continue
        m = MD_RE_EXP1.match(s) or MD_RE_EXP2.match(s) or MD_RE_EXP3.match(s)
        if m:
            cur["expl"] = m.group(1).strip()
            state = "exp"
            continue
        m = MD_RE_TPC1.match(s) or MD_RE_TPC2.match(s)
        if m:
            cur["topic"] = m.group(1).strip()
            state = "topic"
            continue
        m = MD_RE_OPT1.match(s)
        if m and m.group(1) in set("ABCDEF"):
            cur["options"][m.group(1)] = m.group(2).strip()
            state = "opt"
            continue
        m = MD_RE_OPT2.match(s)
        if m and m.group(1) in set("ABCDEF") and len(s) > 1:
            cur["options"][m.group(1)] = m.group(2).strip()
            state = "opt"
            continue
        m = MD_RE_STEM.match(s)
        if m:
            cur["stem"].append(m.group(1).strip())
            state = "stem"
            continue
        if state == "stem" and not s.startswith("**") and not s.startswith("|"):
            if not cur["options"]:
                r = _md_split_inline(s)
                if r and all(v for v in r[1].values()):
                    head, opts = r
                    if head:
                        cur["stem"].append(head)
                    cur["options"] = opts
                    state = "opt"
                    continue
            cur["stem"].append(s)
        elif state == "exp":
            cur["expl"] += " " + s
    for c in out.values():
        c["stem"] = " ".join(x for x in c["stem"] if x).strip()
        c["expl"] = c["expl"].strip()
    return out


def _md_ok(d):
    return bool(d.get("stem")) and len(d.get("options", {})) >= 2 and bool(d.get("answer"))


def _md_emit(d, subject, out, extra_topic=False):
    if not _md_ok(d):
        return
    expl = d["expl"]
    if extra_topic and d["topic"]:
        expl = ("考点：" + d["topic"] + " " + expl).strip() if expl else "考点：" + d["topic"]
    out.append({"question": d["stem"], "options": d["options"],
                "answer": "".join(sorted(set(d["answer"]))),
                "explanation": expl, "subject": subject})


def read_ruankao_md():
    """awesome-ruankao：真题/模拟题 Markdown。

    真题：5 科目 × 历年「综合知识.md」（部分年份无答案，自动跳过）
    模拟题：题目与答案分离，需按题号配对
    注：网络规划设计师/系统规划与管理师的真题只有题干无答案，无法入库。
    """
    base = os.path.join(DATA_DIR, "ruankao/awesome/awesome-ruankao-main")
    out = []
    if not os.path.isdir(base):
        return out

    def subject_of(path, idx):
        parts = path.replace("\\", "/").split("/")
        return parts[idx] if len(parts) > abs(idx) else "软考其他"

    # 真题（题目+答案同文件）
    for f in sorted(glob.glob(os.path.join(base, "真题", "*", "*", "综合知识*.md"))):
        subj = subject_of(f, -3)
        for d in _md_parse(f).values():
            _md_emit(d, subj, out)
    # 模拟题（题目/答案分离，按题号合并）
    for qf in sorted(glob.glob(os.path.join(base, "模拟题", "**", "综合知识_题目.md"),
                              recursive=True)):
        af = qf.replace("_题目.md", "_答案.md")
        if not os.path.exists(af):
            continue
        subj = subject_of(qf, -4)
        qs = _md_parse(qf)
        for n, a in _md_parse(af).items():
            if n in qs and a["answer"]:
                qs[n]["answer"] = a["answer"]
                qs[n]["expl"] = a["expl"]
                qs[n]["topic"] = a["topic"]
        for d in qs.values():
            _md_emit(d, subj, out, extra_topic=True)
    return out


def read_ruankao_lemon():
    """Lemon_ExamSystem：cache/questions.json，系统集成项目管理工程师 1247 题。

    字段：stem / options[{letter,text}] / answer / explanation
    注意：answer 有 '未给出'、'需人工核对（原答案：A）' 等脏值，需清洗。
    """
    p = os.path.join(DATA_DIR, "ruankao/Lemon_ExamSystem-main/cache/questions.json")
    out = []
    if not os.path.exists(p):
        return out
    data = json.load(open(p, encoding="utf-8"))
    for q in data.get("questions", []):
        stem = (q.get("stem") or "").strip()
        if not stem:
            continue
        opts = {}
        for o in q.get("options", []):
            k, v = str(o.get("letter", "")).strip().upper(), str(o.get("text", "")).strip()
            if k and v:
                opts[k] = v
        if len(opts) < 2:
            continue
        ans = _md_clean_ans(q.get("answer"))
        if not ans:
            continue
        # explanation 是 AI 复核记录，含套话，只保留有信息量的部分
        expl = re.sub(r"\s*\*\s*", "\n", (q.get("explanation") or "")).strip()
        out.append({"question": stem, "options": opts,
                    "answer": "".join(sorted(set(ans))),
                    "explanation": expl,
                    "subject": "系统集成项目管理工程师"})
    return out


def read_ruankao_architect():
    """Zhang-986/ruankao-architect-practice：data/bank.json，系统架构设计师 1822 题。

    字段：stem / options{A..D} / answer / analysis / module / knowledge
    另有 cases（案例）与 essays（论文），非选择题，不入库。
    """
    p = os.path.join(DATA_DIR, "ruankao/Zhang-986_ruankao-architect-practice_main/"
                               "ruankao-architect-practice-main/data/bank.json")
    out = []
    if not os.path.exists(p):
        return out
    data = json.load(open(p, encoding="utf-8"))
    for q in data.get("choices", []):
        stem = (q.get("stem") or "").strip()
        if not stem:
            continue
        opts = {str(k).strip().upper(): str(v).strip()
                for k, v in (q.get("options") or {}).items() if str(v).strip()}
        if len(opts) < 2:
            continue
        ans = _md_clean_ans(q.get("answer"))
        if not ans:
            continue
        expl = (q.get("analysis") or "").strip()
        kn = (q.get("knowledge") or "").strip()
        if kn:
            expl = ("考点：" + kn + " " + expl).strip() if expl else "考点：" + kn
        out.append({"question": stem, "options": opts,
                    "answer": "".join(sorted(set(ans))),
                    "explanation": expl, "subject": "系统架构设计师"})
    return out


def read_ruankao_infosec():
    """MrBaoquan/infosec-exam：static/questions/*.json，信息安全工程师。

    字段：question / options(数组) / answer(0 基下标) / explanation / topic
    """
    d = os.path.join(DATA_DIR, "ruankao/MrBaoquan_infosec-exam_main",
                     "infosec-exam-main/static/questions")
    out = []
    if not os.path.isdir(d):
        return out
    for fn in sorted(os.listdir(d)):
        if not fn.endswith(".json"):
            continue
        try:
            rows = json.load(open(os.path.join(d, fn), encoding="utf-8"))
        except Exception:
            continue
        for r in rows:
            q = (r.get("question") or "").strip()
            if not q:
                continue
            items = [str(x).strip() for x in (r.get("options") or []) if str(x).strip()]
            if len(items) < 2:
                continue
            opts = {chr(ord("A") + i): v for i, v in enumerate(items[:6])}
            try:
                idx = int(r.get("answer"))
            except Exception:
                continue
            if not (0 <= idx < len(items)):
                continue
            expl = (r.get("explanation") or "").strip()
            tp = (r.get("topic") or "").strip()
            if tp:
                expl = ("考点：" + tp + " " + expl).strip() if expl else "考点：" + tp
            out.append({"question": q, "options": opts,
                        "answer": chr(ord("A") + idx), "explanation": expl,
                        "subject": "信息安全工程师"})
    return out


XIEZHI_MIN_SUBJECT = 3  # 学科题数少于此值则归并到「综合学科」，避免分类表被塞满


def _xiezhi_clean(s):
    """Xiezhi 的 options / answer 字段末尾常残留引号与空白。"""
    return (s or "").strip().strip('"').strip("'").strip()


def _xiezhi_cat(labels):
    """labels 形如 ['工学', '电气工程']（学科门类 -> 具体学科，可能多级）。

    取最后一个（最具体）作为二级分类名；一级统一挂「考研/升学」，
    因为 Xiezhi 的题目取自中国研究生入学考试与各学科资格考试。
    """
    labs = [x.strip() for x in (labels or []) if x and x.strip()]
    if not labs:
        return ("考研/升学", "综合学科")
    return ("考研/升学", labs[-1])


def read_xiezhi():
    """Xiezhi（MikeGu721/XiezhiBenchmark）：JSONL，一行一题。

    字段：question / options(换行分隔的候选文本) / answer(答案文本，不是字母)
         / labels(学科标签，多级)
    答案给的是选项文本，需要自己映射成 A/B/C/...；映射不上说明是噪声题，丢弃。
    """
    import collections
    out = []
    per_subject = collections.Counter()
    d = os.path.join(DATA_DIR, "xiezhi")
    if not os.path.isdir(d):
        return out
    for fn in sorted(os.listdir(d)):
        p = os.path.join(d, fn)
        if not (fn.endswith(".json") or fn.endswith(".jsonl")):
            continue
        with open(p, encoding="utf-8", errors="ignore") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    o = json.loads(line)
                except Exception:
                    continue
                q = (o.get("question") or "").strip()
                opts_list = [_xiezhi_clean(x) for x in (o.get("options") or "").split("\n")]
                opts_list = [x for x in opts_list if x]
                if not q or len(opts_list) < 2 or len(opts_list) > 26:
                    continue
                ans_txt = _xiezhi_clean(o.get("answer"))
                if not ans_txt:
                    continue
                try:
                    idx = opts_list.index(ans_txt)
                except ValueError:
                    continue
                opts = {chr(ord("A") + i): v for i, v in enumerate(opts_list)}
                top, sub = _xiezhi_cat(o.get("labels"))
                out.append({"question": q, "options": opts,
                            "answer": chr(ord("A") + idx),
                            "explanation": "", "subject": sub})
                per_subject[sub] += 1
    for r in out:
        if per_subject[r["subject"]] < XIEZHI_MIN_SUBJECT:
            r["subject"] = "综合学科"
    return out


def validate_record(rec):
    """入库前的硬校验：答案不全的题一律拒绝。

    返回 (是否通过, 拒绝原因)。用户明确要求「连答案都没有的题不能入库」，
    所以这里是**强制闸门**，任何源都无法绕过。

    检查项：
    1. 题干非空（≥4 字符）
    2. 选项 2~8 个（上限放到 8 是为了容纳 TAL-SCQ5K 这类数学题的颜色/星期选项）、
       无空值、无重复文本
    3. 答案存在且非空
    4. 答案字符只能是 A-H
    5. **答案的每个字母都必须能在 options 里找到对应 key**（核心）
    6. 多选答案不能有重复字母
    """
    q = (rec.get("question") or "").strip()
    if not q:
        return False, "题干为空"
    if len(q) < 4:
        return False, "题干过短"

    opts = rec.get("options") or {}
    if not isinstance(opts, dict):
        return False, "选项格式非法"
    if any(not str(v).strip() for v in opts.values()):
        return False, "存在空选项"
    opts = {str(k).strip(): str(v).strip() for k, v in opts.items() if str(v).strip()}
    if len(opts) < 2:
        return False, "选项少于2个"
    if len(opts) > 8:
        return False, "选项多于8个"
    if len(set(opts.values())) != len(opts):
        return False, "选项文本重复"

    ans = (rec.get("answer") or "").strip().upper()
    if not ans:
        return False, "答案为空"
    if not all("A" <= ch <= "H" for ch in ans):
        return False, f"答案含非 A-H 字符: {ans}"
    if len(set(ans)) != len(ans):
        return False, f"答案字母重复: {ans}"
    missing = [ch for ch in ans if ch not in opts]
    if missing:
        return False, f"答案字母 {''.join(missing)} 不在选项中(实际选项 {''.join(sorted(opts))})"
    return True, ""


def build_rows(records, platform, url, cat_resolver, cache, stats=None):
    rows, seen = [], set()
    rejected = {}
    for rec in records:
        ok, reason = validate_record(rec)
        if not ok:
            rejected[reason] = rejected.get(reason, 0) + 1
            continue
        q = (rec.get("question") or "").strip()
        h = md5(q)
        if h in seen:
            continue
        seen.add(h)
        top, sub = cat_resolver(rec)
        rows.append({
            "source_platform": platform,
            "source_url": rec.get("url") or url,
            "question": q,
            "question_hash": h,
            "options": json.dumps(rec["options"], ensure_ascii=False),
            "correct_answer": (rec["answer"] or "").strip().upper(),
            "explanation": rec["explanation"],
            "category_id": ensure_category(top, sub, cache),
        })
    if rejected:
        detail = ", ".join(f"{k}×{v}" for k, v in sorted(rejected.items(), key=lambda x: -x[1]))
        print(f"  [校验拦截] {sum(rejected.values())} 条不合格题已丢弃：{detail}")
    if stats is not None:
        stats["rejected"] = rejected
    return rows


def write_csv(rows, path):
    with open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
        for r in rows:
            w.writerow([r["source_platform"], r["source_url"], r["question"],
                        r["question_hash"], r["options"], r["correct_answer"],
                        r["explanation"], r["category_id"]])


def load_csv(path, platform):
    """staging -> questions，冲突跳过。

    CSV 在宿主机，而 psql 跑在容器里，容器看不到宿主机路径，
    所以用 \\copy ... FROM STDIN 把数据经由 psql 的 stdin 流式灌进去。
    """
    before = _int(psql("SELECT count(*) FROM questions WHERE source_platform='%s'" % platform,
                       fetch=True))
    head = """BEGIN;
CREATE TEMP TABLE staging (
    source_platform VARCHAR(32), source_url TEXT, question TEXT, question_hash TEXT,
    options JSONB, correct_answer VARCHAR(16), explanation TEXT, category_id BIGINT
);
\\copy staging FROM STDIN WITH (FORMAT csv, ENCODING 'UTF8')
"""
    tail = """\\.
INSERT INTO questions (source_platform, source_url, question, question_hash,
                       options, correct_answer, explanation, category_id)
SELECT source_platform, source_url, question, question_hash,
       options, correct_answer, NULLIF(explanation,''), category_id
FROM staging
ON CONFLICT (source_platform, question_hash) DO NOTHING;
COMMIT;
"""
    with open(path, "r", encoding="utf-8") as f:
        body = f.read()
    cmd = ["sudo", "docker", "exec", "-i", CONTAINER, "psql", "-U", "postgres", "-d", DB,
           "-v", "ON_ERROR_STOP=1"]
    r = subprocess.run(cmd, input=head + body + tail, capture_output=True, text=True)
    if r.returncode != 0:
        print("导入失败:\n" + r.stderr[-3000:], file=sys.stderr)
        sys.exit(1)
    after = _int(psql("SELECT count(*) FROM questions WHERE source_platform='%s'" % platform,
                      fetch=True))
    return before, after


def main():
    dry = "--dry-run" in sys.argv
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1]

    cache = {}
    jobs = []

    if only in (None, "cmexam"):
        recs = read_cmexam()
        cfg = SOURCES["cmexam"]
        top, sub = cfg["category"]
        jobs.append(("CMExam", recs, cfg["platform"], cfg["url"],
                     lambda r, t=top, s=sub: (t, s)))

    if only in (None, "financeiq"):
        recs = read_financeiq()
        cfg = SOURCES["financeiq"]
        jobs.append(("FinanceIQ", recs, cfg["platform"], cfg["url"],
                     lambda r: FINANCEIQ_MAP.get(r["subject"], ("考证", "金融其他"))))

    if only in (None, "talscq"):
        cfg = SOURCES["talscq"]
        top, sub = cfg["category"]
        jobs.append(("TAL-SCQ5K", read_talscq(), cfg["platform"], cfg["url"],
                     lambda r, t=top, s=sub: (t, s)))

    if only in (None, "aclue"):
        cfg = SOURCES["aclue"]
        top, sub = cfg["category"]
        jobs.append(("ACLUE", read_aclue(), cfg["platform"], cfg["url"],
                     lambda r, t=top, s=sub: (t, s)))

    if only in (None, "agieval"):
        cfg = SOURCES["agieval"]
        jobs.append(("AGIEval", read_agieval(), cfg["platform"], cfg["url"],
                     lambda r: AGIEVAL_MAP.get(r["subject"], ("考研/升学", "其他"))))

    if only in (None, "tcm"):
        cfg = SOURCES["tcm"]
        jobs.append(("TCM-Exam", read_tcm(), cfg["platform"], cfg["url"],
                     lambda r: (("考证", "药师资格") if r["subject"] == "药师资格"
                                else ("其他", "中医"))))

    if only in (None, "coig"):
        cfg = SOURCES["coig"]
        jobs.append(("COIG-Exam", read_coig(), cfg["platform"], cfg["url"],
                     lambda r: COIG_MAP.get(r["subject"], ("考研/升学", "高考其他"))))

    if only in (None, "gmmlu"):
        cfg = SOURCES["gmmlu"]
        jobs.append(("Global-MMLU", read_gmmlu(), cfg["platform"], cfg["url"],
                     lambda r: GMMLU_MAP.get(r["subject"], ("其他", "通识知识"))))

    if only in (None, "fineval"):
        cfg = SOURCES["fineval"]
        jobs.append(("FinEval", read_fineval(), cfg["platform"], cfg["url"],
                     lambda r: FINEVAL_MAP.get(r["subject"], ("考证", "金融其他"))))

    if only in (None, "eeval"):
        cfg = SOURCES["eeval"]
        jobs.append(("E-EVAL", read_eeval(), cfg["platform"], cfg["url"],
                     lambda r: _eeval_cat(r["subject"])))

    if only in (None, "devops"):
        cfg = SOURCES["devops"]
        jobs.append(("DevOps-Eval", read_devops(), cfg["platform"], cfg["url"],
                     lambda r: DEVOPS_MAP.get(r["subject"], ("计算机/IT", "DevOps其他"))))

    if only in (None, "secbench"):
        cfg = SOURCES["secbench"]
        jobs.append(("SecBench", read_secbench(), cfg["platform"], cfg["url"],
                     lambda r: ("计算机/IT", r["subject"] or "网络安全")))

    # 软考：4 个仓库合并后一次性导入，靠 md5(题干) 在 Python 层就去重
    if only in (None, "ruankao"):
        cfg = SOURCES["ruankao"]
        recs, urls = [], {
            "系统集成项目管理工程师": "https://github.com/qingningmneg/-1",
            "系统架构设计师": "https://github.com/Zhang-986/ruankao-architect-practice",
            "信息安全工程师": "https://github.com/MrBaoquan/infosec-exam",
        }
        for fn in (read_ruankao_lemon, read_ruankao_architect,
                   read_ruankao_infosec, read_ruankao_md):
            part = fn()
            for r in part:
                r["url"] = urls.get(r["subject"],
                                    "https://github.com/wujiaming88/awesome-ruankao")
            recs += part
            print("   软考子源 %-24s %5d 条" % (fn.__name__, len(part)))
        jobs.append(("软考Ruankao", recs, cfg["platform"], cfg["url"],
                     lambda r: RUANKAO_MAP.get(r["subject"], ("计算机/IT", "软考其他"))))

    if only in (None, "xiezhi"):
        cfg = SOURCES["xiezhi"]
        jobs.append(("Xiezhi", read_xiezhi(), cfg["platform"], cfg["url"],
                     lambda r: ("考研/升学", r["subject"])))

    if only in (None, "jyshare"):
        cfg = SOURCES["jyshare"]
        jobs.append(("Jyshare", read_jyshare(), cfg["platform"], cfg["url"],
                     lambda r: JYSHARE_MAP.get(r["subject"], ("计算机/IT", "编程基础"))))

    if only in (None, "cs408"):
        cfg = SOURCES["cs408"]
        jobs.append(("CS408", read_cs408(), cfg["platform"], cfg["url"],
                     lambda r: CS408_MAP.get(r["subject"], ("计算机/IT", "计算机基础"))))

    if only in (None, "ncre"):
        cfg = SOURCES["ncre"]
        top, sub = cfg["category"]
        jobs.append(("NCRE", read_ncre(), cfg["platform"], cfg["url"],
                     lambda r, t=top, s=sub: (t, s)))

    if only in (None, "runoob"):
        cfg = SOURCES["runoob"]
        jobs.append(("RUNOOB", read_runoob(), cfg["platform"], cfg["url"],
                     lambda r: RUNOOB_MAP.get(r["subject"], ("计算机/IT", "编程基础"))))

    if only in (None, "xiaohei"):
        cfg = SOURCES["xiaohei"]
        jobs.append(("XiaoHei", read_xiaohei(), cfg["platform"], cfg["url"],
                     lambda r: XIAOHEI_MAP.get(r["subject"], ("计算机/IT", "计算机基础"))))

    for name, recs, platform, url, resolver in jobs:
        if not recs:
            print("[%s] 无数据，跳过" % name)
            continue
        rows = build_rows(recs, platform, url, resolver, cache)
        multi = sum(1 for r in rows if len(r["correct_answer"]) > 1)
        print("[%s] 解析 %d 条 -> 校验+去重后 %d 条（其中多选 %d 条）" % (name, len(recs), len(rows), multi))
        if not rows:
            print("[%s] 警告：全部题目未通过答案校验，未入库" % name)
            continue
        if dry:
            continue
        path = "/tmp/import_%s.csv" % platform
        write_csv(rows, path)
        before, after = load_csv(path, platform)
        print("[%s] 入库：%d -> %d（新增 %d，跳过重复 %d）"
              % (name, before, after, after - before, len(rows) - (after - before)))


if __name__ == "__main__":
    main()
