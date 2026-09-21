#!/usr/bin/env python3
"""Minimal LAN API for Apotek Naufal Farma."""
import argparse, json, os, sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

DB_FILE=os.environ.get("NF_DB_FILE","naufal_server.db")
API_KEY=os.environ.get("NF_API_KEY","")
FIELDS=("code","barcode","name","jenis","brand","satuan","cost","purchase_price","price","stok","rak","supplier","keterangan","status","updated_at")

def init_db():
    with sqlite3.connect(DB_FILE) as db:
        db.execute("""CREATE TABLE IF NOT EXISTS products(
          id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, barcode TEXT, name TEXT NOT NULL,
          jenis TEXT, brand TEXT, satuan TEXT, cost INTEGER DEFAULT 0, purchase_price INTEGER DEFAULT 0,
          price INTEGER DEFAULT 0, stok REAL DEFAULT 0, rak TEXT, supplier TEXT, keterangan TEXT,
          status TEXT, updated_at INTEGER)""")
        db.execute("CREATE INDEX IF NOT EXISTS idx_code ON products(code)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_barcode ON products(barcode)")
        db.commit()

def clean(row):
    return {k: row.get(k) for k in FIELDS if k in row}

def key(row):
    return (str(row.get("code") or "").strip(), str(row.get("barcode") or "").strip(), str(row.get("name") or "").strip().lower())

def find_existing(db, code, barcode, name):
    if code:
        row=db.execute("SELECT id FROM products WHERE code=? LIMIT 1",(code,)).fetchone()
        if row: return row
    if barcode:
        row=db.execute("SELECT id FROM products WHERE barcode=? LIMIT 1",(barcode,)).fetchone()
        if row: return row
    if name:
        row=db.execute("SELECT id FROM products WHERE LOWER(name)=? LIMIT 1",(name,)).fetchone()
        if row: return row
    return None

class Handler(BaseHTTPRequestHandler):
    def _auth(self):
        return not API_KEY or self.headers.get("X-API-Key","")==API_KEY
    def _send(self, code, data):
        raw=json.dumps(data,ensure_ascii=False).encode()
        self.send_response(code); self.send_header("Content-Type","application/json; charset=utf-8")
        self.send_header("Content-Length",str(len(raw))); self.end_headers(); self.wfile.write(raw)
    def _body(self):
        n=int(self.headers.get("Content-Length","0"))
        return json.loads(self.rfile.read(n) or b"{}")
    def do_GET(self):
        if not self._auth(): return self._send(401,{"ok":False,"error":"Unauthorized"})
        path=urlparse(self.path)
        if path.path=="/health":
            with sqlite3.connect(DB_FILE) as db:
                count=db.execute("SELECT COUNT(*) FROM products").fetchone()[0]
            return self._send(200,{"ok":True,"service":"naufal-farma-db","count":count})
        if path.path=="/api/products":
            limit=min(int(parse_qs(path.query).get("limit",["50000"])[0]),50000)
            with sqlite3.connect(DB_FILE) as db:
                db.row_factory=sqlite3.Row
                rows=[dict(x) for x in db.execute("SELECT * FROM products ORDER BY id LIMIT ?",(limit,))]
            return self._send(200,{"ok":True,"products":rows,"count":len(rows)})
        self._send(404,{"ok":False,"error":"Not found"})
    def do_POST(self):
        if not self._auth(): return self._send(401,{"ok":False,"error":"Unauthorized"})
        path=urlparse(self.path)
        if path.path!="/api/products/sync": return self._send(404,{"ok":False,"error":"Not found"})
        payload=self._body(); rows=payload.get("products",[])
        if not isinstance(rows,list): return self._send(400,{"ok":False,"error":"products harus array"})
        added=updated=0
        with sqlite3.connect(DB_FILE) as db:
            for raw in rows:
                r=clean(raw); code,barcode,name=key(r)
                existing=find_existing(db,code,barcode,name)
                if existing:
                    sets=[]; args=[]
                    for f in FIELDS:
                        if f in r:
                            sets.append(f+"=?"); args.append(r[f])
                    if sets:
                        args.append(existing[0])
                        db.execute("UPDATE products SET "+",".join(sets)+" WHERE id=?",args)
                    updated+=1
                else:
                    if not name: continue
                    cols=[f for f in FIELDS if f in r]
                    vals=[r[f] for f in cols]
                    db.execute("INSERT INTO products("+",".join(cols)+") VALUES("+",".join("?" for _ in cols)+")",vals)
                    added+=1
            db.commit()
        self._send(200,{"ok":True,"count":len(rows),"added":added,"updated":updated})
    def log_message(self, *args):
        pass

if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("--host",default="0.0.0.0")
    ap.add_argument("--port",type=int,default=8080)
    args=ap.parse_args()
    init_db()
    print("Naufal Farma DB Server: http://%s:%s"%(args.host,args.port))
    print("API key:", "enabled" if API_KEY else "disabled")
    ThreadingHTTPServer((args.host,args.port),Handler).serve_forever()
