from __future__ import annotations

import json
from pathlib import Path

import httpx

from narayanganj_pipeline.collector import NarayanganjCollector
from narayanganj_pipeline.config import PipelineConfig
from narayanganj_pipeline.database import LandGeoDatabase
from narayanganj_pipeline.exporter import PipelineExporter
from narayanganj_pipeline.http_client import ConservativeHttpClient
from narayanganj_pipeline.verifier import DatasetVerifier


def _build_mock_transport(requested_urls: list[str]) -> httpx.MockTransport:
    def handler(request: httpx.Request) -> httpx.Response:
        url = str(request.url)
        requested_urls.append(f"{request.method} {url}")

        if url == "https://dlrms.land.gov.bd/robots.txt" or url == "https://dlrms.land.gov.bd/":
            return httpx.Response(
                403,
                headers={"Server": "Caddy", "X-Blocked": "true"},
                text="Request blocked by WAF.",
            )

        if url == "https://e-porcha.com/robots.txt":
            return httpx.Response(200, text="User-agent: *\nAllow: /\nContent-Signal: search=yes,ai-train=no\n")

        if url.startswith("https://e-porcha.com/"):
            return httpx.Response(200, text="<html><body><h1>e-porcha</h1><p>" + ("Public info " * 20) + "</p></body></html>")

        if url.startswith("https://dlrms-final.onrender.com/api/districts"):
            return httpx.Response(
                200,
                json=[
                    {"ID": 1, "BBS_CODE": "26", "NAME": "ঢাকা", "NAME_EN": "DHAKA", "DIVISION_BBS_CODE": "30"},
                    {"ID": 28, "BBS_CODE": "67", "NAME": "নারায়ণগঞ্জ", "NAME_EN": "NARAYANGANJ", "DIVISION_BBS_CODE": "30"},
                ],
            )

        if url.startswith("https://dlrms-final.onrender.com/api/upazilas"):
            return httpx.Response(
                200,
                json=[
                    {"ID": 201, "BBS_CODE": "02", "NAME": "আড়াইহাজার", "NAME_EN": "ARAIGHAZAR", "IS_CIRCLE": 0, "ROW_STATUS": 1},
                    {"ID": 202, "BBS_CODE": "10", "NAME": "ফতুল্লা রাজস্ব সার্কেল", "NAME_EN": None, "IS_CIRCLE": 1, "LAMS_ID": 16},
                ],
            )

        if any(
            url.startswith(f"https://dlrms-final.onrender.com/api/{ep}")
            for ep in ("unions", "thanas", "municipalities", "circles")
        ):
            return httpx.Response(404, text="Not Found")

        if url.startswith("https://dlrms-final.onrender.com/api/surveys"):
            return httpx.Response(
                200,
                json=[
                    {"SURVEY_ID": 1, "LOCAL_NAME": "সি এস", "SURVEY_ORDER": 1},
                    {"SURVEY_ID": 3, "LOCAL_NAME": "আর এস", "SURVEY_ORDER": 3},
                ],
            )

        if url.startswith("https://dlrms-final.onrender.com/api/mouzas"):
            params = dict(request.url.params)
            upz = params.get("UPAZILA_BBS_CODE")
            surv = params.get("SURVEY_ID")
            page_token = params.get("pageToken")

            if upz == "02" and surv == "1" and not page_token:
                return httpx.Response(
                    200,
                    json={
                        "items": [
                            {
                                "ID": 9001,
                                "MOUZA_ID": "M-001",
                                "UUID": "uuid-m-001",
                                "MOUZA_NAME": "দুপ্তারা",
                                "JL_NUMBER": "15",
                                "DISTRICT_NAME": "ঢাকা",
                                "UPAZILA_NAME": "আড়াইহাজার",
                                "SURVEY_ID": 1,
                                "SURVEY_NAME": "সি এস",
                                "SURVEY_NAME_EN": "CS",
                                "OWNERS": ["MUST BE STRIPPED"],
                            }
                        ],
                        "nextPageToken": "page-2",
                    },
                )
            if upz == "02" and surv == "1" and page_token == "page-2":
                return httpx.Response(
                    200,
                    json={
                        "items": [
                            {
                                "ID": 9002,
                                "MOUZA_ID": "M-002",
                                "UUID": "uuid-m-002",
                                "MOUZA_NAME": "দুপ্তারা",
                                "JL_NUMBER": "15",
                                "DISTRICT_NAME": "নারায়ণগঞ্জ",
                                "UPAZILA_NAME": "আড়াইহাজার",
                                "SURVEY_ID": 1,
                                "SURVEY_NAME": "সি এস",
                                "SURVEY_NAME_EN": "CS",
                            }
                        ],
                        "nextPageToken": None,
                    },
                )
            if upz == "10" and surv == "3":
                return httpx.Response(
                    200,
                    json=[
                        {
                            "ID": 9003,
                            "MOUZA_ID": "M-003",
                            "UUID": "uuid-m-003",
                            "MOUZA_NAME": "কাশিপুর",
                            "JL_NUMBER": None,
                            "DISTRICT_NAME": "নারায়ণগঞ্জ",
                            "UPAZILA_NAME": "ফতুল্লা",
                            "SURVEY_ID": 3,
                            "SURVEY_NAME": "আর এস",
                            "SURVEY_NAME_EN": "RS",
                        },
                        {
                            "ID": 9004,
                            "MOUZA_ID": "M-004",
                            "UUID": "uuid-m-004",
                            "MOUZA_NAME": "এনায়েতনগর",
                            "JL_NUMBER": "18",
                            "DISTRICT_NAME": "নারায়ণগঞ্জ",
                            "UPAZILA_NAME": "ফতুল্লা",
                            "SURVEY_ID": 3,
                            "SURVEY_NAME": "আর এস",
                            "SURVEY_NAME_EN": "RS",
                        },
                    ],
                )
            return httpx.Response(200, json=[])

        if url == "https://dlrms-final.onrender.com/api/mouza-map-tech":
            body = json.loads(request.content.decode("utf-8"))
            path = body.get("path")
            if path == "0:/Narayanganj/":
                return httpx.Response(
                    200,
                    json={
                        "nextPageToken": None,
                        "data": {
                            "files": [
                                {
                                    "kind": "drive#file",
                                    "name": "ফতুল্লা",
                                    "id": "folder-fatulla",
                                    "driveId": "drv-1",
                                    "mimeType": "application/vnd.google-apps.folder",
                                    "link": None,
                                }
                            ]
                        },
                    },
                )
            if path == "0:/Narayanganj/ফতুল্লা/":
                return httpx.Response(
                    200,
                    json={
                        "nextPageToken": None,
                        "data": {
                            "files": [
                                {
                                    "kind": "drive#file",
                                    "name": "Enayetnagar_JL-18_RS_Sheet_1.pdf",
                                    "id": "file-enayet-1",
                                    "driveId": "drv-1",
                                    "mimeType": "application/pdf",
                                    "modifiedTime": "2025-07-20T06:44:37.573Z",
                                    "link": "https://example.invalid/never-download.pdf",
                                }
                            ]
                        },
                    },
                )
            return httpx.Response(200, json={"nextPageToken": None, "data": {"files": []}})

        return httpx.Response(404, text="Unexpected mock route")

    return httpx.MockTransport(handler)


