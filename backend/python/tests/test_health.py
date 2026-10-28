"""Health endpoint contract tests."""

from httpx import AsyncClient


async def test_liveness(client: AsyncClient) -> None:
    response = await client.get("/health/live")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


async def test_readiness_with_mock_provider(client: AsyncClient) -> None:
    response = await client.get("/health/ready")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


async def test_request_id_is_echoed(client: AsyncClient) -> None:
    request_id = "test-request-1234"
    response = await client.get("/health/live", headers={"X-Request-Id": request_id})

    assert response.headers["X-Request-Id"] == request_id
    assert response.headers["X-Trace-Id"] == request_id
