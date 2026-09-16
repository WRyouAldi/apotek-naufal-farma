package com.naufalfarma.apotek

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private lateinit var webView: WebView
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);setContentView(R.layout.activity_main)
        webView=findViewById(R.id.webView);webView.settings.javaScriptEnabled=true;webView.settings.domStorageEnabled=true;webView.settings.allowFileAccess=true;webView.settings.allowContentAccess=true;webView.webViewClient=WebViewClient();webView.addJavascriptInterface(AppBridge(),"Android");webView.loadUrl("file:///android_asset/index.html")
    }
    inner class AppBridge {
        @JavascriptInterface fun scanBarcode(){runOnUiThread{val o=com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder().setBarcodeFormats(1,2,32,4,8,16,3,UPC_E_PLACEHOLDER,256).enableAutoZoom().build();val sc=com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this@MainActivity,o);sc.startScan().addOnSuccessListener{b->val raw=b.rawValue?:return@addOnSuccessListener;webView.evaluateJavascript("window.onNativeBarcodeScanned(${org.json.JSONObject.quote(raw)});",null)}.addOnFailureListener{Toast.makeText(this@MainActivity,"Scanner tidak dapat dibuka. Pastikan Google Play services tersedia.",Toast.LENGTH_SHORT).show()}}}
        @JavascriptInterface fun saveReport(filename:String,content:String,mimeType:String){try{val name=filename.replace(Regex("[^A-Za-z0-9._-]"),"_");val bytes=content.toByteArray(Charsets.UTF_8);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){val v=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,mimeType);put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);put(MediaStore.Downloads.IS_PENDING,1)};val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v)?:throw IllegalStateException("Gagal membuat file Download");contentResolver.openOutputStream(uri)?.use{it.write(bytes)};v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);contentResolver.update(uri,v,null,null);toast("Laporan tersimpan di Download/$name")}else{val d=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?:throw IllegalStateException("Folder Download tidak tersedia");d.mkdirs();val f=File(d,name);FileOutputStream(f).use{it.write(bytes)};toast("Laporan tersimpan: ${f.absolutePath}")}}catch(e:Exception){toast("Gagal menyimpan laporan: ${e.message}")}}
        @JavascriptInterface fun savePdf(title:String,bodyHtml:String){runOnUiThread{val w=WebView(this@MainActivity);w.settings.javaScriptEnabled=false;w.setBackgroundColor(Color.WHITE);w.loadDataWithBaseURL(null,"<html><head><meta charset='utf-8'><style>@page{size:A4 portrait;margin:12mm}body{font-family:Arial;color:#111;font-size:9pt}h1{text-align:center;font-size:17pt;margin:0}h2{text-align:center;font-size:11pt;margin:4px 0 14px}table{width:100%;border-collapse:collapse}th,td{border:1px solid #aaa;padding:6px 7px}th{background:#eee}.r{text-align:right}.total{margin:14px 0 0 auto;width:330px}.total th,.total td{font-weight:700}</style></head><body>$bodyHtml</body></html>","text/html","UTF-8",null);w.webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView,url:String){super.onPageFinished(view,url);val adapter=view.createPrintDocumentAdapter(title);val a=PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(PrintAttributes.Resolution("pdf","pdf",300,300)).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();val name=(title.replace(Regex("[^A-Za-z0-9._-]"),"_")+".pdf");if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){val v=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,name);put(MediaStore.Downloads.MIME_TYPE,"application/pdf");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);put(MediaStore.Downloads.IS_PENDING,1)};val uri=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri!=null){contentResolver.openFileDescriptor(uri,"w")?.use{fd->writePdf(adapter,a,fd)};v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);contentResolver.update(uri,v,null,null);toast("PDF tersimpan di Download/$name")}else toast("Gagal membuat file PDF")}else{val d=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(d!=null){d.mkdirs();val f=File(d,name);ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE).use{fd->writePdf(adapter,a,fd)};toast("PDF tersimpan: ${f.absolutePath}")}else toast("Folder Download tidak tersedia")}}}}}}
        private fun writePdf(adapter:PrintDocumentAdapter,attrs:PrintAttributes,fd:ParcelFileDescriptor){adapter.onLayout(null,attrs, null,object:PrintDocumentAdapter.LayoutResultCallback(){override fun onLayoutFinished(info:PrintDocumentInfo,changed:Boolean){adapter.onWrite(arrayOf(PageRange.ALL_PAGES),fd,null,object:PrintDocumentAdapter.WriteResultCallback(){override fun onWriteFinished(pages:Array<android.print.PageRange>){fd.close()};override fun onWriteFailed(error:CharSequence?){fd.close()}})}} ,null)}
        private fun toast(s:String)=runOnUiThread{Toast.makeText(this@MainActivity,s,Toast.LENGTH_LONG).show()}
    }
}
