"""Environment-backed settings for the internal AI service."""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration read from environment variables."""

    model_config = SettingsConfigDict(
        env_file=None,
        case_sensitive=False,
        extra="ignore",
    )

    ai_provider: str = "mock"
    ai_model: str = "mock-model"
    ai_api_key: str = ""
    ai_request_timeout_seconds: int = Field(default=30, ge=1, le=300)
    java_ai_service_token: str = Field(default="local-internal-token", min_length=1)
    log_level: str = "INFO"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return cached process-wide settings."""
    return Settings()
