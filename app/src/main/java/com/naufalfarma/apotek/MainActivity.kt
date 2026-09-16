package com.naufalfarma.apotek

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

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
        webView.addJavascriptInterface(BarcodeBridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class BarcodeBridge {
        @JavascriptInterface
        fun scanBarcode() {
            runOnUiThread {
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_QR_CODE
                    )
                    .enableAutoZoom()
                    .build()

                val scanner = GmsBarcodeScanning.getClient(this@MainActivity, options)
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
    }
}
