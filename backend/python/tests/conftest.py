"""Shared pytest fixtures for the AI service test suite."""

from collections.abc import AsyncIterator
from uuid import uuid4

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from app import __version__
from app.config import Settings
from app.main import create_app

TEST_TOKEN = "test-internal-token"


@pytest.fixture(scope="session")
def app_version() -> str:
    """Expose the package version to configuration smoke tests."""
    return __version__


@pytest.fixture
def settings() -> Settings:
    """Deterministic settings for mock-provider tests."""
    return Settings(
        ai_provider="mock",
        ai_model="mock-model",
        ai_api_key="",
        ai_request_timeout_seconds=30,
        java_ai_service_token=TEST_TOKEN,
    )


@pytest_asyncio.fixture
async def client(settings: Settings) -> AsyncIterator[AsyncClient]:
    """HTTPX client bound to an in-process FastAPI application."""
    app = create_app(settings)
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as http_client:
        yield http_client


@pytest.fixture
def internal_headers() -> dict[str, str]:
    """Headers required by the restricted Java-to-Python contract."""
    return {
        "X-Internal-Token": TEST_TOKEN,
        "X-Trace-Id": str(uuid4()),
        "X-Request-Id": str(uuid4()),
    }
