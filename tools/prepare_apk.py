from pathlib import Path
import base64, json, re, zlib

p = Path("app/src/main/assets/index.html")
s = p.read_text(encoding="utf-8")

# Prevent the native barcode bridge from being accidentally embedded in the Excel export HTML.
xls_start = s.find("const xls=")
bridge = s.find("<!-- Native Android barcode bridge.", xls_start)
if xls_start >= 0 and bridge >= 0:
    end = s.find("</body></html>';", bridge)
    if end >= 0:
        s = s[:bridge] + "</table></body></html>';" + s[end + len("</body></html>;"):]

# Attach Harga Beli from the xReport2 database to every product.
cost_file = Path("tools/harga_beli.b64")
if cost_file.exists():
    try:
        costs = json.loads(zlib.decompress(base64.b64decode(cost_file.read_text(encoding="ascii"))).decode("utf-8"))
        m = re.search(r"const ITEMS=(\[.*?\]);", s, re.S)
        if m:
            items = json.loads(m.group(1))
            for i, item in enumerate(items):
                item["cost"] = int(costs[i] or 0) if i < len(costs) else 0
            s = s[:m.start()] + "const ITEMS=" + json.dumps(items, ensure_ascii=False, separators=(",", ":")) + ";" + s[m.end():]
    except Exception as e:
        print("Harga Beli injection skipped:", e)

# Inject Barang Keluar into the offline APK.
module = Path("tools/barang_keluar.js")
if module.exists() and "id=\"barangKeluarV1\"" not in s:
    code = module.read_text(encoding="utf-8")
    s = s.replace("</body>", "<script id=\"barangKeluarV1\">\n" + code + "\n</script>\n</body>", 1)

p.write_text(s, encoding="utf-8")
print("Prepared APK HTML with Harga Beli + Barang Keluar")
