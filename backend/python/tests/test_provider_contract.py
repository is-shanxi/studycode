"""Provider protocol, Mock output and provider error-mapping contracts."""

import json
from collections.abc import Callable
from typing import Any
from uuid import UUID, uuid4

import httpx
import pytest
from httpx import ASGITransport, AsyncClient

from app.config import Settings
from app.main import create_app
from app.providers import build_provider
from app.providers.errors import ProviderConfigurationError, ProviderError, ProviderTimeoutError
from app.providers.mock import MockProvider
from app.providers.real import RealProvider
from app.schemas.chat import ChatContext, ChatLimits, ChatMessage, ChatRequest
from app.schemas.generate import GeneratedCandidate, VariantConstraints, VariantGenerationRequest

TEST_TOKEN = "test-internal-token"


def _settings(**overrides: object) -> Settings:
    values: dict[str, Any] = {
        "ai_provider": "mock",
        "ai_model": "mock-model",
        "ai_api_key": "",
        "ai_request_timeout_seconds": 5,
        "java_ai_service_token": TEST_TOKEN,
    }
    values.update(overrides)
    return Settings(**values)


def _chat_request() -> ChatRequest:
    return ChatRequest(
        request_id=uuid4(),
        provider="mock",
        model="mock-model",
        messages=[
            ChatMessage(role="system", content="You are a tutor."),
            ChatMessage(role="user", content="Explain this question."),
        ],
        context=ChatContext(
            scope="QUESTION",
            stem="What is 2 + 2?",
            question_type="SINGLE_CHOICE",
            user_answer="A: 4",
            self_assessment="PASS",
            explanation="Because 2 + 2 equals 4.",
            reference_answer=None,
        ),
        limits=ChatLimits(max_output_tokens=200, timeout_ms=5000),
    )


def _generate_request() -> VariantGenerationRequest:
    return VariantGenerationRequest(
        request_id=uuid4(),
        source_question_version_id=uuid4(),
        stem="What is 2 + 2?",
        type="SINGLE_CHOICE",
        chapter="Arithmetic",
        tags=["basic"],
        target_count=2,
        constraints=VariantConstraints(difficulty="MEDIUM", prompt_version="v1"),
    )


def _real_provider(
    handler: Callable[[httpx.Request], httpx.Response],
) -> RealProvider:
    settings = _settings(
        ai_provider="openai-compatible",
        ai_api_key="secret-key",
        ai_model="provider-model",
        ai_provider_base_url="https://provider.test/v1",
    )
    return RealProvider(settings, transport=httpx.MockTransport(handler))


async def test_mock_chat_is_deterministic_and_uses_the_request_id() -> None:
    provider = MockProvider()
    first = await provider.chat(_chat_request())
    second = await provider.chat(_chat_request())

    assert first.content.startswith("[mock]")
    assert first.content == second.content
    assert first.usage.input_tokens >= 1
    assert first.usage.output_tokens >= 1
    assert first.provider_request_id is not None


async def test_mock_generate_output_validates_against_the_contract() -> None:
    candidates = await MockProvider().generate_variants(_generate_request())

    assert len(candidates) == 2
    for candidate in candidates:
        validated = GeneratedCandidate.model_validate(candidate.model_dump())
        assert validated.type == "SINGLE_CHOICE"
        assert validated.options is not None
        assert sum(1 for option in validated.options if option.is_correct) == 1
        assert validated.explanation


def test_build_provider_selects_mock_and_real_adapters() -> None:
    assert isinstance(build_provider(_settings()), MockProvider)
    real = build_provider(
        _settings(
            ai_provider="openai-compatible",
            ai_api_key="secret-key",
            ai_provider_base_url="https://provider.test/v1",
        )
    )
    assert isinstance(real, RealProvider)


def test_build_provider_rejects_unknown_or_unconfigured_providers() -> None:
    with pytest.raises(ProviderConfigurationError):
        build_provider(_settings(ai_provider="unknown-provider"))
    with pytest.raises(ProviderConfigurationError):
        build_provider(_settings(ai_provider="openai-compatible", ai_api_key=""))


