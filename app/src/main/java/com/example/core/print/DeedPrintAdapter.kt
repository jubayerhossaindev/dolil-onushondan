package com.example.core.print

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import java.io.FileOutputStream
import kotlin.math.roundToInt

class DeedPrintDocumentAdapter internal constructor(
    private val context: Context,
    private val deed: DeedRecord,
    private val language: AppLanguage,
    private val backend: PdfExportBackend = AndroidPdfDocumentBackend
) : PrintDocumentAdapter() {

    constructor(
        context: Context,
        deed: DeedRecord,
        language: AppLanguage
    ) : this(context, deed, language, AndroidPdfDocumentBackend)

    private var currentPlan: PaginatedDocumentPlan? = null

    internal sealed interface LayoutOutcome {
        data class Finished(val info: PrintDocumentInfo, val changed: Boolean) : LayoutOutcome
        data class Failed(val message: String) : LayoutOutcome
        data object Cancelled : LayoutOutcome
    }

    internal sealed interface WriteOutcome {
        data class Finished(val writtenRanges: Array<PageRange>) : WriteOutcome
        data class Failed(val message: String) : WriteOutcome
        data object Cancelled : WriteOutcome
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        when (val outcome = performLayout(oldAttributes, newAttributes, cancellationSignal)) {
            is LayoutOutcome.Cancelled -> callback.onLayoutCancelled()
            is LayoutOutcome.Failed -> callback.onLayoutFailed(outcome.message)
            is LayoutOutcome.Finished -> callback.onLayoutFinished(outcome.info, outcome.changed)
        }
    }

    internal fun performLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?
    ): LayoutOutcome {
        if (cancellationSignal?.isCanceled == true) {
            return LayoutOutcome.Cancelled
        }

        val mediaSize = newAttributes.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
        val widthPt = ((mediaSize.widthMils / 1000f) * 72f).roundToInt().coerceAtLeast(360)
        val heightPt = ((mediaSize.heightMils / 1000f) * 72f).roundToInt().coerceAtLeast(480)

        val oldMedia = oldAttributes?.mediaSize
        val sizeChanged = oldMedia == null ||
            oldMedia.widthMils != mediaSize.widthMils ||
            oldMedia.heightMils != mediaSize.heightMils ||
            currentPlan == null

        val plan = runCatching {
            DeedPdfRenderer.planDocument(
                context = context,
                deed = deed,
                language = language,
                pageWidthPt = widthPt,
                pageHeightPt = heightPt
            )
        }.getOrElse { err ->
            return LayoutOutcome.Failed(err.message ?: "Unable to lay out document")
        }

        if (cancellationSignal?.isCanceled == true) {
            return LayoutOutcome.Cancelled
        }

        currentPlan = plan

        val docInfo = PrintDocumentInfo.Builder(DeedDocumentBuilder.suggestedPdfFileName(deed))
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(plan.pages.size)
            .build()

        return LayoutOutcome.Finished(docInfo, sizeChanged)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        when (val outcome = performWrite(pages, destination, cancellationSignal)) {
            is WriteOutcome.Cancelled -> callback.onWriteCancelled()
            is WriteOutcome.Failed -> callback.onWriteFailed(outcome.message)
            is WriteOutcome.Finished -> callback.onWriteFinished(outcome.writtenRanges)
        }
    }

    internal fun performWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?
    ): WriteOutcome {
        if (cancellationSignal?.isCanceled == true) {
            return WriteOutcome.Cancelled
        }

        val plan = currentPlan ?: runCatching {
            DeedPdfRenderer.planDocument(context, deed, language)
        }.getOrElse { err ->
            return WriteOutcome.Failed(err.message ?: "Missing document layout")
        }

        val totalPages = plan.pages.size
        val selectedIndices = (0 until totalPages).filter { idx ->
            containsPage(pages, idx)
        }

        return try {
            var completed = false
            FileOutputStream(destination.fileDescriptor).use { out ->
                completed = backend.writePages(
                    plan = plan,
                    pageIndices = selectedIndices,
                    outputStream = out,
                    isCancelled = { cancellationSignal?.isCanceled == true }
                )
            }
            if (!completed || cancellationSignal?.isCanceled == true) {
                return WriteOutcome.Cancelled
            }

            val writtenRanges = if (selectedIndices.size == totalPages) {
                arrayOf(PageRange.ALL_PAGES)
            } else {
                selectedIndices.map { PageRange(it, it) }.toTypedArray()
            }
            WriteOutcome.Finished(writtenRanges)
        } catch (t: Throwable) {
            WriteOutcome.Failed(t.message ?: "Failed to write print output")
        }
    }

    private fun containsPage(ranges: Array<out PageRange>, zeroBasedPage: Int): Boolean {
        for (range in ranges) {
            if (range == PageRange.ALL_PAGES) return true
            if (zeroBasedPage >= range.start && zeroBasedPage <= range.end) return true
        }
        return false
    }
}

object DeedPrintHelper {
    fun defaultA4PrintAttributes(): PrintAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()

    fun launchPrintJob(
        context: Context,
        deed: DeedRecord,
        language: AppLanguage
    ): Boolean {
        val hostActivity = context.findActivity()
        val printManager = (hostActivity ?: context).getSystemService(PrintManager::class.java)
            ?: return false

        val jobName = DeedDocumentBuilder.suggestedPdfFileName(deed).removeSuffix(".pdf")
        val adapter = DeedPrintDocumentAdapter(
            context = hostActivity ?: context,
            deed = deed,
            language = language
        )
        return runCatching {
            printManager.print(jobName, adapter, defaultA4PrintAttributes())
        }.isSuccess
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
