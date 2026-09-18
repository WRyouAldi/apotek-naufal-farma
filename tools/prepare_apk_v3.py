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
    # Emit a standalone native seed file so Android can initialize SQLite
    # deterministically without depending on WebView/JS bridge timing.
    for i,x in enumerate(items):
        x['cost']=int(costs[i] or 0) if i<len(costs) else 0
    seed_path=Path('app/src/main/assets/products_seed.json')
    seed_path.write_text(json.dumps(items,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
    print(f'Native seed written: {len(items)} products')
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

# Remove generated feature modules before injecting current versions.
for script_id in ['barangKeluarV3','barangKeluarV2','daftarItemCrudV1','reportUiV2','naufalPageArchitectureV4','naufalPageArchitectureV5']:
    s=re.sub(r'<script id="'+script_id+r'">.*?</script>\s*', '', s, flags=re.S)

modules=[
    ('barangKeluarV3',Path('tools/barang_keluar_v3.js')),
    ('daftarItemCrudV1',Path('tools/daftar_item_crud.js')),
    ('reportUiV2',Path('tools/report_ui_v2.js')),
    ('naufalPageArchitectureV5',Path('tools/page_architecture_v5.js')),
]
# Remove the legacy page-shell injection that was accidentally embedded in the
# Excel export HTML string by an older build.
s=re.sub(r'<script id="naufalPageArchitectureV3">.*?</script>\s*', '', s, flags=re.S)
s=re.sub(r'<script id="naufalLiquidLayoutFix">.*?</script>\s*', '', s, flags=re.S)

# Use the last complete document closing sequence. The report exporter also
# contains a literal </body></html> earlier in a JavaScript string.
pos=s.lower().rfind('</body></html>')
if pos<0:
    raise SystemExit('index.html: document closing tag not found')
inject=''.join('<script id="'+sid+'">\n'+path.read_text(encoding='utf-8')+'\n</script>\n' for sid,path in modules)

# Runtime DOM normalization prevents legacy inline text nodes from collapsing
# labels in the compact mobile cards. It only wraps direct text nodes and does
# not change feature logic, data, navigation, or transaction behavior.
layout_fix='''
<script id="naufalLiquidLayoutFix">
(function(){
  function wrapDirectText(root){
    Array.from(root.childNodes).forEach(function(node){
      if(node.nodeType===3 && node.textContent.trim()){
        var span=document.createElement('span');
        span.className='nf-auto-label';
        span.textContent=node.textContent.trim();
        root.replaceChild(span,node);
      }
    });
  }
  function fix(){
    document.querySelectorAll('.stat-card').forEach(function(card){
      var copy=card.querySelector(':scope > div:last-child');
      if(copy && !copy.classList.contains('stat-icon')){
        wrapDirectText(copy);
        copy.classList.add('nf-stat-copy');
      }
    });
    document.querySelectorAll('.quick-btn').forEach(function(btn){
      wrapDirectText(btn);
      btn.querySelectorAll('.nf-auto-label').forEach(function(el){
        if(!el.classList.contains('nf-quick-label')) el.classList.add('nf-quick-label');
      });
    });
  }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',fix);
  else fix();
  new MutationObserver(function(){fix()}).observe(document.body,{childList:true,subtree:true});
})();
</script>
'''

dom_end=inject+layout_fix
s=s[:pos]+dom_end+s[pos:]

# Keep exactly one application theme. Remove superseded UI theme layers before injecting V3.
for style_id in ['sampleUIUXV15','referenceUIUX','naufalPharmacyThemeV2','naufalLiquidThemeV3']:
    s=re.sub(r'<style id="'+style_id+r'">.*?</style>\s*', '', s, flags=re.S)

theme=Path('tools/ui_theme_v2.css').read_text(encoding='utf-8')
style_tag='<style id="naufalLiquidThemeV3">\n'+theme+'\n</style>\n'
head_pos=s.lower().find('</head>')
if head_pos<0:
    raise SystemExit('index.html: </head> not found')
s=s[:head_pos]+style_tag+s[head_pos:]

p.write_text(s,encoding='utf-8')
print('Prepared APK HTML with consolidated Liquid Glass UI V3 and mobile layout normalization')
