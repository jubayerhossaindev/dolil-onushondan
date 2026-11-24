from __future__ import annotations

import sqlite3
from pathlib import Path

import pytest

from narayanganj_pipeline.database import LandGeoDatabase
from narayanganj_pipeline.models import (
    CollectionRunRecord,
    DistrictRecord,
    MouzaRecord,
    ProvenanceRecord,
    SurveyTypeRecord,
    UpazilaRecord,
    VerificationStatus,
)


def test_sqlite_schema_foreign_keys_and_uniqueness(tmp_path: Path) -> None:
    db_path = tmp_path / "schema.sqlite"
    with LandGeoDatabase(db_path) as db:
        db.start_run(
            CollectionRunRecord(
                run_id="r1",
                started_at="2026-11-23T00:00:00Z",
                user_agent="pytest",
                rate_limit_seconds=0.0,
            )
        )
        prov = ProvenanceRecord(
            provenance_id="p1",
            run_id="r1",
            source_name="test",
            source_tier="reference_eporcha_api",
            source_url="https://example.test/api",
            http_status=200,
            access_status="allowed",
            collected_at="2026-11-23T00:00:00Z",
        )
        db.insert_provenance_batch([prov])

        d_id = db.upsert_district(
            DistrictRecord(
                source_district_id="28",
                bbs_code="67",
                name_bn="নারায়ণগঞ্জ",
                name_en="NARAYANGANJ",
                verification_status=VerificationStatus.UNVERIFIED,
                provenance_id="p1",
            )
        )
        u_id = db.upsert_upazila(
            d_id,
            UpazilaRecord(
                district_bbs_code="67",
                source_upazila_id="1",
                bbs_code="06",
                name_bn="বন্দর",
                provenance_id="p1",
            ),
        )
        s_id = db.upsert_survey_type(
            d_id,
            SurveyTypeRecord(
                district_bbs_code="67",
                survey_id="3",
                survey_code_en="RS",
                name_bn="আর এস",
                survey_order=3,
                provenance_id="p1",
            ),
        )

        # Upserting the same Mouza twice must remain 1 row (no duplicate insertion)
        mouza = MouzaRecord(
            district_bbs_code="67",
            upazila_bbs_code="06",
            source_record_id="555",
            mouza_identifier="M-555",
            mouza_uuid="uuid-555",
            name_bn="মদনপুর",
            jl_number="42",
            survey_id="3",
            survey_name_bn="আর এস",
            survey_name_en="RS",
            provenance_id="p1",
        )
        id1 = db.upsert_mouza(district_id=d_id, upazila_id=u_id, thana_id=None, survey_type_id=s_id, m=mouza)
        id2 = db.upsert_mouza(district_id=d_id, upazila_id=u_id, thana_id=None, survey_type_id=s_id, m=mouza)
        assert id1 == id2

        # Foreign key enforcement must reject invalid district_id
        with pytest.raises(sqlite3.IntegrityError):
            db.conn.execute(
                """
                INSERT INTO upazilas (
                    district_id, district_bbs_code, bbs_code, name_bn, canonical_key,
                    verification_status, provenance_id
                ) VALUES (9999, '67', '99', 'অজানা', 'অজানা', 'unverified', 'p1')
                """
            )
