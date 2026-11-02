"""Single real provider adapter behind the provider protocol.

The adapter speaks an OpenAI-compatible chat-completions dialect. It never logs
prompts, answers, request bodies or secrets; failures are converted to stable
provider errors that the FastAPI layer maps to internal error codes.
"""

import json
from typing import Any

import httpx
from pydantic import TypeAdapter, ValidationError

from app.config import Settings
from app.prompts import (
    CHAT_SYSTEM_PROMPT,
    GENERATION_SYSTEM_PROMPT,
    generation_user_prompt,
    render_context,
)
from app.providers.errors import ProviderError, ProviderTimeoutError
from app.schemas.chat import ChatRequest, ChatResponse, ChatUsage
from app.schemas.generate import GeneratedCandidate, VariantGenerationRequest

_CANDIDATE_LIST = TypeAdapter(list[GeneratedCandidate])


class RealProvider:
    """OpenAI-compatible provider with deterministic error mapping."""

    name = "real"

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._settings = settings
        self._client = httpx.AsyncClient(
            base_url=settings.ai_provider_base_url.rstrip("/"),
            timeout=float(settings.ai_request_timeout_seconds),
            transport=transport,
        )

    async def chat(self, request: ChatRequest) -> ChatResponse:
        messages: list[dict[str, str]] = [{"role": "system", "content": CHAT_SYSTEM_PROMPT}]
        context = render_context(request)
        if context is not None:
            messages.append({"role": "system", "content": context})
        messages.extend(
            {"role": message.role, "content": message.content} for message in request.messages
        )
        data = await self._post(
            "/chat/completions",
            {
                "model": request.model,
                "messages": messages,
                "max_tokens": request.limits.max_output_tokens,
                "temperature": 0.2,
            },
        )
        content = self._extract_content(data)
        usage = data.get("usage") if isinstance(data, dict) else None
        input_tokens = _non_negative_int(usage, "prompt_tokens")
        output_tokens = _non_negative_int(usage, "completion_tokens")
        provider_request_id = data.get("id") if isinstance(data, dict) else None
        return ChatResponse(
            request_id=request.request_id,
            content=content,
            usage=ChatUsage(input_tokens=input_tokens, output_tokens=output_tokens),
            provider_request_id=(
                provider_request_id if isinstance(provider_request_id, str) else None
            ),
        )

    async def generate_variants(
        self, request: VariantGenerationRequest
    ) -> list[GeneratedCandidate]:
        data = await self._post(
            "/chat/completions",
            {
                "model": self._settings.ai_model,
                "messages": [
                    {"role": "system", "content": GENERATION_SYSTEM_PROMPT},
                    {"role": "user", "content": generation_user_prompt(request)},
                ],
                "max_tokens": 4096,
                "temperature": 0.4,
            },
        )
        content = self._extract_content(data)
        return self._parse_candidates(content)

    async def _post(self, path: str, payload: dict[str, Any]) -> Any:
        try:
            response = await self._client.post(
                path,
                json=payload,
                headers={"Authorization": f"Bearer {self._settings.ai_api_key}"},
            )
        except httpx.TimeoutException as exc:
            raise ProviderTimeoutError("AI provider timed out") from exc
        except httpx.HTTPError as exc:
            raise ProviderError("AI provider request failed") from exc
        if response.status_code >= 400:
            raise ProviderError(f"AI provider returned HTTP {response.status_code}")
        try:
            return response.json()
        except ValueError as exc:
            raise ProviderError("AI provider returned invalid JSON") from exc

    def _extract_content(self, data: Any) -> str:
        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise ProviderError("AI provider response is missing choices") from exc
        if not isinstance(content, str) or not content.strip():
            raise ProviderError("AI provider returned an empty response")
        return content

    def _parse_candidates(self, content: str) -> list[GeneratedCandidate]:
        text = content.strip()
        if text.startswith("```"):
            lines = text.splitlines()
            text = "\n".join(lines[1:]) if len(lines) > 1 else ""
            if text.endswith("```"):
                text = text[:-3].rstrip()
            if text.startswith("json"):
                text = text[4:].lstrip()
        try:
            parsed = json.loads(text)
        except ValueError as exc:
            raise ProviderError("AI provider returned invalid candidate JSON") from exc
        if isinstance(parsed, dict) and isinstance(parsed.get("candidates"), list):
            parsed = parsed["candidates"]
        if not isinstance(parsed, list):
            raise ProviderError("AI provider candidate payload must be a JSON array")
        try:
            return _CANDIDATE_LIST.validate_python(parsed)
        except ValidationError as exc:
            raise ProviderError("AI provider candidates failed validation") from exc


def _non_negative_int(usage: Any, key: str) -> int:
    if not isinstance(usage, dict):
        return 0
    value = usage.get(key)
    if isinstance(value, int) and value >= 0:
        return value
    return 0
