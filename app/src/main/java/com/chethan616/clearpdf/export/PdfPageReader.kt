package com.chethan616.clearpdf.export

import android.content.Context
import android.net.Uri
import com.kyant.ocrcore.OcrService
import com.kyant.pdfcore.internal.PdfBox
import com.kyant.pdfcore.raster.PdfRasterizer
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads every page of a PDF as positioned words for [DocxExporter] / [XlsxExporter].
 *
 * Pages that carry real text are read straight from the PDF (exact, fast). Pages that are
 * only an image — scans and photos — go through on-device OCR (Spanish + English), so
 * mixed documents convert correctly page by page.
 */
object PdfPageReader {

    /** A page with fewer recognisable characters than this is treated as a scan. */
    private const val MIN_TEXT_CHARS = 15

    suspend fun read(
        context: Context,
        uri: Uri,
        ocr: OcrService,
        forceOcr: Boolean = false,
        onProgress: (page: Int, total: Int, usingOcr: Boolean) -> Unit = { _, _, _ -> }
    ): List<ExportPage> = withContext(Dispatchers.IO) {
        PdfBox.ensureInitialized(context)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open PDF")
        val pages = ArrayList<ExportPage>()
        input.use { stream ->
            PDDocument.load(stream).use { doc ->
                val total = doc.numberOfPages
                for (i in 0 until total) {
                    val page = doc.getPage(i)
                    val box = page.cropBox ?: page.mediaBox
                    val rotated = (page.rotation % 180) != 0
                    val w = if (rotated) box.height else box.width
                    val h = if (rotated) box.width else box.height

                    val digital = if (forceOcr) emptyList() else readTextLayer(doc, i, w, h)
                    val chars = digital.sumOf { it.text.length }
                    if (chars >= MIN_TEXT_CHARS) {
                        onProgress(i, total, false)
                        pages.add(ExportPage(w, h, digital, false))
                    } else {
                        onProgress(i, total, true)
                        val words = runCatching {
                            val bitmap = PdfRasterizer.rasterizePageBitmap(context, uri, i)
                            try {
                                bitmap?.let { ocr.recognize(context, it).words }.orEmpty()
                            } finally {
                                if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
                            }
                        }.getOrNull().orEmpty()
                        val chunks = words.map { TextChunk(it.text, it.left, it.top, it.right, it.bottom) }
                        // Keep whatever digital text there was if OCR found nothing better.
                        pages.add(if (chunks.isNotEmpty()) ExportPage(w, h, chunks, true) else ExportPage(w, h, digital, false))
                    }
                }
            }
        }
        pages
    }

    private fun readTextLayer(doc: PDDocument, pageIndex: Int, pageW: Float, pageH: Float): List<TextChunk> {
        val out = ArrayList<TextChunk>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                // One call per word normally, but split on spaces anyway to be safe.
                val word = ArrayList<TextPosition>()
                fun flush() {
                    if (word.isEmpty()) return
                    out.add(toChunk(word, pageW, pageH))
                    word.clear()
                }
                for (tp in textPositions) {
                    val u = tp.unicode
                    if (u.isNullOrBlank()) flush() else word.add(tp)
                }
                flush()
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        runCatching { stripper.getText(doc) }
        return out
    }

    private fun toChunk(word: List<TextPosition>, pageW: Float, pageH: Float): TextChunk {
        val first = word.first()
        val last = word.last()
        var fs = first.fontSizeInPt
        if (fs < 2f) fs = first.heightDir * 1.5f
        if (fs < 2f) fs = 10f
        val left = first.xDirAdj
        val right = last.xDirAdj + last.widthDirAdj
        val baseline = word.maxOf { it.yDirAdj }
        val top = baseline - fs * 0.85f
        val bottom = baseline + fs * 0.2f
        val fontName = runCatching { first.font?.name }.getOrNull().orEmpty()
        val bold = fontName.contains("bold", ignoreCase = true) || fontName.contains("black", ignoreCase = true) ||
            fontName.contains("heavy", ignoreCase = true)
        val text = word.joinToString("") { it.unicode ?: "" }
        return TextChunk(
            text,
            (left / pageW).coerceIn(0f, 1f),
            (top / pageH).coerceIn(0f, 1f),
            (right / pageW).coerceIn(0f, 1f),
            (bottom / pageH).coerceIn(0f, 1f),
            fs,
            bold
        )
    }
}
