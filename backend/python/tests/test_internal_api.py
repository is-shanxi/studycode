"""Internal contract and token-gating tests."""

from uuid import uuid4

from httpx import AsyncClient


async def test_chat_requires_internal_token(client: AsyncClient) -> None:
    response = await client.post("/internal/v1/chat", json=_chat_payload())

    assert response.status_code == 401
    body = response.json()
    assert body["code"] == "UNAUTHORIZED"
    assert body["requestId"] == response.headers["X-Request-Id"]


async def test_chat_returns_mock_response(
    client: AsyncClient, internal_headers: dict[str, str]
) -> None:
    payload = _chat_payload()
    response = await client.post("/internal/v1/chat", json=payload, headers=internal_headers)

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == payload["requestId"]
    assert body["content"].startswith("[mock]")
    assert body["usage"]["inputTokens"] >= 1
    assert body["providerRequestId"].startswith("mock-")


async def test_generate_variants_returns_candidates(
    client: AsyncClient, internal_headers: dict[str, str]
) -> None:
    payload = {
        "requestId": str(uuid4()),
        "sourceQuestionVersionId": str(uuid4()),
        "stem": "示例题干",
        "type": "SINGLE_CHOICE",
        "chapter": "计算机基础",
        "tags": ["高频"],
        "targetCount": 2,
        "constraints": {"difficulty": "MEDIUM", "promptVersion": "v1"},
    }

    response = await client.post(
        "/internal/v1/generate-variants",
        json=payload,
        headers=internal_headers,
    )

    assert response.status_code == 200
    candidates = response.json()
    assert len(candidates) == 2
    assert candidates[0]["type"] == "SINGLE_CHOICE"
    assert candidates[0]["options"][0]["isCorrect"] is True
    assert candidates[0]["suggestedTags"] == ["高频"]


def _chat_payload() -> dict[str, object]:
    return {
        "requestId": str(uuid4()),
        "provider": "mock",
        "model": "mock-model",
        "messages": [{"role": "user", "content": "请解释这道题"}],
        "context": {"scope": "GENERAL"},
        "limits": {"maxOutputTokens": 200, "timeoutMs": 5000},
    }
