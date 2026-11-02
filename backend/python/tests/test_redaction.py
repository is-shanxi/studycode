"""Logs and error responses must never contain prompts, answers or secrets."""

import logging
from uuid import uuid4

import httpx
import pytest
from httpx import ASGITransport, AsyncClient

from app.config import Settings
from app.main import create_app
from app.providers.errors import ProviderError
from app.schemas.chat import ChatMessage, ChatRequest
from app.schemas.generate import VariantGenerationRequest

TEST_TOKEN = "test-internal-token"
SECRET_ANSWER = "SECRET-ANSWER-MUST-NOT-APPEAR"


def _settings() -> Settings:
    return Settings(
        ai_provider="mock",
        ai_model="mock-model",
        ai_api_key="secret-api-key",
        ai_request_timeout_seconds=5,
        java_ai_service_token=TEST_TOKEN,
    )


async def test_provider_failures_do_not_log_prompts_or_answers(
    caplog: pytest.LogCaptureFixture,
) -> None:
    class FailingProvider:
        name = "failing"

        async def chat(self, request: ChatRequest) -> object:
            raise ProviderError(f"provider rejected: {SECRET_ANSWER}")

        async def generate_variants(self, request: VariantGenerationRequest) -> list[object]:
            raise ProviderError(f"provider rejected: {SECRET_ANSWER}")

    app = create_app(_settings())
    app.state.provider = FailingProvider()
    with caplog.at_level(logging.DEBUG):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://testserver"
        ) as client:
            response = await client.post(
                "/internal/v1/chat",
                json={
                    "requestId": str(uuid4()),
                    "provider": "mock",
                    "model": "mock-model",
                    "messages": [{"role": "user", "content": SECRET_ANSWER}],
                    "context": {"scope": "GENERAL"},
                    "limits": {"maxOutputTokens": 200, "timeoutMs": 5000},
                },
                headers={"X-Internal-Token": TEST_TOKEN},
            )

    assert response.status_code == 502
    assert SECRET_ANSWER not in caplog.text
    assert "secret-api-key" not in caplog.text
    assert SECRET_ANSWER not in response.text


async def test_unhandled_failures_log_only_the_exception_type(
    caplog: pytest.LogCaptureFixture,
) -> None:
    class ExplodingProvider:
        name = "exploding"

        async def chat(self, request: ChatRequest) -> object:
            raise RuntimeError(SECRET_ANSWER)

        async def generate_variants(self, request: VariantGenerationRequest) -> list[object]:
            raise RuntimeError(SECRET_ANSWER)

    app = create_app(_settings())
    app.state.provider = ExplodingProvider()
    with caplog.at_level(logging.DEBUG):
        async with AsyncClient(
            transport=ASGITransport(app=app, raise_app_exceptions=False),
            base_url="http://testserver",
        ) as client:
            response = await client.post(
                "/internal/v1/chat",
                json={
                    "requestId": str(uuid4()),
                    "provider": "mock",
                    "model": "mock-model",
                    "messages": [{"role": "user", "content": SECRET_ANSWER}],
                    "context": {"scope": "GENERAL"},
                    "limits": {"maxOutputTokens": 200, "timeoutMs": 5000},
                },
                headers={"X-Internal-Token": TEST_TOKEN},
            )

    assert response.status_code == 500
    assert SECRET_ANSWER not in caplog.text
    assert SECRET_ANSWER not in response.text


async def test_internal_token_is_never_echoed(caplog: pytest.LogCaptureFixture) -> None:
    app = create_app(_settings())
    with caplog.at_level(logging.DEBUG):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://testserver"
        ) as client:
            response = await client.post(
                "/internal/v1/chat",
                json={
                    "requestId": str(uuid4()),
                    "provider": "mock",
                    "model": "mock-model",
                    "messages": [{"role": "user", "content": "hello"}],
                    "context": {"scope": "GENERAL"},
                    "limits": {"maxOutputTokens": 200, "timeoutMs": 5000},
                },
                headers={"X-Internal-Token": "wrong-token"},
            )

    assert response.status_code == 401
    assert "wrong-token" not in caplog.text
    assert "wrong-token" not in response.text


async def test_real_provider_does_not_log_request_or_response_bodies(
    caplog: pytest.LogCaptureFixture,
) -> None:
    from app.providers.real import RealProvider

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "id": "provider-1",
                "choices": [{"message": {"content": SECRET_ANSWER}}],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1},
            },
        )

    settings = Settings(
        ai_provider="openai-compatible",
        ai_model="provider-model",
        ai_api_key="secret-api-key",
        ai_provider_base_url="https://provider.test/v1",
        ai_request_timeout_seconds=5,
        java_ai_service_token=TEST_TOKEN,
    )
    provider = RealProvider(settings, transport=httpx.MockTransport(handler))
    with caplog.at_level(logging.DEBUG):
        await provider.chat(
            ChatRequest(
                request_id=uuid4(),
                provider="openai-compatible",
                model="provider-model",
                messages=[ChatMessage(role="user", content=SECRET_ANSWER)],
            )
        )

    assert SECRET_ANSWER not in caplog.text
    assert "secret-api-key" not in caplog.text