def test_end_to_end_mocked_pipeline(tmp_path: Path) -> None:
    requested: list[str] = []
    transport = _build_mock_transport(requested)
    cfg = PipelineConfig(
        request_delay_seconds=0.0,
        map_request_delay_seconds=0.0,
        retry_backoff_seconds=0.0,
        output_dir=tmp_path / "output",
        reports_dir=tmp_path / "reports",
        logs_dir=tmp_path / "logs",
        db_path=tmp_path / "output" / "test.sqlite",
        enable_playwright_fallback=False,
    )

    with (
        LandGeoDatabase(cfg.db_path) as db,
        httpx.Client(transport=transport) as raw_client,
        ConservativeHttpClient(
            run_id="test-run-1",
            user_agent=cfg.user_agent,
            default_delay_seconds=0.0,
            retry_backoff_seconds=0.0,
            client=raw_client,
        ) as http_client,
    ):
        summary = NarayanganjCollector(cfg, db, http_client).collect(run_id="test-run-1")
        assert summary.status == "completed"
        assert summary.policy_checks["dlrms.land.gov.bd"].waf_or_captcha_detected is True
        assert summary.counts["districts"] == 1
        assert summary.counts["upazilas"] == 1
        assert summary.counts["thanas"] == 1
        assert summary.counts["mouzas"] == 4
        assert summary.counts["mouza_maps_metadata"] == 1
        # Map binary download link must never be fetched
        assert not any("never-download" in r for r in requested)

        verification = DatasetVerifier(db.conn, "67").verify()
        assert verification.scope_validation["all_records_scoped_to_bbs_67"] is True
        assert verification.hierarchy_validation["fk_integrity_valid"] is True
        assert verification.hierarchy_validation["checks"]["maps_matched_to_mouza"] == 1
        assert verification.duplicate_detection["same_subdivision_survey_name_jl_count"] == 1
        assert verification.missing_identifiers["counts"]["mouzas_missing_jl_number"] == 1
        assert verification.verified_vs_unverified["mouzas"] == {"verified": 0, "unverified": 4}

        exported = PipelineExporter(db.conn, cfg.output_dir, cfg.reports_dir).export_all(
            verification, summary
        )
        assert len(exported["reports"]) == 4
        assert (cfg.output_dir / "csv" / "verified" / "mouzas.csv").exists()
        assert (cfg.output_dir / "csv" / "unverified" / "mouzas.csv").exists()
        assert (cfg.output_dir / "json" / "unverified_dataset.json").exists()
        final_md = (cfg.reports_dir / "final_coverage_report.md").read_text(encoding="utf-8")
        assert "Narayanganj District (BBS 67)" in final_md
        assert "দুপ্তারা" not in final_md or "আড়াইহাজার" in final_md
