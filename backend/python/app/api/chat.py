"""Internal chat route consumed only by the Java business service."""

from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.dependencies import get_provider, verify_internal_token
from app.providers.base import ModelProvider
from app.schemas.chat import ChatRequest, ChatResponse

router = APIRouter(
    prefix="/internal/v1",
    tags=["internal"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post("/chat", response_model=ChatResponse)
async def chat(
    request: ChatRequest,
    provider: Annotated[ModelProvider, Depends(get_provider)],
) -> ChatResponse:
    return await provider.chat(request)
