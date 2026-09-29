# Build 186 regression gate

Target regression: GitHub CSV sync and Indonesian number parsing.

Acceptance criteria:
- `20.000` parses as 20000.
- `250.000` parses as 250000.
- `1.250.000` parses as 1250000.
- Reset then Cloud Sync preserves price values.
- Empty/invalid cloud CSV must not overwrite local database.
- Cloud sync must report added, updated, and total counts.

Build 186 is a regression-build marker; device verification remains required before declaring the APK fully PASS.
