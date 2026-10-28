# Python AI 服务

Python 3.12 + FastAPI 无状态服务，只通过 `/internal/v1` 接口接收 Java 服务的最小上下文并返回模型结果；不访问业务数据库，不保存业务事实。

## 阶段状态

- P1 核心刷题闭环不调用 AI；Java 端已提供 `AiServiceClient` 外壳。
- Phase 2 已建立 FastAPI 应用、内部令牌校验、MockProvider、chat/generate 路由骨架和健康检查。
- 真实供应商适配、额度/成本、自测变式题与 AI 辅导在 P3 接入。

## 命令

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -e ".[dev]"
python -m ruff check .
python -m mypy app tests
python -m pytest
python -m uvicorn app.main:app --port 8000 --reload
```

内部接口默认使用 `JAVA_AI_SERVICE_TOKEN`（本地占位值 `local-internal-token`），真实密钥只从本地环境读取，不提交仓库。
