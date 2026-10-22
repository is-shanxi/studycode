-- ================= 建表：categories（分类表，支持多级） =================
CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(64)  NOT NULL,
    parent_id  BIGINT REFERENCES categories(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ================= 表注释 =================
COMMENT ON TABLE categories IS '分类表：parent_id 为空是一级大类，否则指向父分类；可层层递归到任意层级（如 计算机→计算机网络→传输层→TCP）';

-- ================= 字段注释 =================
COMMENT ON COLUMN categories."id"       IS '主键：自增编号，数据库自动生成';
COMMENT ON COLUMN categories.name       IS '分类名称，如：计算机网络、公务员、教师资格';
COMMENT ON COLUMN categories.parent_id  IS '父分类：为空表示一级大类；非空时指向父分类的 id';
COMMENT ON COLUMN categories.created_at IS '分类创建时间，自动生成';

-- ================= questions 表增加分类外键 =================
ALTER TABLE questions ADD COLUMN category_id BIGINT REFERENCES categories(id);
COMMENT ON COLUMN questions.category_id IS '所属分类，关联 categories 表的 id，题目一般挂在最末级分类';

-- ================= 分类查询索引 =================
CREATE INDEX idx_questions_category_id ON questions(category_id);
COMMENT ON INDEX idx_questions_category_id IS '按分类查题目用，外键不会自动建索引';
