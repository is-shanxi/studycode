"""Internal variant-generation contract shared with the Java AI gateway."""

from typing import Literal
from uuid import UUID

from pydantic import Field

from app.schemas.base import InternalModel

Difficulty = Literal["EASY", "MEDIUM", "HARD"]
QuestionType = Literal["SINGLE_CHOICE", "SINGLE_BLANK"]


class VariantConstraints(InternalModel):
    difficulty: str | None = None
    prompt_version: str | None = None


class VariantGenerationRequest(InternalModel):
    request_id: UUID
    source_question_version_id: UUID
    stem: str = Field(min_length=1)
    type: QuestionType
    chapter: str | None = None
    tags: list[str] = Field(default_factory=list)
    target_count: int = Field(default=10, ge=1, le=100)
    constraints: VariantConstraints = Field(default_factory=VariantConstraints)


class CandidateOption(InternalModel):
    key: str
    content: str
    is_correct: bool


class GeneratedCandidate(InternalModel):
    stem: str
    type: QuestionType
    options: list[CandidateOption] | None = None
    reference_answer: str | None = None
    explanation: str
    difficulty: Difficulty
    suggested_tags: list[str] = Field(default_factory=list)
