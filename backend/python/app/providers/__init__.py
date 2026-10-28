"""Provider selection for the internal AI service."""

import logging

from app.config import Settings
from app.providers.base import ModelProvider
from app.providers.mock import MockProvider

logger = logging.getLogger(__name__)


def build_provider(settings: Settings) -> ModelProvider:
    """Select the configured provider. Only MockProvider exists before Phase 5."""
    if settings.ai_provider != "mock":
        logger.warning(
            "AI provider %s is not implemented yet; using MockProvider",
            settings.ai_provider,
        )
    return MockProvider()
