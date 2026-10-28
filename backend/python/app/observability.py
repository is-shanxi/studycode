"""Request-scoped logging context and metadata-only access logging."""

import logging
import re
import time
from contextvars import ContextVar
from uuid import uuid4

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp

REQUEST_ID_HEADER = "X-Request-Id"
TRACE_ID_HEADER = "X-Trace-Id"
_VALID_ID = re.compile(r"[A-Za-z0-9._-]{8,64}")
_request_id: ContextVar[str] = ContextVar("request_id", default="-")


class RequestIdLogFilter(logging.Filter):
    """Adds the current request ID to each log record."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = _request_id.get()
        return True


def configure_logging(level: str) -> None:
    """Configure metadata-only structured logging without clearing host handlers."""
    formatter = logging.Formatter(
        "%(asctime)s %(levelname)s [%(request_id)s] %(name)s - %(message)s"
    )
    root = logging.getLogger()
    if not root.handlers:
        stream_handler = logging.StreamHandler()
        stream_handler.setFormatter(formatter)
        root.addHandler(stream_handler)
    for existing_handler in root.handlers:
        existing_handler.setFormatter(formatter)
        if not any(
            isinstance(log_filter, RequestIdLogFilter) for log_filter in existing_handler.filters
        ):
            existing_handler.addFilter(RequestIdLogFilter())
    root.setLevel(level.upper())


def _valid_id_or_none(value: str | None) -> str | None:
    if value is None or not _VALID_ID.fullmatch(value):
        return None
    return value


class RequestContextMiddleware(BaseHTTPMiddleware):
    """Propagates request/trace IDs and emits one metadata-only access log."""

    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        request_id = (
            _valid_id_or_none(request.headers.get(REQUEST_ID_HEADER))
            or _valid_id_or_none(request.headers.get(TRACE_ID_HEADER))
            or str(uuid4())
        )
        request.state.request_id = request_id
        token = _request_id.set(request_id)
        started_at = time.perf_counter()
        response: Response | None = None
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
        finally:
            duration_ms = (time.perf_counter() - started_at) * 1000
            logging.getLogger("app.access").info(
                "http_request method=%s path=%s status=%s duration_ms=%.1f",
                request.method,
                request.url.path,
                status_code,
                duration_ms,
            )
            _request_id.reset(token)

        assert response is not None
        response.headers[REQUEST_ID_HEADER] = request_id
        response.headers[TRACE_ID_HEADER] = request_id
        return response
