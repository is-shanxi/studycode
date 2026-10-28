# Python AI 服务

Python 3.12 + FastAPI 无状态服务，只通过 `/internal/v1` 接口接收 Java 服务的最小上下文并返回模型结果；不访问业务数据库，不保存业务事实。

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

应用入口、MockProvider 和内部路由将在 P1 基础阶段（Phase 2）接入。
