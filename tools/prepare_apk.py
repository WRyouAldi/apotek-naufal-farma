from pathlib import Path
import base64, json, re, zlib

p = Path("app/src/main/assets/index.html")
s = p.read_text(encoding="utf-8")

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

# Make the existing Excel report work inside the Android WebView.
# The normal browser download remains available as a fallback.
native_download = '''\n    if (window.Android && typeof window.Android.saveReport === "function") {\n      window.Android.saveReport(filename, xls, "application/vnd.ms-excel");\n      return;\n    }\n'''
if "window.Android.saveReport(filename, xls" not in s:
    marker = '    const blob=new Blob([xls],{type:"application/vnd.ms-excel;charset=utf-8"});'
    if marker in s:
        s = s.replace(marker, native_download + marker, 1)

# Inject Barang Keluar into the APK AFTER the real document body.
# Do not use s.replace("</body>", ...) because the Excel export HTML
# itself contains </body> and would corrupt the JavaScript string.
module = Path("tools/barang_keluar.js")
if module.exists() and 'id="barangKeluarV1"' not in s:
    code = module.read_text(encoding="utf-8")
    pos = s.lower().rfind("</body>")
    if pos >= 0:
        s = s[:pos] + '<script id="barangKeluarV1">\n' + code + '\n</script>\n' + s[pos:]
    else:
        s += '\n<script id="barangKeluarV1">\n' + code + '\n</script>\n'

p.write_text(s, encoding="utf-8")
print("Prepared APK HTML with Harga Beli + Barang Keluar + native report download")
