#!/usr/bin/env python3
import csv, json, os, subprocess, sys
from pathlib import Path

DUMP = Path(os.environ.get('DUMP_FILE', '170926.i4bu'))
OUT = Path(os.environ.get('OUT_DIR', 'converted'))
DB = os.environ.get('PGDATABASE', 'naufal_restore')
USER = os.environ.get('PGUSER', 'postgres')
HOST = os.environ.get('PGHOST', 'localhost')
PORT = os.environ.get('PGPORT', '5432')

if not DUMP.exists():
    raise SystemExit(f'Dump tidak ditemukan: {DUMP}')
OUT.mkdir(parents=True, exist_ok=True)

subprocess.run(['pg_restore', '--clean', '--if-exists', '--no-owner', '--no-privileges', '-d', DB, str(DUMP)], check=True)

# Use psql for portable CSV export; inspect table/column names first.
def q(sql):
    p = subprocess.run(['psql','-X','-A','-t','-F','\t','-d',DB,'-c',sql], check=True, capture_output=True, text=True)
    return p.stdout.splitlines()

tables = q("SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name;")
(OUT/'tables.txt').write_text('\n'.join(tables)+'\n', encoding='utf-8')

# Export the principal item tables if present, preserving all columns exactly as stored.
for table in ['tbl_item', 'tbl_itemsatuanjml', 'tbl_itemhj', 'tbl_itemstok']:
    if table not in tables:
        continue
    path = OUT / f'{table}.csv'
    subprocess.run([
        'psql','-X','-d',DB,'-c',
        f"\\copy (SELECT * FROM public.\"{table}\") TO STDOUT WITH (FORMAT csv, HEADER true, DELIMITER ';', ENCODING 'UTF8')"
    ], check=True, stdout=path.open('w', encoding='utf-8'), text=True)

# Build a normalized app import file from whatever columns actually exist in tbl_item.
if 'tbl_item' in tables:
    cols = q("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='tbl_item' ORDER BY ordinal_position;")
    wanted = ['kodeitem','namaitem','jenis','merek','rak','satuan','hargapokok','prhargajual1','hargajual1','stok','supplier1','keterangan','statusjual','statushapus']
    available = [c for c in wanted if c in cols]
    # Add an explicit source marker so the app knows this came from the PostgreSQL master.
    select = ', '.join('"'+c+'"' for c in available)
    if select:
        subprocess.run([
            'psql','-X','-d',DB,'-c',
            f"\\copy (SELECT {select} FROM public.\"tbl_item\") TO STDOUT WITH (FORMAT csv, HEADER true, DELIMITER ';', ENCODING 'UTF8')"
        ], check=True, stdout=(OUT/'items_normalized.csv').open('w', encoding='utf-8'), text=True)

# Small machine-readable manifest for the APK/app importer.
manifest = {
    'source': DUMP.name,
    'format': 'PostgreSQL custom dump',
    'tables_found': [t for t in tables if t],
    'exports': [p.name for p in OUT.iterdir() if p.is_file()]
}
(OUT/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(manifest, ensure_ascii=False, indent=2))
