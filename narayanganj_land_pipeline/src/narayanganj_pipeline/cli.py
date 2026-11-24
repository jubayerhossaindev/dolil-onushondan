from __future__ import annotations

import argparse
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from .collector import CollectionSummary, NarayanganjCollector
from .config import PipelineConfig
from .database import LandGeoDatabase
from .exporter import PipelineExporter
from .http_client import ConservativeHttpClient
from .logging_utils import configure_run_logger
from .verifier import DatasetVerifier


def run_full_pipeline(config: Optional[PipelineConfig] = None) -> tuple[CollectionSummary, dict]:
    cfg = config or PipelineConfig()
    run_id = datetime.now(timezone.utc).strftime("run-%Y%m%dT%H%M%SZ")
    log_file = cfg.logs_dir / f"{run_id}.log"
    logger = configure_run_logger(log_file)
    logger.info("Starting Narayanganj land-geographic collection run %s", run_id)

    with (
        LandGeoDatabase(cfg.db_path) as db,
        ConservativeHttpClient(
            run_id=run_id,
            user_agent=cfg.user_agent,
            default_delay_seconds=cfg.request_delay_seconds,
            timeout_seconds=cfg.request_timeout_seconds,
            max_retries=cfg.max_retries,
            retry_backoff_seconds=cfg.retry_backoff_seconds,
            logger=logger,
        ) as http_client,
    ):
        collector = NarayanganjCollector(cfg, db, http_client, logger=logger)
        summary = collector.collect(run_id=run_id)
        verification = DatasetVerifier(db.conn, cfg.target_district_bbs_code).verify()
        exporter = PipelineExporter(db.conn, cfg.output_dir, cfg.reports_dir)
        exported = exporter.export_all(verification, summary)
        logger.info(
            "Run %s finished with status=%s; DB counts=%s",
            run_id,
            summary.status,
            summary.counts,
        )
        return summary, {"verification": verification, "exported": exported, "log_file": log_file}


def verify_and_export_existing(config: Optional[PipelineConfig] = None) -> Path:
    cfg = config or PipelineConfig()
    with LandGeoDatabase(cfg.db_path) as db:
        verification = DatasetVerifier(db.conn, cfg.target_district_bbs_code).verify()
        exporter = PipelineExporter(db.conn, cfg.output_dir, cfg.reports_dir)
        exporter.export_all(verification, None)
    return cfg.reports_dir / "final_coverage_report.md"


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(
        prog="narayanganj-pipeline",
        description="Policy-compliant Narayanganj District land-geographic metadata collection pipeline.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    run_p = sub.add_parser("run", help="Run preflight policy check, collection, verification, and export.")
    run_p.add_argument("--max-map-depth", type=int, default=4, help="Max folder recursion depth for map metadata.")
    run_p.add_argument("--db-path", type=Path, default=None, help="Optional custom SQLite output path.")

    verify_p = sub.add_parser("verify", help="Verify an existing SQLite database and regenerate reports/exports.")
    verify_p.add_argument("--db-path", type=Path, default=None, help="Optional custom SQLite input path.")

    args = parser.parse_args(argv)
    base_cfg = PipelineConfig()
    if args.command == "run":
        cfg = PipelineConfig(
            max_map_depth=args.max_map_depth,
            db_path=args.db_path or base_cfg.db_path,
        )
        summary, _ = run_full_pipeline(cfg)
        print(f"Status: {summary.status} | Counts: {summary.counts}")
        return 0 if summary.status in {"completed", "blocked"} else 1

    cfg = PipelineConfig(db_path=args.db_path or base_cfg.db_path)
    report_path = verify_and_export_existing(cfg)
    print(f"Verification reports regenerated at: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
