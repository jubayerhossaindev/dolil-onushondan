# Narayanganj District Land-Geographic Collection Pipeline

Policy-compliant, modular Python pipeline for collecting, normalizing, storing, verifying, and auditing publicly exposed land-geographic metadata for **Narayanganj District (BBS Code: 67 / নারায়ণগঞ্জ)** from:

- Official portal referenced: `https://dlrms.land.gov.bd/`
- Primary public reference: `https://e-porcha.com/`

## Compliance & Data Guardrails

- **Preflight Inspection**: Checks `robots.txt`, terms/disclaimer pages, HTTP status codes, and WAF/CAPTCHA blocks before collecting data.
- **No Bypass**: Immediately stops on `HTTP 403` WAF blocks (`dlrms.land.gov.bd`) without attempting evasion.
- **No PII**: Never calls Khatian/owner endpoints (`/khatian`) and strips prohibited personal fields (`OWNERS`, `GUARDIANS`, `NID`, `phone`, `email`, etc.).
- **Metadata Only**: Lists public Mouza map directory metadata without downloading map binary files (`link` / `/mouza-map-tech-file`).
- **No Fabrication**: Empty/unexposed administrative divisions (`unions`, `municipalities`) remain `0` rows and are documented in `reports/missing_data_report.md`.
- **Verified vs. Unverified Separation**: Records uncorroborated against `dlrms.land.gov.bd` are labeled `unverified` and exported separately from `verified` datasets.

## Setup & Usage

```bash
cd narayanganj_land_pipeline
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt

# Run unit & mocked end-to-end tests
PYTHONPATH=src .venv/bin/python -m pytest -v

# Execute live collection, verification, CSV/JSON export, and Markdown audit reports
PYTHONPATH=src .venv/bin/python -m narayanganj_pipeline.cli run --max-map-depth 3

# Re-verify an existing SQLite database and regenerate reports
PYTHONPATH=src .venv/bin/python -m narayanganj_pipeline.cli verify
```

## Outputs

- SQLite database: `output/narayanganj_land_geo.sqlite`
- CSV exports: `output/csv/` (`verified/` and `unverified/` splits included)
- JSON exports: `output/json/` (`full_dataset.json`, `verified_dataset.json`, `unverified_dataset.json`, `verification_summary.json`)
- Audit reports: `reports/` (`source_attribution_report.md`, `duplicate_detection_report.md`, `missing_data_report.md`, `final_coverage_report.md`)
- Execution logs: `logs/`
