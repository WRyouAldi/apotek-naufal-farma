# GitHub Cloud Database

`xReport2.csv` is the cloud source used by the Android app's **Sync dari GitHub** button.

## Update database
1. Export the latest CSV from iPos.
2. Replace `cloud/xReport2.csv` with the exported file.
3. Keep the file name exactly `xReport2.csv`.
4. Open the app and tap **Daftar Item → Sync dari GitHub**.
5. The app validates the CSV, then uses the existing native `add_update` importer.

## Important
- GitHub is used only as the cloud CSV source, not as a realtime transaction database.
- SQLite remains the local/offline database.
- The sync never deletes items that are absent from the CSV; it adds/updates according to the existing importer.
- Do not put passwords, API keys, or private personal data in this CSV because the repository is currently public.
