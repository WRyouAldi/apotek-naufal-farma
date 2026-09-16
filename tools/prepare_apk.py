from pathlib import Path

p = Path('app/src/main/assets/index.html')
s = p.read_text(encoding='utf-8')

# The barcode bridge was accidentally embedded inside the Excel-export HTML string.
# Remove that embedded copy so the WebView does not render JavaScript as visible text.
xls_start = s.find('const xls=')
bridge = s.find('<!-- Native Android barcode bridge.', xls_start)
if xls_start >= 0 and bridge >= 0:
    end = s.find("</body></html>';", bridge)
    if end >= 0:
        s = s[:bridge] + "</table></body></html>';" + s[end + len("</body></html>;"):]

# Apply the visual layout used by the approved sample: branded green header,
# logo, two-column menu tiles, rounded search card and mobile-first spacing.
if 'id="sampleUIUXV15"' not in s:
    css = '''\n<style id="sampleUIUXV15">\n:root{--green:#0b8064;--green2:#0a6f58;--mint:#e8f7f2;--ink:#17312b;--muted:#71807b;--line:#e2ebe7}\nbody{background:#f5f7f6;color:var(--ink)}\nheader{background:linear-gradient(145deg,#087c61,#0c9272);padding:18px 16px 20px;border-radius:0 0 22px 22px;box-shadow:0 5px 18px #063d3022}\n.header-inner{max-width:760px;margin:auto;display:flex;align-items:center;gap:12px}\n.brand-logo{width:54px;height:54px;flex:none;border-radius:16px;background:rgba(255,255,255,.15);padding:5px}\n.brand-title{font-size:20px;font-weight:800;line-height:1.08}.brand-sub{font-size:11px;margin-top:5px;opacity:.9;letter-spacing:.2px}\n.wrap{padding:14px;max-width:760px}.dashboard-title{font-size:15px;font-weight:800;margin:4px 2px 10px}\n.dashboard{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-bottom:16px}\n.dash-tile{background:#fff;border:1px solid var(--line);border-radius:17px;padding:16px 10px;min-height:112px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;box-shadow:0 3px 12px #12352b0b;color:var(--ink);font-weight:700}\n.dash-tile .ico{width:46px;height:46px;border-radius:14px;background:var(--mint);color:var(--green);display:flex;align-items:center;justify-content:center;font-size:25px;margin-bottom:8px}\n.dash-tile small{font-weight:500;color:var(--muted);margin-top:3px;font-size:10px}\n.search-card{background:#fff;border-radius:18px;padding:12px;border:1px solid var(--line);box-shadow:0 3px 12px #12352b0b}\n.search{margin:0;align-items:center}.search input{border-radius:13px;background:#f7faf9;border-color:#dfe9e5}\n#scanBarcode{background:var(--green);min-width:58px;height:48px;border-radius:13px;padding:0 13px}\n.card{border:1px solid var(--line);box-shadow:0 3px 12px #12352b0b}\n#laporanSection{border:1px solid var(--line)!important;background:#fff!important;box-shadow:0 3px 12px #12352b0b!important}\n#laporanSection .report-controls button{background:var(--green)}\n@media(max-width:430px){.dashboard{gap:8px}.dash-tile{min-height:104px;padding:13px 8px}.brand-logo{width:50px;height:50px}}\n</style>\n'''
    s = s.replace('</head>', css + '</head>', 1)

old_header = '<header><h1>Apotek Naufal Farma</h1><div class="sub">Price Checker • Offline • Sumber: xReport.pdf</div></header>'
if old_header in s:
    logo = '<svg class="brand-logo" viewBox="0 0 100 100" aria-label="Logo Apotek Naufal Farma" xmlns="http://www.w3.org/2000/svg"><rect width="100" height="100" rx="22" fill="#ffffff"/><path fill="#0b8064" d="M39 14h22v25h25v22H61v25H39V61H14V39h25z"/><path fill="#0b8064" d="M73 45c-18 2-31 11-37 29 10-9 20-13 30-14-5 9-12 16-22 22 18-3 31-16 35-37 1-1 1-1-6 0z"/></svg>'
    header = f'<header><div class="header-inner">{logo}<div><div class="brand-title">Apotek<br>Naufal Farma</div><div class="brand-sub">CEPAT • MUDAH • LENGKAP</div></div></div></header>'
    s = s.replace(old_header, header, 1)

old_prefix = '''<div class="wrap">\n<div class="search"><button type="button" id="scanBarcode" aria-label="Scan barcode">📷 Scan</button><input id="q" placeholder="Cari nama barang / kode item..." autocomplete="off"></div>\n<div class="small">Data: 4,348 item. Harga diambil dari kolom <b>Harga Jual</b> pada xReport.</div>\n<div id="results"></div>\n'''
if old_prefix in s and 'class="dashboard"' not in s:
    dashboard = '''<div class="wrap">\n<div class="dashboard-title">Menu Utama</div>\n<div class="dashboard">\n<button type="button" class="dash-tile" onclick="focusSearch()"><span class="ico">⌕</span>Cek Harga<br>& Cari Produk<small>Nama, kode, barcode</small></button>\n<button type="button" class="dash-tile" onclick="focusSearch(true)"><span class="ico">▥</span>Scan Barcode<small>Scan produk dengan kamera</small></button>\n<button type="button" class="dash-tile" onclick="focusCart()"><span class="ico">🛒</span>Transaksi<br>Penjualan<small>Keranjang & pembayaran</small></button>\n<button type="button" class="dash-tile" onclick="focusReport()"><span class="ico">▤</span>Laporan<br>& Riwayat<small>Penjualan harian</small></button>\n<button type="button" class="dash-tile" onclick="focusSearch()"><span class="ico">▣</span>Database<small>4.348 item offline</small></button>\n<button type="button" class="dash-tile" onclick="focusSettings()"><span class="ico">⚙</span>Pengaturan<small>Backup & restore</small></button>\n</div>\n<div class="search-card" id="searchArea">\n<div class="search"><button type="button" id="scanBarcode" aria-label="Scan barcode">▥</button><input id="q" placeholder="Cari nama barang / kode item / barcode..." autocomplete="off"></div>\n<div id="barcodeStatus" class="small" style="margin-top:7px"></div>\n</div>\n<div class="small" style="margin:8px 2px 0">Data: 4.348 item • Harga dari kolom <b>Harga Jual</b> pada database baru.</div>\n<div id="results"></div>\n<div id="cartAnchor"></div>'''
    s = s.replace(old_prefix, dashboard + '\n', 1)

if 'id="sampleUIUXHelpers"' not in s:
    helpers = '''<script id="sampleUIUXHelpers">\nfunction focusSearch(scan){const el=document.getElementById("q");if(el){el.scrollIntoView({behavior:"smooth",block:"center"});setTimeout(()=>el.focus(),350);}if(scan){setTimeout(()=>{const b=document.getElementById("scanBarcode");if(b)b.click();},450);}}\nfunction focusCart(){const el=document.querySelector(".cart");if(el)el.scrollIntoView({behavior:"smooth",block:"start");}\nfunction focusReport(){const el=document.getElementById("laporanSection");if(el)el.scrollIntoView({behavior:"smooth",block:"start");}\nfunction focusSettings(){const el=document.querySelector("#backupBtn");if(el){el.scrollIntoView({behavior:"smooth",block:"center"});el.focus();}}\n</script>\n'''
    marker = '<!-- Native Android barcode bridge.'
    s = s.replace(marker, helpers + marker, 1)

p.write_text(s, encoding='utf-8')
print('Prepared', p)
