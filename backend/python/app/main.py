"""FastAPI entry point for the internal AI service."""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.api import chat, generate, health
from app.config import Settings, get_settings
from app.observability import RequestContextMiddleware, configure_logging
from app.providers import build_provider

logger = logging.getLogger(__name__)


def create_app(settings: Settings | None = None) -> FastAPI:
    """Build the internal-app surface with an explicit provider and settings."""
    resolved_settings = settings or get_settings()
    configure_logging(resolved_settings.log_level)

    app = FastAPI(
        title="Certification Practice AI Service",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    app.state.settings = resolved_settings
    app.state.provider = build_provider(resolved_settings)
    app.add_middleware(RequestContextMiddleware)

    app.include_router(health.router)
    app.include_router(chat.router)
    app.include_router(generate.router)

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        request: Request, _exc: RequestValidationError
    ) -> JSONResponse:
        return _error_response(
            request,
            status_code=400,
            code="INVALID_REQUEST",
            message="Request validation failed",
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        if isinstance(exc.detail, dict):
            payload = dict(exc.detail)
            payload.setdefault("requestId", getattr(request.state, "request_id", "unknown"))
            return JSONResponse(status_code=exc.status_code, content=payload)
        return _error_response(
            request,
            status_code=exc.status_code,
            code=_code_for_status(exc.status_code),
            message=str(exc.detail),
        )

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
        # Log only the type: prompts, answers and provider secrets must not leak.
        logger.error(
            "unhandled_exception type=%s request_id=%s",
            type(exc).__name__,
            getattr(request.state, "request_id", "unknown"),
        )
        return _error_response(
            request,
            status_code=500,
            code="INTERNAL_ERROR",
            message="Internal server error",
        )

    return app


def _error_response(request: Request, *, status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={
            "code": code,
            "message": message,
            "requestId": getattr(request.state, "request_id", "unknown"),
            "retryable": False,
        },
    )


def _code_for_status(status_code: int) -> str:
    if status_code == 401:
        return "UNAUTHORIZED"
    if status_code == 403:
        return "FORBIDDEN"
    if status_code == 404:
        return "NOT_FOUND"
    if status_code == 503:
        return "UNAVAILABLE"
    return "INVALID_REQUEST"


app = create_app()
