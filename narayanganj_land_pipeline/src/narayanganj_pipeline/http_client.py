from __future__ import annotations

import hashlib
import logging
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Optional

import httpx

from .models import AccessStatus, ProvenanceRecord


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


@dataclass
class HttpFetchOutcome:
    url: str
    status_code: Optional[int]
    text: str
    json_data: Optional[Any]
    headers: dict[str, str]
    access_status: AccessStatus
    provenance: ProvenanceRecord
    error_message: Optional[str] = None


class ConservativeHttpClient:
    """Rate-limited, timeout-bounded httpx client that records provenance and never bypasses WAF/403 blocks."""

    def __init__(
        self,
        run_id: str,
        user_agent: str,
        default_delay_seconds: float = 2.2,
        timeout_seconds: float = 25.0,
        max_retries: int = 3,
        retry_backoff_seconds: float = 2.5,
        client: Optional[httpx.Client] = None,
        logger: Optional[logging.Logger] = None,
    ) -> None:
        self.run_id = run_id
        self.user_agent = user_agent
        self.default_delay_seconds = default_delay_seconds
        self.timeout_seconds = timeout_seconds
        self.max_retries = max_retries
        self.retry_backoff_seconds = retry_backoff_seconds
        self.logger = logger or logging.getLogger("narayanganj_pipeline.http")
        self._last_request_ts: float = 0.0
        self._owns_client = client is None
        self._client = client or httpx.Client(
            timeout=httpx.Timeout(timeout_seconds),
            follow_redirects=True,
            headers={"User-Agent": user_agent},
        )
        self.provenance_log: list[ProvenanceRecord] = []

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> "ConservativeHttpClient":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    def _pace(self, delay_seconds: Optional[float] = None) -> None:
        wait_target = self.default_delay_seconds if delay_seconds is None else delay_seconds
        if wait_target <= 0:
            return
        elapsed = time.monotonic() - self._last_request_ts
        if self._last_request_ts > 0 and elapsed < wait_target:
            time.sleep(wait_target - elapsed)

    @staticmethod
    def _classify_response(status_code: int, headers: dict[str, str], body_text: str) -> AccessStatus:
        lower_headers = {k.lower(): v.lower() for k, v in headers.items()}
        lower_body = body_text[:4000].lower()

        if status_code == 403:
            if (
                "x-blocked" in lower_headers
                or "waf" in lower_body
                or "request blocked" in lower_body
                or "cloudflare" in lower_body
            ):
                return AccessStatus.BLOCKED_WAF
            return AccessStatus.BLOCKED_AUTH_OR_CAPTCHA
        if status_code == 401:
            return AccessStatus.BLOCKED_AUTH_OR_CAPTCHA
        if status_code == 429:
            return AccessStatus.RATE_LIMITED
        if status_code == 404:
            return AccessStatus.NOT_FOUND
        if 200 <= status_code < 300:
            if "captcha" in lower_body and "g-recaptcha" in lower_body:
                return AccessStatus.BLOCKED_AUTH_OR_CAPTCHA
            return AccessStatus.ALLOWED
        return AccessStatus.ERROR

    def request(
        self,
        method: str,
        url: str,
        *,
        source_name: str,
        source_tier: str,
        access_policy_note: str = "",
        params: Optional[dict[str, Any]] = None,
        json_body: Optional[dict[str, Any]] = None,
        delay_seconds: Optional[float] = None,
        expect_json: bool = False,
    ) -> HttpFetchOutcome:
        last_exc: Optional[Exception] = None
        response: Optional[httpx.Response] = None
        full_url = url
        if params:
            query_str = "&".join(f"{k}={v}" for k, v in params.items())
            full_url = f"{url}?{query_str}" if "?" not in url else f"{url}&{query_str}"

        for attempt in range(1, self.max_retries + 1):
            self._pace(delay_seconds)
            self._last_request_ts = time.monotonic()
            try:
                response = self._client.request(
                    method=method.upper(),
                    url=url,
                    params=params,
                    json=json_body,
                )
                status = response.status_code
                headers_dict = {k: v for k, v in response.headers.items()}
                body_text = response.text or ""
                access_status = self._classify_response(status, headers_dict, body_text)

                # Never retry explicit WAF/Auth blocks or 404 not-found
                if access_status in {
                    AccessStatus.BLOCKED_WAF,
                    AccessStatus.BLOCKED_AUTH_OR_CAPTCHA,
                    AccessStatus.NOT_FOUND,
                }:
                    self.logger.warning(
                        "%s %s -> %s (%s)",
                        method.upper(),
                        full_url,
                        status,
                        access_status.value,
                    )
                    break

                # Retry transient 429 / 5xx server busy responses politely
                if status in {429, 500, 502, 503, 504} and attempt < self.max_retries:
                    backoff = self.retry_backoff_seconds * (2 ** (attempt - 1))
                    self.logger.warning(
                        "Transient HTTP %s on %s (attempt %d/%d); backing off %.1fs",
                        status,
                        full_url,
                        attempt,
                        self.max_retries,
                        backoff,
                    )
                    time.sleep(backoff)
                    continue

                break
            except (httpx.TimeoutException, httpx.TransportError) as exc:
                last_exc = exc
                if attempt < self.max_retries:
                    backoff = self.retry_backoff_seconds * (2 ** (attempt - 1))
                    self.logger.warning(
                        "Transport error on %s (%s, attempt %d/%d); retrying in %.1fs",
                        full_url,
                        type(exc).__name__,
                        attempt,
                        self.max_retries,
                        backoff,
                    )
                    time.sleep(backoff)
                else:
                    self.logger.error("Request failed after retries: %s (%s)", full_url, exc)

        collected_at = utc_now_iso()
        prov_id = str(uuid.uuid4())

        if response is None:
            prov = ProvenanceRecord(
                provenance_id=prov_id,
                run_id=self.run_id,
                source_name=source_name,
                source_tier=source_tier,
                source_url=full_url,
                http_status=None,
                access_status=AccessStatus.ERROR.value,
                access_policy_note=f"{access_policy_note} | transport_error={last_exc}".strip(" |"),
                collected_at=collected_at,
                content_hash=None,
            )
            self.provenance_log.append(prov)
            return HttpFetchOutcome(
                url=full_url,
                status_code=None,
                text="",
                json_data=None,
                headers={},
                access_status=AccessStatus.ERROR,
                provenance=prov,
                error_message=str(last_exc) if last_exc else "Unknown transport error",
            )

        body_bytes = response.content or b""
        content_hash = hashlib.sha256(body_bytes).hexdigest()
        headers_dict = {k: v for k, v in response.headers.items()}
        body_text = response.text or ""
        access_status = self._classify_response(response.status_code, headers_dict, body_text)
        parsed_json: Optional[Any] = None
        if expect_json and access_status == AccessStatus.ALLOWED:
            try:
                parsed_json = response.json()
            except ValueError:
                parsed_json = None

        note_parts = [p for p in [access_policy_note] if p]
        if json_body and "path" in json_body:
            note_parts.append(f"path={json_body['path']}")
        if access_status != AccessStatus.ALLOWED:
            note_parts.append(f"status={response.status_code}:{access_status.value}")

        prov = ProvenanceRecord(
            provenance_id=prov_id,
            run_id=self.run_id,
            source_name=source_name,
            source_tier=source_tier,
            source_url=str(response.url) if method.upper() == "GET" else full_url,
            http_status=response.status_code,
            access_status=access_status.value,
            access_policy_note=" | ".join(note_parts),
            collected_at=collected_at,
            content_hash=content_hash,
        )
        self.provenance_log.append(prov)
        return HttpFetchOutcome(
            url=full_url,
            status_code=response.status_code,
            text=body_text,
            json_data=parsed_json,
            headers=headers_dict,
            access_status=access_status,
            provenance=prov,
            error_message=None if access_status == AccessStatus.ALLOWED else body_text[:300],
        )
