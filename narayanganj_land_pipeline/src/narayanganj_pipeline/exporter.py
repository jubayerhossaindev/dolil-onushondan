from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from typing import Any, Optional

import pandas as pd

from .collector import CollectionSummary
from .verifier import VerificationReport


ALL_TABLES = (
    "districts",
    "upazilas",
    "thanas",
    "unions",
    "municipalities",
    "survey_types",
    "mouzas",
    "mouza_maps_metadata",
    "source_provenance",
    "collection_runs",
)

ENTITY_TABLES = (
    "districts",
    "upazilas",
    "thanas",
    "unions",
    "municipalities",
    "survey_types",
    "mouzas",
    "mouza_maps_metadata",
)


class PipelineExporter:
    """Exports SQLite data to CSV, JSON (full/verified/unverified), and Markdown audit reports."""

    def __init__(
        self,
        conn: sqlite3.Connection,
        output_dir: Path,
        reports_dir: Path,
    ) -> None:
        self.conn = conn
        self.output_dir = output_dir
        self.reports_dir = reports_dir
        self.csv_dir = output_dir / "csv"
        self.csv_verified_dir = self.csv_dir / "verified"
        self.csv_unverified_dir = self.csv_dir / "unverified"
        self.json_dir = output_dir / "json"

    def export_all(
        self,
        verification: VerificationReport,
        collection_summary: Optional[CollectionSummary] = None,
    ) -> dict[str, list[Path]]:
        for directory in (
            self.csv_dir,
            self.csv_verified_dir,
            self.csv_unverified_dir,
            self.json_dir,
            self.reports_dir,
        ):
            directory.mkdir(parents=True, exist_ok=True)

        csv_paths: list[Path] = []
        json_paths: list[Path] = []
        report_paths: list[Path] = []

        full_payload: dict[str, list[dict[str, Any]]] = {}
        verified_payload: dict[str, list[dict[str, Any]]] = {}
        unverified_payload: dict[str, list[dict[str, Any]]] = {}

        for table in ALL_TABLES:
            df = pd.read_sql_query(f"SELECT * FROM {table}", self.conn)
            out_csv = self.csv_dir / f"{table}.csv"
            df.to_csv(out_csv, index=False, encoding="utf-8-sig")
            csv_paths.append(out_csv)
            full_payload[table] = df.where(pd.notnull(df), None).to_dict(orient="records")

            if table in ENTITY_TABLES:
                v_df = df[df["verification_status"] == "verified"]
                u_df = df[df["verification_status"] == "unverified"]

                v_csv = self.csv_verified_dir / f"{table}.csv"
                u_csv = self.csv_unverified_dir / f"{table}.csv"
                v_df.to_csv(v_csv, index=False, encoding="utf-8-sig")
                u_df.to_csv(u_csv, index=False, encoding="utf-8-sig")
                csv_paths.extend([v_csv, u_csv])

                verified_payload[table] = v_df.where(pd.notnull(v_df), None).to_dict(orient="records")
                unverified_payload[table] = u_df.where(pd.notnull(u_df), None).to_dict(orient="records")

        for filename, payload in (
            ("full_dataset.json", full_payload),
            ("verified_dataset.json", verified_payload),
            ("unverified_dataset.json", unverified_payload),
            ("verification_summary.json", verification.to_dict()),
        ):
            out_json = self.json_dir / filename
            out_json.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            json_paths.append(out_json)

        report_paths.append(self._write_source_attribution_report(verification, collection_summary))
        report_paths.append(self._write_duplicate_detection_report(verification))
        report_paths.append(self._write_missing_data_report(verification, collection_summary))
        report_paths.append(self._write_final_coverage_report(verification, collection_summary))

        return {
            "csv": csv_paths,
            "json": json_paths,
            "reports": report_paths,
        }

    def _write_source_attribution_report(
        self,
        verification: VerificationReport,
        summary: Optional[CollectionSummary],
    ) -> Path:
        path = self.reports_dir / "source_attribution_report.md"
        lines: list[str] = [
            "# Source Attribution & Access Policy Report — Narayanganj District (BBS 67)",
            "",
            "## 1. Inspected Target Sources & Compliance Findings",
            "",
        ]
        if summary and summary.policy_checks:
            lines.append("| Source | Base URL | robots.txt | Home Status | Access Classification | Evidence |")
            lines.append("|---|---|---|---|---|---|")
            for name, chk in summary.policy_checks.items():
                headers_str = ", ".join(f"{k}={v}" for k, v in chk.evidence_headers.items()) or "-"
                lines.append(
                    f"| `{name}` | `{chk.base_url}` | HTTP {chk.robots_http_status} (`{chk.robots_notes}`) "
                    f"| HTTP {chk.home_http_status} | `{chk.access_status.value}` | `{headers_str}` |"
                )
            lines.append("")
            for name, chk in summary.policy_checks.items():
                if chk.terms_summary:
                    lines.extend(
                        [
                            f"### Terms & Policy Notes — `{name}`",
                            f"- Checked at: `{chk.checked_at}`",
                            f"- Extracted terms excerpt: {chk.terms_summary}",
                            "",
                        ]
                    )
            lines.extend(
                [
                    "### Rendering Inspection",
                    f"- {summary.playwright_note}",
                    "",
                ]
            )
        else:
            lines.append(
                "- Policy preflight details were loaded from `source_provenance` database records below."
            )
            lines.append("")

        lines.extend(
            [
                "## 2. Recorded Provenance Ledger Summary (`source_provenance`)",
                "",
                "| Source Name | Tier | Access Status | HTTP Status | Request Count | First Collected (UTC) | Last Collected (UTC) |",
                "|---|---|---|---|---:|---|---|",
            ]
        )
        for row in verification.provenance_summary:
            lines.append(
                f"| `{row['source_name']}` | `{row['source_tier']}` | `{row['access_status']}` "
                f"| {row['http_status']} | {row['request_count']} | `{row['first_at']}` | `{row['last_at']}` |"
            )

        lines.extend(
            [
                "",
                "## 3. Ethical & Legal Guardrails Applied",
                "- No WAF, CAPTCHA, login, or rate-limit bypasses were attempted; HTTP 403 WAF responses on `dlrms.land.gov.bd` stopped requests to that host immediately.",
                "- Personal landowner, khatian, NID, phone, and guardian endpoints (`/khatian`) were never queried, and prohibited PII keys are stripped defensively.",
                "- Public map binaries (`link` / `/api/mouza-map-tech-file`) were not downloaded; only directory/file metadata was recorded.",
                "- Because `dlrms.land.gov.bd` was blocked by WAF (`HTTP 403`), records obtained solely from reference `e-porcha.com` public endpoints remain marked `unverified` in both the database and exports.",
                "",
            ]
        )
        path.write_text("\n".join(lines), encoding="utf-8")
        return path

    def _write_duplicate_detection_report(self, verification: VerificationReport) -> Path:
        path = self.reports_dir / "duplicate_detection_report.md"
        dup = verification.duplicate_detection
        lines: list[str] = [
            "# Duplicate Detection Report — Narayanganj District (BBS 67)",
            "",
            "## 1. Summary Metrics",
            f"- Total `mouzas` rows audited: **{verification.table_counts.get('mouzas', 0)}**",
            f"- Duplicate `mouza_uuid` values: **{dup.get('duplicate_mouza_uuid_count', 0)}**",
            f"- Same `(subdivision, survey_id, canonical_name_key, jl_ascii)` collisions: **{dup.get('same_subdivision_survey_name_jl_count', 0)}**",
            f"- Same `(subdivision, survey_id, jl_ascii)` with distinct Bengali Mouza names: **{dup.get('same_subdivision_survey_jl_different_name_count', 0)}**",
            f"- Cross-survey longitudinal Mouza keys (expected same Mouza appearing across CS/SA/RS/BS): **{dup.get('cross_survey_longitudinal_mouza_keys', 0)}**",
            f"- Duplicate Google Drive file IDs in `mouza_maps_metadata`: **{dup.get('duplicate_map_drive_file_id_count', 0)}**",
            "",
            "## 2. Same-Survey Name + JL Collisions (Potential Source Duplicates)",
        ]
        samples = dup.get("same_subdivision_survey_name_jl_samples", [])
        if samples:
            for item in samples:
                names = ", ".join(r["name_bn"] for r in item["records"])
                ids = ", ".join(str(r["source_record_id"]) for r in item["records"])
                lines.append(
                    f"- Subdivision `{item['subdivision_bbs']}`, Survey `{item['survey_id']}`, JL `{item['jl_ascii']}` ({item['count']} rows): {names} [source IDs: {ids}]"
                )
        else:
            lines.append("- None detected within the same `(subdivision, survey_id, canonical_name_key, jl_ascii)` tuple.")

        lines.extend(
            [
                "",
                "## 3. Same-Survey JL Sharing Multiple Distinct Names",
            ]
        )
        diff_samples = dup.get("same_subdivision_survey_jl_different_name_samples", [])
        if diff_samples:
            for item in diff_samples:
                rec_desc = "; ".join(f"{r['name_bn']} (ID={r['source_record_id']})" for r in item["records"])
                lines.append(
                    f"- Subdivision `{item['subdivision_bbs']}`, Survey `{item['survey_id']}`, JL `{item['jl_ascii']}`: {rec_desc}"
                )
        else:
            lines.append("- None detected.")

        lines.extend(
            [
                "",
                "## 4. Duplicate Map Drive IDs",
            ]
        )
        dup_maps = dup.get("duplicate_map_drive_file_ids", [])
        if dup_maps:
            for m in dup_maps:
                lines.append(f"- `drive_file_id={m['drive_file_id']}` appears **{m['cnt']}** times.")
        else:
            lines.append("- None detected across collected public map metadata.")

        lines.append("")
        path.write_text("\n".join(lines), encoding="utf-8")
        return path

    def _write_missing_data_report(
        self,
        verification: VerificationReport,
        summary: Optional[CollectionSummary],
    ) -> Path:
        path = self.reports_dir / "missing_data_report.md"
        missing = verification.missing_identifiers.get("counts", {})
        lines: list[str] = [
            "# Missing Identifiers & Data Gap Report — Narayanganj District (BBS 67)",
            "",
            "## 1. Field & Identifier Completeness",
            "",
            "| Check | Missing Count |",
            "|---|---:|",
        ]
        for key, val in missing.items():
            lines.append(f"| `{key}` | {val} |")

        lines.extend(
            [
                "",
                "## 2. Administrative & Endpoint Coverage Gaps",
                f"- `unions` collected: **{verification.table_counts.get('unions', 0)}**",
                f"- `municipalities` collected: **{verification.table_counts.get('municipalities', 0)}**",
                f"- `thanas` (Revenue Circles) missing parent `upazila_id` link: **{verification.hierarchy_validation.get('checks', {}).get('thanas_without_parent_upazila', 0)}** (API does not declare parent Upazila for Revenue Circles)",
                f"- `mouza_maps_metadata` unmatched to a Mouza row: **{verification.hierarchy_validation.get('checks', {}).get('maps_without_matched_mouza', 0)}** (unmatched conservatively left NULL rather than guessed)",
            ]
        )

        if summary and summary.endpoint_probes:
            lines.extend(
                [
                    "",
                    "### Secondary Endpoint Probes",
                    "",
                    "| Endpoint | HTTP Status | Access Status | Records Parsed | Note |",
                    "|---|---:|---|---:|---|",
                ]
            )
            for p in summary.endpoint_probes:
                lines.append(
                    f"| `{p.endpoint}` | {p.http_status} | `{p.access_status}` | {p.records_found} | {p.note} |"
                )

        if summary and summary.map_folders_failed:
            lines.extend(["", "### Map Directory Errors"])
            for err in summary.map_folders_failed:
                lines.append(f"- `{err}`")

        jl_samples = verification.missing_identifiers.get("mouzas_missing_jl_samples", [])
        lines.extend(["", "## 3. Sample Mouzas Missing JL Number"])
        if jl_samples:
            for s in jl_samples:
                lines.append(
                    f"- ID={s['id']} | `MOUZA_ID={s['mouza_identifier']}` | `{s['name_bn']}` | Survey `{s['survey_id']}` | `{s['source_reported_upazila_bn']}`"
                )
        else:
            lines.append("- Zero Mouzas missing JL numbers.")

        lines.append("")
        path.write_text("\n".join(lines), encoding="utf-8")
        return path

    def _write_final_coverage_report(
        self,
        verification: VerificationReport,
        summary: Optional[CollectionSummary],
    ) -> Path:
        path = self.reports_dir / "final_coverage_report.md"
        scope = verification.scope_validation
        hier = verification.hierarchy_validation
        lines: list[str] = [
            "# Final Coverage & Verification Report — Narayanganj District (BBS 67)",
            "",
            "## 1. Verified vs. Unverified Record Separation",
            "",
            "| Table | Total | Verified | Unverified |",
            "|---|---:|---:|---:|",
        ]
        for table in ENTITY_TABLES:
            split = verification.verified_vs_unverified.get(table, {})
            total = verification.table_counts.get(table, 0)
            lines.append(
                f"| `{table}` | {total} | {split.get('verified', 0)} | {split.get('unverified', 0)} |"
            )

        lines.extend(
            [
                "",
                "## 2. Scope & Hierarchy Integrity",
                f"- Strictly scoped to Narayanganj (`BBS 67`): **{scope.get('all_records_scoped_to_bbs_67')}**",
                f"- Foreign-key hierarchy integrity valid: **{hier.get('fk_integrity_valid')}**",
                "- Historical `DISTRICT_NAME` values reported by source inside Narayanganj (`BBS 67`) Mouza records:",
            ]
        )
        for lbl in scope.get("mouza_source_reported_district_labels", []):
            lines.append(
                f"  - `{lbl['reported_district_bn']}`: {lbl['count']} rows (alias_match={lbl['matches_narayanganj_alias']})"
            )

        lines.extend(
            [
                "",
                "## 3. Breakdown by Upazila & Revenue Circle / Thana",
                "",
                "| Type | BBS Code | Bengali Name | English Name | Mouza Survey Rows | Distinct (Name + JL) Keys |",
                "|---|---|---|---|---:|---:|",
            ]
        )
        for sub in verification.subdivision_breakdown:
            lines.append(
                f"| `{sub['subdivision_type']}` | `{sub['bbs_code']}` | {sub['name_bn']} "
                f"| {sub['name_en'] or '-'} | {sub['mouza_rows']} | {sub['distinct_mouzas']} |"
            )

        lines.extend(
            [
                "",
                "## 4. Breakdown by Survey Type",
                "",
                "| Survey ID | Code (EN) | Bengali Name | Order | Mouza Rows |",
                "|---|---|---|---:|---:|",
            ]
        )
        for srv in verification.survey_breakdown:
            lines.append(
                f"| `{srv['survey_id']}` | `{srv['survey_code_en'] or '-'}` | {srv['name_bn']} "
                f"| {srv['survey_order'] if srv['survey_order'] is not None else '-'} | {srv['mouza_rows']} |"
            )

        lines.extend(
            [
                "",
                "## 5. Honest Completeness Assessment",
                "- Official `dlrms.land.gov.bd` access was blocked at the WAF layer (`HTTP 403`), so all collected records come from the public geographic endpoints exposed by `e-porcha.com` and remain classified as `unverified` pending direct official verification.",
                "- Upazilas, Revenue Circles (`thanas` table), available Surveys, Mouzas (with JL numbers and Mouza UUIDs), and public Mouza Map directory metadata were collected where publicly exposed.",
                "- Separate Union Parishad and Municipality (Pourashava / City Corporation) geographic endpoints are not exposed by the public `e-porcha.com` API (`HTTP 404`); therefore `unions` and `municipalities` contain 0 fabricated records.",
            ]
        )
        if summary and summary.warnings:
            lines.extend(["", "### Run Warnings"])
            for w in summary.warnings:
                lines.append(f"- {w}")

        lines.append("")
        path.write_text("\n".join(lines), encoding="utf-8")
        return path
