# Search, Normalization, Sorting & Area Calculation

## 1. Bilingual Search Normalization (`SearchNormalizer.kt`)

- Applies Unicode `Normalizer.Form.NFC` so decomposed Bengali characters match consistently.
- Converts Bengali numerals (`০-৯`) to ASCII (`0-9`) across deed numbers, years, J.L. numbers, khatian, dag, and parcel areas.
- Collapses whitespace around slashes (`১২৫৬ / ২০২৪` -> `1256/2024`), trims edge punctuation, and lowercases Latin text without altering distinct Bengali letters (`ব/ভ`, `স/শ/ষ`, `জ/য`).
- Generates bounded token sets (`<= 140` tokens/record) for Room SQL indexed search and Firestore `array-contains` indexing.

## 2. Ranked Matching (`DefaultIndexedSearchStrategy`)

Results are ranked deterministically by score -> registration date desc -> UUID asc, with explicit `MatchReason` badges:

1. `EXACT_DEED_NUMBER` (score 1000)
2. `EXACT_DAG_OR_KHATIAN` (score 900)
3. `EXACT_PERSON_NAME` (score 800)
4. `MOUZA_MATCH` (score 700)
5. `PREFIX_MATCH` (score 600)
6. `SUBSTRING_MATCH` (score 400 — supports multi-word queries)

## 3. Deterministic Sorting & Bounded Pagination (`DeedSorter.kt`)

10 supported `SortOption`s, every one terminating with `.thenBy { it.id }` tie-breaker:

- `REGISTRATION_DATE_DESC`, `REGISTRATION_DATE_ASC`
- `DEED_YEAR_DESC`, `DEED_YEAR_ASC`
- `DEED_NUMBER_ASC`
- `GRANTOR_NAME_ASC`, `GRANTEE_NAME_ASC`, `MOUZA_NAME_ASC`
- `LAND_AREA_DESC` (by computed `BigDecimal` Shotok)
- `UPDATED_AT_DESC`

Pagination bounds page size to `1..50` (default `20` items per page).

## 4. Bangladesh Land Area Conversions (`LandParcel.areaAsShotok()`)

Computed using `java.math.BigDecimal` (`HALF_UP`):

- `1 Decimal / Shotok = 1.0 Shotok`
- `1 Katha = 1.65 Shotok`
- `1 Bigha = 33.0 Shotok`
- `1 Acre = 100.0 Shotok`
- `1 Sq. Ft = 1 / 435.6 Shotok`
