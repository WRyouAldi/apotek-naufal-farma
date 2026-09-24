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
    for i,x in enumerate(items): x['cost']=int(costs[i] or 0) if i<len(costs) else 0
    seed_path=Path('app/src/main/assets/products_seed.json')
    seed_json=json.dumps(items,ensure_ascii=False,separators=(',',':'))
    seed_path.write_text(seed_json,encoding='utf-8')
    seed_check=json.loads(seed_json)
    seed_costs=sum(1 for x in seed_check if int(x.get('cost',0) or 0)>0)
    if len(seed_check)!=len(items) or seed_costs<4000: raise SystemExit(f'Native seed validation failed: {len(seed_check)} products, {seed_costs} with Harga Beli')
    print(f'Native seed written: {len(items)} products; Harga Beli in seed: {seed_costs}')
    s=s[:m.start()]+'const ITEMS='+json.dumps(items,ensure_ascii=False,separators=(',',':'))+';'+s[m.end():]

nd='''\n    if (window.Android && typeof window.Android.saveReport === "function") {\n      window.Android.saveReport(filename, xls, "application/vnd.ms-excel");\n      return;\n    }\n'''
if 'window.Android.saveReport(filename, xls' not in s:
    marker='    const blob=new Blob([xls],{type:"application/vnd.ms-excel;charset=utf-8"});'
    if marker in s: s=s.replace(marker,nd+marker,1)

for script_id in ['barangKeluarV3','barangKeluarV2','daftarItemCrudV1','databaseManagerV1','reportUiV2','naufalPageArchitectureV4','naufalPageArchitectureV5']:
    s=re.sub(r'<script id="'+script_id+r'">.*?</script>\s*','',s,flags=re.S)
s=re.sub(r'<script id="naufalPageArchitectureV3">.*?</script>\s*','',s,flags=re.S)
s=re.sub(r'<script id="naufalLiquidLayoutFix">.*?</script>\s*','',s,flags=re.S)
modules=[
    ('barangKeluarV3',Path('tools/barang_keluar_v3.js')),
    ('daftarItemCrudV1',Path('tools/daftar_item_crud.js')),
    ('databaseManagerV1',Path('tools/database_manager_v1.js')),
    ('reportUiV2',Path('tools/report_ui_v2.js')),
    ('naufalPageArchitectureV5',Path('tools/page_architecture_v5.js')),
]
pos=s.lower().rfind('</body></html>')
if pos<0: raise SystemExit('index.html: document closing tag not found')
inject=''.join('<script id="'+sid+'">\n'+path.read_text(encoding='utf-8')+'\n</script>\n' for sid,path in modules)
layout_fix='''<script id="naufalLiquidLayoutFix">\n(function(){function wrapDirectText(root){Array.from(root.childNodes).forEach(function(node){if(node.nodeType===3&&node.textContent.trim()){var span=document.createElement('span');span.className='nf-auto-label';span.textContent=node.textContent.trim();root.replaceChild(span,node)}})}function fix(){document.querySelectorAll('.stat-card').forEach(function(card){var copy=card.querySelector(':scope > div:last-child');if(copy&&!copy.classList.contains('stat-icon')){wrapDirectText(copy);copy.classList.add('nf-stat-copy')}});document.querySelectorAll('.quick-btn').forEach(function(btn){wrapDirectText(btn);btn.querySelectorAll('.nf-auto-label').forEach(function(el){if(!el.classList.contains('nf-quick-label'))el.classList.add('nf-quick-label')})})}if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',fix);else fix();new MutationObserver(function(){fix()}).observe(document.body,{childList:true,subtree:true})})();\n</script>\n'''
s=s[:pos]+inject+layout_fix+s[pos:]
for style_id in ['sampleUIUXV15','referenceUIUX','naufalPharmacyThemeV2','naufalLiquidThemeV3']:
    s=re.sub(r'<style id="'+style_id+r'">.*?</style>\s*','',s,flags=re.S)
theme=Path('tools/ui_theme_v2.css').read_text(encoding='utf-8')
style_tag='<style id="naufalLiquidThemeV3">\n'+theme+'\n</style>\n'
head_pos=s.lower().find('</head>')
if head_pos<0: raise SystemExit('index.html: </head> not found')
s=s[:head_pos]+style_tag+s[head_pos:]
p.write_text(s,encoding='utf-8')
print('Prepared APK HTML with database manager, consolidated UI V3 and mobile layout normalization')
