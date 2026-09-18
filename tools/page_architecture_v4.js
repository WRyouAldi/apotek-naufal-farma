(function(){
  function ensureShell(){
    const wrap=document.querySelector('.wrap');
    if(!wrap || document.getElementById('nfHome')) return false;
    const welcome=wrap.querySelector('.welcome-card');
    const stats=wrap.querySelector('.stats-grid');
    const quick=wrap.querySelector('.quick-section');
    if(!welcome||!stats||!quick) return false;

    const home=document.createElement('section'); home.id='nfHome'; home.className='nf-page';
    const price=document.createElement('section'); price.id='nfPrice'; price.className='nf-page'; price.style.display='none';
    const tx=document.createElement('section'); tx.id='nfTransaction'; tx.className='nf-page'; tx.style.display='none';
    const out=document.createElement('section'); out.id='nfOut'; out.className='nf-page'; out.style.display='none';
    const report=document.createElement('section'); report.id='nfReport'; report.className='nf-page'; report.style.display='none';
    const items=document.createElement('section'); items.id='nfItems'; items.className='nf-page'; items.style.display='none';

    const head=(title,desc)=>{
      const d=document.createElement('div'); d.className='nf-page-head';
      d.innerHTML='<button type="button" class="nf-back" onclick="nfGo(\'home\')">‹</button><div><h2>'+title+'</h2><p>'+desc+'</p></div>';
      return d;
    };
    home.append(welcome,stats,quick);
    const more=document.createElement('div'); more.className='nf-more-link';
    more.innerHTML='<span>Kelola database produk</span><button type="button" onclick="nfGo(\'items\')">Daftar Item →</button>';
    home.append(more);
    price.append(head('Cek Harga','Cari produk, kode, barcode, dan harga jual.'), document.getElementById('searchArea'), document.querySelector('#results')||document.createElement('div'));
    tx.append(head('Transaksi','Keranjang penjualan dan pembayaran.'), document.getElementById('cartAnchor')||document.createElement('div'), document.querySelector('.cart')||document.createElement('div'));
    report.append(head('Laporan','Ringkasan penjualan, detail transaksi, dan ekspor.'), document.getElementById('laporanSection')||document.createElement('div'));
    items.append(head('Daftar Item','Kelola data produk secara offline.'), document.createElement('div')).lastChild.className='nf-crud-host';

    wrap.innerHTML='';
    wrap.append(home,price,tx,out,report,items);

    function nav(){
      let n=document.getElementById('nfBottomNav');
      if(n)return n;
      n=document.createElement('nav'); n.id='nfBottomNav'; n.className='nf-bottom-nav';
      n.innerHTML='<button data-page="home" onclick="nfGo(\'home\')">⌂<span>Beranda</span></button><button data-page="price" onclick="nfGo(\'price\')">▣<span>Cek Harga</span></button><button data-page="tx" onclick="nfGo(\'tx\')">🛒<span>Transaksi</span></button><button data-page="out" onclick="nfGo(\'out\')">↗<span>Keluar</span></button><button data-page="report" onclick="nfGo(\'report\')">▤<span>Laporan</span></button>';
      document.body.appendChild(n); return n;
    }
    nav();
    window.nfGo=function(page){
      const map={home:'nfHome',price:'nfPrice',tx:'nfTransaction',out:'nfOut',report:'nfReport',items:'nfItems'};
      Object.keys(map).forEach(k=>{const el=document.getElementById(map[k]);if(el)el.style.display=k===page?'block':'none';});
      document.querySelectorAll('#nfBottomNav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
      window.scrollTo({top:0,behavior:'smooth'});
      if(page==='items' && typeof window.nfCrudRefresh==='function') window.nfCrudRefresh();
      if(page==='report' && typeof window.nfReportRefresh==='function') window.nfReportRefresh();
    };
    window.focusSearch=function(scan){
      nfGo('price'); setTimeout(()=>{const q=document.getElementById('q'); if(scan && typeof Android!=='undefined' && Android.scanBarcode) Android.scanBarcode(); else if(q)q.focus();},80);
    };
    window.focusCart=function(){nfGo('tx');};
    window.focusReport=function(){nfGo('report');};
    return true;
  }

  function moveDynamic(){
    const out=document.getElementById('nfOut');
    const legacy=document.getElementById('barangKeluarSection');
    if(out && legacy && legacy.parentNode!==out) out.appendChild(legacy);
    const itemsHost=document.querySelector('#nfItems .nf-crud-host');
    if(itemsHost && typeof window.nfCrudRefresh==='function' && !itemsHost.dataset.ready){
      itemsHost.dataset.ready='1';
      window.nfCrudRefresh(itemsHost);
    }
  }
  function boot(){ if(ensureShell()){moveDynamic(); setTimeout(moveDynamic,150); setTimeout(moveDynamic,500); } }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',boot);else boot();
  new MutationObserver(moveDynamic).observe(document.documentElement,{childList:true,subtree:true});
})();