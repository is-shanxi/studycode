"""Health endpoint contract tests."""

from httpx import ASGITransport, AsyncClient

from app.config import Settings
from app.main import create_app


async def test_liveness(client: AsyncClient) -> None:
    response = await client.get("/health/live")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


async def test_readiness_with_mock_provider(client: AsyncClient) -> None:
    response = await client.get("/health/ready")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


async def test_readiness_reports_unavailable_without_provider() -> None:
    app = create_app(
        Settings(
            ai_provider="mock",
            ai_model="mock-model",
            ai_api_key="",
            java_ai_service_token="test-internal-token",
        )
    )
    app.state.provider = None

    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://testserver"
    ) as client:
        response = await client.get("/health/ready")

    assert response.status_code == 503
    assert response.json()["code"] == "UNAVAILABLE"


async def test_request_id_is_echoed(client: AsyncClient) -> None:
    request_id = "test-request-1234"
    response = await client.get("/health/live", headers={"X-Request-Id": request_id})

    assert response.headers["X-Request-Id"] == request_id
    assert response.headers["X-Trace-Id"] == request_id
