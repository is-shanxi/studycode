"""Public liveness and readiness endpoints for the AI service."""

from typing import Literal

from fastapi import APIRouter, HTTPException, Request, status
from pydantic import BaseModel

router = APIRouter(tags=["health"])


class HealthStatus(BaseModel):
    status: Literal["ok"]
    service: str


@router.get("/health/live", response_model=HealthStatus)
async def liveness() -> HealthStatus:
    return HealthStatus(status="ok", service="cert-practice-ai")


@router.get("/health/ready", response_model=HealthStatus)
async def readiness(request: Request) -> HealthStatus:
    if getattr(request.app.state, "provider", None) is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"code": "UNAVAILABLE", "message": "Provider is not configured"},
        )
    return HealthStatus(status="ok", service="cert-practice-ai")
