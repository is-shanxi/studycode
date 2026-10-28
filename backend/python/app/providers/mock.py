"""Deterministic MockProvider used for local development and contract tests."""

from typing import cast

from app.schemas.chat import ChatRequest, ChatResponse, ChatUsage
from app.schemas.generate import (
    CandidateOption,
    Difficulty,
    GeneratedCandidate,
    VariantGenerationRequest,
)


class MockProvider:
    """No-network provider with stable output and no external secrets."""

    name = "mock"

    async def chat(self, request: ChatRequest) -> ChatResponse:
        last_user_message = next(
            (message.content for message in reversed(request.messages) if message.role == "user"),
            "",
        )
        content = f"[mock] 已收到你的问题: {last_user_message[:120]}"
        input_tokens = sum(max(1, len(message.content) // 4) for message in request.messages)
        output_tokens = max(1, len(content) // 4)
        return ChatResponse(
            request_id=request.request_id,
            content=content,
            usage=ChatUsage(input_tokens=input_tokens, output_tokens=output_tokens),
            provider_request_id=f"mock-{request.request_id}",
        )

    async def generate_variants(
        self, request: VariantGenerationRequest
    ) -> list[GeneratedCandidate]:
        count = min(request.target_count, 3)
        return [self._candidate(request, index) for index in range(1, count + 1)]

    def _candidate(self, request: VariantGenerationRequest, index: int) -> GeneratedCandidate:
        difficulty = self._difficulty(request)
        if request.type == "SINGLE_CHOICE":
            return GeneratedCandidate(
                stem=f"[mock 变式 {index}] {request.stem}",
                type=request.type,
                options=[
                    CandidateOption(key="A", content=f"选项 A {index}", is_correct=True),
                    CandidateOption(key="B", content=f"选项 B {index}", is_correct=False),
                ],
                reference_answer=None,
                explanation="MockProvider 生成的本地演示解析。",
                difficulty=difficulty,
                suggested_tags=list(request.tags),
            )
        return GeneratedCandidate(
            stem=f"[mock 变式 {index}] {request.stem}",
            type=request.type,
            options=None,
            reference_answer=f"mock-answer-{index}",
            explanation="MockProvider 生成的本地演示解析。",
            difficulty=difficulty,
            suggested_tags=list(request.tags),
        )

    def _difficulty(self, request: VariantGenerationRequest) -> Difficulty:
        requested = request.constraints.difficulty
        if requested in ("EASY", "MEDIUM", "HARD"):
            return cast(Difficulty, requested)
        return "MEDIUM"
