"""Provider selection for the internal AI service."""

from app.config import Settings
from app.providers.base import ModelProvider
from app.providers.errors import ProviderConfigurationError
from app.providers.mock import MockProvider
from app.providers.real import RealProvider

_REAL_PROVIDERS = frozenset({"openai", "openai-compatible"})


def build_provider(settings: Settings) -> ModelProvider:
    """Build the single configured provider; Mock remains the offline default."""
    provider = settings.ai_provider.strip().lower()
    if provider == "mock":
        return MockProvider()
    if provider in _REAL_PROVIDERS:
        if not settings.ai_api_key.strip():
            raise ProviderConfigurationError(
                "AI_API_KEY is required when a real provider is selected"
            )
        return RealProvider(settings)
    raise ProviderConfigurationError(f"Unsupported AI provider: {settings.ai_provider}")
