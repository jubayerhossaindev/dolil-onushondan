from __future__ import annotations

import re
import unicodedata
from typing import Optional

_BN_TO_EN_DIGITS = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")
_ZERO_WIDTH_RE = re.compile(r"[\u200B\u200C\u200D\uFEFF]")
_MULTISPACE_RE = re.compile(r"\s+")

# Canonical Narayanganj district aliases (Bangla & English, normalized NFC)
NARAYANGANJ_ALIASES_BN = {
    "নারায়ণগঞ্জ",
    "নারায়ণগঞ্জ",
    "নারায়নগঞ্জ",
    "নারায়নগঞ্জ",
    "নারাইনগঞ্জ",
}
NARAYANGANJ_ALIASES_EN = {
    "narayanganj",
    "narayangonj",
    "naraynganj",
}

# PII / personal data field markers strictly prohibited from storage
PROHIBITED_PII_FIELDS = {
    "owners",
    "owner_name",
    "guardians",
    "guardian_name",
    "nid",
    "national_id",
    "phone",
    "mobile",
    "email",
    "address",
    "citizen_id",
}


def normalize_nfc(text: Optional[str]) -> Optional[str]:
    """Normalize Unicode to NFC and clean invisible zero-width/redundant whitespace while preserving Bengali script."""
    if text is None:
        return None
    cleaned = _ZERO_WIDTH_RE.sub("", str(text))
    cleaned = unicodedata.normalize("NFC", cleaned)
    cleaned = _MULTISPACE_RE.sub(" ", cleaned).strip()
    return cleaned if cleaned else None


def normalize_identifier(value: Optional[str | int]) -> Optional[str]:
    """Preserve original identifier string (including leading zeros) under NFC normalization."""
    if value is None:
        return None
    return normalize_nfc(str(value))


def bn_digits_to_ascii(text: Optional[str]) -> Optional[str]:
    """Convert Bengali numerals to ASCII for duplicate comparison keys without overwriting original values."""
    norm = normalize_nfc(text)
    if norm is None:
        return None
    return norm.translate(_BN_TO_EN_DIGITS)


def canonical_name_key(text: Optional[str]) -> str:
    """Create a comparison key for duplicate detection across slight Bengali/English spelling variants."""
    norm = normalize_nfc(text) or ""
    norm = bn_digits_to_ascii(norm) or ""
    # Normalize Ya-nukta (য় -> য + ় in NFD, already NFC) and common য়/য/ি/ী spelling variations only in key
    key = norm.lower()
    key = key.replace("য়", "য").replace("য়", "য").replace("ং", "ঙ").replace("ণ", "ন")
    key = re.sub(r"[-_.,/\\()\[\]:;'\"]+", " ", key)
    return _MULTISPACE_RE.sub(" ", key).strip()


def is_narayanganj_district(
    district_bbs_code: Optional[str] = None,
    name_bn: Optional[str] = None,
    name_en: Optional[str] = None,
) -> bool:
    if normalize_identifier(district_bbs_code) == "67":
        return True
    bn = normalize_nfc(name_bn)
    if bn and bn in {normalize_nfc(x) for x in NARAYANGANJ_ALIASES_BN}:
        return True
    en = (normalize_nfc(name_en) or "").lower()
    if en and en in NARAYANGANJ_ALIASES_EN:
        return True
    return False


def strip_prohibited_pii(payload: dict) -> dict:
    """Ensure personal landowner fields are never propagated even if encountered in an API response."""
    return {
        k: v
        for k, v in payload.items()
        if str(k).strip().lower() not in PROHIBITED_PII_FIELDS
    }


def parse_map_filename_metadata(filename: str) -> dict[str, Optional[str]]:
    """Extract optional JL/sheet/survey/extension metadata from public Mouza map file names."""
    clean = normalize_nfc(filename) or ""
    ext_match = re.search(r"\.([A-Za-z0-9]{2,5})$", clean)
    file_ext = ext_match.group(1).lower() if ext_match else None

    jl_match = re.search(r"(?:JL|J\.L\.|জে[\s.]*এল)[\s._:-]*([0-9০-৯]+)", clean, flags=re.IGNORECASE)
    if not jl_match:
        jl_match = re.search(r"(?:^|[\s_-])([0-9০-৯]{1,4})(?:[\s_-]|$)", clean)

    sheet_match = re.search(
        r"(?:^|[\s._-])(?:sheet|sh|পৃষ্ঠা|সিট|খন্ড|part)[\s._:-]*([0-9০-৯]+(?:[A-Za-z])?)(?:[\s._-]|$)",
        clean,
        flags=re.IGNORECASE,
    )
    survey_match = re.search(
        r"(?:^|[\s._@-])(CS|SA|RS|BS|BRS|BDS|DIARA)(?:[\s._@-]|$)",
        clean,
        flags=re.IGNORECASE,
    )

    return {
        "file_extension": file_ext,
        "parsed_jl_number": bn_digits_to_ascii(jl_match.group(1)) if jl_match else None,
        "parsed_sheet_number": normalize_nfc(sheet_match.group(1)) if sheet_match else None,
        "parsed_survey_hint": survey_match.group(1).upper() if survey_match else None,
    }
