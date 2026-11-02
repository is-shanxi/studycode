"""Stable provider failures mapped to internal API error codes."""


class ProviderError(Exception):
    """A provider request failed or returned an unusable payload."""


class ProviderTimeoutError(ProviderError):
    """The provider did not answer within the configured timeout."""


class ProviderConfigurationError(ProviderError):
    """The configured provider cannot be used (missing key or unknown name)."""
