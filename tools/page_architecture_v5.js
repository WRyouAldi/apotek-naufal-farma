(function(){
'use strict';
(function(){
  const wrap=document.querySelector('.wrap');
  if(!wrap || document.getElementById('nfHome')) return;

  const welcome=wrap.querySelector('.welcome-card');
  const stats=wrap.querySelector('.stats-grid');
  const quick=wrap.querySelector('.quick-section');
  const legacyStatus=Array.from(wrap.querySelectorAll('.small')).find(e=>/produk offline/i.test(e.textContent||''));
  if(!welcome||!stats||!quick) return;

  const nodes={
    search:document.getElementById('searchArea'),
    results:document.getElementById('results'),
    cartAnchor:document.getElementById('cartAnchor'),
    cart:document.querySelector('.cart'),
    report:document.getElementById('laporanSection'),
    bottom:document.getElementById('bottomNavV15')
  };

  const make=(id,title,desc)=>{
    const p=document.createElement('section');
    p.id=id; p.className='nf-page';
    const head=document.createElement('div'); head.className='nf-page-head';
    head.innerHTML='<button type="button" class="nf-back" data-back>‹</button><div><h2>'+title+'</h2><p>'+desc+'</p></div>';
    head.querySelector('[data-back]').onclick=()=>window.nfGo('home');
    p.appendChild(head);
    wrap.appendChild(p);
    return p;
  };

  const home=document.createElement('section'); home.id='nfHome'; home.className='nf-page';
  home.append(welcome,stats,quick);
  if(legacyStatus) home.appendChild(legacyStatus);

  const more=document.createElement('div'); more.className='nf-more-link';
  more.innerHTML='<span>Kelola database produk</span><button type="button">Daftar Item →</button>';
  home.appendChild(more);

  const price=make('nfPrice','Cek Harga','Cari nama, kode, barcode, dan harga jual.');
  const priceIntro=document.createElement('div'); priceIntro.className='nf-page-intro';
  priceIntro.textContent='Harga jual, satuan, dan stok dibaca dari database lokal.';
  price.appendChild(priceIntro);
  if(nodes.search)price.appendChild(nodes.search);
  if(nodes.results)price.appendChild(nodes.results);

  const tx=make('nfTransaction','Transaksi','Keranjang penjualan dan pembayaran.');
  if(nodes.cartAnchor)tx.appendChild(nodes.cartAnchor);
  if(nodes.cart)tx.appendChild(nodes.cart);

  const out=make('nfOut','Barang Keluar','Pencatatan barang keluar menggunakan Harga Beli.');
  const report=make('nfReport','Laporan','Ringkasan penjualan, detail transaksi, dan ekspor.');
  if(nodes.report)report.appendChild(nodes.report);

  const items=make('nfItems','Daftar Item','Kelola database produk secara offline.');
  const host=document.createElement('div'); host.id='nfItemsHost'; host.className='nf-crud-host'; items.appendChild(host);

  // Rebuild the existing navigation instead of creating a second navigation bar.
  if(nodes.bottom){
    nodes.bottom.innerHTML=
      '<button type="button" data-page="home"><span>⌂</span><b>Beranda</b></button>'+
      '<button type="button" data-page="price"><span>⌕</span><b>Cek Harga</b></button>'+
      '<button type="button" data-page="transaction"><span>🛒</span><b>Transaksi</b></button>'+
      '<button type="button" data-page="out"><span>↗</span><b>Keluar</b></button>'+
      '<button type="button" data-page="report"><span>▤</span><b>Laporan</b></button>';
    nodes.bottom.classList.add('nf-glass-nav');
    nodes.bottom.querySelectorAll('button').forEach(b=>b.addEventListener('click',()=>window.nfGo(b.dataset.page)));
  }

  wrap.innerHTML='';
  wrap.append(home,price,tx,out,report,items);

  // Keep dynamically created Barang Keluar inside its own page.
  const relocate=()=>{
    const el=document.getElementById('barangKeluarSection');
    if(el && el.parentNode!==out) out.appendChild(el);
  };
  const observer=new MutationObserver(relocate);
  observer.observe(document.body,{childList:true,subtree:true});
  relocate();
  setTimeout(relocate,100);
  setTimeout(relocate,500);

  const map={home:'nfHome',price:'nfPrice',transaction:'nfTransaction',out:'nfOut',report:'nfReport',items:'nfItems'};
  window.nfGo=function(page){
    if(!map[page])page='home';
    Object.entries(map).forEach(([key,id])=>{
      const el=document.getElementById(id);
      if(el)el.classList.toggle('nf-active',key===page);
    });
    if(nodes.bottom)nodes.bottom.querySelectorAll('button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
    window.scrollTo({top:0,behavior:'smooth'});
    if(page==='price')setTimeout(()=>document.getElementById('q')?.focus(),120);
    if(page==='items')window.nfCrudRefresh?.();
    if(page==='report')window.nfReportRefresh?.();
  };

  window.focusSearch=function(scan){
    window.nfGo('price');
    setTimeout(()=>{
      if(scan && window.Android?.scanBarcode){try{window.Android.scanBarcode();}catch(e){}}
      else document.getElementById('q')?.focus();
    },140);
  };
  window.focusCart=function(){window.nfGo('transaction')};
  window.focusReport=function(){window.nfGo('report')};
  window.focusOut=function(){window.nfGo('out')};
  window.focusItems=function(){window.nfGo('items')};
  more.querySelector('button').onclick=window.focusItems;

  // Remove legacy bottom navigation and menu hooks that could scroll instead of navigating.
  document.querySelectorAll('#bottomNavV15 button').forEach(b=>{
    b.onclick=null;
  });

  window.nfGo('home');
})();
})();