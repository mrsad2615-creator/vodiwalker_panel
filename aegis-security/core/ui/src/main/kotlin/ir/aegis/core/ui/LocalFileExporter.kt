package ir.aegis.core.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalFileExporter {

    fun exportPdfReport(
        context: Context,
        fileNamePrefix: String,
        title: String,
        subtitle: String,
        sections: List<Pair<String, List<String>>>,
    ): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(dir, "${fileNamePrefix}_$stamp.pdf")

        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(12, 24, 48)
            textSize = 18f
            isFakeBoldText = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 11f
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(2, 132, 199)
            textSize = 13f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10.5f
        }

        var y = 48f
        canvas.drawText(title, 36f, y, titlePaint)
        y += 18f
        canvas.drawText(subtitle, 36f, y, subtitlePaint)
        y += 12f
        canvas.drawLine(36f, y, pageWidth - 36f, y, headerPaint)
        y += 24f

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 40f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 44f
            }
        }

        for ((sectionTitle, lines) in sections) {
            ensureSpace(32f)
            canvas.drawText(sectionTitle, 36f, y, headerPaint)
            y += 16f
            for (rawLine in lines) {
                val wrapped = rawLine.chunked(82)
                for (chunk in wrapped) {
                    ensureSpace(15f)
                    canvas.drawText(chunk, 44f, y, bodyPaint)
                    y += 14f
                }
            }
            y += 10f
        }

        doc.finishPage(page)
        outFile.outputStream().use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun exportTextFile(
        context: Context,
        subdir: String,
        fileName: String,
        content: String,
    ): File {
        val dir = File(context.filesDir, subdir).apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        return file
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
