from __future__ import annotations

import logging
from collections import deque
from dataclasses import dataclass, field
from typing import Any, Optional

from .config import PipelineConfig
from .database import LandGeoDatabase
from .http_client import ConservativeHttpClient, utc_now_iso
from .models import (
    AccessStatus,
    CollectionRunRecord,
    DistrictRecord,
    MouzaMapMetadataRecord,
    MouzaRecord,
    MunicipalityRecord,
    PolicyCheckResult,
    SurveyTypeRecord,
    ThanaRecord,
    UnionRecord,
    UpazilaRecord,
    VerificationStatus,
)
from .normalizer import (
    bn_digits_to_ascii,
    canonical_name_key,
    is_narayanganj_district,
    normalize_identifier,
    normalize_nfc,
    parse_map_filename_metadata,
    strip_prohibited_pii,
)
from .playwright_fallback import inspect_js_rendered_page_if_needed
from .policy import inspect_source_policy


@dataclass
class EndpointProbeResult:
    endpoint: str
    url: str
    http_status: Optional[int]
    access_status: str
    records_found: int
    note: str


@dataclass
class CollectionSummary:
    run_id: str
    started_at: str
    finished_at: str
    status: str
    policy_checks: dict[str, PolicyCheckResult] = field(default_factory=dict)
    playwright_note: str = "Not triggered (API/static endpoints sufficient)."
    endpoint_probes: list[EndpointProbeResult] = field(default_factory=list)
    counts: dict[str, int] = field(default_factory=dict)
    map_folders_visited: int = 0
    map_folders_failed: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def _extract_items_and_next_token(payload: Any) -> tuple[list[dict[str, Any]], Optional[str]]:
    if isinstance(payload, list):
        return [x for x in payload if isinstance(x, dict)], None
    if isinstance(payload, dict):
        next_token = (
            payload.get("nextPageToken")
            or payload.get("next_page_token")
            or payload.get("nextPage")
        )
        data_node = payload.get("data")
        if isinstance(data_node, dict):
            for key in ("files", "items", "results", "rows"):
                if isinstance(data_node.get(key), list):
                    return [x for x in data_node[key] if isinstance(x, dict)], next_token
        elif isinstance(data_node, list):
            return [x for x in data_node if isinstance(x, dict)], next_token
        for key in ("files", "items", "results", "rows"):
            if isinstance(payload.get(key), list):
                return [x for x in payload[key] if isinstance(x, dict)], next_token
    return [], None


