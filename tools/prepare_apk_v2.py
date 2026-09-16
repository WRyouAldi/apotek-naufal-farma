from pathlib import Path
import base64, json, re, zlib
p=Path('app/src/main/assets/index.html');s=p.read_text(encoding='utf-8')
parts=sorted(Path('tools/harga_beli_parts').glob('[0-9][0-9]'))
encoded=''.join(x.read_text(encoding='ascii').strip() for x in parts)
costs=json.loads(zlib.decompress(base64.b64decode(encoded,validate=True)).decode('utf-8'))
m=re.search(r'const ITEMS=(\[.*?\]);',s,re.S)
if m:
 items=json.loads(m.group(1))
 for i,x in enumerate(items):x['cost']=int(costs[i] or 0) if i<len(costs) else 0
 s=s[:m.start()]+'const ITEMS='+json.dumps(items,ensure_ascii=False,separators=(',',':'))+';'+s[m.end():]
 print(f'Harga Beli injected: {sum(1 for x in items if x.get("cost",0))}/{len(items)}')
nd='''\n    if (window.Android && typeof window.Android.saveReport === "function") {\n      window.Android.saveReport(filename, xls, "application/vnd.ms-excel");\n      return;\n    }\n'''
if 'window.Android.saveReport(filename, xls' not in s:
 marker='    const blob=new Blob([xls],{type:"application/vnd.ms-excel;charset=utf-8"});'
 if marker in s:s=s.replace(marker,nd+marker,1)
mod=Path('tools/barang_keluar_v2.js');code=mod.read_text(encoding='utf-8')
if 'id="barangKeluarV2"' not in s:
 pos=s.lower().rfind('</body>');inject='<script id="barangKeluarV2">\n'+code+'\n</script>\n'
 s=s[:pos]+inject+s[pos:] if pos>=0 else s+'\n'+inject
p.write_text(s,encoding='utf-8')
print('Prepared APK HTML with Harga Beli + Barang Keluar V2')
