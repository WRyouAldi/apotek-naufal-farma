package com.naufalfarma.apotek

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
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
        webView.addJavascriptInterface(AppBridge(), "Android")
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
                val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning
                    .getClient(this@MainActivity, options)
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val raw = barcode.rawValue ?: return@addOnSuccessListener
                        webView.evaluateJavascript(
                            "window.onNativeBarcodeScanned(${org.json.JSONObject.quote(raw)});",
                            null
                        )
                    }
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
                val name = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val bytes = content.toByteArray(Charsets.UTF_8)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Gagal membuat file Download")
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    toast("Laporan tersimpan di Download/$name")
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: throw IllegalStateException("Folder Download tidak tersedia")
                    dir.mkdirs()
                    val file = File(dir, name)
                    FileOutputStream(file).use { it.write(bytes) }
                    toast("Laporan tersimpan: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                toast("Gagal menyimpan laporan: ${e.message}")
            }
        }

        @JavascriptInterface
        fun savePdf(title: String, bodyHtml: String) {
            runOnUiThread {
                val pdfWebView = WebView(this@MainActivity)
                pdfWebView.settings.javaScriptEnabled = false
                pdfWebView.setBackgroundColor(Color.WHITE)
                pdfWebView.alpha = 0f

                val html = """
                    <!doctype html>
                    <html><head><meta charset='utf-8'>
                    <style>
                    *{box-sizing:border-box}
                    html,body{margin:0;padding:0;background:#fff}
                    body{font-family:Arial,sans-serif;color:#111;font-size:9pt;padding:28px}
                    h1{text-align:center;font-size:17pt;margin:0 0 4px}
                    h2{text-align:center;font-size:11pt;margin:0 0 14px}
                    table{width:100%;border-collapse:collapse}
                    th,td{border:1px solid #888;padding:6px 7px}
                    th{background:#eee}
                    .r{text-align:right}
                    .total{margin:14px 0 0 auto;width:330px}
                    .total th,.total td{font-weight:700}
                    </style></head><body>$bodyHtml</body></html>
                """.trimIndent()

                pdfWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.postDelayed({
                            savePdfFromWebView(pdfWebView, title)
                        }, 350)
                    }
                }

                val root = window.decorView as? ViewGroup
                root?.addView(
                    pdfWebView,
                    ViewGroup.LayoutParams(595, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                pdfWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }

        private fun savePdfFromWebView(view: WebView, title: String) {
            try {
                val pageWidth = 595
                val pageHeight = 842
                val cssHeight = view.contentHeight
                val scale = if (view.scale > 0f) view.scale else 1f
                val contentHeight = maxOf((cssHeight * scale).toInt(), 1)

                view.measure(
                    View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, pageWidth, contentHeight)

                val pageCount = (contentHeight + pageHeight - 1) / pageHeight
                val document = PdfDocument()

                for (pageNumber in 0 until pageCount) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.save()
                    page.canvas.clipRect(0, 0, pageWidth, pageHeight)
                    page.canvas.translate(0f, -pageNumber.toFloat() * pageHeight)
                    view.draw(page.canvas)
                    page.canvas.restore()
                    document.finishPage(page)
                }

                val safeTitle = title.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val filename = if (safeTitle.lowercase().endsWith(".pdf")) safeTitle else "$safeTitle.pdf"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, filename)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Gagal membuat file PDF")
                    val output = contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Gagal membuka file PDF")
                    output.use { document.writeTo(it) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: throw IllegalStateException("Folder Download tidak tersedia")
                    dir.mkdirs()
                    val file = File(dir, filename)
                    FileOutputStream(file).use { document.writeTo(it) }
                }

                document.close()
                toast("PDF tersimpan di Download/$filename")
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            } catch (e: Exception) {
                toast("Gagal menyimpan PDF: ${e.message}")
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
        }

        private fun toast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
