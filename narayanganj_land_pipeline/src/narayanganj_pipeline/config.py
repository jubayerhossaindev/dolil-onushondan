from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path


PACKAGE_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT_DIR = PACKAGE_ROOT / "output"
DEFAULT_REPORTS_DIR = PACKAGE_ROOT / "reports"
DEFAULT_LOGS_DIR = PACKAGE_ROOT / "logs"
DEFAULT_DB_PATH = DEFAULT_OUTPUT_DIR / "narayanganj_land_geo.sqlite"


@dataclass(frozen=True)
class PipelineConfig:
    primary_reference_url: str = "https://e-porcha.com/"
    official_source_url: str = "https://dlrms.land.gov.bd/"
    # Public geographic endpoints discovered in e-porcha.com frontend scripts
    geo_api_base: str = "https://dlrms-final.onrender.com/api"
    mouza_map_api_url: str = "https://dlrms-final.onrender.com/api/mouza-map-tech"

    target_division_bbs_code: str = "30"
    target_division_name_bn: str = "ঢাকা"
    target_division_name_en: str = "Dhaka"
    target_district_bbs_code: str = "67"
    target_district_name_bn: str = "নারায়ণগঞ্জ"
    target_district_name_en: str = "NARAYANGANJ"

    user_agent: str = (
        "NarayanganjLandGeoPipeline/1.0 "
        "(+research-public-metadata; conservative-rate-limit; no-pii)"
    )
    request_delay_seconds: float = 2.2
    map_request_delay_seconds: float = 2.6
    request_timeout_seconds: float = 25.0
    max_retries: int = 3
    retry_backoff_seconds: float = 2.5

    # Safety bounds for recursive map folder inspection
    max_map_pages_per_folder: int = 10
    max_map_depth: int = 4

    output_dir: Path = DEFAULT_OUTPUT_DIR
    reports_dir: Path = DEFAULT_REPORTS_DIR
    logs_dir: Path = DEFAULT_LOGS_DIR
    db_path: Path = DEFAULT_DB_PATH

    enable_playwright_fallback: bool = True
