package com.naufalfarma.apotek

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.webViewClient = WebViewClient()
        val bridge = AppBridge()
        webView.addJavascriptInterface(bridge, "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class AppBridge {
        @JavascriptInterface
        fun scanBarcode() {
            runOnUiThread {
                val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128,
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39,
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_93,
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF,
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E,
                        com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE
                    )
                    .enableAutoZoom()
                    .build()

                val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this@MainActivity, options)
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val raw = barcode.rawValue ?: return@addOnSuccessListener
                        val escaped = org.json.JSONObject.quote(raw)
                        webView.evaluateJavascript(
                            "window.onNativeBarcodeScanned($escaped);",
                            null
                        )
                    }
                    .addOnCanceledListener { }
                    .addOnFailureListener {
                        Toast.makeText(
                            this@MainActivity,
                            "Scanner tidak dapat dibuka. Pastikan Google Play services tersedia.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }

        @JavascriptInterface
        fun saveReport(filename: String, content: String, mimeType: String) {
            try {
                val safeName = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val bytes = content.toByteArray(Charsets.UTF_8)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Gagal membuat file Download")
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Gagal membuka file Download")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Laporan tersimpan di Download/$safeName", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: throw IllegalStateException("Folder Download tidak tersedia")
                    dir.mkdirs()
                    val file = File(dir, safeName)
                    FileOutputStream(file).use { it.write(bytes) }
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Laporan tersimpan: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Gagal menyimpan laporan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