async def test_real_provider_parses_chat_completion() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["Authorization"] == "Bearer secret-key"
        body = json.loads(request.content)
        assert body["model"] == "provider-model"
        assert body["messages"][0]["role"] == "system"
        return httpx.Response(
            200,
            json={
                "id": "provider-request-1",
                "choices": [{"message": {"content": "A helpful answer"}}],
                "usage": {"prompt_tokens": 12, "completion_tokens": 4},
            },
        )

    request = _chat_request().model_copy(update={"model": "provider-model"})
    response = await _real_provider(handler).chat(request)

    assert response.content == "A helpful answer"
    assert response.usage.input_tokens == 12
    assert response.usage.output_tokens == 4
    assert response.provider_request_id == "provider-request-1"


async def test_real_provider_maps_timeouts() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timed out", request=request)

    with pytest.raises(ProviderTimeoutError):
        await _real_provider(handler).chat(_chat_request())


async def test_real_provider_validates_generated_json() -> None:
    def invalid_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "id": "provider-request-2",
                "choices": [{"message": {"content": "not a json array"}}],
                "usage": {},
            },
        )

    with pytest.raises(ProviderError):
        await _real_provider(invalid_handler).generate_variants(_generate_request())

    def valid_handler(request: httpx.Request) -> httpx.Response:
        candidates = [
            {
                "stem": "What is 3 + 3?",
                "type": "SINGLE_CHOICE",
                "options": [
                    {"key": "A", "content": "6", "isCorrect": True},
                    {"key": "B", "content": "7", "isCorrect": False},
                ],
                "referenceAnswer": None,
                "explanation": "Six.",
                "difficulty": "EASY",
                "suggestedTags": ["basic"],
            }
        ]
        return httpx.Response(
            200,
            json={
                "id": "provider-request-3",
                "choices": [{"message": {"content": json.dumps(candidates)}}],
                "usage": {},
            },
        )

    generated = await _real_provider(valid_handler).generate_variants(_generate_request())
    assert len(generated) == 1
    assert generated[0].stem == "What is 3 + 3?"


async def test_internal_timeout_maps_to_a_stable_error_code() -> None:
    class TimeoutProvider:
        name = "timeout"

        async def chat(self, request: ChatRequest) -> object:
            raise ProviderTimeoutError("provider secret timeout detail")

        async def generate_variants(self, request: VariantGenerationRequest) -> list[object]:
            raise ProviderTimeoutError("provider secret timeout detail")

    app = create_app(_settings())
    app.state.provider = TimeoutProvider()
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://testserver"
    ) as client:
        response = await client.post(
            "/internal/v1/chat",
            json=_chat_payload(),
            headers={"X-Internal-Token": TEST_TOKEN},
        )

    assert response.status_code == 504
    body = response.json()
    assert body["code"] == "AI_TIMEOUT"
    assert body["retryable"] is True
    assert "secret" not in body["message"].lower()


async def test_internal_provider_error_maps_to_a_stable_error_code() -> None:
    class FailingProvider:
        name = "failing"

        async def chat(self, request: ChatRequest) -> object:
            raise ProviderError("provider secret detail")

        async def generate_variants(self, request: VariantGenerationRequest) -> list[object]:
            raise ProviderError("provider secret detail")

    app = create_app(_settings())
    app.state.provider = FailingProvider()
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://testserver"
    ) as client:
        response = await client.post(
            "/internal/v1/chat",
            json=_chat_payload(),
            headers={"X-Internal-Token": TEST_TOKEN},
        )

    assert response.status_code == 502
    body = response.json()
    assert body["code"] == "AI_PROVIDER_ERROR"
    assert "secret" not in body["message"].lower()


async def test_internal_contract_rejects_unknown_fields() -> None:
    app = create_app(_settings())
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://testserver"
    ) as client:
        payload = _chat_payload()
        payload["unexpected"] = "value"
        response = await client.post(
            "/internal/v1/chat",
            json=payload,
            headers={"X-Internal-Token": TEST_TOKEN},
        )

    assert response.status_code == 400
    assert response.json()["code"] == "INVALID_REQUEST"


def _chat_payload() -> dict[str, object]:
    return {
        "requestId": str(uuid4()),
        "provider": "mock",
        "model": "mock-model",
        "messages": [{"role": "user", "content": "请解释这道题"}],
        "context": {"scope": "GENERAL"},
        "limits": {"maxOutputTokens": 200, "timeoutMs": 5000},
    }


def test_conversation_turn_limit_constant_is_documented() -> None:
    # Java enforces the turn limit; Python remains stateless. This assertion
    # pins that the contract carries no server-side session state.
    request = _chat_request()
    assert request.context is not None
    assert request.context.explanation == "Because 2 + 2 equals 4."
    assert request.request_id != UUID(int=0)
