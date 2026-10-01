from pathlib import Path
import re

p = Path('app/src/main/java/com/naufalfarma/apotek/MainActivity.kt')
s = p.read_text(encoding='utf-8')

html_start = s.index('                val html = """', s.index('fun savePdf(title: String, bodyHtml: String)'))
html_end = s.index('                """.trimIndent()', html_start) + len('                """.trimIndent()')

new_html = '''                val html = """
                    <!doctype html><html><head><meta charset='utf-8'>
                    <meta name='viewport' content='width=595, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>
                    <style>
                    *{box-sizing:border-box}
                    html,body{margin:0;padding:0;background:#fff;width:595px;min-width:595px;max-width:595px}
                    body{font-family:Arial,sans-serif;color:#111;font-size:9pt;padding:34px;width:595px;max-width:595px;overflow:visible}
                    #pdfRoot{width:527px;max-width:527px;margin:0 auto;overflow:visible}
                    h1{text-align:center;font-size:17pt;line-height:1.15;margin:0 0 4px;max-width:527px}
                    h2{text-align:center;font-size:11pt;line-height:1.2;margin:0 0 10px;max-width:527px}
                    p{margin:4px 0 10px;max-width:527px}
                    table{width:527px!important;max-width:527px!important;border-collapse:collapse;table-layout:fixed}
                    thead{display:table-header-group}
                    tr{break-inside:avoid;page-break-inside:avoid}
                    th,td{border:1px solid #888;padding:5px 6px;vertical-align:top;overflow-wrap:anywhere;word-break:break-word;white-space:normal}
                    th{background:#eee}
                    img,svg,canvas,iframe,pre{max-width:527px!important}
                    .r{text-align:right;white-space:nowrap}
                    .total{margin:12px 0 0 auto;width:300px;max-width:527px}
                    .total th,.total td{font-weight:700}
                    </style></head><body><div id='pdfRoot'>$bodyHtml</div></body></html>
                """.trimIndent()'''

s = s[:html_start] + new_html + s[html_end:]

start = s.index('        private fun savePdfFromWebView(view: WebView, title: String) {')
end = s.index('        private fun toast(message: String)', start)

new_func = '''        private fun savePdfFromWebView(view: WebView, title: String) {
            try {
                val pageWidth = 595
                val pageHeight = 842
                val measuredWidth = pageWidth
                val baseScale = if (view.scale > 0f) view.scale else 1f
                val rawContentWidth = maxOf((view.contentWidth * baseScale).toInt(), 1)
                val fitScale = minOf(1f, pageWidth.toFloat() / rawContentWidth.toFloat())
                val rawContentHeight = maxOf((view.contentHeight * baseScale).toInt(), 1)
                val contentHeight = maxOf((rawContentHeight * fitScale).toInt(), 1)

                view.measure(
                    View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(rawContentHeight, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, measuredWidth, rawContentHeight)

                val document = PdfDocument()
                val sourcePageHeight = maxOf((pageHeight / fitScale).toInt(), 1)
                val pageCount = maxOf((rawContentHeight + sourcePageHeight - 1) / sourcePageHeight, 1)

                for (pageNumber in 0 until pageCount) {
                    val page = document.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber + 1).create()
                    )
                    page.canvas.save()
                    page.canvas.clipRect(0, 0, pageWidth, pageHeight)
                    page.canvas.scale(fitScale, fitScale)
                    page.canvas.translate(0f, -pageNumber.toFloat() * sourcePageHeight)
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
                    contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?: throw IllegalStateException("Folder Download tidak tersedia")
                    dir.mkdirs()
                    FileOutputStream(File(dir, filename)).use { document.writeTo(it) }
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

'''

s = s[:start] + new_func + s[end:]
p.write_text(s, encoding='utf-8')
print('A4 PDF patch applied')
