-- ================= 建表：questions（题目表） =================
CREATE TABLE questions (
    id              BIGSERIAL PRIMARY KEY,
    source_platform VARCHAR(32) NOT NULL,
    source_url      TEXT,
    question        TEXT        NOT NULL,
    question_hash   TEXT        NOT NULL,
    options         JSONB       NOT NULL,
    correct_answer  VARCHAR(16) NOT NULL,
    explanation     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ================= 表注释 =================
COMMENT ON TABLE questions IS '题目表：一行一道选择题，来源可能是卷子URL（一个URL含多题）';

-- ================= 字段注释 =================
COMMENT ON COLUMN questions.id              IS '主键：自增编号，数据库自动生成，不用手动填';
COMMENT ON COLUMN questions.source_platform IS '采集来源平台，如：牛客、力扣、洛谷';
COMMENT ON COLUMN questions.source_url      IS '原题/原卷链接：题目是从哪个网页采来的';
COMMENT ON COLUMN questions.question        IS '题干：题目问什么（例：以下哪个是TCP协议？）';
COMMENT ON COLUMN questions.question_hash   IS '题干指纹：md5(题干)计算出来的，用来判断是不是同一道题、防重复采集';
COMMENT ON COLUMN questions.options         IS '选项：JSON对象，格式 {"A":"选项内容","B":"选项内容","C":"…","D":"…"}';
COMMENT ON COLUMN questions.correct_answer  IS '正确答案：单选填"A"，多选填"AB"（按字母顺序排列）';
COMMENT ON COLUMN questions.explanation     IS '题目解析/答案解释：采到了就填，没有就留空';
COMMENT ON COLUMN questions.created_at      IS '首次入库时间：第一次采进数据库的时间，自动生成';

-- ================= 防重复索引 =================
CREATE UNIQUE INDEX uq_questions_platform_hash ON questions (source_platform, question_hash);
COMMENT ON INDEX uq_questions_platform_hash IS '防重复索引：同一平台下题干哈希相同的视为同一道题，重复采集自动跳过';
