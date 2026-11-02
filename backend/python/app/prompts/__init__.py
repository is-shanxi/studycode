"""Versioned prompt builders for the stateless AI service."""

import json

from app.schemas.chat import ChatRequest
from app.schemas.generate import VariantGenerationRequest

PROMPT_VERSION = "v1"

CHAT_SYSTEM_PROMPT = (
    "You are a certification-exam tutor. Explain the reasoning, keep the answer "
    "concise, and never invent official answers that are not in the provided context."
)

GENERATION_SYSTEM_PROMPT = (
    "You generate practice-question variants for a certification exam. "
    "Return only a JSON array. Each item must contain: stem, type "
    "(SINGLE_CHOICE or SINGLE_BLANK), options (a list of key/content/isCorrect, "
    "exactly one correct for SINGLE_CHOICE), referenceAnswer (for SINGLE_BLANK), "
    "explanation, difficulty (EASY, MEDIUM or HARD), and suggestedTags. "
    "Do not include markdown fences or prose."
)


def render_context(request: ChatRequest) -> str | None:
    """Render the minimal context Java already authorized for this request."""
    context = request.context
    if context is None:
        return None
    lines = [f"scope: {context.scope}"]
    if context.stem:
        lines.append(f"stem: {context.stem}")
    if context.question_type:
        lines.append(f"question_type: {context.question_type}")
    if context.user_answer:
        lines.append(f"user_answer: {context.user_answer}")
    if context.self_assessment:
        lines.append(f"self_assessment: {context.self_assessment}")
    if context.explanation:
        lines.append(f"official_explanation: {context.explanation}")
    if context.reference_answer:
        lines.append(f"reference_answer: {context.reference_answer}")
    return "\n".join(lines)


def generation_user_prompt(request: VariantGenerationRequest) -> str:
    """Serialize the authorized source question and generation constraints."""
    payload = {
        "source_question_version_id": str(request.source_question_version_id),
        "stem": request.stem,
        "type": request.type,
        "chapter": request.chapter,
        "tags": request.tags,
        "target_count": request.target_count,
        "difficulty": request.constraints.difficulty,
        "prompt_version": request.constraints.prompt_version or PROMPT_VERSION,
    }
    return json.dumps(payload, ensure_ascii=False)
