"""Provider-agnostic model interface used by internal routes."""

from typing import Protocol

from app.schemas.chat import ChatRequest, ChatResponse
from app.schemas.generate import GeneratedCandidate, VariantGenerationRequest


class ModelProvider(Protocol):
    """A stateless model provider. Implementations must never touch business data."""

    name: str

    async def chat(self, request: ChatRequest) -> ChatResponse:
        """Return a tutoring response for the given conversation."""
        ...

    async def generate_variants(
        self, request: VariantGenerationRequest
    ) -> list[GeneratedCandidate]:
        """Return structured variant candidates for Java-side validation."""
        ...
