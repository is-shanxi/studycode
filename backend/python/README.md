# Python AI 服务

Python 3.12 + FastAPI 无状态服务，只通过 `/internal/v1` 接口接收 Java 服务的最小上下文并返回模型结果；不访问业务数据库，不保存业务事实。

## 能力

- 内部令牌校验的 `/internal/v1/chat` 与 `/internal/v1/generate-variants`。
- 默认 `MockProvider`：确定性、离线，无密钥即可完成本地联调。
- `RealProvider`：OpenAI-compatible chat-completions 适配，模型与地址来自环境变量。
- 超时、供应商错误和结构校验失败映射为稳定错误码；日志不记录提示词、答案或密钥。
- 服务保持无状态，不访问 PostgreSQL，不保存会话、额度或业务事实。

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

真实供应商配置：

```text
AI_PROVIDER=openai-compatible
AI_PROVIDER_BASE_URL=https://api.openai.com/v1
AI_MODEL=<model-name>
AI_API_KEY=<secret>
AI_REQUEST_TIMEOUT_SECONDS=30
```

错误映射：超时 `504 AI_TIMEOUT`，供应商失败 `502 AI_PROVIDER_ERROR`，配置不可用 `503 AI_UNAVAILABLE`。Java 统一转换为 `AI_UNAVAILABLE`，不向浏览器泄露供应商标识。

测试：`python -m pytest` 覆盖内部契约、Mock 输出校验、真实适配器超时/JSON 校验和日志/错误脱敏。
