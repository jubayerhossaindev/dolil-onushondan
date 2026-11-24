from __future__ import annotations

from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field, field_validator

from .normalizer import normalize_identifier, normalize_nfc


class VerificationStatus(str, Enum):
    VERIFIED = "verified"
    UNVERIFIED = "unverified"


class AccessStatus(str, Enum):
    ALLOWED = "allowed"
    DISALLOWED_ROBOTS = "disallowed_robots"
    BLOCKED_WAF = "blocked_waf"
    BLOCKED_AUTH_OR_CAPTCHA = "blocked_auth_or_captcha"
    RATE_LIMITED = "rate_limited"
    NOT_FOUND = "not_found"
    ERROR = "error"


class PolicyCheckResult(BaseModel):
    source_name: str
    base_url: str
    robots_url: str
    robots_http_status: Optional[int] = None
    robots_allows_root: bool = False
    robots_notes: str = ""
    home_http_status: Optional[int] = None
    access_status: AccessStatus
    waf_or_captcha_detected: bool = False
    requires_js_rendering: bool = False
    terms_summary: str = ""
    evidence_headers: dict[str, str] = Field(default_factory=dict)
    checked_at: str


class ProvenanceRecord(BaseModel):
    provenance_id: str
    run_id: str
    source_name: str
    source_tier: str  # "official_dlrms" | "reference_eporcha" | "reference_eporcha_api"
    source_url: str
    http_status: Optional[int] = None
    access_status: str
    access_policy_note: str = ""
    collected_at: str
    content_hash: Optional[str] = None


class CollectionRunRecord(BaseModel):
    run_id: str
    started_at: str
    finished_at: Optional[str] = None
    target_district_bbs: str = "67"
    target_district_name_bn: str = "নারায়ণগঞ্জ"
    status: str = "running"
    user_agent: str
    rate_limit_seconds: float
    notes: str = ""


class DistrictRecord(BaseModel):
    source_district_id: Optional[str] = None
    bbs_code: str
    division_bbs_code: Optional[str] = None
    division_name_bn: Optional[str] = None
    division_name_en: Optional[str] = None
    name_bn: str
    name_en: Optional[str] = None
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    verification_note: str = ""
    provenance_id: str

    @field_validator("name_bn", "name_en", "division_name_bn", "division_name_en", mode="before")
    @classmethod
    def _nfc_text(cls, v):
        return normalize_nfc(v)

    @field_validator("bbs_code", "division_bbs_code", "source_district_id", mode="before")
    @classmethod
    def _norm_id(cls, v):
        return normalize_identifier(v)


class UpazilaRecord(BaseModel):
    district_bbs_code: str
    source_upazila_id: Optional[str] = None
    bbs_code: Optional[str] = None
    name_bn: str
    name_en: Optional[str] = None
    row_status: Optional[int] = None
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    verification_note: str = ""
    provenance_id: str

    @field_validator("name_bn", "name_en", mode="before")
    @classmethod
    def _nfc_text(cls, v):
        return normalize_nfc(v)

    @field_validator("district_bbs_code", "source_upazila_id", "bbs_code", mode="before")
    @classmethod
    def _norm_id(cls, v):
        return normalize_identifier(v)


class ThanaRecord(BaseModel):
    district_bbs_code: str
    upazila_bbs_code: Optional[str] = None
    source_thana_id: Optional[str] = None
    lams_id: Optional[str] = None
    bbs_code: Optional[str] = None
    jurisdiction_type: str = "REVENUE_CIRCLE_OR_THANA"
    name_bn: str
    name_en: Optional[str] = None
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    verification_note: str = ""
    provenance_id: str

    @field_validator("name_bn", "name_en", mode="before")
    @classmethod
    def _nfc_text(cls, v):
        return normalize_nfc(v)

    @field_validator("district_bbs_code", "upazila_bbs_code", "source_thana_id", "lams_id", "bbs_code", mode="before")
    @classmethod
    def _norm_id(cls, v):
        return normalize_identifier(v)


