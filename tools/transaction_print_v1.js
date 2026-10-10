(function(){
  'use strict';
  const KEY='naufal_farma_transactions';
  const $=id=>document.getElementById(id);
  const rupiah=n=>'Rp '+Number(n||0).toLocaleString('id-ID');
  const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const read=()=>{try{const x=JSON.parse(localStorage.getItem(KEY)||'[]');return Array.isArray(x)?x:[]}catch(_){return[]}};
  const items=t=>Array.isArray(t?.items)?t.items:[];
  const qty=i=>Number(i?.qty??i?.quantity??0)||0;
  const price=i=>Number(i?.price??i?.harga??i?.unitPrice??0)||0;
  const sub=i=>i?.subtotal!==undefined?Number(i.subtotal)||0:qty(i)*price(i);
  const total=t=>Number(t?.total??0)||items(t).reduce((a,i)=>a+sub(i),0);
  const date=t=>String(t?.date||new Date(t?.iso||Date.now()).toLocaleString('id-ID',{dateStyle:'short',timeStyle:'medium'}));
  function css(){
    if($('nfTxPrintStyle'))return;
    const s=document.createElement('style');s.id='nfTxPrintStyle';s.textContent=`
      .nf-tx-printbar{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:9px}
      .nf-tx-printbar button{min-height:42px!important;font-weight:800!important;border-radius:11px!important}
      .nf-tx-receipt{background:#eef2f1!important;color:#26342f!important}
      .nf-tx-pdf{background:#e8f7f2!important;color:#08765b!important}
      .nf-tx-history-actions{display:flex;gap:7px;flex-wrap:wrap;margin-top:8px}
      .nf-tx-history-actions button{min-height:34px!important;padding:7px 10px!important;font-size:11px!important;font-weight:800!important}
    `;document.head.appendChild(s);
  }
  function thermalText(t){
    const W=32,clean=s=>String(s??'').replace(/\s+/g,' ').trim(),line='--------------------------------';
    const right=(a,b)=>{a=clean(a);b=clean(b);const n=Math.max(1,W-a.length-b.length);return a+' '.repeat(n)+b};
    const out=['APOTEK NAUFAL FARMA','STRUK TRANSAKSI',line,clean(date(t)),''];
    items(t).forEach(i=>{const name=clean(i.name||i.nama||''),q=qty(i),p=price(i),v=sub(i);out.push(name.slice(0,W));out.push(right(q+' x '+rupiah(p).replace(/^Rp /,''),rupiah(v)))});
    out.push(line,right('TOTAL',rupiah(total(t))),right('ITEM',String(items(t).length)),line,'Terima kasih.');
    return out.join('\n');
  }
  function receipt(t){
    if(!window.Android?.printThermal){alert('Cetak struk thermal tersedia di APK Android.');return}
    let printers=[];try{printers=JSON.parse(Android.thermalPrinters?.()||'[]')}catch(_){ }
    if(!printers.length){Android.requestBluetoothPermission?.();alert('Belum ada printer thermal Bluetooth yang terhubung/pairing.');return}
    const r=Android.printThermal(printers[0].address,thermalText(t));
    try{const x=JSON.parse(r||'{}');if(!x.ok&&!x.permission)alert(x.error||'Cetak struk gagal.')}catch(_){ }
  }
  function pdfBody(t){
    const rows=items(t).map((i,n)=>'<tr><td class="c">'+(n+1)+'</td><td>'+esc(i.code??i.kode??'')+'</td><td>'+esc(i.name??i.nama??'')+'</td><td class="c">'+qty(i)+'</td><td class="r">'+rupiah(price(i))+'</td><td class="r">'+rupiah(sub(i))+'</td></tr>').join('');
    return `<style>*{box-sizing:border-box}html,body{margin:0;padding:0;background:#fff;color:#111;font-family:Arial,Helvetica,sans-serif}body{font-size:9pt;line-height:1.3}html,body{width:100%;min-width:0;max-width:100%;overflow:visible}.page{display:block;width:100%;max-width:186mm;min-width:0;margin:0 auto;overflow:visible}.head{display:flex;justify-content:space-between;align-items:flex-start;gap:5mm;border-bottom:1px solid #333;padding-bottom:5mm;margin-bottom:5mm;min-width:0}.head>div{min-width:0;max-width:100%}.brand{font-size:15pt;font-weight:800;overflow-wrap:anywhere}.sub{font-size:8pt;font-weight:400;margin-top:2mm}.meta{text-align:right;font-size:8pt;line-height:1.5;min-width:0;overflow-wrap:anywhere}h1{text-align:center;font-size:15pt;margin:4mm 0 1mm;overflow-wrap:anywhere}h2{text-align:center;font-size:9pt;font-weight:400;margin:0 0 5mm}table{width:100%;max-width:100%;border-collapse:collapse;table-layout:fixed;break-inside:auto}th,td{border:.5pt solid #777;padding:2.2mm 1.8mm;vertical-align:top;overflow-wrap:anywhere;word-break:break-word}th{background:#eee;text-align:center;font-weight:800}.c{text-align:center}.r{text-align:right;white-space:nowrap}th:nth-child(1){width:7%}th:nth-child(2){width:16%}th:nth-child(3){width:37%}th:nth-child(4){width:8%}th:nth-child(5){width:16%}th:nth-child(6){width:16%}.summary{width:80mm;max-width:100%;margin:6mm 0 0 auto;table-layout:fixed}.summary td,.summary th{font-weight:800;overflow-wrap:anywhere}.foot{display:flex;justify-content:space-between;gap:8mm;margin-top:12mm;font-size:7.5pt;color:#555;break-inside:avoid}.foot>div{max-width:50%;overflow-wrap:anywhere}thead{display:table-header-group}tr{break-inside:avoid;page-break-inside:avoid}td,th{min-width:0}@page{size:A4 portrait;margin:10mm}@media print{html,body{width:auto!important;height:auto!important;margin:0!important;padding:0!important;overflow:visible!important}.page{width:100%!important;max-width:190mm!important;margin:0 auto!important}table{width:100%!important;max-width:100%!important}*{-webkit-print-color-adjust:exact;print-color-adjust:exact}}</style><div class="page"><div class="head"><div><div class="brand">APOTEK NAUFAL FARMA</div><div class="sub">Laporan transaksi penjualan</div></div><div class="meta"><div>Tanggal: ${esc(date(t))}</div><div>Transaksi</div></div></div><h1>DETAIL TRANSAKSI</h1><h2>Nota tersimpan dari aplikasi Apotek Naufal Farma</h2><table><thead><tr><th>No</th><th>Kode</th><th>Nama Barang</th><th>Qty</th><th>Harga</th><th>Subtotal</th></tr></thead><tbody>${rows||'<tr><td colspan="6" class="c">Tidak ada item.</td></tr>'}</tbody></table><table class="summary"><tr><th>Total</th><td class="r">${rupiah(total(t))}</td></tr><tr><th>Bayar</th><td class="r">${rupiah(t?.paid??t?.payment??0)}</td></tr><tr><th>Kembalian</th><td class="r">${rupiah(t?.change??t?.kembalian??0)}</td></tr></table><div class="foot"><div>Dicetak dari aplikasi Apotek Naufal Farma</div><div>Admin<br><em>Sehat Bersama, Setiap Hari</em></div></div></div>`;
  }
  function pdf(t){
    const title='Transaksi_'+String(t?.id||Date.now()),body=pdfBody(t);
    if(window.Android?.savePdf){Android.savePdf(title,body);return}
    const w=window.open('','_blank');if(!w){alert('Browser memblokir jendela cetak.');return}
    w.document.write('<!doctype html><html><head><meta charset="utf-8"><title>'+esc(title)+'</title></head><body>'+body+'</body></html>');w.document.close();setTimeout(()=>w.print(),250);
  }
  function addBar(){
    const cart=document.querySelector('.cart');if(!cart||cart.dataset.nfTxPrint)return;
    cart.dataset.nfTxPrint='1';const bar=document.createElement('div');bar.className='nf-tx-printbar';
    bar.innerHTML='<button type="button" class="nf-tx-receipt">🧾 Cetak Struk</button><button type="button" class="nf-tx-pdf">📄 Cetak PDF A4</button>';cart.appendChild(bar);
    bar.querySelector('.nf-tx-receipt').onclick=()=>{const h=read();if(!h.length){alert('Simpan transaksi terlebih dahulu.');return}receipt(h[0])};
    bar.querySelector('.nf-tx-pdf').onclick=()=>{const h=read();if(!h.length){alert('Simpan transaksi terlebih dahulu.');return}pdf(h[0])};
  }
  function decorateHistory(){
    const hs=document.querySelector('.history');if(!hs)return;const h=read();
    [...hs.querySelectorAll('.history-item')].forEach((row,i)=>{if(row.querySelector('.nf-tx-history-actions'))return;const t=h[i];if(!t)return;const bar=document.createElement('div');bar.className='nf-tx-history-actions';bar.innerHTML='<button type="button" class="nf-tx-receipt">🧾 Struk</button><button type="button" class="nf-tx-pdf">📄 PDF A4</button>';row.appendChild(bar);bar.querySelector('.nf-tx-receipt').onclick=()=>receipt(t);bar.querySelector('.nf-tx-pdf').onclick=()=>pdf(t)});
  }
  function boot(){css();addBar();decorateHistory();setTimeout(addBar,300);setTimeout(decorateHistory,350);setTimeout(addBar,900);setTimeout(decorateHistory,1000)}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);else boot();
  new MutationObserver(()=>{addBar();decorateHistory()}).observe(document.body,{childList:true,subtree:true});
})();
