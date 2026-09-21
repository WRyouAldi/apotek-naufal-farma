(function(){
'use strict';
function esc(s){return String(s??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));}
function bridge(name,...args){try{if(window.Android&&typeof window.Android[name]==='function')return window.Android[name](...args);}catch(e){return ''}return '';}
function config(){
  let x={baseUrl:'',apiKey:''};
  try{if(bridge('getServerConfig')) x=JSON.parse(bridge('getServerConfig'));}catch(e){}
  return x;
}
function render(){
  const p=document.getElementById('nfDatabaseHost'); if(!p)return;
  const c=config();
  p.innerHTML=
    '<div class="nf-db-card">'+
      '<div class="nf-db-head"><div><div class="nf-db-kicker">DATABASE</div><h3>Database via IP / LAN</h3><p>Hubungkan aplikasi ke database server melalui jaringan lokal.</p></div><span class="nf-db-dot">●</span></div>'+
      '<label>IP / URL Server<input id="nfServerUrl" inputmode="url" placeholder="http://192.168.1.100:8080" value="'+esc(c.baseUrl)+'"></label>'+
      '<label>API Key <small>(opsional)</small><input id="nfServerKey" type="password" placeholder="API key server" value="'+esc(c.apiKey)+'"></label>'+
      '<div class="nf-db-actions"><button type="button" id="nfSaveServer">Simpan</button><button type="button" id="nfTestServer">Test Koneksi</button></div>'+
      '<div class="nf-db-status" id="nfServerStatus">Belum terhubung</div>'+
      '<div class="nf-db-sync">'+
        '<button type="button" id="nfPullServer">↓ Ambil Database Server</button>'+
        '<button type="button" id="nfPushServer">↑ Kirim Database Lokal</button>'+
      '</div>'+
      '<div class="nf-db-note">Database lokal tetap dipakai saat offline. Gunakan <b>Ambil</b> untuk memperbarui data lokal dari server, atau <b>Kirim</b> untuk mengirim seluruh database lokal ke server.</div>'+
    '</div>';
  document.getElementById('nfSaveServer').onclick=()=>{
    const u=document.getElementById('nfServerUrl').value.trim().replace(/\/$/,'');
    const k=document.getElementById('nfServerKey').value.trim();
    bridge('saveServerConfig',u,k); status(u?'Konfigurasi server tersimpan.':'IP/URL belum diisi.',u?'ok':'err');
  };
  document.getElementById('nfTestServer').onclick=()=>{
    const u=document.getElementById('nfServerUrl').value.trim().replace(/\/$/,'');
    const k=document.getElementById('nfServerKey').value.trim();
    bridge('saveServerConfig',u,k);
    status('Menghubungkan ke server...','wait');
    bridge('testServerConnection');
  };
  document.getElementById('nfPullServer').onclick=()=>{
    const u=document.getElementById('nfServerUrl').value.trim();
    if(!u){status('Isi IP/URL server terlebih dahulu.','err');return}
    bridge('saveServerConfig',u,document.getElementById('nfServerKey').value.trim());
    status('Mengambil database dari server...','wait'); bridge('pullServerDatabase','add_update');
  };
  document.getElementById('nfPushServer').onclick=()=>{
    const u=document.getElementById('nfServerUrl').value.trim();
    if(!u){status('Isi IP/URL server terlebih dahulu.','err');return}
    if(!confirm('Kirim seluruh database lokal ke server? Data server dengan kode/barcode/nama yang sama akan diperbarui.'))return;
    bridge('saveServerConfig',u,document.getElementById('nfServerKey').value.trim());
    status('Mengirim database lokal...','wait'); bridge('pushLocalDatabase');
  };
}
function status(t,kind){
  const e=document.getElementById('nfServerStatus'); if(!e)return;
  e.textContent=t; e.className='nf-db-status '+(kind||'');
}
window.nfServerTestResult=function(raw){
  try{const r=JSON.parse(raw); status(r.ok?'● Server terhubung'+(r.count!=null?' • '+r.count+' produk':''):'Koneksi gagal: '+(r.error||'Tidak dapat terhubung'),r.ok?'ok':'err');}
  catch(e){status('Koneksi gagal.','err')}
};
window.nfServerPullResult=function(raw){
  try{const r=JSON.parse(raw); status(r.ok?'✓ Database masuk: '+(r.total||r.serverCount||0)+' produk. Ditambah '+(r.added||0)+', diperbarui '+(r.updated||0):'Gagal: '+(r.error||'Import gagal'),r.ok?'ok':'err'); window.nfCrudRefresh?.(); window.nfGo?.('items');}
  catch(e){status('Respons server tidak valid.','err')}
};
window.nfServerPushResult=function(raw){
  try{const r=JSON.parse(raw); status(r.ok?'✓ Database lokal berhasil dikirim ke server. '+(r.count!=null?r.count+' produk.':''):'Gagal: '+(r.error||'Upload gagal'),r.ok?'ok':'err');}
  catch(e){status('Respons server tidak valid.','err')}
};
window.nfDatabaseRefresh=render;
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',render);else render();
})();