class UnionRecord(BaseModel):
    district_bbs_code: str
    upazila_bbs_code: Optional[str] = None
    source_union_id: Optional[str] = None
    bbs_code: Optional[str] = None
    name_bn: str
    name_en: Optional[str] = None
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    verification_note: str = ""
    provenance_id: str

    @field_validator("name_bn", "name_en", mode="before")
    @classmethod
    def _nfc_text(cls, v):
        return normalize_nfc(v)


class MunicipalityRecord(BaseModel):
    district_bbs_code: str
    upazila_bbs_code: Optional[str] = None
    source_municipality_id: Optional[str] = None
    bbs_code: Optional[str] = None
    municipality_type: str = "POURASHAVA_OR_CITY_CORP"
    name_bn: str
    name_en: Optional[str] = None
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    verification_note: str = ""
    provenance_id: str

    @field_validator("name_bn", "name_en", mode="before")
    @classmethod
    def _nfc_text(cls, v):
        return normalize_nfc(v)


class SurveyTypeRecord(BaseModel):
    district_bbs_code: str
    survey_id: str
    survey_code_en: Optional[str] = None
    name_bn: str
    survey_order: Optional[int] = None
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    verification_note: str = ""
    provenance_id: str

    @field_validator("name_bn", "survey_code_en", mode="before")
    @classmethod
    def _nfc_text(cls, v):
        return normalize_nfc(v)

    @field_validator("district_bbs_code", "survey_id", mode="before")
    @classmethod
    def _norm_id(cls, v):
        return normalize_identifier(v)


class MouzaRecord(BaseModel):
    district_bbs_code: str
    upazila_bbs_code: Optional[str] = None
    thana_bbs_code: Optional[str] = None
    union_source_id: Optional[str] = None
    municipality_source_id: Optional[str] = None
    source_record_id: Optional[str] = None
    mouza_identifier: Optional[str] = None
    mouza_uuid: Optional[str] = None
    name_bn: str
    name_en: Optional[str] = None
    jl_number: Optional[str] = None
    survey_id: Optional[str] = None
    survey_name_bn: Optional[str] = None
    survey_name_en: Optional[str] = None
    mutation_survey_id: Optional[str] = None
    mutation_survey_name_en: Optional[str] = None
    source_reported_district_bn: Optional[str] = None
    source_reported_upazila_bn: Optional[str] = None
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    verification_note: str = ""
    provenance_id: str

    @field_validator(
        "name_bn",
        "name_en",
        "survey_name_bn",
        "survey_name_en",
        "mutation_survey_name_en",
        "source_reported_district_bn",
        "source_reported_upazila_bn",
        mode="before",
    )
    @classmethod
    def _nfc_text(cls, v):
        return normalize_nfc(v)

    @field_validator(
        "district_bbs_code",
        "upazila_bbs_code",
        "thana_bbs_code",
        "source_record_id",
        "mouza_identifier",
        "mouza_uuid",
        "jl_number",
        "survey_id",
        "mutation_survey_id",
        mode="before",
    )
    @classmethod
    def _norm_id(cls, v):
        return normalize_identifier(v)


class MouzaMapMetadataRecord(BaseModel):
    district_bbs_code: str
    upazila_folder_name: Optional[str] = None
    survey_folder_name: Optional[str] = None
    folder_path: str
    file_name: str
    drive_file_id: Optional[str] = None
    drive_id: Optional[str] = None
    kind: Optional[str] = None
    mime_type: Optional[str] = None
    modified_time: Optional[str] = None
    sheet_number: Optional[str] = None
    parsed_jl_number: Optional[str] = None
    parsed_survey_hint: Optional[str] = None
    has_download_link_flag: bool = False
    matched_mouza_uuid: Optional[str] = None
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    verification_note: str = ""
    provenance_id: str

    @field_validator("upazila_folder_name", "survey_folder_name", "folder_path", "file_name", "sheet_number", mode="before")
    @classmethod
    def _nfc_text(cls, v):
        return normalize_nfc(v)
