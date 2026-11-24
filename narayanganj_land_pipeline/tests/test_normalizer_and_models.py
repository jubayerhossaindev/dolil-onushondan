from __future__ import annotations

import unicodedata

from narayanganj_pipeline.models import DistrictRecord, MouzaRecord, VerificationStatus
from narayanganj_pipeline.normalizer import (
    bn_digits_to_ascii,
    canonical_name_key,
    is_narayanganj_district,
    normalize_nfc,
    parse_map_filename_metadata,
    strip_prohibited_pii,
)


def test_unicode_nfc_and_identifier_preservation() -> None:
    nfd_name = unicodedata.normalize("NFD", "নারায়ণগঞ্জ   সদর\u200b")
    assert normalize_nfc(nfd_name) == unicodedata.normalize("NFC", "নারায়ণগঞ্জ সদর")

    dist = DistrictRecord(
        source_district_id="28",
        bbs_code="67",
        division_bbs_code="30",
        name_bn=nfd_name,
        name_en="NARAYANGANJ",
        verification_status=VerificationStatus.UNVERIFIED,
        provenance_id="prov-1",
    )
    assert dist.bbs_code == "67"
    assert dist.name_bn == unicodedata.normalize("NFC", "নারায়ণগঞ্জ সদর")

    mouza = MouzaRecord(
        district_bbs_code="67",
        upazila_bbs_code="02",
        name_bn="চর  কিশোরগঞ্জ",
        jl_number="০০১",
        provenance_id="prov-1",
    )
    # Leading zeros & original Bengali numerals preserved
    assert mouza.upazila_bbs_code == "02"
    assert mouza.jl_number == "০০১"
    assert bn_digits_to_ascii(mouza.jl_number) == "001"


def test_narayanganj_scope_aliases_and_canonical_key() -> None:
    assert is_narayanganj_district(district_bbs_code="67") is True
    assert is_narayanganj_district(name_bn="নারায়নগঞ্জ") is True
    assert is_narayanganj_district(name_en="Narayanganj") is True
    assert is_narayanganj_district(district_bbs_code="26", name_bn="ঢাকা", name_en="Dhaka") is False

    assert canonical_name_key("নারায়ণগঞ্জ") == canonical_name_key("নারায়নগঞ্জ")


def test_strip_prohibited_pii() -> None:
    raw = {
        "ID": 101,
        "MOUZA_NAME": "কাশিপুর",
        "JL_NUMBER": "12",
        "OWNERS": ["Secret Owner"],
        "guardians": ["Secret Father"],
        "nid": "1234567890",
        "phone": "01700000000",
    }
    cleaned = strip_prohibited_pii(raw)
    assert cleaned == {"ID": 101, "MOUZA_NAME": "কাশিপুর", "JL_NUMBER": "12"}


def test_parse_map_filename_metadata() -> None:
    meta = parse_map_filename_metadata("Kashipur_JL-012_RS_Sheet_2.pdf")
    assert meta["file_extension"] == "pdf"
    assert meta["parsed_jl_number"] == "012"
    assert meta["parsed_survey_hint"] == "RS"
    assert meta["parsed_sheet_number"] == "2"
