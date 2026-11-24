from __future__ import annotations

import logging
import urllib.robotparser
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from .http_client import ConservativeHttpClient, utc_now_iso
from .models import AccessStatus, PolicyCheckResult
from .normalizer import normalize_nfc


def inspect_source_policy(
    http_client: ConservativeHttpClient,
    *,
    source_name: str,
    source_tier: str,
    base_url: str,
    terms_paths: tuple[str, ...] = (),
    logger: logging.Logger | None = None,
) -> PolicyCheckResult:
    log = logger or logging.getLogger("narayanganj_pipeline.policy")
    robots_url = urljoin(base_url, "/robots.txt")

    robots_outcome = http_client.request(
        "GET",
        robots_url,
        source_name=source_name,
        source_tier=source_tier,
        access_policy_note="Preflight robots.txt compliance inspection",
    )

    robots_allows_root = True
    robots_notes_parts: list[str] = []
    if robots_outcome.access_status == AccessStatus.ALLOWED:
        rp = urllib.robotparser.RobotFileParser()
        rp.parse(robots_outcome.text.splitlines())
        robots_allows_root = rp.can_fetch(http_client.user_agent, base_url)
        signals = [
            line.strip()
            for line in robots_outcome.text.splitlines()
            if "content-signal" in line.lower() or "crawl-delay" in line.lower()
        ]
        robots_notes_parts.append(
            f"robots.txt HTTP {robots_outcome.status_code}; can_fetch('/')={robots_allows_root}"
        )
        if signals:
            robots_notes_parts.append("; ".join(signals[:3]))
    elif robots_outcome.access_status == AccessStatus.NOT_FOUND:
        robots_notes_parts.append("robots.txt returned 404 (no explicit robots exclusion file)")
    else:
        robots_allows_root = False
        robots_notes_parts.append(
            f"robots.txt inaccessible: HTTP {robots_outcome.status_code} ({robots_outcome.access_status.value})"
        )

    home_outcome = http_client.request(
        "GET",
        base_url,
        source_name=source_name,
        source_tier=source_tier,
        access_policy_note="Preflight landing page & terms discovery",
    )

    requires_js = False
    terms_snippets: list[str] = []
    if home_outcome.access_status == AccessStatus.ALLOWED and home_outcome.text:
        soup = BeautifulSoup(home_outcome.text, "html.parser")
        body_text = normalize_nfc(soup.get_text(" ", strip=True)) or ""
        if len(body_text) < 120 and ("<div id=\"app\"" in home_outcome.text or "<div id=\"root\"" in home_outcome.text):
            requires_js = True
        for path in terms_paths:
            t_url = urljoin(base_url, path)
            t_out = http_client.request(
                "GET",
                t_url,
                source_name=source_name,
                source_tier=source_tier,
                access_policy_note=f"Preflight policy/terms inspection ({path})",
            )
            if t_out.access_status == AccessStatus.ALLOWED and t_out.text:
                t_soup = BeautifulSoup(t_out.text, "html.parser")
                for tag in t_soup(["script", "style"]):
                    tag.decompose()
                t_text = normalize_nfc(t_soup.get_text(" ", strip=True)) or ""
                terms_snippets.append(f"{path}: {t_text[:320]}")

    overall_status = home_outcome.access_status
    if not robots_allows_root and overall_status == AccessStatus.ALLOWED:
        overall_status = AccessStatus.DISALLOWED_ROBOTS

    evidence_headers = {
        k: v
        for k, v in home_outcome.headers.items()
        if k.lower() in {"server", "x-blocked", "content-type", "cf-ray", "x-turbo-charged-by"}
    }

    result = PolicyCheckResult(
        source_name=source_name,
        base_url=base_url,
        robots_url=robots_url,
        robots_http_status=robots_outcome.status_code,
        robots_allows_root=robots_allows_root,
        robots_notes=" | ".join(robots_notes_parts),
        home_http_status=home_outcome.status_code,
        access_status=overall_status,
        waf_or_captcha_detected=overall_status in {
            AccessStatus.BLOCKED_WAF,
            AccessStatus.BLOCKED_AUTH_OR_CAPTCHA,
        },
        requires_js_rendering=requires_js,
        terms_summary=" || ".join(terms_snippets),
        evidence_headers=evidence_headers,
        checked_at=utc_now_iso(),
    )
    log.info(
        "Policy check [%s]: access=%s robots_http=%s home_http=%s",
        source_name,
        result.access_status.value,
        result.robots_http_status,
        result.home_http_status,
    )
    return result
