from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Optional


@dataclass
class PlaywrightInspectionResult:
    attempted: bool
    available: bool
    url: str
    title: Optional[str] = None
    extracted_divisions: list[str] | None = None
    note: str = ""


def inspect_js_rendered_page_if_needed(
    url: str,
    *,
    logger: Optional[logging.Logger] = None,
) -> PlaywrightInspectionResult:
    """
    Conditional Playwright fallback invoked ONLY when a permitted public page renders an empty JS shell.
    Never attempts to bypass CAPTCHA, WAF, or authentication gates.
    """
    log = logger or logging.getLogger("narayanganj_pipeline.playwright")
    try:
        from playwright.sync_api import sync_playwright  # type: ignore
    except ImportError:
        msg = "Playwright not installed; httpx static/API inspection used exclusively."
        log.info(msg)
        return PlaywrightInspectionResult(attempted=False, available=False, url=url, note=msg)

    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(url, wait_until="domcontentloaded", timeout=20000)
            title = page.title()
            items = page.locator("#divisionList li").all_inner_texts()
            browser.close()
            return PlaywrightInspectionResult(
                attempted=True,
                available=True,
                url=url,
                title=title,
                extracted_divisions=items,
                note=f"Playwright rendered DOM cleanly ({len(items)} division items).",
            )
    except Exception as exc:
        msg = f"Playwright execution unavailable or aborted cleanly: {exc}"
        log.warning(msg)
        return PlaywrightInspectionResult(attempted=True, available=True, url=url, note=msg)