class NarayanganjCollector:
    """Collects publicly available Narayanganj District land-geographic metadata safely."""

    def __init__(
        self,
        config: PipelineConfig,
        db: LandGeoDatabase,
        http_client: ConservativeHttpClient,
        logger: Optional[logging.Logger] = None,
    ) -> None:
        self.config = config
        self.db = db
        self.http = http_client
        self.logger = logger or logging.getLogger("narayanganj_pipeline.collector")
        self._flushed_prov_count = 0

    def _flush_provenance(self) -> None:
        total = len(self.http.provenance_log)
        if total > self._flushed_prov_count:
            self.db.insert_provenance_batch(self.http.provenance_log[self._flushed_prov_count :])
            self._flushed_prov_count = total

    def collect(self, *, run_id: Optional[str] = None) -> CollectionSummary:
        effective_run_id = run_id or self.http.run_id
        started_at = utc_now_iso()
        self.db.start_run(
            CollectionRunRecord(
                run_id=effective_run_id,
                started_at=started_at,
                target_district_bbs=self.config.target_district_bbs_code,
                target_district_name_bn=self.config.target_district_name_bn,
                status="running",
                user_agent=self.config.user_agent,
                rate_limit_seconds=self.config.request_delay_seconds,
                notes="Policy preflight & public geographic metadata collection started.",
            )
        )

        summary = CollectionSummary(
            run_id=effective_run_id,
            started_at=started_at,
            finished_at=started_at,
            status="running",
        )

        try:
            # 1. Preflight policy & robots inspection for both sources
            official_policy = inspect_source_policy(
                self.http,
                source_name="dlrms.land.gov.bd",
                source_tier="official_dlrms",
                base_url=self.config.official_source_url,
                logger=self.logger,
            )
            ref_policy = inspect_source_policy(
                self.http,
                source_name="e-porcha.com",
                source_tier="reference_eporcha",
                base_url=self.config.primary_reference_url,
                terms_paths=("/terms-service", "/disclaimer", "/privacy-policy", "/mouza-map"),
                logger=self.logger,
            )
            self._flush_provenance()
            summary.policy_checks["dlrms.land.gov.bd"] = official_policy
            summary.policy_checks["e-porcha.com"] = ref_policy

            if ref_policy.requires_js_rendering and self.config.enable_playwright_fallback:
                pw_res = inspect_js_rendered_page_if_needed(
                    self.config.primary_reference_url,
                    logger=self.logger,
                )
                summary.playwright_note = pw_res.note
            elif ref_policy.requires_js_rendering:
                summary.playwright_note = (
                    "Page flagged as JS shell, Playwright fallback disabled by configuration."
                )

            if ref_policy.access_status in {
                AccessStatus.DISALLOWED_ROBOTS,
                AccessStatus.BLOCKED_WAF,
                AccessStatus.BLOCKED_AUTH_OR_CAPTCHA,
            }:
                msg = (
                    f"Primary reference {self.config.primary_reference_url} inaccessible "
                    f"({ref_policy.access_status.value}, HTTP {ref_policy.home_http_status}). Stopping."
                )
                summary.warnings.append(msg)
                summary.finished_at = utc_now_iso()
                summary.status = "blocked"
                self.db.finish_run(effective_run_id, summary.finished_at, "blocked", msg)
                return summary

            official_reachable = official_policy.access_status == AccessStatus.ALLOWED
            default_verif_status = (
                VerificationStatus.VERIFIED if official_reachable else VerificationStatus.UNVERIFIED
            )
            if official_reachable:
                base_verif_note = "Cross-checked with official dlrms.land.gov.bd and reference e-porcha.com."
            else:
                base_verif_note = (
                    f"Collected from reference e-porcha.com public API ({self.config.geo_api_base}); "
                    f"direct verification on official {self.config.official_source_url} blocked "
                    f"({official_policy.access_status.value}, HTTP {official_policy.home_http_status})."
                )

            # 2. Districts (filter strictly to Narayanganj BBS=67)
            district_url = f"{self.config.geo_api_base.rstrip('/')}/districts"
            dist_out = self.http.request(
                "GET",
                district_url,
                source_name="e-porcha public geo API",
                source_tier="reference_eporcha_api",
                access_policy_note="Fetch Dhaka division districts; filter strictly to Narayanganj BBS 67",
                params={"DIVISION_BBS_CODE": self.config.target_division_bbs_code},
                expect_json=True,
            )
            self._flush_provenance()
            dist_items, _ = _extract_items_and_next_token(dist_out.json_data)
            narayanganj_raw = next(
                (
                    d
                    for d in dist_items
                    if is_narayanganj_district(
                        normalize_identifier(d.get("BBS_CODE")),
                        d.get("NAME"),
                        d.get("NAME_EN"),
                    )
                ),
                None,
            )
            if narayanganj_raw is None:
                msg = (
                    f"Narayanganj District (BBS {self.config.target_district_bbs_code}) not returned by "
                    f"{district_url} (HTTP {dist_out.status_code})."
                )
                summary.warnings.append(msg)
                summary.finished_at = utc_now_iso()
                summary.status = "failed_missing_district"
                self.db.finish_run(effective_run_id, summary.finished_at, summary.status, msg)
                return summary

            district_rec = DistrictRecord(
                source_district_id=narayanganj_raw.get("ID"),
                bbs_code=narayanganj_raw.get("BBS_CODE") or self.config.target_district_bbs_code,
                division_bbs_code=narayanganj_raw.get("DIVISION_BBS_CODE")
                or self.config.target_division_bbs_code,
                division_name_bn=self.config.target_division_name_bn,
                division_name_en=self.config.target_division_name_en,
                name_bn=narayanganj_raw.get("NAME") or self.config.target_district_name_bn,
                name_en=narayanganj_raw.get("NAME_EN") or self.config.target_district_name_en,
                verification_status=default_verif_status,
                verification_note=base_verif_note,
                provenance_id=dist_out.provenance.provenance_id,
            )
            district_db_id = self.db.upsert_district(district_rec)

            # 3. Upazilas & Revenue Circles (IS_CIRCLE == 1 stored in thanas table)
            upazila_url = f"{self.config.geo_api_base.rstrip('/')}/upazilas"
            upz_out = self.http.request(
                "GET",
                upazila_url,
                source_name="e-porcha public geo API",
                source_tier="reference_eporcha_api",
                access_policy_note="Fetch Narayanganj Upazilas and Revenue Circles (BBS 67)",
                params={"DISTRICT_BBS_CODE": district_rec.bbs_code},
                expect_json=True,
            )
            self._flush_provenance()
            upz_items, _ = _extract_items_and_next_token(upz_out.json_data)

            # Store (bbs_code, is_circle, upazila_db_id, thana_db_id, name_bn)
            subdivisions: list[tuple[str, bool, Optional[int], Optional[int], str]] = []
            upazila_name_to_id: dict[str, tuple[int, str]] = {}
            thana_name_to_id: dict[str, tuple[int, str]] = {}

            for raw_sub in upz_items:
                clean_sub = strip_prohibited_pii(raw_sub)
                is_circle = str(clean_sub.get("IS_CIRCLE", 0)) == "1"
                bbs_code = normalize_identifier(clean_sub.get("BBS_CODE"))
                name_bn = normalize_nfc(clean_sub.get("NAME"))
                if not bbs_code or not name_bn:
                    continue
                if is_circle:
                    thana_rec = ThanaRecord(
                        district_bbs_code=district_rec.bbs_code,
                        upazila_bbs_code=None,
                        source_thana_id=clean_sub.get("ID"),
                        lams_id=clean_sub.get("LAMS_ID"),
                        bbs_code=bbs_code,
                        jurisdiction_type="REVENUE_CIRCLE",
                        name_bn=name_bn,
                        name_en=clean_sub.get("NAME_EN"),
                        verification_status=default_verif_status,
                        verification_note=(
                            f"{base_verif_note} Listed in upazilas endpoint with IS_CIRCLE=1 "
                            f"(LAMS_ID={clean_sub.get('LAMS_ID')})."
                        ),
                        provenance_id=upz_out.provenance.provenance_id,
                    )
                    t_id = self.db.upsert_thana(district_db_id, None, thana_rec)
                    subdivisions.append((bbs_code, True, None, t_id, name_bn))
                    thana_name_to_id[canonical_name_key(name_bn)] = (t_id, bbs_code)
                else:
                    upz_rec = UpazilaRecord(
                        district_bbs_code=district_rec.bbs_code,
                        source_upazila_id=clean_sub.get("ID"),
                        bbs_code=bbs_code,
                        name_bn=name_bn,
                        name_en=clean_sub.get("NAME_EN"),
                        row_status=clean_sub.get("ROW_STATUS"),
                        verification_status=default_verif_status,
                        verification_note=base_verif_note,
                        provenance_id=upz_out.provenance.provenance_id,
                    )
                    u_id = self.db.upsert_upazila(district_db_id, upz_rec)
                    subdivisions.append((bbs_code, False, u_id, None, name_bn))
                    upazila_name_to_id[canonical_name_key(name_bn)] = (u_id, bbs_code)

            # 4. Probe secondary administrative endpoints (unions, thanas, municipalities, circles)
            self._probe_optional_admin_endpoints(
                district_db_id=district_db_id,
                district_bbs=district_rec.bbs_code,
                default_verif_status=default_verif_status,
                base_verif_note=base_verif_note,
                upazila_name_to_id=upazila_name_to_id,
                summary=summary,
            )

            # 5. Collect Surveys & Mouzas per Upazila / Revenue Circle
            survey_db_ids: dict[str, int] = {}
            survey_records: dict[str, SurveyTypeRecord] = {}
            surveys_url = f"{self.config.geo_api_base.rstrip('/')}/surveys"
            mouzas_url = f"{self.config.geo_api_base.rstrip('/')}/mouzas"

            for sub_bbs, is_circle, upazila_db_id, thana_db_id, sub_name_bn in subdivisions:
                surv_out = self.http.request(
                    "GET",
                    surveys_url,
                    source_name="e-porcha public geo API",
                    source_tier="reference_eporcha_api",
                    access_policy_note=f"Fetch surveys for Narayanganj subdivision BBS {sub_bbs} ({sub_name_bn})",
                    params={
                        "DISTRICT_BBS_CODE": district_rec.bbs_code,
                        "UPAZILA_BBS_CODE": sub_bbs,
                    },
                    expect_json=True,
                )
                self._flush_provenance()
                surv_items, _ = _extract_items_and_next_token(surv_out.json_data)
                if not surv_items:
                    summary.warnings.append(
                        f"No surveys returned for subdivision BBS {sub_bbs} ({sub_name_bn}); HTTP {surv_out.status_code}."
                    )
                    continue

                for raw_surv in surv_items:
                    s_id = normalize_identifier(raw_surv.get("SURVEY_ID"))
                    s_name_bn = normalize_nfc(raw_surv.get("LOCAL_NAME") or raw_surv.get("SURVEY_NAME"))
                    if not s_id or not s_name_bn:
                        continue
                    existing_code_en = (
                        survey_records[s_id].survey_code_en if s_id in survey_records else None
                    )
                    s_rec = SurveyTypeRecord(
                        district_bbs_code=district_rec.bbs_code,
                        survey_id=s_id,
                        survey_code_en=normalize_nfc(raw_surv.get("SURVEY_NAME_EN")) or existing_code_en,
                        name_bn=s_name_bn,
                        survey_order=raw_surv.get("SURVEY_ORDER"),
                        verification_status=default_verif_status,
                        verification_note=base_verif_note,
                        provenance_id=surv_out.provenance.provenance_id,
                    )
                    survey_records[s_id] = s_rec
                    survey_db_ids[s_id] = self.db.upsert_survey_type(district_db_id, s_rec)

                # Collect Mouzas for every survey available in this subdivision
                for raw_surv in surv_items:
                    s_id = normalize_identifier(raw_surv.get("SURVEY_ID"))
                    if not s_id:
                        continue
                    self._collect_mouzas_for_pair(
                        district_db_id=district_db_id,
                        district_bbs=district_rec.bbs_code,
                        sub_bbs=sub_bbs,
                        is_circle=is_circle,
                        upazila_db_id=upazila_db_id,
                        thana_db_id=thana_db_id,
                        survey_id=s_id,
                        survey_db_ids=survey_db_ids,
                        survey_records=survey_records,
                        mouzas_url=mouzas_url,
                        default_verif_status=default_verif_status,
                        base_verif_note=base_verif_note,
                        summary=summary,
                    )

            # 6. Collect Public Mouza Map Metadata (never download map files)
            self._collect_mouza_map_metadata(
                district_db_id=district_db_id,
                district_bbs=district_rec.bbs_code,
                survey_db_ids=survey_db_ids,
                survey_records=survey_records,
                default_verif_status=default_verif_status,
                base_verif_note=base_verif_note,
                summary=summary,
            )

            self._flush_provenance()
            summary.counts = self._fetch_table_counts()
            summary.finished_at = utc_now_iso()
            summary.status = "completed"
            self.db.finish_run(
                effective_run_id,
                summary.finished_at,
                "completed",
                (
                    f"Collected {summary.counts.get('mouzas', 0)} mouza records across "
                    f"{summary.counts.get('upazilas', 0)} upazilas and "
                    f"{summary.counts.get('thanas', 0)} revenue circles/thanas, plus "
                    f"{summary.counts.get('mouza_maps_metadata', 0)} map metadata rows."
                ),
            )
            return summary

        except Exception as exc:
            self.logger.exception("Collection failed unexpectedly: %s", exc)
            self._flush_provenance()
            summary.warnings.append(f"Unhandled exception: {exc}")
            summary.finished_at = utc_now_iso()
            summary.status = "failed"
            self.db.finish_run(effective_run_id, summary.finished_at, "failed", str(exc)[:400])
            raise

    def _probe_optional_admin_endpoints(
        self,
        *,
        district_db_id: int,
        district_bbs: str,
        default_verif_status: VerificationStatus,
        base_verif_note: str,
        upazila_name_to_id: dict[str, tuple[int, str]],
        summary: CollectionSummary,
    ) -> None:
        base = self.config.geo_api_base.rstrip("/")
        for endpoint in ("unions", "thanas", "municipalities", "circles"):
            url = f"{base}/{endpoint}"
            out = self.http.request(
                "GET",
                url,
                source_name="e-porcha public geo API",
                source_tier="reference_eporcha_api",
                access_policy_note=f"Probe optional {endpoint} endpoint for Narayanganj BBS {district_bbs}",
                params={"DISTRICT_BBS_CODE": district_bbs},
                expect_json=True,
            )
            self._flush_provenance()
            items, _ = _extract_items_and_next_token(out.json_data)
            if endpoint == "unions" and items:
                for raw in items:
                    clean = strip_prohibited_pii(raw)
                    name_bn = normalize_nfc(clean.get("NAME") or clean.get("UNION_NAME"))
                    if not name_bn:
                        continue
                    upz_bbs = normalize_identifier(clean.get("UPAZILA_BBS_CODE"))
                    upz_id = None
                    if clean.get("UPAZILA_NAME"):
                        match = upazila_name_to_id.get(canonical_name_key(clean.get("UPAZILA_NAME")))
                        if match:
                            upz_id, upz_bbs = match[0], upz_bbs or match[1]
                    self.db.upsert_union(
                        district_db_id,
                        upz_id,
                        UnionRecord(
                            district_bbs_code=district_bbs,
                            upazila_bbs_code=upz_bbs,
                            source_union_id=normalize_identifier(clean.get("ID")),
                            bbs_code=normalize_identifier(clean.get("BBS_CODE")),
                            name_bn=name_bn,
                            name_en=normalize_nfc(clean.get("NAME_EN")),
                            verification_status=default_verif_status,
                            verification_note=base_verif_note,
                            provenance_id=out.provenance.provenance_id,
                        ),
                    )
            elif endpoint == "municipalities" and items:
                for raw in items:
                    clean = strip_prohibited_pii(raw)
                    name_bn = normalize_nfc(clean.get("NAME"))
                    if not name_bn:
                        continue
                    self.db.upsert_municipality(
                        district_db_id,
                        None,
                        MunicipalityRecord(
                            district_bbs_code=district_bbs,
                            upazila_bbs_code=normalize_identifier(clean.get("UPAZILA_BBS_CODE")),
                            source_municipality_id=normalize_identifier(clean.get("ID")),
                            bbs_code=normalize_identifier(clean.get("BBS_CODE")),
                            name_bn=name_bn,
                            name_en=normalize_nfc(clean.get("NAME_EN")),
                            verification_status=default_verif_status,
                            verification_note=base_verif_note,
                            provenance_id=out.provenance.provenance_id,
                        ),
                    )
            summary.endpoint_probes.append(
                EndpointProbeResult(
                    endpoint=endpoint,
                    url=url,
                    http_status=out.status_code,
                    access_status=out.access_status.value,
                    records_found=len(items),
                    note=(
                        f"Endpoint returned HTTP {out.status_code} ({out.access_status.value}); "
                        f"{len(items)} items parsed."
                    ),
                )
            )

    def _collect_mouzas_for_pair(
        self,
        *,
        district_db_id: int,
        district_bbs: str,
        sub_bbs: str,
        is_circle: bool,
        upazila_db_id: Optional[int],
        thana_db_id: Optional[int],
        survey_id: str,
        survey_db_ids: dict[str, int],
        survey_records: dict[str, SurveyTypeRecord],
        mouzas_url: str,
        default_verif_status: VerificationStatus,
        base_verif_note: str,
        summary: CollectionSummary,
    ) -> None:
        page_token: Optional[str] = None
        seen_tokens: set[str] = set()
        for page_num in range(1, 51):
            params: dict[str, Any] = {
                "DISTRICT_BBS_CODE": district_bbs,
                "UPAZILA_BBS_CODE": sub_bbs,
                "SURVEY_ID": survey_id,
            }
            if page_token:
                params["pageToken"] = page_token
            out = self.http.request(
                "GET",
                mouzas_url,
                source_name="e-porcha public geo API",
                source_tier="reference_eporcha_api",
                access_policy_note=(
                    f"Fetch Mouzas for Narayanganj BBS {district_bbs}, "
                    f"subdivision {sub_bbs}, survey {survey_id}, page {page_num}"
                ),
                params=params,
                expect_json=True,
            )
            self._flush_provenance()
            if out.access_status != AccessStatus.ALLOWED:
                summary.warnings.append(
                    f"Mouzas fetch blocked/failed for subdivision={sub_bbs}, survey={survey_id}: "
                    f"HTTP {out.status_code} ({out.access_status.value})."
                )
                break

            items, next_token = _extract_items_and_next_token(out.json_data)
            for raw_m in items:
                clean = strip_prohibited_pii(raw_m)
                name_bn = normalize_nfc(clean.get("MOUZA_NAME") or clean.get("NAME"))
                if not name_bn:
                    continue

                row_survey_en = normalize_nfc(clean.get("SURVEY_NAME_EN"))
                if row_survey_en and survey_id in survey_records and not survey_records[survey_id].survey_code_en:
                    updated_survey = survey_records[survey_id].model_copy(
                        update={"survey_code_en": row_survey_en}
                    )
                    survey_records[survey_id] = updated_survey
                    survey_db_ids[survey_id] = self.db.upsert_survey_type(district_db_id, updated_survey)

                reported_dist_bn = normalize_nfc(clean.get("DISTRICT_NAME"))
                note = base_verif_note
                if reported_dist_bn and not is_narayanganj_district(name_bn=reported_dist_bn):
                    note = (
                        f"{base_verif_note} Historical survey jurisdiction label in source row is "
                        f"'{reported_dist_bn}' under query DISTRICT_BBS_CODE={district_bbs}."
                    )

                mouza_rec = MouzaRecord(
                    district_bbs_code=district_bbs,
                    upazila_bbs_code=None if is_circle else sub_bbs,
                    thana_bbs_code=sub_bbs if is_circle else None,
                    source_record_id=clean.get("ID"),
                    mouza_identifier=clean.get("MOUZA_ID"),
                    mouza_uuid=clean.get("UUID"),
                    name_bn=name_bn,
                    name_en=clean.get("MOUZA_NAME_EN") or clean.get("NAME_EN"),
                    jl_number=clean.get("JL_NUMBER"),
                    survey_id=clean.get("SURVEY_ID") or survey_id,
                    survey_name_bn=clean.get("SURVEY_NAME"),
                    survey_name_en=row_survey_en,
                    mutation_survey_id=clean.get("MUTATION_SURVEY_ID"),
                    mutation_survey_name_en=clean.get("MUTATION_SURVEY_NAME_EN"),
                    source_reported_district_bn=reported_dist_bn,
                    source_reported_upazila_bn=clean.get("UPAZILA_NAME"),
                    verification_status=default_verif_status,
                    verification_note=note,
                    provenance_id=out.provenance.provenance_id,
                )
                self.db.upsert_mouza(
                    district_id=district_db_id,
                    upazila_id=upazila_db_id,
                    thana_id=thana_db_id,
                    survey_type_id=survey_db_ids.get(mouza_rec.survey_id or survey_id),
                    m=mouza_rec,
                )

            if not next_token or next_token in seen_tokens:
                break
            seen_tokens.add(next_token)
            page_token = next_token

    def _collect_mouza_map_metadata(
        self,
        *,
        district_db_id: int,
        district_bbs: str,
        survey_db_ids: dict[str, int],
        survey_records: dict[str, SurveyTypeRecord],
        default_verif_status: VerificationStatus,
        base_verif_note: str,
        summary: CollectionSummary,
    ) -> None:
        if self.config.max_map_depth <= 0:
            return

        # Preload collected Mouzas for conservative JL + Upazila/Survey matching
        mouza_rows = self.db.conn.execute(
            """
            SELECT m.id, m.mouza_uuid, m.jl_number, m.survey_id, m.survey_type_id,
                   m.survey_name_en, m.source_reported_upazila_bn,
                   COALESCE(u.name_bn, t.name_bn, m.source_reported_upazila_bn) AS sub_name_bn
            FROM mouzas m
            LEFT JOIN upazilas u ON m.upazila_id = u.id
            LEFT JOIN thanas t ON m.thana_id = t.id
            WHERE m.district_id = ?
            """,
            (district_db_id,),
        ).fetchall()

        mouzas_by_jl: dict[str, list[dict[str, Any]]] = {}
        for r in mouza_rows:
            jl_ascii = bn_digits_to_ascii(r["jl_number"])
            if not jl_ascii:
                continue
            mouzas_by_jl.setdefault(jl_ascii, []).append(
                {
                    "id": int(r["id"]),
                    "uuid": r["mouza_uuid"],
                    "survey_id": r["survey_id"],
                    "survey_type_id": r["survey_type_id"],
                    "survey_en": (r["survey_name_en"] or "").upper(),
                    "sub_key": canonical_name_key(r["sub_name_bn"] or ""),
                }
            )

        survey_hint_to_id: dict[str, int] = {}
        for s_id, s_rec in survey_records.items():
            if s_rec.survey_code_en and s_id in survey_db_ids:
                survey_hint_to_id[s_rec.survey_code_en.upper()] = survey_db_ids[s_id]

        queue: deque[tuple[str, int, Optional[str], Optional[str]]] = deque(
            [("0:/Narayanganj/", 0, None, None)]
        )
        visited_folders: set[str] = set()

        while queue:
            folder_path, depth, upazila_folder, survey_folder = queue.popleft()
            if folder_path in visited_folders:
                continue
            visited_folders.add(folder_path)
            summary.map_folders_visited += 1

            page_token: Optional[str] = None
            seen_tokens: set[str] = set()
            for page_idx in range(self.config.max_map_pages_per_folder):
                payload: dict[str, Any] = {"path": folder_path}
                if page_token:
                    payload["page_token"] = page_token
                    payload["pageToken"] = page_token
                    payload["page_index"] = page_idx

                out = self.http.request(
                    "POST",
                    self.config.mouza_map_api_url,
                    source_name="e-porcha public mouza-map API",
                    source_tier="reference_eporcha_api",
                    access_policy_note=f"List public Mouza map folder metadata (depth={depth}, page={page_idx})",
                    json_body=payload,
                    delay_seconds=self.config.map_request_delay_seconds,
                    expect_json=True,
                )
                self._flush_provenance()

                if out.access_status != AccessStatus.ALLOWED or out.json_data is None:
                    summary.map_folders_failed.append(
                        f"{folder_path} (HTTP {out.status_code}, {out.access_status.value})"
                    )
                    break

                items, next_token = _extract_items_and_next_token(out.json_data)
                for item in items:
                    item_name = normalize_nfc(item.get("name"))
                    if not item_name:
                        continue
                    mime_type = normalize_nfc(item.get("mimeType"))
                    if mime_type == "application/vnd.google-apps.folder":
                        next_depth = depth + 1
                        if next_depth < self.config.max_map_depth:
                            next_upz = upazila_folder if depth > 0 else item_name
                            next_surv = item_name if depth == 1 else survey_folder
                            child_path = f"{folder_path.rstrip('/')}/{item_name}/"
                            if child_path not in visited_folders:
                                queue.append((child_path, next_depth, next_upz, next_surv))
                        continue

                    parsed = parse_map_filename_metadata(item_name)
                    parsed_jl = parsed["parsed_jl_number"]
                    parsed_survey_hint = parsed["parsed_survey_hint"]
                    if not parsed_survey_hint and survey_folder:
                        folder_parsed = parse_map_filename_metadata(survey_folder)
                        parsed_survey_hint = folder_parsed["parsed_survey_hint"]

                    matched_mouza_id: Optional[int] = None
                    matched_mouza_uuid: Optional[str] = None
                    matched_survey_type_id: Optional[int] = (
                        survey_hint_to_id.get(parsed_survey_hint) if parsed_survey_hint else None
                    )

                    if parsed_jl and parsed_jl in mouzas_by_jl:
                        candidates = mouzas_by_jl[parsed_jl]
                        if upazila_folder:
                            upz_key = canonical_name_key(upazila_folder)
                            sub_filtered = [
                                c
                                for c in candidates
                                if c["sub_key"]
                                and (c["sub_key"] in upz_key or upz_key.split(" ")[0] in c["sub_key"])
                            ]
                            if sub_filtered:
                                candidates = sub_filtered
                        if parsed_survey_hint:
                            surv_filtered = [
                                c for c in candidates if c["survey_en"] == parsed_survey_hint
                            ]
                            if surv_filtered:
                                candidates = surv_filtered
                        if len(candidates) == 1:
                            matched_mouza_id = candidates[0]["id"]
                            matched_mouza_uuid = candidates[0]["uuid"]
                            matched_survey_type_id = (
                                matched_survey_type_id or candidates[0]["survey_type_id"]
                            )

                    rec = MouzaMapMetadataRecord(
                        district_bbs_code=district_bbs,
                        upazila_folder_name=upazila_folder,
                        survey_folder_name=survey_folder,
                        folder_path=folder_path,
                        file_name=item_name,
                        drive_file_id=normalize_identifier(item.get("id")),
                        drive_id=normalize_identifier(item.get("driveId")),
                        kind=normalize_nfc(item.get("kind")),
                        mime_type=mime_type,
                        modified_time=normalize_nfc(item.get("modifiedTime")),
                        sheet_number=parsed["parsed_sheet_number"],
                        parsed_jl_number=parsed_jl,
                        parsed_survey_hint=parsed_survey_hint,
                        has_download_link_flag=bool(item.get("link")),
                        matched_mouza_uuid=matched_mouza_uuid,
                        verification_status=default_verif_status,
                        verification_note=(
                            f"{base_verif_note} Public map directory metadata only; "
                            f"file binary not downloaded."
                        ),
                        provenance_id=out.provenance.provenance_id,
                    )
                    self.db.upsert_mouza_map_metadata(
                        district_id=district_db_id,
                        mouza_id=matched_mouza_id,
                        survey_type_id=matched_survey_type_id,
                        rec=rec,
                    )

                if not next_token or next_token in seen_tokens:
                    break
                seen_tokens.add(next_token)
                page_token = next_token

    def _fetch_table_counts(self) -> dict[str, int]:
        tables = (
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
        counts: dict[str, int] = {}
        for table in tables:
            row = self.db.conn.execute(f"SELECT COUNT(*) AS c FROM {table}").fetchone()
            counts[table] = int(row["c"])
        return counts
