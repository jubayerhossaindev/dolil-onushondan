from __future__ import annotations

import sqlite3
from dataclasses import asdict, dataclass, field
from typing import Any

from .normalizer import bn_digits_to_ascii, is_narayanganj_district


@dataclass
class VerificationReport:
    target_district_bbs: str
    target_district_name_bn: str
    table_counts: dict[str, int] = field(default_factory=dict)
    verified_vs_unverified: dict[str, dict[str, int]] = field(default_factory=dict)
    scope_validation: dict[str, Any] = field(default_factory=dict)
    duplicate_detection: dict[str, Any] = field(default_factory=dict)
    hierarchy_validation: dict[str, Any] = field(default_factory=dict)
    missing_identifiers: dict[str, Any] = field(default_factory=dict)
    subdivision_breakdown: list[dict[str, Any]] = field(default_factory=list)
    survey_breakdown: list[dict[str, Any]] = field(default_factory=list)
    provenance_summary: list[dict[str, Any]] = field(default_factory=list)
    latest_run: dict[str, Any] | None = None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class DatasetVerifier:
    """Runs post-collection scope, duplicate, hierarchy, missing-ID, and provenance audits."""

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

    def __init__(self, conn: sqlite3.Connection, target_district_bbs: str = "67") -> None:
        self.conn = conn
        self.target_district_bbs = target_district_bbs

    def verify(self) -> VerificationReport:
        district_row = self.conn.execute(
            "SELECT * FROM districts WHERE bbs_code = ? LIMIT 1",
            (self.target_district_bbs,),
        ).fetchone()
        target_name_bn = district_row["name_bn"] if district_row else "নারায়ণগঞ্জ"

        report = VerificationReport(
            target_district_bbs=self.target_district_bbs,
            target_district_name_bn=target_name_bn,
        )

        # 1. Table counts & verified/unverified split
        for table in (*self.ENTITY_TABLES, "source_provenance", "collection_runs"):
            c_row = self.conn.execute(f"SELECT COUNT(*) AS c FROM {table}").fetchone()
            report.table_counts[table] = int(c_row["c"])

        for table in self.ENTITY_TABLES:
            rows = self.conn.execute(
                f"SELECT verification_status, COUNT(*) AS c FROM {table} GROUP BY verification_status"
            ).fetchall()
            split = {"verified": 0, "unverified": 0}
            for r in rows:
                status_val = str(r["verification_status"]).lower()
                split[status_val] = int(r["c"])
            report.verified_vs_unverified[table] = split

        # 2. Scope validation (every record must belong to Narayanganj BBS 67)
        out_of_scope_counts: dict[str, int] = {}
        for table in self.ENTITY_TABLES:
            bbs_col = "bbs_code" if table == "districts" else "district_bbs_code"
            row = self.conn.execute(
                f"SELECT COUNT(*) AS c FROM {table} WHERE COALESCE({bbs_col}, '') != ?",
                (self.target_district_bbs,),
            ).fetchone()
            out_of_scope_counts[table] = int(row["c"])

        hist_label_rows = self.conn.execute(
            """
            SELECT COALESCE(source_reported_district_bn, '<NULL>') AS reported_district,
                   COUNT(*) AS c
            FROM mouzas
            GROUP BY COALESCE(source_reported_district_bn, '<NULL>')
            ORDER BY c DESC
            """
        ).fetchall()
        historical_labels = [
            {
                "reported_district_bn": r["reported_district"],
                "count": int(r["c"]),
                "matches_narayanganj_alias": is_narayanganj_district(name_bn=r["reported_district"]),
            }
            for r in hist_label_rows
        ]
        report.scope_validation = {
            "all_records_scoped_to_bbs_67": all(v == 0 for v in out_of_scope_counts.values()),
            "out_of_scope_counts_by_table": out_of_scope_counts,
            "mouza_source_reported_district_labels": historical_labels,
        }

        # 3. Duplicate detection across Mouzas
        dup_uuid_rows = self.conn.execute(
            """
            SELECT mouza_uuid, COUNT(*) AS cnt
            FROM mouzas
            WHERE mouza_uuid IS NOT NULL AND mouza_uuid != ''
            GROUP BY mouza_uuid
            HAVING COUNT(*) > 1
            """
        ).fetchall()

        mouza_all = self.conn.execute(
            """
            SELECT id, upazila_bbs_code, thana_bbs_code, survey_id, survey_name_en,
                   source_record_id, mouza_identifier, mouza_uuid, name_bn,
                   canonical_name_key, jl_number, source_reported_upazila_bn
            FROM mouzas
            """
        ).fetchall()

        by_sub_surv_name_jl: dict[tuple[str, str, str, str], list[dict[str, Any]]] = {}
        by_sub_surv_jl: dict[tuple[str, str, str], list[dict[str, Any]]] = {}
        by_sub_name_jl_cross_survey: dict[tuple[str, str, str], set[str]] = {}

        for r in mouza_all:
            sub_key = r["upazila_bbs_code"] or r["thana_bbs_code"] or ""
            surv_key = r["survey_id"] or ""
            jl_ascii = bn_digits_to_ascii(r["jl_number"]) or ""
            canon = r["canonical_name_key"] or ""

            item = {
                "id": int(r["id"]),
                "subdivision_bbs": sub_key,
                "upazila_label": r["source_reported_upazila_bn"],
                "survey_id": surv_key,
                "survey_en": r["survey_name_en"],
                "source_record_id": r["source_record_id"],
                "mouza_identifier": r["mouza_identifier"],
                "mouza_uuid": r["mouza_uuid"],
                "name_bn": r["name_bn"],
                "canonical_name_key": canon,
                "jl_number": r["jl_number"],
                "jl_ascii": jl_ascii,
            }
            by_sub_surv_name_jl.setdefault((sub_key, surv_key, canon, jl_ascii), []).append(item)
            if jl_ascii:
                by_sub_surv_jl.setdefault((sub_key, surv_key, jl_ascii), []).append(item)
            by_sub_name_jl_cross_survey.setdefault((sub_key, canon, jl_ascii), set()).add(surv_key)

        same_sub_surv_name_jl_dups = [
            {
                "subdivision_bbs": k[0],
                "survey_id": k[1],
                "canonical_name_key": k[2],
                "jl_ascii": k[3],
                "count": len(rows),
                "records": rows,
            }
            for k, rows in by_sub_surv_name_jl.items()
            if len(rows) > 1
        ]

        same_sub_surv_jl_diff_name = []
        for k, rows in by_sub_surv_jl.items():
            distinct_names = {x["canonical_name_key"] for x in rows}
            if len(distinct_names) > 1:
                same_sub_surv_jl_diff_name.append(
                    {
                        "subdivision_bbs": k[0],
                        "survey_id": k[1],
                        "jl_ascii": k[2],
                        "distinct_names": sorted(distinct_names),
                        "records": rows,
                    }
                )

        cross_survey_multi_count = sum(
            1 for surveys in by_sub_name_jl_cross_survey.values() if len(surveys) > 1
        )

        # Duplicate map drive_file_ids across folders
        dup_map_drive_ids = [
            dict(r)
            for r in self.conn.execute(
                """
                SELECT drive_file_id, COUNT(*) AS cnt
                FROM mouza_maps_metadata
                WHERE drive_file_id IS NOT NULL AND drive_file_id != ''
                GROUP BY drive_file_id
                HAVING COUNT(*) > 1
                """
            ).fetchall()
        ]

        report.duplicate_detection = {
            "duplicate_mouza_uuid_count": len(dup_uuid_rows),
            "duplicate_mouza_uuids": [dict(r) for r in dup_uuid_rows],
            "same_subdivision_survey_name_jl_count": len(same_sub_surv_name_jl_dups),
            "same_subdivision_survey_name_jl_samples": same_sub_surv_name_jl_dups[:25],
            "same_subdivision_survey_jl_different_name_count": len(same_sub_surv_jl_diff_name),
            "same_subdivision_survey_jl_different_name_samples": same_sub_surv_jl_diff_name[:25],
            "cross_survey_longitudinal_mouza_keys": cross_survey_multi_count,
            "duplicate_map_drive_file_id_count": len(dup_map_drive_ids),
            "duplicate_map_drive_file_ids": dup_map_drive_ids[:25],
        }

        # 4. Geographic hierarchy validation
        orphans = {
            "upazilas_without_district": int(
                self.conn.execute(
                    "SELECT COUNT(*) AS c FROM upazilas u LEFT JOIN districts d ON u.district_id=d.id WHERE d.id IS NULL"
                ).fetchone()["c"]
            ),
            "thanas_without_district": int(
                self.conn.execute(
                    "SELECT COUNT(*) AS c FROM thanas t LEFT JOIN districts d ON t.district_id=d.id WHERE d.id IS NULL"
                ).fetchone()["c"]
            ),
            "thanas_without_parent_upazila": int(
                self.conn.execute(
                    "SELECT COUNT(*) AS c FROM thanas WHERE upazila_id IS NULL"
                ).fetchone()["c"]
            ),
            "mouzas_without_upazila_or_thana": int(
                self.conn.execute(
                    "SELECT COUNT(*) AS c FROM mouzas WHERE upazila_id IS NULL AND thana_id IS NULL"
                ).fetchone()["c"]
            ),
            "mouzas_without_survey_type": int(
                self.conn.execute(
                    "SELECT COUNT(*) AS c FROM mouzas WHERE survey_type_id IS NULL"
                ).fetchone()["c"]
            ),
            "maps_without_matched_mouza": int(
                self.conn.execute(
                    "SELECT COUNT(*) AS c FROM mouza_maps_metadata WHERE mouza_id IS NULL"
                ).fetchone()["c"]
            ),
            "maps_matched_to_mouza": int(
                self.conn.execute(
                    "SELECT COUNT(*) AS c FROM mouza_maps_metadata WHERE mouza_id IS NOT NULL"
                ).fetchone()["c"]
            ),
        }
        report.hierarchy_validation = {
            "fk_integrity_valid": (
                orphans["upazilas_without_district"] == 0
                and orphans["thanas_without_district"] == 0
                and orphans["mouzas_without_upazila_or_thana"] == 0
                and orphans["mouzas_without_survey_type"] == 0
            ),
            "checks": orphans,
        }

        # 5. Missing identifiers & structural gaps
        missing_checks = {
            "upazilas_missing_bbs_code": "SELECT COUNT(*) AS c FROM upazilas WHERE COALESCE(bbs_code, '') = ''",
            "upazilas_missing_name_en": "SELECT COUNT(*) AS c FROM upazilas WHERE COALESCE(name_en, '') = ''",
            "thanas_missing_bbs_code": "SELECT COUNT(*) AS c FROM thanas WHERE COALESCE(bbs_code, '') = ''",
            "thanas_missing_name_en": "SELECT COUNT(*) AS c FROM thanas WHERE COALESCE(name_en, '') = ''",
            "mouzas_missing_jl_number": "SELECT COUNT(*) AS c FROM mouzas WHERE COALESCE(jl_number, '') = ''",
            "mouzas_missing_mouza_identifier": "SELECT COUNT(*) AS c FROM mouzas WHERE COALESCE(mouza_identifier, '') = ''",
            "mouzas_missing_uuid": "SELECT COUNT(*) AS c FROM mouzas WHERE COALESCE(mouza_uuid, '') = ''",
            "mouzas_missing_name_en": "SELECT COUNT(*) AS c FROM mouzas WHERE COALESCE(name_en, '') = ''",
            "mouzas_missing_union_link": "SELECT COUNT(*) AS c FROM mouzas WHERE union_id IS NULL",
            "mouzas_missing_municipality_link": "SELECT COUNT(*) AS c FROM mouzas WHERE municipality_id IS NULL",
            "maps_missing_parsed_jl": "SELECT COUNT(*) AS c FROM mouza_maps_metadata WHERE COALESCE(parsed_jl_number, '') = ''",
        }
        missing_counts = {
            key: int(self.conn.execute(sql).fetchone()["c"]) for key, sql in missing_checks.items()
        }
        missing_jl_samples = [
            dict(r)
            for r in self.conn.execute(
                """
                SELECT id, source_record_id, mouza_identifier, name_bn, survey_id, source_reported_upazila_bn
                FROM mouzas
                WHERE COALESCE(jl_number, '') = ''
                LIMIT 20
                """
            ).fetchall()
        ]
        report.missing_identifiers = {
            "counts": missing_counts,
            "mouzas_missing_jl_samples": missing_jl_samples,
        }

        # 6. Breakdown by subdivision and survey
        sub_rows = self.conn.execute(
            """
            SELECT 'UPAZILA' AS subdivision_type, u.bbs_code, u.name_bn, u.name_en,
                   COUNT(m.id) AS mouza_rows,
                   COUNT(DISTINCT m.canonical_name_key || '|' || COALESCE(m.jl_number, '')) AS distinct_mouzas
            FROM upazilas u
            LEFT JOIN mouzas m ON m.upazila_id = u.id
            GROUP BY u.id
            UNION ALL
            SELECT t.jurisdiction_type AS subdivision_type, t.bbs_code, t.name_bn, t.name_en,
                   COUNT(m.id) AS mouza_rows,
                   COUNT(DISTINCT m.canonical_name_key || '|' || COALESCE(m.jl_number, '')) AS distinct_mouzas
            FROM thanas t
            LEFT JOIN mouzas m ON m.thana_id = t.id
            GROUP BY t.id
            ORDER BY subdivision_type DESC, bbs_code ASC
            """
        ).fetchall()
        report.subdivision_breakdown = [dict(r) for r in sub_rows]

        surv_rows = self.conn.execute(
            """
            SELECT st.survey_id, st.survey_code_en, st.name_bn, st.survey_order,
                   COUNT(m.id) AS mouza_rows
            FROM survey_types st
            LEFT JOIN mouzas m ON m.survey_type_id = st.id
            GROUP BY st.id
            ORDER BY COALESCE(st.survey_order, 999), st.survey_id
            """
        ).fetchall()
        report.survey_breakdown = [dict(r) for r in surv_rows]

        prov_rows = self.conn.execute(
            """
            SELECT source_name, source_tier, access_status, COALESCE(http_status, 0) AS http_status,
                   COUNT(*) AS request_count, MIN(collected_at) AS first_at, MAX(collected_at) AS last_at
            FROM source_provenance
            GROUP BY source_name, source_tier, access_status, COALESCE(http_status, 0)
            ORDER BY source_tier, request_count DESC
            """
        ).fetchall()
        report.provenance_summary = [dict(r) for r in prov_rows]

        latest_run = self.conn.execute(
            "SELECT * FROM collection_runs ORDER BY started_at DESC LIMIT 1"
        ).fetchone()
        report.latest_run = dict(latest_run) if latest_run else None

        return report
