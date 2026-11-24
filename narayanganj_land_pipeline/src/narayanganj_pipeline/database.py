from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Iterable, Optional

from .models import (
    CollectionRunRecord,
    DistrictRecord,
    MouzaMapMetadataRecord,
    MouzaRecord,
    MunicipalityRecord,
    ProvenanceRecord,
    SurveyTypeRecord,
    ThanaRecord,
    UnionRecord,
    UpazilaRecord,
)
from .normalizer import canonical_name_key

SCHEMA_SQL = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS collection_runs (
    run_id TEXT PRIMARY KEY,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    target_district_bbs TEXT NOT NULL,
    target_district_name_bn TEXT NOT NULL,
    status TEXT NOT NULL,
    user_agent TEXT NOT NULL,
    rate_limit_seconds REAL NOT NULL,
    notes TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS source_provenance (
    provenance_id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    source_name TEXT NOT NULL,
    source_tier TEXT NOT NULL,
    source_url TEXT NOT NULL,
    http_status INTEGER,
    access_status TEXT NOT NULL,
    access_policy_note TEXT NOT NULL DEFAULT '',
    collected_at TEXT NOT NULL,
    content_hash TEXT,
    FOREIGN KEY (run_id) REFERENCES collection_runs(run_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS districts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_district_id TEXT,
    bbs_code TEXT NOT NULL UNIQUE,
    division_bbs_code TEXT,
    division_name_bn TEXT,
    division_name_en TEXT,
    name_bn TEXT NOT NULL,
    name_en TEXT,
    verification_status TEXT NOT NULL,
    verification_note TEXT NOT NULL DEFAULT '',
    provenance_id TEXT NOT NULL,
    FOREIGN KEY (provenance_id) REFERENCES source_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS upazilas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    district_id INTEGER NOT NULL,
    district_bbs_code TEXT NOT NULL,
    source_upazila_id TEXT,
    bbs_code TEXT,
    name_bn TEXT NOT NULL,
    name_en TEXT,
    canonical_key TEXT NOT NULL,
    row_status INTEGER,
    verification_status TEXT NOT NULL,
    verification_note TEXT NOT NULL DEFAULT '',
    provenance_id TEXT NOT NULL,
    UNIQUE (district_id, bbs_code, canonical_key),
    FOREIGN KEY (district_id) REFERENCES districts(id) ON DELETE CASCADE,
    FOREIGN KEY (provenance_id) REFERENCES source_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS thanas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    district_id INTEGER NOT NULL,
    upazila_id INTEGER,
    district_bbs_code TEXT NOT NULL,
    upazila_bbs_code TEXT,
    source_thana_id TEXT,
    lams_id TEXT,
    bbs_code TEXT,
    jurisdiction_type TEXT NOT NULL,
    name_bn TEXT NOT NULL,
    name_en TEXT,
    canonical_key TEXT NOT NULL,
    verification_status TEXT NOT NULL,
    verification_note TEXT NOT NULL DEFAULT '',
    provenance_id TEXT NOT NULL,
    UNIQUE (district_id, bbs_code, canonical_key),
    FOREIGN KEY (district_id) REFERENCES districts(id) ON DELETE CASCADE,
    FOREIGN KEY (upazila_id) REFERENCES upazilas(id) ON DELETE SET NULL,
    FOREIGN KEY (provenance_id) REFERENCES source_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS unions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    district_id INTEGER NOT NULL,
    upazila_id INTEGER,
    district_bbs_code TEXT NOT NULL,
    upazila_bbs_code TEXT,
    source_union_id TEXT,
    bbs_code TEXT,
    name_bn TEXT NOT NULL,
    name_en TEXT,
    canonical_key TEXT NOT NULL,
    verification_status TEXT NOT NULL,
    verification_note TEXT NOT NULL DEFAULT '',
    provenance_id TEXT NOT NULL,
    UNIQUE (district_id, upazila_bbs_code, canonical_key),
    FOREIGN KEY (district_id) REFERENCES districts(id) ON DELETE CASCADE,
    FOREIGN KEY (upazila_id) REFERENCES upazilas(id) ON DELETE SET NULL,
    FOREIGN KEY (provenance_id) REFERENCES source_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS municipalities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    district_id INTEGER NOT NULL,
    upazila_id INTEGER,
    district_bbs_code TEXT NOT NULL,
    upazila_bbs_code TEXT,
    source_municipality_id TEXT,
    bbs_code TEXT,
    municipality_type TEXT NOT NULL,
    name_bn TEXT NOT NULL,
    name_en TEXT,
    canonical_key TEXT NOT NULL,
    verification_status TEXT NOT NULL,
    verification_note TEXT NOT NULL DEFAULT '',
    provenance_id TEXT NOT NULL,
    UNIQUE (district_id, canonical_key),
    FOREIGN KEY (district_id) REFERENCES districts(id) ON DELETE CASCADE,
    FOREIGN KEY (upazila_id) REFERENCES upazilas(id) ON DELETE SET NULL,
    FOREIGN KEY (provenance_id) REFERENCES source_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS survey_types (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    district_id INTEGER NOT NULL,
    district_bbs_code TEXT NOT NULL,
    survey_id TEXT NOT NULL,
    survey_code_en TEXT,
    name_bn TEXT NOT NULL,
    survey_order INTEGER,
    verification_status TEXT NOT NULL,
    verification_note TEXT NOT NULL DEFAULT '',
    provenance_id TEXT NOT NULL,
    UNIQUE (district_id, survey_id),
    FOREIGN KEY (district_id) REFERENCES districts(id) ON DELETE CASCADE,
    FOREIGN KEY (provenance_id) REFERENCES source_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS mouzas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    district_id INTEGER NOT NULL,
    upazila_id INTEGER,
    thana_id INTEGER,
    union_id INTEGER,
    municipality_id INTEGER,
    survey_type_id INTEGER,
    district_bbs_code TEXT NOT NULL,
    upazila_bbs_code TEXT,
    thana_bbs_code TEXT,
    source_record_id TEXT,
    mouza_identifier TEXT,
    mouza_uuid TEXT,
    name_bn TEXT NOT NULL,
    name_en TEXT,
    canonical_name_key TEXT NOT NULL,
    jl_number TEXT,
    survey_id TEXT,
    survey_name_bn TEXT,
    survey_name_en TEXT,
    mutation_survey_id TEXT,
    mutation_survey_name_en TEXT,
    source_reported_district_bn TEXT,
    source_reported_upazila_bn TEXT,
    verification_status TEXT NOT NULL,
    verification_note TEXT NOT NULL DEFAULT '',
    provenance_id TEXT NOT NULL,
    UNIQUE (district_id, upazila_bbs_code, survey_id, source_record_id, canonical_name_key, jl_number),
    FOREIGN KEY (district_id) REFERENCES districts(id) ON DELETE CASCADE,
    FOREIGN KEY (upazila_id) REFERENCES upazilas(id) ON DELETE SET NULL,
    FOREIGN KEY (thana_id) REFERENCES thanas(id) ON DELETE SET NULL,
    FOREIGN KEY (union_id) REFERENCES unions(id) ON DELETE SET NULL,
    FOREIGN KEY (municipality_id) REFERENCES municipalities(id) ON DELETE SET NULL,
    FOREIGN KEY (survey_type_id) REFERENCES survey_types(id) ON DELETE SET NULL,
    FOREIGN KEY (provenance_id) REFERENCES source_provenance(provenance_id)
);

CREATE TABLE IF NOT EXISTS mouza_maps_metadata (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    district_id INTEGER NOT NULL,
    mouza_id INTEGER,
    survey_type_id INTEGER,
    district_bbs_code TEXT NOT NULL,
    upazila_folder_name TEXT,
    survey_folder_name TEXT,
    folder_path TEXT NOT NULL,
    file_name TEXT NOT NULL,
    drive_file_id TEXT,
    drive_id TEXT,
    kind TEXT,
    mime_type TEXT,
    modified_time TEXT,
    sheet_number TEXT,
    parsed_jl_number TEXT,
    parsed_survey_hint TEXT,
    has_download_link_flag INTEGER NOT NULL DEFAULT 0,
    matched_mouza_uuid TEXT,
    verification_status TEXT NOT NULL,
    verification_note TEXT NOT NULL DEFAULT '',
    provenance_id TEXT NOT NULL,
    UNIQUE (district_id, folder_path, file_name),
    FOREIGN KEY (district_id) REFERENCES districts(id) ON DELETE CASCADE,
    FOREIGN KEY (mouza_id) REFERENCES mouzas(id) ON DELETE SET NULL,
    FOREIGN KEY (survey_type_id) REFERENCES survey_types(id) ON DELETE SET NULL,
    FOREIGN KEY (provenance_id) REFERENCES source_provenance(provenance_id)
);

CREATE INDEX IF NOT EXISTS idx_provenance_run ON source_provenance(run_id);
CREATE INDEX IF NOT EXISTS idx_provenance_source ON source_provenance(source_name, access_status);
CREATE INDEX IF NOT EXISTS idx_upazilas_bbs ON upazilas(bbs_code);
CREATE INDEX IF NOT EXISTS idx_thanas_bbs ON thanas(bbs_code);
CREATE INDEX IF NOT EXISTS idx_mouzas_district ON mouzas(district_id);
CREATE INDEX IF NOT EXISTS idx_mouzas_upazila ON mouzas(upazila_id);
CREATE INDEX IF NOT EXISTS idx_mouzas_thana ON mouzas(thana_id);
CREATE INDEX IF NOT EXISTS idx_mouzas_jl ON mouzas(jl_number);
CREATE INDEX IF NOT EXISTS idx_mouzas_uuid ON mouzas(mouza_uuid);
CREATE INDEX IF NOT EXISTS idx_mouzas_canon ON mouzas(canonical_name_key, jl_number);
CREATE INDEX IF NOT EXISTS idx_mouzas_verification ON mouzas(verification_status);
CREATE INDEX IF NOT EXISTS idx_maps_mouza ON mouza_maps_metadata(mouza_id);
CREATE INDEX IF NOT EXISTS idx_maps_verification ON mouza_maps_metadata(verification_status);
"""


class LandGeoDatabase:
    def __init__(self, db_path: Path) -> None:
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(str(self.db_path))
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA foreign_keys = ON;")
        self.conn.executescript(SCHEMA_SQL)
        self.conn.commit()

    def close(self) -> None:
        self.conn.close()

    def __enter__(self) -> "LandGeoDatabase":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    def start_run(self, run: CollectionRunRecord) -> None:
        self.conn.execute(
            """
            INSERT INTO collection_runs (
                run_id, started_at, finished_at, target_district_bbs,
                target_district_name_bn, status, user_agent, rate_limit_seconds, notes
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                run.run_id,
                run.started_at,
                run.finished_at,
                run.target_district_bbs,
                run.target_district_name_bn,
                run.status,
                run.user_agent,
                run.rate_limit_seconds,
                run.notes,
            ),
        )
        self.conn.commit()

    def finish_run(self, run_id: str, finished_at: str, status: str, notes: str) -> None:
        self.conn.execute(
            """
            UPDATE collection_runs
            SET finished_at = ?, status = ?, notes = ?
            WHERE run_id = ?
            """,
            (finished_at, status, notes, run_id),
        )
        self.conn.commit()

    def insert_provenance_batch(self, records: Iterable[ProvenanceRecord]) -> None:
        self.conn.executemany(
            """
            INSERT OR IGNORE INTO source_provenance (
                provenance_id, run_id, source_name, source_tier, source_url,
                http_status, access_status, access_policy_note, collected_at, content_hash
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    r.provenance_id,
                    r.run_id,
                    r.source_name,
                    r.source_tier,
                    r.source_url,
                    r.http_status,
                    r.access_status,
                    r.access_policy_note,
                    r.collected_at,
                    r.content_hash,
                )
                for r in records
            ],
        )
        self.conn.commit()

    def upsert_district(self, d: DistrictRecord) -> int:
        self.conn.execute(
            """
            INSERT INTO districts (
                source_district_id, bbs_code, division_bbs_code, division_name_bn,
                division_name_en, name_bn, name_en, verification_status, verification_note, provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(bbs_code) DO UPDATE SET
                source_district_id = excluded.source_district_id,
                division_bbs_code = excluded.division_bbs_code,
                division_name_bn = excluded.division_name_bn,
                division_name_en = excluded.division_name_en,
                name_bn = excluded.name_bn,
                name_en = excluded.name_en,
                verification_status = excluded.verification_status,
                verification_note = excluded.verification_note,
                provenance_id = excluded.provenance_id
            """,
            (
                d.source_district_id,
                d.bbs_code,
                d.division_bbs_code,
                d.division_name_bn,
                d.division_name_en,
                d.name_bn,
                d.name_en,
                d.verification_status.value,
                d.verification_note,
                d.provenance_id,
            ),
        )
        self.conn.commit()
        row = self.conn.execute(
            "SELECT id FROM districts WHERE bbs_code = ?", (d.bbs_code,)
        ).fetchone()
        return int(row["id"])

    def upsert_upazila(self, district_id: int, u: UpazilaRecord) -> int:
        canon = canonical_name_key(u.name_bn)
        self.conn.execute(
            """
            INSERT INTO upazilas (
                district_id, district_bbs_code, source_upazila_id, bbs_code,
                name_bn, name_en, canonical_key, row_status, verification_status,
                verification_note, provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(district_id, bbs_code, canonical_key) DO UPDATE SET
                source_upazila_id = excluded.source_upazila_id,
                name_bn = excluded.name_bn,
                name_en = excluded.name_en,
                row_status = excluded.row_status,
                verification_status = excluded.verification_status,
                verification_note = excluded.verification_note,
                provenance_id = excluded.provenance_id
            """,
            (
                district_id,
                u.district_bbs_code,
                u.source_upazila_id,
                u.bbs_code or "",
                u.name_bn,
                u.name_en,
                canon,
                u.row_status,
                u.verification_status.value,
                u.verification_note,
                u.provenance_id,
            ),
        )
        self.conn.commit()
        row = self.conn.execute(
            "SELECT id FROM upazilas WHERE district_id = ? AND bbs_code = ? AND canonical_key = ?",
            (district_id, u.bbs_code or "", canon),
        ).fetchone()
        return int(row["id"])

    def upsert_thana(self, district_id: int, upazila_id: Optional[int], t: ThanaRecord) -> int:
        canon = canonical_name_key(t.name_bn)
        self.conn.execute(
            """
            INSERT INTO thanas (
                district_id, upazila_id, district_bbs_code, upazila_bbs_code,
                source_thana_id, lams_id, bbs_code, jurisdiction_type,
                name_bn, name_en, canonical_key, verification_status, verification_note, provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(district_id, bbs_code, canonical_key) DO UPDATE SET
                upazila_id = excluded.upazila_id,
                upazila_bbs_code = excluded.upazila_bbs_code,
                source_thana_id = excluded.source_thana_id,
                lams_id = excluded.lams_id,
                jurisdiction_type = excluded.jurisdiction_type,
                name_bn = excluded.name_bn,
                name_en = excluded.name_en,
                verification_status = excluded.verification_status,
                verification_note = excluded.verification_note,
                provenance_id = excluded.provenance_id
            """,
            (
                district_id,
                upazila_id,
                t.district_bbs_code,
                t.upazila_bbs_code,
                t.source_thana_id,
                t.lams_id,
                t.bbs_code or "",
                t.jurisdiction_type,
                t.name_bn,
                t.name_en,
                canon,
                t.verification_status.value,
                t.verification_note,
                t.provenance_id,
            ),
        )
        self.conn.commit()
        row = self.conn.execute(
            "SELECT id FROM thanas WHERE district_id = ? AND bbs_code = ? AND canonical_key = ?",
            (district_id, t.bbs_code or "", canon),
        ).fetchone()
        return int(row["id"])

    def upsert_union(self, district_id: int, upazila_id: Optional[int], u: UnionRecord) -> int:
        canon = canonical_name_key(u.name_bn)
        self.conn.execute(
            """
            INSERT INTO unions (
                district_id, upazila_id, district_bbs_code, upazila_bbs_code,
                source_union_id, bbs_code, name_bn, name_en, canonical_key,
                verification_status, verification_note, provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(district_id, upazila_bbs_code, canonical_key) DO UPDATE SET
                upazila_id = excluded.upazila_id,
                source_union_id = excluded.source_union_id,
                bbs_code = excluded.bbs_code,
                name_bn = excluded.name_bn,
                name_en = excluded.name_en,
                verification_status = excluded.verification_status,
                verification_note = excluded.verification_note,
                provenance_id = excluded.provenance_id
            """,
            (
                district_id,
                upazila_id,
                u.district_bbs_code,
                u.upazila_bbs_code or "",
                u.source_union_id,
                u.bbs_code,
                u.name_bn,
                u.name_en,
                canon,
                u.verification_status.value,
                u.verification_note,
                u.provenance_id,
            ),
        )
        self.conn.commit()
        row = self.conn.execute(
            "SELECT id FROM unions WHERE district_id = ? AND upazila_bbs_code = ? AND canonical_key = ?",
            (district_id, u.upazila_bbs_code or "", canon),
        ).fetchone()
        return int(row["id"])

    def upsert_municipality(
        self, district_id: int, upazila_id: Optional[int], m: MunicipalityRecord
    ) -> int:
        canon = canonical_name_key(m.name_bn)
        self.conn.execute(
            """
            INSERT INTO municipalities (
                district_id, upazila_id, district_bbs_code, upazila_bbs_code,
                source_municipality_id, bbs_code, municipality_type, name_bn, name_en,
                canonical_key, verification_status, verification_note, provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(district_id, canonical_key) DO UPDATE SET
                upazila_id = excluded.upazila_id,
                upazila_bbs_code = excluded.upazila_bbs_code,
                source_municipality_id = excluded.source_municipality_id,
                bbs_code = excluded.bbs_code,
                municipality_type = excluded.municipality_type,
                name_bn = excluded.name_bn,
                name_en = excluded.name_en,
                verification_status = excluded.verification_status,
                verification_note = excluded.verification_note,
                provenance_id = excluded.provenance_id
            """,
            (
                district_id,
                upazila_id,
                m.district_bbs_code,
                m.upazila_bbs_code,
                m.source_municipality_id,
                m.bbs_code,
                m.municipality_type,
                m.name_bn,
                m.name_en,
                canon,
                m.verification_status.value,
                m.verification_note,
                m.provenance_id,
            ),
        )
        self.conn.commit()
        row = self.conn.execute(
            "SELECT id FROM municipalities WHERE district_id = ? AND canonical_key = ?",
            (district_id, canon),
        ).fetchone()
        return int(row["id"])

    def upsert_survey_type(self, district_id: int, s: SurveyTypeRecord) -> int:
        self.conn.execute(
            """
            INSERT INTO survey_types (
                district_id, district_bbs_code, survey_id, survey_code_en,
                name_bn, survey_order, verification_status, verification_note, provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(district_id, survey_id) DO UPDATE SET
                survey_code_en = COALESCE(excluded.survey_code_en, survey_types.survey_code_en),
                name_bn = excluded.name_bn,
                survey_order = excluded.survey_order,
                verification_status = excluded.verification_status,
                verification_note = excluded.verification_note,
                provenance_id = excluded.provenance_id
            """,
            (
                district_id,
                s.district_bbs_code,
                s.survey_id,
                s.survey_code_en,
                s.name_bn,
                s.survey_order,
                s.verification_status.value,
                s.verification_note,
                s.provenance_id,
            ),
        )
        self.conn.commit()
        row = self.conn.execute(
            "SELECT id FROM survey_types WHERE district_id = ? AND survey_id = ?",
            (district_id, s.survey_id),
        ).fetchone()
        return int(row["id"])

    def upsert_mouza(
        self,
        *,
        district_id: int,
        upazila_id: Optional[int],
        thana_id: Optional[int],
        survey_type_id: Optional[int],
        m: MouzaRecord,
    ) -> int:
        canon = canonical_name_key(m.name_bn)
        upazila_key = m.upazila_bbs_code or m.thana_bbs_code or ""
        survey_key = m.survey_id or ""
        src_key = m.source_record_id or ""
        jl_key = m.jl_number or ""
        self.conn.execute(
            """
            INSERT INTO mouzas (
                district_id, upazila_id, thana_id, union_id, municipality_id, survey_type_id,
                district_bbs_code, upazila_bbs_code, thana_bbs_code, source_record_id,
                mouza_identifier, mouza_uuid, name_bn, name_en, canonical_name_key,
                jl_number, survey_id, survey_name_bn, survey_name_en,
                mutation_survey_id, mutation_survey_name_en,
                source_reported_district_bn, source_reported_upazila_bn,
                verification_status, verification_note, provenance_id
            ) VALUES (?, ?, ?, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(district_id, upazila_bbs_code, survey_id, source_record_id, canonical_name_key, jl_number)
            DO UPDATE SET
                upazila_id = excluded.upazila_id,
                thana_id = excluded.thana_id,
                survey_type_id = excluded.survey_type_id,
                mouza_identifier = excluded.mouza_identifier,
                mouza_uuid = excluded.mouza_uuid,
                name_bn = excluded.name_bn,
                name_en = excluded.name_en,
                survey_name_bn = excluded.survey_name_bn,
                survey_name_en = excluded.survey_name_en,
                mutation_survey_id = excluded.mutation_survey_id,
                mutation_survey_name_en = excluded.mutation_survey_name_en,
                source_reported_district_bn = excluded.source_reported_district_bn,
                source_reported_upazila_bn = excluded.source_reported_upazila_bn,
                verification_status = excluded.verification_status,
                verification_note = excluded.verification_note,
                provenance_id = excluded.provenance_id
            """,
            (
                district_id,
                upazila_id,
                thana_id,
                survey_type_id,
                m.district_bbs_code,
                upazila_key,
                m.thana_bbs_code,
                src_key,
                m.mouza_identifier,
                m.mouza_uuid,
                m.name_bn,
                m.name_en,
                canon,
                jl_key,
                survey_key,
                m.survey_name_bn,
                m.survey_name_en,
                m.mutation_survey_id,
                m.mutation_survey_name_en,
                m.source_reported_district_bn,
                m.source_reported_upazila_bn,
                m.verification_status.value,
                m.verification_note,
                m.provenance_id,
            ),
        )
        self.conn.commit()
        row = self.conn.execute(
            """
            SELECT id FROM mouzas
            WHERE district_id = ? AND upazila_bbs_code = ? AND survey_id = ?
              AND source_record_id = ? AND canonical_name_key = ? AND jl_number = ?
            """,
            (district_id, upazila_key, survey_key, src_key, canon, jl_key),
        ).fetchone()
        return int(row["id"])

    def upsert_mouza_map_metadata(
        self,
        *,
        district_id: int,
        mouza_id: Optional[int],
        survey_type_id: Optional[int],
        rec: MouzaMapMetadataRecord,
    ) -> int:
        self.conn.execute(
            """
            INSERT INTO mouza_maps_metadata (
                district_id, mouza_id, survey_type_id, district_bbs_code,
                upazila_folder_name, survey_folder_name, folder_path, file_name,
                drive_file_id, drive_id, kind, mime_type, modified_time,
                sheet_number, parsed_jl_number, parsed_survey_hint,
                has_download_link_flag, matched_mouza_uuid,
                verification_status, verification_note, provenance_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(district_id, folder_path, file_name) DO UPDATE SET
                mouza_id = excluded.mouza_id,
                survey_type_id = excluded.survey_type_id,
                drive_file_id = excluded.drive_file_id,
                drive_id = excluded.drive_id,
                kind = excluded.kind,
                mime_type = excluded.mime_type,
                modified_time = excluded.modified_time,
                sheet_number = excluded.sheet_number,
                parsed_jl_number = excluded.parsed_jl_number,
                parsed_survey_hint = excluded.parsed_survey_hint,
                has_download_link_flag = excluded.has_download_link_flag,
                matched_mouza_uuid = excluded.matched_mouza_uuid,
                verification_status = excluded.verification_status,
                verification_note = excluded.verification_note,
                provenance_id = excluded.provenance_id
            """,
            (
                district_id,
                mouza_id,
                survey_type_id,
                rec.district_bbs_code,
                rec.upazila_folder_name,
                rec.survey_folder_name,
                rec.folder_path,
                rec.file_name,
                rec.drive_file_id,
                rec.drive_id,
                rec.kind,
                rec.mime_type,
                rec.modified_time,
                rec.sheet_number,
                rec.parsed_jl_number,
                rec.parsed_survey_hint,
                1 if rec.has_download_link_flag else 0,
                rec.matched_mouza_uuid,
                rec.verification_status.value,
                rec.verification_note,
                rec.provenance_id,
            ),
        )
        self.conn.commit()
        row = self.conn.execute(
            "SELECT id FROM mouza_maps_metadata WHERE district_id = ? AND folder_path = ? AND file_name = ?",
            (district_id, rec.folder_path, rec.file_name),
        ).fetchone()
        return int(row["id"])
