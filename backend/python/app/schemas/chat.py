"""Internal chat contract shared with the Java AI gateway."""

from typing import Literal
from uuid import UUID

from pydantic import Field

from app.schemas.base import InternalModel


class ChatMessage(InternalModel):
    role: Literal["system", "user", "assistant"]
    content: str


class ChatContext(InternalModel):
    scope: Literal["GENERAL", "QUESTION"]
    stem: str | None = None
    question_type: str | None = None
    user_answer: str | None = None
    self_assessment: str | None = None


class ChatLimits(InternalModel):
    max_output_tokens: int = Field(default=1200, ge=1, le=8192)
    timeout_ms: int = Field(default=30000, ge=1000, le=120000)


class ChatRequest(InternalModel):
    request_id: UUID
    provider: str
    model: str
    messages: list[ChatMessage] = Field(min_length=1)
    context: ChatContext | None = None
    limits: ChatLimits = Field(default_factory=ChatLimits)


class ChatUsage(InternalModel):
    input_tokens: int = Field(ge=0)
    output_tokens: int = Field(ge=0)


class ChatResponse(InternalModel):
    request_id: UUID
    content: str
    usage: ChatUsage
    provider_request_id: str | None = None
