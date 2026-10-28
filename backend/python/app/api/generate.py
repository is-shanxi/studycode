"""Internal variant-generation route consumed only by the Java service."""

from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.dependencies import get_provider, verify_internal_token
from app.providers.base import ModelProvider
from app.schemas.generate import GeneratedCandidate, VariantGenerationRequest

router = APIRouter(
    prefix="/internal/v1",
    tags=["internal"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post("/generate-variants", response_model=list[GeneratedCandidate])
async def generate_variants(
    request: VariantGenerationRequest,
    provider: Annotated[ModelProvider, Depends(get_provider)],
) -> list[GeneratedCandidate]:
    return await provider.generate_variants(request)
