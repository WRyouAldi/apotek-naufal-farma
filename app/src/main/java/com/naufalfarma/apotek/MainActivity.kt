package com.naufalfarma.apotek

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import java.util.UUID
import android.view.animation.AccelerateDecelerateInterpolator
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var productDb: ProductDb
    private var pendingThermalMac: String? = null
    private var pendingThermalText: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)
        productDb = ProductDb(this)

        // Minimal branded splash: only the application logo, with a smooth
        // entrance followed by a subtle breathing pulse.
        findViewById<android.view.View>(R.id.splashLogo)?.apply {
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f

            animate()
                .alpha(1f)
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(460)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(260)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        }
        // Seed the persistent SQLite database natively from the bundled master.
        // This avoids relying on WebView bridge timing during startup.
        try {
            productDb.seedFromAsset(assets.open("products_seed.json"))
        } catch (e: Exception) {
            Toast.makeText(this, "Database produk belum berhasil diinisialisasi: ${e.message}", Toast.LENGTH_LONG).show()
        }
        android.os.Handler(mainLooper).postDelayed({
            setContentView(R.layout.activity_main)
            webView = findViewById(R.id.webView)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.allowFileAccess = true
            webView.settings.allowContentAccess = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(PERSISTENT_DB_JS, null)
                }
            }
            webView.addJavascriptInterface(AppBridge(), "Android")
            webView.loadUrl("file:///android_asset/index.html")
        }, 820)
    }

    inner class AppBridge {
        @JavascriptInterface
        fun requestBluetoothPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), REQUEST_BT)
            } else {
                webView.post { webView.evaluateJavascript("window.onThermalPermissionReady && window.onThermalPermissionReady();", null) }
            }
        }

        @JavascriptInterface
        fun thermalPrinters(): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return "[]"
            return try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return "[]"
                val out = JSONArray()
                adapter.bondedDevices.sortedBy { it.name ?: it.address }.forEach { d ->
                    out.put(JSONObject().put("name", d.name ?: "Thermal Printer").put("address", d.address))
                }
                out.toString()
            } catch (_: Exception) { "[]" }
        }

        @JavascriptInterface
        fun printThermal(mac: String, text: String): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                pendingThermalMac = mac; pendingThermalText = text
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), REQUEST_BT)
                return JSONObject().put("ok", false).put("permission", true).toString()
            }
            return try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw IllegalStateException("Bluetooth tidak tersedia")
                if (!adapter.isEnabled) throw IllegalStateException("Bluetooth belum aktif")
                val device = adapter.getRemoteDevice(mac)
                val socket = device.createRfcommSocketToServiceRecord(THERMAL_UUID)
                socket.connect()
                socket.outputStream.use { out ->
                    out.write(byteArrayOf(0x1B, 0x40))
                    out.write(byteArrayOf(0x1B, 0x61, 0x01))
                    out.write(text.toThermalBytes())
                    out.write("\\n\\n\\n".toByteArray(Charsets.US_ASCII))
                    out.write(byteArrayOf(0x1D, 0x56, 0x00))
                    out.flush()
                }
                socket.close()
                JSONObject().put("ok", true).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message ?: "Printer tidak dapat dihubungkan").toString()
            }
        }

        private fun String.toThermalBytes(): ByteArray {
            val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "")
                .replace("[^\\u000A\\u000D\\u0020-\\u007E]".toRegex(), "?")
            return normalized.toByteArray(Charsets.US_ASCII)
        }

        @JavascriptInterface
        fun searchProducts(query: String, limit: Int): String = productDb.search(query, limit.coerceIn(1, 100))
        @JavascriptInterface
        fun importProducts(json: String, mode: String): String = try { productDb.importProducts(JSONArray(json), mode) } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "Import gagal").toString() }
        @JavascriptInterface
        fun productCount(): Int = productDb.count()

        @JavascriptInterface
        fun listProducts(query: String, limit: Int): String = productDb.search(query, limit.coerceIn(1, 200))

        @JavascriptInterface
        fun upsertProduct(json: String): String = try {
            val row = JSONObject(json)
            productDb.importProducts(JSONArray().put(row), "add_update")
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "Simpan produk gagal").toString()
        }

        @JavascriptInterface
        fun deleteProduct(id: Long): String = try {
            val deleted = productDb.deleteById(id)
            JSONObject().put("ok", deleted).put("deleted", if (deleted) 1 else 0).toString()
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: "Hapus produk gagal").toString()
        }

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
                    ).enableAutoZoom().build()
                com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this@MainActivity, options).startScan()
                    .addOnSuccessListener { barcode ->
                        val raw = barcode.rawValue ?: return@addOnSuccessListener
                        webView.evaluateJavascript("window.onNativeBarcodeScanned(${JSONObject.quote(raw)});", null)
                    }
                    .addOnFailureListener { Toast.makeText(this@MainActivity, "Scanner tidak dapat dibuka. Pastikan Google Play services tersedia.", Toast.LENGTH_SHORT).show() }
            }
        }

        @JavascriptInterface
        fun saveReport(filename: String, content: String, mimeType: String) {
            try {
                val name = filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val bytes = content.toByteArray(Charsets.UTF_8)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mimeType); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS); put(MediaStore.Downloads.IS_PENDING, 1) }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Gagal membuat file Download")
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); contentResolver.update(uri, values, null, null)
                    toast("Laporan tersimpan di Download/$name")
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IllegalStateException("Folder Download tidak tersedia")
                    dir.mkdirs(); FileOutputStream(File(dir, name)).use { it.write(bytes) }; toast("Laporan tersimpan: ${dir.absolutePath}/$name")
                }
            } catch (e: Exception) { toast("Gagal menyimpan laporan: ${e.message}") }
        }

        @JavascriptInterface
        fun savePdf(title: String, bodyHtml: String) {
            runOnUiThread {
                val pdfWebView = WebView(this@MainActivity)
                pdfWebView.settings.javaScriptEnabled = false
                pdfWebView.settings.domStorageEnabled = false
                pdfWebView.settings.useWideViewPort = false
                pdfWebView.settings.loadWithOverviewMode = false
                pdfWebView.setBackgroundColor(Color.WHITE)
                pdfWebView.alpha = 0f
                val html = """
                    <!doctype html><html><head><meta charset='utf-8'>
                    <meta name='viewport' content='width=595, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>
                    <style>
                    *{box-sizing:border-box}
                    html,body{margin:0;padding:0;background:#fff;width:595px;min-width:595px}
                    body{font-family:Arial,sans-serif;color:#111;font-size:9pt;padding:24px;width:595px;overflow:hidden}
                    h1{text-align:center;font-size:17pt;line-height:1.15;margin:0 0 4px}
                    h2{text-align:center;font-size:11pt;line-height:1.2;margin:0 0 10px}
                    p{margin:4px 0 10px}
                    table{width:100%;max-width:100%;border-collapse:collapse;table-layout:fixed}
                    th,td{border:1px solid #888;padding:5px 6px;vertical-align:top;overflow-wrap:anywhere;word-break:break-word}
                    th{background:#eee}
                    .r{text-align:right;white-space:nowrap}
                    .total{margin:12px 0 0 auto;width:300px;max-width:100%}
                    .total th,.total td{font-weight:700}
                    </style></head><body>$bodyHtml</body></html>
                """.trimIndent()
                pdfWebView.webViewClient = object : WebViewClient() { override fun onPageFinished(view: WebView, url: String) { view.postDelayed({ savePdfFromWebView(pdfWebView, title) }, 350) } }
                (window.decorView as? ViewGroup)?.addView(pdfWebView, ViewGroup.LayoutParams(595, ViewGroup.LayoutParams.WRAP_CONTENT))
                pdfWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }

        private fun savePdfFromWebView(view: WebView, title: String) {
            try {
                val pageWidth = 595; val pageHeight = 842; val scale = if (view.scale > 0f) view.scale else 1f
                val contentHeight = maxOf((view.contentHeight * scale).toInt(), 1)
                view.measure(View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)); view.layout(0, 0, pageWidth, contentHeight)
                val document = PdfDocument(); val pageCount = (contentHeight + pageHeight - 1) / pageHeight
                for (pageNumber in 0 until pageCount) {
                    val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create())
                    page.canvas.save(); page.canvas.clipRect(0, 0, pageWidth, pageHeight); page.canvas.translate(0f, -pageNumber.toFloat() * pageHeight); view.draw(page.canvas); page.canvas.restore(); document.finishPage(page)
                }
                val safeTitle = title.replace(Regex("[^A-Za-z0-9._-]"), "_"); val filename = if (safeTitle.lowercase().endsWith(".pdf")) safeTitle else "$safeTitle.pdf"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, filename); put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS); put(MediaStore.Downloads.IS_PENDING, 1) }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Gagal membuat file PDF")
                    contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }; values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); contentResolver.update(uri, values, null, null)
                } else { val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IllegalStateException("Folder Download tidak tersedia"); dir.mkdirs(); FileOutputStream(File(dir, filename)).use { document.writeTo(it) } }
                document.close(); toast("PDF tersimpan di Download/$filename"); (view.parent as? ViewGroup)?.removeView(view); view.destroy()
            } catch (e: Exception) { toast("Gagal menyimpan PDF: ${e.message}"); (view.parent as? ViewGroup)?.removeView(view); view.destroy() }
        }

        private fun toast(message: String) = runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show() }
    }

    class ProductDb(context: android.content.Context) : SQLiteOpenHelper(context, "naufal_products.db", null, 2) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
            db.enableWriteAheadLogging()
        }
        override fun onCreate(db: SQLiteDatabase) {
            createSchema(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            createSchema(db)
        }
        private fun createSchema(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT, barcode TEXT, name TEXT NOT NULL, jenis TEXT, brand TEXT, satuan TEXT, purchase_price INTEGER DEFAULT 0, price INTEGER DEFAULT 0, stok REAL DEFAULT 0, min_stok REAL DEFAULT 0, rak TEXT, supplier TEXT, keterangan TEXT, status TEXT, updated_at INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS product_units (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL, unit TEXT, conversion REAL DEFAULT 1, barcode TEXT, purchase_price INTEGER DEFAULT 0, updated_at INTEGER, UNIQUE(product_id, unit, barcode), FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE IF NOT EXISTS product_prices (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL, unit TEXT, level INTEGER DEFAULT 1, quantity_until REAL DEFAULT 0, percentage REAL DEFAULT 0, price INTEGER DEFAULT 0, updated_at INTEGER, UNIQUE(product_id, unit, level, quantity_until), FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE IF NOT EXISTS stock (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL, office TEXT DEFAULT 'default', quantity REAL DEFAULT 0, updated_at INTEGER, UNIQUE(product_id, office), FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE CASCADE)")
            db.execSQL("CREATE TABLE IF NOT EXISTS db_meta (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_code ON products(code)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_name ON products(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_units_barcode ON product_units(barcode)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_prices_product ON product_prices(product_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_product ON stock(product_id)")
            // Migration from schema v1.
            val cols = db.rawQuery("PRAGMA table_info(products)", null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(1)) } }
            if (!cols.contains("purchase_price")) db.execSQL("ALTER TABLE products ADD COLUMN purchase_price INTEGER DEFAULT 0")
            if (!cols.contains("min_stok")) db.execSQL("ALTER TABLE products ADD COLUMN min_stok REAL DEFAULT 0")
            if (!cols.contains("supplier")) db.execSQL("ALTER TABLE products ADD COLUMN supplier TEXT")
            if (!cols.contains("keterangan")) db.execSQL("ALTER TABLE products ADD COLUMN keterangan TEXT")
            if (!cols.contains("status")) db.execSQL("ALTER TABLE products ADD COLUMN status TEXT")
        }
        fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM products", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        fun exportAll(): String {
            val db = readableDatabase
            val out = JSONArray()
            db.rawQuery("SELECT id,code,barcode,name,jenis,brand,satuan,purchase_price,price,stok,rak,supplier,keterangan,status,updated_at FROM products ORDER BY id", null).use { c ->
                while (c.moveToNext()) {
                    out.put(JSONObject().put("id", c.getLong(0)).put("code", c.getString(1) ?: "").put("barcode", c.getString(2) ?: "")
                        .put("name", c.getString(3) ?: "").put("jenis", c.getString(4) ?: "").put("brand", c.getString(5) ?: "")
                        .put("satuan", c.getString(6) ?: "").put("cost", c.getLong(7)).put("purchase_price", c.getLong(7))
                        .put("price", c.getLong(8)).put("stok", c.getDouble(9)).put("rak", c.getString(10) ?: "")
                        .put("supplier", c.getString(11) ?: "").put("keterangan", c.getString(12) ?: "").put("status", c.getString(13) ?: "")
                        .put("updated_at", c.getLong(14)))
                }
            }
            return out.toString()
        }

        fun seedFromAsset(input: java.io.InputStream) {
            input.use { stream ->
                val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val rows = JSONArray(json)
                val db = writableDatabase
                val seedVersion = "2026-09-19-db-v3"
                val current = db.rawQuery("SELECT value FROM db_meta WHERE key='seed_version' LIMIT 1", null).use { if (it.moveToFirst()) it.getString(0) else "" }
                if (current == seedVersion) return
                db.beginTransaction()
                try {
                    for (i in 0 until rows.length()) {
                        val o = rows.getJSONObject(i)
                        val id = upsertNormalized(db, o)
                        syncNormalizedChildren(db, id, o)
                    }
                    db.delete("db_meta", "key=?", arrayOf("seed_version"))
                    db.execSQL("INSERT INTO db_meta(key,value) VALUES('seed_version',?)", arrayOf(seedVersion))
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }

        private fun upsertNormalized(db: SQLiteDatabase, o: JSONObject): Long {
            val code=o.optString("code").trim(); val barcode=o.optString("barcode").trim(); val name=o.optString("name").trim().ifEmpty{"(Tanpa nama)"}
            val existing=findId(db,code,barcode,name)
            val values=values(o)
            if(existing!=null){db.update("products",values,"id=?",arrayOf(existing.toString()));return existing}
            return db.insertOrThrow("products",null,values)
        }

        private fun syncNormalizedChildren(db: SQLiteDatabase, productId: Long, o: JSONObject) {
            val unit=o.optString("satuan").trim()
            val barcode=o.optString("barcode").trim()
            val cost = if (o.has("cost")) o.optLong("cost") else if (o.has("purchase_price")) o.optLong("purchase_price") else run {
                db.rawQuery("SELECT purchase_price FROM products WHERE id=?", arrayOf(productId.toString())).use {
                    if (it.moveToFirst()) it.getLong(0) else 0L
                }
            }
            val price=o.optLong("price",0L)
            val now=System.currentTimeMillis()
            db.delete("product_units","product_id=?",arrayOf(productId.toString()))
            db.delete("product_prices","product_id=?",arrayOf(productId.toString()))
            db.delete("stock","product_id=?",arrayOf(productId.toString()))
            db.execSQL("INSERT INTO product_units(product_id,unit,conversion,barcode,purchase_price,updated_at) VALUES(?,?,?,?,?,?)",arrayOf(productId,unit.ifEmpty{"PCS"},1.0,barcode,cost,now))
            db.execSQL("INSERT INTO product_prices(product_id,unit,level,quantity_until,percentage,price,updated_at) VALUES(?,?,?,?,?,?,?)",arrayOf(productId,unit.ifEmpty{"PCS"},1,0.0,0.0,price,now))
            db.execSQL("INSERT INTO stock(product_id,office,quantity,updated_at) VALUES(?,?,?,?)",arrayOf(productId,"default",o.optDouble("stok",0.0),now))
        }

        fun seedChunk(rows: JSONArray): String {
            val db = writableDatabase; db.beginTransaction(); var inserted = 0
            try { for (i in 0 until rows.length()) if (insertOrUpdate(db, rows.getJSONObject(i), "add_update") == 1) inserted++; db.setTransactionSuccessful() } finally { db.endTransaction() }
            return JSONObject().put("ok", true).put("inserted", inserted).toString()
        }

        fun importProducts(rows: JSONArray, mode: String): String {
            val normalizedMode = when (mode.lowercase()) { "add", "update", "replace" -> mode.lowercase(); else -> "add_update" }
            val db = writableDatabase; var added = 0; var updated = 0; var skipped = 0
            db.beginTransaction()
            try {
                if (normalizedMode == "replace") db.delete("products", null, null)
                val operationMode = if (normalizedMode == "replace") "add" else normalizedMode
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val result = insertOrUpdate(db, row, operationMode)
                    if (result == 1 || result == 2) {
                        findId(db, row.optString("code").trim(), row.optString("barcode").trim(), row.optString("name").trim())?.let { syncNormalizedChildren(db, it, row) }
                    }
                    when (result) { 1 -> added++; 2 -> updated++; else -> skipped++ }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            return JSONObject().put("ok", true).put("added", added).put("updated", updated).put("skipped", skipped).put("total", count()).toString()
        }

        private fun insertOrUpdate(db: SQLiteDatabase, o: JSONObject, mode: String): Int {
            val code = o.optString("code").trim(); val barcode = o.optString("barcode").trim(); val name = o.optString("name").trim()
            if (name.isEmpty() && code.isEmpty() && barcode.isEmpty()) return 0
            val existing = findId(db, code, barcode, name)
            if (existing != null) {
                if (mode == "add") return 0
                val v = values(o)
                // CSV import may omit Harga Beli. Preserve the existing value in that case.
                if (!o.has("cost") && !o.has("purchase_price")) v.remove("purchase_price")
                db.update("products", v, "id=?", arrayOf(existing.toString()))
                return 2
            }
            if (mode == "update") return 0
            db.insertOrThrow("products", null, values(o))
            return 1
        }
        private fun findId(db: SQLiteDatabase, code: String, barcode: String, name: String): Long? {
            if (code.isNotEmpty()) db.rawQuery("SELECT id FROM products WHERE code=? ORDER BY id LIMIT 1", arrayOf(code)).use { if (it.moveToFirst()) return it.getLong(0) }
            if (barcode.isNotEmpty()) db.rawQuery("SELECT id FROM products WHERE barcode=? ORDER BY id LIMIT 1", arrayOf(barcode)).use { if (it.moveToFirst()) return it.getLong(0) }
            if (name.isNotEmpty()) db.rawQuery("SELECT id FROM products WHERE lower(name)=lower(?) ORDER BY id LIMIT 1", arrayOf(name)).use { if (it.moveToFirst()) return it.getLong(0) }
            return null
        }
        private fun values(o: JSONObject): ContentValues = ContentValues().apply {
            put("code", o.optString("code").trim())
            put("barcode", o.optString("barcode").trim())
            put("name", o.optString("name").trim().ifEmpty { "(Tanpa nama)" })
            put("jenis", o.optString("jenis").trim())
            put("brand", o.optString("brand").trim())
            put("satuan", o.optString("satuan").trim())
            put("purchase_price", o.optLong("cost", o.optLong("purchase_price", 0L)))
            put("price", o.optLong("price", 0L))
            put("stok", o.optDouble("stok", 0.0))
            put("min_stok", o.optDouble("min_stok", 0.0))
            put("rak", o.optString("rak").trim())
            put("supplier", o.optString("supplier").trim())
            put("keterangan", o.optString("keterangan").trim())
            put("status", o.optString("status").trim())
            put("updated_at", System.currentTimeMillis())
        }
        fun search(query: String, limit: Int): String {
            val db = readableDatabase; val q = query.trim()
            val cursor = if (q.isEmpty()) db.rawQuery("SELECT id,code,barcode,name,jenis,brand,satuan,purchase_price,price,stok,rak,supplier,min_stok FROM products ORDER BY name LIMIT ?", arrayOf(limit.toString()))
            else { val like = "%"+q.lowercase()+"%"; db.rawQuery("SELECT id,code,barcode,name,jenis,brand,satuan,purchase_price,price,stok,rak,supplier,min_stok FROM products WHERE lower(code) LIKE ? OR lower(barcode) LIKE ? OR lower(name) LIKE ? OR lower(jenis) LIKE ? OR lower(brand) LIKE ? ORDER BY CASE WHEN lower(code)=? THEN 0 WHEN lower(barcode)=? THEN 1 WHEN lower(name) LIKE ? THEN 2 ELSE 3 END, name LIMIT ?", arrayOf(like,like,like,like,like,q,q,like,limit.toString())) }
            val out = JSONArray(); cursor.use { while (it.moveToNext()) out.put(JSONObject().apply { put("id",it.getLong(0)); put("code",it.getString(1)?:("")); put("barcode",it.getString(2)?:("")); put("name",it.getString(3)?:("")); put("jenis",it.getString(4)?:("")); put("brand",it.getString(5)?:("")); put("satuan",it.getString(6)?:("")); put("purchasePrice",it.getLong(7)); put("price",it.getLong(8)); put("stok",it.getDouble(9)); put("rak",it.getString(10)?:("")); put("supplier",it.getString(11)?:("")); put("minStok",it.getDouble(12)) }) }
            return out.toString()
        }

        fun deleteById(id: Long): Boolean {
            if (id <= 0) return false
            val db = writableDatabase
            db.beginTransaction()
            return try {
                val deleted = db.delete("products", "id=?", arrayOf(id.toString()))
                if (deleted > 0) {
                    // Foreign keys are enabled, but explicitly clear normalized
                    // children as a defensive measure for older databases.
                    db.delete("product_units", "product_id=?", arrayOf(id.toString()))
                    db.delete("product_prices", "product_id=?", arrayOf(id.toString()))
                    db.delete("stock", "product_id=?", arrayOf(id.toString()))
                }
                db.setTransactionSuccessful()
                deleted > 0
            } finally {
                db.endTransaction()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {\n        super.onRequestPermissionsResult(requestCode, permissions, grantResults)\n        if (requestCode == REQUEST_BT) {\n            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || grantResults.any { it == PackageManager.PERMISSION_GRANTED }\n            if (granted) {\n                val mac = pendingThermalMac\n                val text = pendingThermalText\n                pendingThermalMac = null\n                pendingThermalText = null\n                webView.post {\n                    webView.evaluateJavascript("window.onThermalPermissionReady && window.onThermalPermissionReady();", null)\n                    if (!mac.isNullOrBlank() && !text.isNullOrBlank()) {\n                        val result = AppBridge().printThermal(mac, text)\n                        webView.evaluateJavascript("window.onThermalPrintResult && window.onThermalPrintResult(" + JSONObject.quote(result) + ");", null)\n                    }\n                }\n            } else {\n                webView.post { webView.evaluateJavascript("window.onThermalPermissionDenied && window.onThermalPermissionDenied();", null) }\n            }\n        }\n    }\n\n    companion object {\n        private const val REQUEST_BT = 9201\n        private val THERMAL_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val PERSISTENT_DB_JS = """
            (function(){
              if(window.__naufalDbReady)return; window.__naufalDbReady=true;
              function setStatus(t,ok=true){const e=document.getElementById('saveStatus');if(e){e.className='small '+(ok?'status-ok':'status-err');e.textContent=t;}}
              function nativeSearch(){try{return JSON.parse(Android.searchProducts((document.getElementById('q')?.value||''),80));}catch(e){return [];}}
              function persistentSearch(){let list=nativeSearch();if(!list.length&&Array.isArray(window.ITEMS)){const q=(document.getElementById("q")?.value||"").trim().toLowerCase();list=q?ITEMS.filter(x=>[x.code,x.barcode,x.name,x.jenis,x.brand].some(v=>String(v||"").toLowerCase().includes(q))).slice(0,80):ITEMS.slice(0,80);}window.__naufalResults=list;renderResults(list);}
              function csvParse(text){const rows=[];let row=[],cell='',quoted=false;for(let i=0;i<text.length;i++){const ch=text[i],nx=text[i+1];if(ch==='"'){if(quoted&&nx==='"'){cell+='"';i++;}else quoted=!quoted;}else if(ch===','&&!quoted){row.push(cell);cell='';}else if((ch==='\n'||ch==='\r')&&!quoted){if(ch==='\r'&&nx==='\n')i++;row.push(cell);if(row.some(v=>v.trim()!==''))rows.push(row);row=[];cell='';}else cell+=ch;}row.push(cell);if(row.some(v=>v.trim()!==''))rows.push(row);return rows;}
              function norm(s){return String(s||'').toLowerCase().replace(/[^a-z0-9]+/g,'');}
              function toRows(text){const rows=csvParse(text);if(rows.length<2)throw new Error('CSV kosong atau hanya berisi header.');const h=rows[0].map(norm),idx=(...names)=>{for(const n of names){const i=h.indexOf(norm(n));if(i>=0)return i;}return -1;};const ix={code:idx('code','kode','kodeitem','kode barang'),barcode:idx('barcode','bar code'),name:idx('name','nama','namaitem','nama item','nama barang'),jenis:idx('jenis','type','kategori'),brand:idx('brand','merek'),satuan:idx('satuan','unit'),price:idx('price','harga','hargajual','hargajual1','harga jual','harga jual 1'),stok:idx('stok','stock'),rak:idx('rak','lokasi')};if(ix.name<0&&ix.code<0&&ix.barcode<0)throw new Error('Kolom nama/kode/barcode tidak ditemukan.');return rows.slice(1).map(r=>({code:ix.code>=0?String(r[ix.code]||'').trim():'',barcode:ix.barcode>=0?String(r[ix.barcode]||'').trim():'',name:ix.name>=0?String(r[ix.name]||'').trim():'',jenis:ix.jenis>=0?String(r[ix.jenis]||'').trim():'',brand:ix.brand>=0?String(r[ix.brand]||'').trim():'',satuan:ix.satuan>=0?String(r[ix.satuan]||'').trim():'',price:ix.price>=0?Number(String(r[ix.price]||'').replace(/[^0-9.-]/g,''))||0:0,stok:ix.stok>=0?Number(String(r[ix.stok]||'').replace(',','.'))||0:0,rak:ix.rak>=0?String(r[ix.rak]||'').trim():''})).filter(x=>x.code||x.barcode||x.name);}
              function addImportUi(){if(document.getElementById('nativeImportCard'))return;const host=document.getElementById('searchArea');if(!host)return;const box=document.createElement('div');box.id='nativeImportCard';box.style.cssText='display:flex;gap:7px;align-items:center;margin-top:8px';box.innerHTML='<input id="nativeCsvFile" type="file" accept=".csv,text/csv" style="display:none"><select id="nativeCsvMode" style="flex:1;min-width:0;padding:10px;border:1px solid #dfe9e5;border-radius:12px;background:#f8fbfa;font-size:13px"><option value="add_update">Tambah + Update</option><option value="add">Tambah saja</option><option value="update">Update saja</option><option value="replace">Ganti semua</option></select><button type="button" id="nativeCsvBtn" style="white-space:nowrap">📥 Import CSV</button>';host.appendChild(box);const file=document.getElementById('nativeCsvFile');document.getElementById('nativeCsvBtn').onclick=()=>file.click();file.onchange=()=>{const f=file.files&&file.files[0];if(!f)return;const mode=document.getElementById('nativeCsvMode').value;const r=new FileReader();r.onload=()=>{try{const rows=toRows(r.result);const res=JSON.parse(Android.importProducts(JSON.stringify(rows),mode));if(!res.ok)throw new Error(res.error||'Import gagal');setStatus('✓ Import selesai: '+res.added+' ditambah, '+res.updated+' diperbarui. Total '+res.total+' produk.');persistentSearch();}catch(e){setStatus('Import gagal: '+e.message,false);}file.value='';};r.readAsText(f,'UTF-8');};}
              function enhanceResultMeta(){if(window.__naufalRenderWrapped)return;window.__naufalRenderWrapped=true;const original=window.renderResults;window.renderResults=function(list){original(list);document.querySelectorAll('#results .card').forEach((card,i)=>{const x=window.__naufalResults&&window.__naufalResults[i];if(!x)return;const meta=card.querySelector('.meta');const extra=[x.brand,x.satuan,x.stok?('Stok: '+x.stok):''].filter(Boolean).join(' • ');if(extra){const d=document.createElement('div');d.className='meta';d.textContent=extra;if(meta)meta.insertAdjacentElement('afterend',d);else card.querySelector('.name')?.insertAdjacentElement('afterend',d);}});};}
              function init(){try{if(typeof Android==='undefined')return;enhanceResultMeta();const q=document.getElementById('q');if(q){const clone=q.cloneNode(true);q.parentNode.replaceChild(clone,q);clone.addEventListener('input',persistentSearch);}addImportUi();persistentSearch();const dbCount=Android.productCount();const stat=document.querySelector('.stat-products b');if(stat)stat.textContent=dbCount.toLocaleString('id-ID');const note=document.querySelector('.small[style*="margin:8px 2px 0"]');if(note)note.innerHTML='Database lokal persistent • '+dbCount.toLocaleString('id-ID')+' produk';}catch(e){setStatus('Database lokal belum siap: '+e.message,false);}}
              setTimeout(init,80);
            })();
        """.trimIndent()
    }
}
