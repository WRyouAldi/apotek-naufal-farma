from pathlib import Path
import base64, json, re, zlib

p=Path('app/src/main/assets/index.html')
s=p.read_text(encoding='utf-8')
parts=sorted(Path('tools/harga_beli_parts').glob('[0-9][0-9]'))
encoded=''.join(x.read_text(encoding='ascii').strip() for x in parts)
costs=json.loads(zlib.decompress(base64.b64decode(encoded,validate=True)).decode('utf-8'))
m=re.search(r'const ITEMS=(\[.*?\]);',s,re.S)
if m:
    items=json.loads(m.group(1))
    for i,x in enumerate(items):
        x['cost']=int(costs[i] or 0) if i<len(costs) else 0
    s=s[:m.start()]+'const ITEMS='+json.dumps(items,ensure_ascii=False,separators=(',',':'))+';'+s[m.end():]
    print(f'Harga Beli injected: {sum(1 for x in items if x.get("cost",0))}/{len(items)}')

nd='''
    if (window.Android && typeof window.Android.saveReport === "function") {
      window.Android.saveReport(filename, xls, "application/vnd.ms-excel");
      return;
    }
'''
if 'window.Android.saveReport(filename, xls' not in s:
    marker='    const blob=new Blob([xls],{type:"application/vnd.ms-excel;charset=utf-8"});'
    if marker in s:
        s=s.replace(marker,nd+marker,1)

# Remove previous generated modules before injecting current versions.
for script_id in ['barangKeluarV3','barangKeluarV2','daftarItemCrudV1']:
    s=re.sub(r'<script id="'+script_id+r'">.*?</script>\s*', '', s, flags=re.S)

modules=[
    ('barangKeluarV3',Path('tools/barang_keluar_v3.js')),
    ('daftarItemCrudV1',Path('tools/daftar_item_crud.js')),
]
pos=s.lower().rfind('</body>')
if pos<0:
    raise SystemExit('index.html: </body> not found')
inject=''.join('<script id="'+sid+'">\n'+path.read_text(encoding='utf-8')+'\n</script>\n' for sid,path in modules)
s=s[:pos]+inject+s[pos:]

# Apply the pharmacy-focused mobile theme at build time so the source UI/features remain intact.
theme=Path('tools/ui_theme_v2.css').read_text(encoding='utf-8')
style_tag='<style id="naufalPharmacyThemeV2">\n'+theme+'\n</style>\n'
s=re.sub(r'<style id="naufalPharmacyThemeV2">.*?</style>\s*', '', s, flags=re.S)
head_pos=s.lower().find('</head>')
if head_pos<0:
    raise SystemExit('index.html: </head> not found')
s=s[:head_pos]+style_tag+s[head_pos:]

p.write_text(s,encoding='utf-8')
print('Prepared APK HTML with Harga Beli + Barang Keluar V3 + Daftar Item CRUD + Pharmacy UI Theme V2')
