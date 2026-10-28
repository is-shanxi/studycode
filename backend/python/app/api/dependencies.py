"""Shared FastAPI dependencies for internal routes."""

import secrets
from typing import Annotated

from fastapi import Header, HTTPException, Request, status

from app.config import Settings
from app.providers.base import ModelProvider


def get_provider(request: Request) -> ModelProvider:
    """Return the provider configured on the application state."""
    provider: ModelProvider = request.app.state.provider
    return provider


def verify_internal_token(
    request: Request,
    x_internal_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None,
) -> None:
    """Reject requests without the shared Java-to-Python service token."""
    settings: Settings = request.app.state.settings
    if x_internal_token is None or not secrets.compare_digest(
        x_internal_token, settings.java_ai_service_token
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "code": "UNAUTHORIZED",
                "message": "Invalid internal service token",
                "requestId": getattr(request.state, "request_id", "unknown"),
                "retryable": False,
            },
        )
