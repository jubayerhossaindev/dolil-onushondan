package com.example.core.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.DeedRecord
import com.example.domain.model.DeedStatus
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private object PrintPalette {
    val pageBg = Color.WHITE
    val inkPrimary = Color.parseColor("#27364B")
    val inkBody = Color.parseColor("#1E2022")
    val inkMuted = Color.parseColor("#5A554A")
    val goldAccent = Color.parseColor("#C69A45")
    val headerFill = Color.parseColor("#F5F3EE")
    val zebraFill = Color.parseColor("#FAF9F6")
    val border = Color.parseColor("#D6D1C6")
    val borderLight = Color.parseColor("#E6E1D6")

    val statusApprovedFg = Color.parseColor("#246B4A")
    val statusApprovedBg = Color.parseColor("#DCEFE3")
    val statusPendingFg = Color.parseColor("#875A05")
    val statusPendingBg = Color.parseColor("#F6E7BE")
    val statusDraftFg = Color.parseColor("#315D8A")
    val statusDraftBg = Color.parseColor("#DDEAF7")
    val statusArchivedFg = Color.parseColor("#5B6470")
    val statusArchivedBg = Color.parseColor("#E7EAEE")
}

private class PrintFonts(context: Context, language: AppLanguage) {
    val headingFace: Typeface = runCatching {
        ResourcesCompat.getFont(context, R.font.hind_siliguri)
    }.getOrNull() ?: Typeface.DEFAULT

    val bodyFace: Typeface = runCatching {
        val primaryRes = if (language == AppLanguage.BN) R.font.noto_sans_bengali else R.font.plus_jakarta_sans
        ResourcesCompat.getFont(context, primaryRes)
            ?: ResourcesCompat.getFont(context, R.font.noto_sans_bengali)
    }.getOrNull() ?: Typeface.DEFAULT

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = headingFace
        isFakeBoldText = true
        textSize = 15.5f
        color = PrintPalette.inkPrimary
    }
    val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFace
        textSize = 9.5f
        color = PrintPalette.inkMuted
    }
    val kickerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFace
        isFakeBoldText = true
        textSize = 8.5f
        color = PrintPalette.inkPrimary
    }
    val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = headingFace
        isFakeBoldText = true
        textSize = 11f
        color = PrintPalette.inkPrimary
    }
    val subHeadingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = headingFace
        isFakeBoldText = true
        textSize = 9.5f
        color = PrintPalette.inkPrimary
    }
    val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFace
        isFakeBoldText = true
        textSize = 8f
        color = PrintPalette.inkMuted
    }
    val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFace
        textSize = 9.5f
        color = PrintPalette.inkBody
    }
    val tableHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFace
        isFakeBoldText = true
        textSize = 8.2f
        color = PrintPalette.inkPrimary
    }
    val tableCellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFace
        textSize = 8.4f
        color = PrintPalette.inkBody
    }
    val summaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = headingFace
        isFakeBoldText = true
        textSize = 9.8f
        color = PrintPalette.inkPrimary
    }
    val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFace
        textSize = 7.8f
        color = PrintPalette.inkMuted
    }
    val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bodyFace
        isFakeBoldText = true
        textSize = 8.2f
    }
}

sealed interface DocumentBlock {
    val heightPt: Float
    val keepWithNext: Boolean
    val isParcelTableHeader: Boolean get() = false
    val isParcelTableRow: Boolean get() = false
    fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float)
}

private class MastheadBlock(
    private val data: DeedDocumentData,
    private val fonts: PrintFonts,
    contentWidth: Float
) : DocumentBlock {
    private val appLayout = buildLayout(data.appTitle, fonts.kickerPaint, (contentWidth * 0.65f).toInt())
    private val officeLayout = buildLayout(data.officeName, fonts.subtitlePaint, (contentWidth * 0.65f).toInt())
    private val titleLayout = buildLayout(data.documentTitle, fonts.titlePaint, contentWidth.toInt())
    private val deedNoLayout = buildLayout(data.deedNumberDisplay, fonts.subHeadingPaint, (contentWidth * 0.60f).toInt())
    private val regDateLayout = buildLayout(data.regDateText, fonts.subtitlePaint, (contentWidth * 0.38f).toInt())

    private val statusFg: Int
    private val statusBg: Int

    init {
        val (fg, bg) = when (data.status) {
            DeedStatus.APPROVED -> PrintPalette.statusApprovedFg to PrintPalette.statusApprovedBg
            DeedStatus.PENDING_REVIEW -> PrintPalette.statusPendingFg to PrintPalette.statusPendingBg
            DeedStatus.DRAFT -> PrintPalette.statusDraftFg to PrintPalette.statusDraftBg
            DeedStatus.SOFT_DELETED -> PrintPalette.statusArchivedFg to PrintPalette.statusArchivedBg
        }
        statusFg = fg
        statusBg = bg
    }

    private val statusTextWidth = fonts.statusPaint.measureText(data.statusText)
    private val headerTopHeight = max(
        appLayout.height + 2f + officeLayout.height,
        22f
    )
    private val stripHeight = max(deedNoLayout.height, regDateLayout.height) + 14f

    override val heightPt: Float = headerTopHeight + 6f + titleLayout.height + 8f + stripHeight + 14f
    override val keepWithNext: Boolean = false

    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) {
        var cursorY = y

        // Top row: App + Office on left, Status pill on right
        drawLayout(canvas, appLayout, x, cursorY)
        drawLayout(canvas, officeLayout, x, cursorY + appLayout.height + 2f)

        val pillPadH = 10f
        val pillW = statusTextWidth + pillPadH * 2f
        val pillH = 19f
        val pillRect = RectF(x + contentWidth - pillW, cursorY, x + contentWidth, cursorY + pillH)
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = statusBg }
        canvas.drawRoundRect(pillRect, 5f, 5f, pillBgPaint)

        fonts.statusPaint.color = statusFg
        val baselineY = pillRect.top + (pillH - (fonts.statusPaint.descent() + fonts.statusPaint.ascent())) / 2f
        canvas.drawText(data.statusText, pillRect.left + pillPadH, baselineY, fonts.statusPaint)

        cursorY += headerTopHeight + 6f

        // Document title
        drawLayout(canvas, titleLayout, x, cursorY)
        cursorY += titleLayout.height + 6f

        // Subtle Antique Gold rule
        val goldPaint = Paint().apply {
            color = PrintPalette.goldAccent
            strokeWidth = 1.6f
        }
        canvas.drawLine(x, cursorY, x + contentWidth, cursorY, goldPaint)
        cursorY += 8f

        // Info strip: Deed Number on left, Registration Date on right
        val stripRect = RectF(x, cursorY, x + contentWidth, cursorY + stripHeight)
        val stripFillPaint = Paint().apply { color = PrintPalette.headerFill }
        val borderPaint = Paint().apply {
            color = PrintPalette.border
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        canvas.drawRoundRect(stripRect, 4f, 4f, stripFillPaint)
        canvas.drawRoundRect(stripRect, 4f, 4f, borderPaint)

        val innerY = cursorY + (stripHeight - deedNoLayout.height) / 2f
        drawLayout(canvas, deedNoLayout, x + 10f, innerY)
        val dateX = x + contentWidth - 10f - regDateLayout.getLineWidth(0).coerceAtMost(contentWidth * 0.38f)
        val dateY = cursorY + (stripHeight - regDateLayout.height) / 2f
        drawLayout(canvas, regDateLayout, dateX, dateY)
    }
}

private class SectionHeaderBlock(
    title: String,
    private val fonts: PrintFonts,
    contentWidth: Float
) : DocumentBlock {
    private val titleLayout = buildLayout(title, fonts.sectionTitlePaint, (contentWidth - 16f).toInt())
    override val heightPt: Float = titleLayout.height + 14f
    override val keepWithNext: Boolean = true

    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) {
        val barTop = y + 4f
        val barBottom = barTop + titleLayout.height + 6f
        val fillPaint = Paint().apply { color = PrintPalette.headerFill }
        val accentPaint = Paint().apply { color = PrintPalette.inkPrimary }
        canvas.drawRect(x, barTop, x + contentWidth, barBottom, fillPaint)
        canvas.drawRect(x, barTop, x + 3f, barBottom, accentPaint)
        drawLayout(canvas, titleLayout, x + 10f, barTop + 3f)
    }
}

private class TwoColGridRowBlock(
    left: DocumentKeyValue,
    right: DocumentKeyValue?,
    fonts: PrintFonts,
    contentWidth: Float,
    private val drawBottomDivider: Boolean = true
) : DocumentBlock {
    private val gap = 16f
    private val colWidth = ((contentWidth - gap) / 2f).toInt().coerceAtLeast(60)
    private val leftLabelLayout = buildLayout(left.label, fonts.labelPaint, colWidth)
    private val leftValueLayout = buildLayout(left.value, fonts.valuePaint, colWidth)
    private val rightLabelLayout = right?.let { buildLayout(it.label, fonts.labelPaint, colWidth) }
    private val rightValueLayout = right?.let { buildLayout(it.value, fonts.valuePaint, colWidth) }

    private val leftHeight = leftLabelLayout.height + 1.5f + leftValueLayout.height
    private val rightHeight = if (rightLabelLayout != null && rightValueLayout != null) {
        rightLabelLayout.height + 1.5f + rightValueLayout.height
    } else 0f

    override val heightPt: Float = max(leftHeight, rightHeight) + 10f
    override val keepWithNext: Boolean = false

    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) {
        val padTop = 4f
        drawLayout(canvas, leftLabelLayout, x, y + padTop)
        drawLayout(canvas, leftValueLayout, x, y + padTop + leftLabelLayout.height + 1.5f)

        if (rightLabelLayout != null && rightValueLayout != null) {
            val rightX = x + colWidth + gap
            drawLayout(canvas, rightLabelLayout, rightX, y + padTop)
            drawLayout(canvas, rightValueLayout, rightX, y + padTop + rightLabelLayout.height + 1.5f)
        }

        if (drawBottomDivider) {
            val linePaint = Paint().apply {
                color = PrintPalette.borderLight
                strokeWidth = 0.5f
            }
            canvas.drawLine(x, y + heightPt - 1f, x + contentWidth, y + heightPt - 1f, linePaint)
        }
    }
}

private class FullWidthFieldBlock(
    item: DocumentKeyValue,
    fonts: PrintFonts,
    contentWidth: Float,
    private val drawBottomDivider: Boolean = true
) : DocumentBlock {
    private val labelLayout = buildLayout(item.label, fonts.labelPaint, contentWidth.toInt())
    private val valueLayout = buildLayout(item.value, fonts.valuePaint, contentWidth.toInt())

    override val heightPt: Float = labelLayout.height + 2f + valueLayout.height + 10f
    override val keepWithNext: Boolean = false

    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) {
        drawLayout(canvas, labelLayout, x, y + 4f)
        drawLayout(canvas, valueLayout, x, y + 4f + labelLayout.height + 2f)
        if (drawBottomDivider) {
            val linePaint = Paint().apply {
                color = PrintPalette.borderLight
                strokeWidth = 0.5f
            }
            canvas.drawLine(x, y + heightPt - 1f, x + contentWidth, y + heightPt - 1f, linePaint)
        }
    }
}

private class PartySubHeaderBlock(
    heading: String,
    fonts: PrintFonts,
    contentWidth: Float
) : DocumentBlock {
    private val headingLayout = buildLayout(heading, fonts.subHeadingPaint, contentWidth.toInt())
    override val heightPt: Float = headingLayout.height + 8f
    override val keepWithNext: Boolean = true

    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) {
        drawLayout(canvas, headingLayout, x, y + 4f)
        val underlinePaint = Paint().apply {
            color = PrintPalette.border
            strokeWidth = 0.75f
        }
        canvas.drawLine(x, y + heightPt - 1.5f, x + contentWidth, y + heightPt - 1.5f, underlinePaint)
    }
}

private class ParcelTableHeaderBlock(
    headers: List<String>,
    private val colWidths: List<Float>,
    fonts: PrintFonts
) : DocumentBlock {
    private val padH = 5f
    private val padV = 5f
    private val layouts = headers.mapIndexed { idx, text ->
        val w = (colWidths[idx] - padH * 2f).toInt().coerceAtLeast(16)
        buildLayout(text, fonts.tableHeaderPaint, w)
    }
    private val maxTextH = layouts.maxOfOrNull { it.height.toFloat() } ?: 12f
    override val heightPt: Float = maxTextH + padV * 2f + 2f
    override val keepWithNext: Boolean = true
    override val isParcelTableHeader: Boolean = true

    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) {
        val top = y + 2f
        val bottom = y + heightPt
        val fillPaint = Paint().apply { color = PrintPalette.headerFill }
        val borderPaint = Paint().apply {
            color = PrintPalette.border
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        canvas.drawRect(x, top, x + contentWidth, bottom, fillPaint)
        canvas.drawRect(x, top, x + contentWidth, bottom, borderPaint)

        var colX = x
        layouts.forEachIndexed { idx, layout ->
            if (idx > 0) {
                canvas.drawLine(colX, top, colX, bottom, borderPaint)
            }
            val textY = top + (bottom - top - layout.height) / 2f
            drawLayout(canvas, layout, colX + padH, textY)
            colX += colWidths[idx]
        }
    }
}

private class ParcelTableRowBlock(
    row: DocumentParcelRow,
    private val colWidths: List<Float>,
    private val isEvenRow: Boolean,
    fonts: PrintFonts
) : DocumentBlock {
    private val padH = 5f
    private val padV = 5f
    private val cellTexts = listOf(
        row.indexText,
        row.surveyText,
        row.khatianText,
        row.dagText,
        row.landClassText,
        row.areaText,
        row.boundaryAndNotesText
    )
    private val layouts = cellTexts.mapIndexed { idx, text ->
        val w = (colWidths[idx] - padH * 2f).toInt().coerceAtLeast(16)
        buildLayout(text, fonts.tableCellPaint, w)
    }
    private val maxTextH = layouts.maxOfOrNull { it.height.toFloat() } ?: 14f

    override val heightPt: Float = maxTextH + padV * 2f
    override val keepWithNext: Boolean = false
    override val isParcelTableRow: Boolean = true

    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) {
        val top = y
        val bottom = y + heightPt
        if (isEvenRow) {
            val zebraPaint = Paint().apply { color = PrintPalette.zebraFill }
            canvas.drawRect(x, top, x + contentWidth, bottom, zebraPaint)
        }
        val borderPaint = Paint().apply {
            color = PrintPalette.border
            style = Paint.Style.STROKE
            strokeWidth = 0.65f
        }
        canvas.drawRect(x, top, x + contentWidth, bottom, borderPaint)

        var colX = x
        layouts.forEachIndexed { idx, layout ->
            if (idx > 0) {
                canvas.drawLine(colX, top, colX, bottom, borderPaint)
            }
            drawLayout(canvas, layout, colX + padH, top + padV)
            colX += colWidths[idx]
        }
    }
}

private class TotalAreaBannerBlock(
    summaryText: String,
    fonts: PrintFonts,
    contentWidth: Float
) : DocumentBlock {
    private val summaryLayout = buildLayout(summaryText, fonts.summaryPaint, (contentWidth - 20f).toInt())
    override val heightPt: Float = summaryLayout.height + 20f
    override val keepWithNext: Boolean = false

    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) {
        val top = y + 6f
        val bottom = top + summaryLayout.height + 12f
        val rect = RectF(x, top, x + contentWidth, bottom)
        val fillPaint = Paint().apply { color = PrintPalette.headerFill }
        val borderPaint = Paint().apply {
            color = PrintPalette.inkPrimary
            style = Paint.Style.STROKE
            strokeWidth = 0.85f
        }
        canvas.drawRoundRect(rect, 4f, 4f, fillPaint)
        canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
        drawLayout(canvas, summaryLayout, x + 10f, top + 6f)
    }
}

private class SpacerBlock(override val heightPt: Float) : DocumentBlock {
    override val keepWithNext: Boolean = false
    override fun draw(canvas: Canvas, x: Float, y: Float, contentWidth: Float) = Unit
}

data class PositionedBlock(
    val block: DocumentBlock,
    val y: Float
)

data class PaginatedPage(
    val pageNumber: Int,
    val blocks: List<PositionedBlock>
)

data class PaginatedDocumentPlan(
    val pageWidthPt: Int,
    val pageHeightPt: Int,
    val contentWidthPt: Float,
    val data: DeedDocumentData,
    val pages: List<PaginatedPage>,
    private val metaPaint: TextPaint
) {
    fun drawPage(canvas: Canvas, pageIndex: Int) {
        val page = pages.getOrNull(pageIndex) ?: return
        val totalPages = pages.size
        val contentX = A4Spec.MARGIN_HORIZONTAL_PT

        // Clean white printable surface
        canvas.drawColor(PrintPalette.pageBg)

        // Running Header on Page 2+
        if (page.pageNumber > 1) {
            val headerY = A4Spec.MARGIN_TOP_PT
            val leftLayout = buildLayout(
                data.runningHeaderLeft,
                metaPaint,
                (contentWidthPt * 0.56f).toInt()
            )
            val rightLayout = buildLayout(
                data.runningHeaderRight,
                metaPaint,
                (contentWidthPt * 0.40f).toInt(),
                Layout.Alignment.ALIGN_OPPOSITE
            )
            drawLayout(canvas, leftLayout, contentX, headerY)
            drawLayout(
                canvas,
                rightLayout,
                contentX + contentWidthPt - (contentWidthPt * 0.40f),
                headerY
            )
            val lineY = headerY + max(leftLayout.height, rightLayout.height) + 4f
            val dividerPaint = Paint().apply {
                color = PrintPalette.border
                strokeWidth = 0.65f
            }
            canvas.drawLine(contentX, lineY, contentX + contentWidthPt, lineY, dividerPaint)
        }

        // Body Blocks
        page.blocks.forEach { positioned ->
            positioned.block.draw(canvas, contentX, positioned.y, contentWidthPt)
        }

        // Footer on all pages (Page 1..N)
        val footerTop = pageHeightPt - A4Spec.MARGIN_BOTTOM_PT - A4Spec.FOOTER_HEIGHT_PT
        val dividerPaint = Paint().apply {
            color = PrintPalette.border
            strokeWidth = 0.65f
        }
        canvas.drawLine(contentX, footerTop, contentX + contentWidthPt, footerTop, dividerPaint)

        val footerLeftLayout = buildLayout(
            data.footerLeftText,
            metaPaint,
            (contentWidthPt * 0.68f).toInt()
        )
        val pageLabel = DeedDocumentBuilder.formatPageIndicator(
            language = data.language,
            pageNumber = page.pageNumber,
            totalPages = totalPages
        )
        val footerRightLayout = buildLayout(
            pageLabel,
            metaPaint,
            (contentWidthPt * 0.28f).toInt(),
            Layout.Alignment.ALIGN_OPPOSITE
        )
        val textY = footerTop + 6f
        drawLayout(canvas, footerLeftLayout, contentX, textY)
        drawLayout(
            canvas,
            footerRightLayout,
            contentX + contentWidthPt - (contentWidthPt * 0.28f),
            textY
        )
    }
}

object DeedPdfRenderer {

    private val parcelColFractions = listOf(0.055f, 0.095f, 0.130f, 0.120f, 0.150f, 0.185f, 0.265f)

    fun planDocument(
        context: Context,
        deed: DeedRecord,
        language: AppLanguage,
        pageWidthPt: Int = A4Spec.WIDTH_PT,
        pageHeightPt: Int = A4Spec.HEIGHT_PT,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): PaginatedDocumentPlan {
        val safeW = pageWidthPt.coerceAtLeast(360)
        val safeH = pageHeightPt.coerceAtLeast(480)
        val contentWidth = (safeW - A4Spec.MARGIN_HORIZONTAL_PT * 2f).coerceAtLeast(240f)
        val fonts = PrintFonts(context, language)
        val data = DeedDocumentBuilder.build(deed, language, generatedAtMillis)
        val colWidths = parcelColFractions.map { it * contentWidth }

        val elements = buildList {
            // Masthead
            add(MastheadBlock(data, fonts, contentWidth))
            add(SpacerBlock(4f))

            // Section A: General Info & Notes
            add(SectionHeaderBlock(data.generalSectionTitle, fonts, contentWidth))
            data.generalGridRows.forEachIndexed { idx, (left, right) ->
                val isLastGridRow = idx == data.generalGridRows.lastIndex
                add(TwoColGridRowBlock(left, right, fonts, contentWidth, drawBottomDivider = !isLastGridRow))
            }
            add(FullWidthFieldBlock(data.notesField, fonts, contentWidth, drawBottomDivider = false))
            add(SpacerBlock(6f))

            // Section B: Grantors
            add(SectionHeaderBlock(data.grantorsSectionTitle, fonts, contentWidth))
            data.grantorSections.forEachIndexed { pIdx, party ->
                if (pIdx > 0) add(SpacerBlock(4f))
                add(PartySubHeaderBlock(party.heading, fonts, contentWidth))
                party.gridRows.forEach { (left, right) ->
                    add(TwoColGridRowBlock(left, right, fonts, contentWidth, drawBottomDivider = true))
                }
                add(FullWidthFieldBlock(party.address, fonts, contentWidth, drawBottomDivider = true))
                add(FullWidthFieldBlock(party.aliases, fonts, contentWidth, drawBottomDivider = false))
            }
            add(SpacerBlock(6f))

            // Section C: Grantees
            add(SectionHeaderBlock(data.granteesSectionTitle, fonts, contentWidth))
            data.granteeSections.forEachIndexed { pIdx, party ->
                if (pIdx > 0) add(SpacerBlock(4f))
                add(PartySubHeaderBlock(party.heading, fonts, contentWidth))
                party.gridRows.forEach { (left, right) ->
                    add(TwoColGridRowBlock(left, right, fonts, contentWidth, drawBottomDivider = true))
                }
                add(FullWidthFieldBlock(party.address, fonts, contentWidth, drawBottomDivider = true))
                add(FullWidthFieldBlock(party.aliases, fonts, contentWidth, drawBottomDivider = false))
            }
            add(SpacerBlock(6f))

            // Section D: Location
            add(SectionHeaderBlock(data.locationSectionTitle, fonts, contentWidth))
            data.locationGridRows.forEachIndexed { idx, (left, right) ->
                val isLast = idx == data.locationGridRows.lastIndex
                add(TwoColGridRowBlock(left, right, fonts, contentWidth, drawBottomDivider = !isLast))
            }
            add(SpacerBlock(6f))

            // Section E: Land & Parcel Table
            add(SectionHeaderBlock(data.landSectionTitle, fonts, contentWidth))
            add(ParcelTableHeaderBlock(data.parcelHeaders, colWidths, fonts))
            data.parcelRows.forEachIndexed { idx, row ->
                add(ParcelTableRowBlock(row, colWidths, isEvenRow = idx % 2 == 1, fonts))
            }
            add(TotalAreaBannerBlock(data.totalAreaSummaryText, fonts, contentWidth))
        }

        val pages = paginate(
            elements = elements,
            pageHeightPt = safeH,
            repeatTableHeaderFactory = { ParcelTableHeaderBlock(data.parcelHeaders, colWidths, fonts) }
        )

        return PaginatedDocumentPlan(
            pageWidthPt = safeW,
            pageHeightPt = safeH,
            contentWidthPt = contentWidth,
            data = data,
            pages = pages,
            metaPaint = fonts.metaPaint
        )
    }

    private fun paginate(
        elements: List<DocumentBlock>,
        pageHeightPt: Int,
        repeatTableHeaderFactory: () -> DocumentBlock
    ): List<PaginatedPage> {
        val pages = mutableListOf<PaginatedPage>()
        val usableBottom = pageHeightPt - A4Spec.MARGIN_BOTTOM_PT - A4Spec.FOOTER_HEIGHT_PT - 8f

        fun startPageTop(pageNum: Int): Float =
            if (pageNum == 1) A4Spec.MARGIN_TOP_PT
            else A4Spec.MARGIN_TOP_PT + A4Spec.RUNNING_HEADER_HEIGHT_PT + 8f

        var currentPageNum = 1
        var cursorY = startPageTop(currentPageNum)
        var currentBlocks = mutableListOf<PositionedBlock>()

        fun flushPage() {
            if (currentBlocks.isNotEmpty()) {
                pages.add(PaginatedPage(pageNumber = currentPageNum, blocks = currentBlocks))
                currentPageNum++
                cursorY = startPageTop(currentPageNum)
                currentBlocks = mutableListOf()
            }
        }

        var idx = 0
        while (idx < elements.size) {
            val block = elements[idx]

            // Skip leading spacer at top of fresh page
            if (currentBlocks.isEmpty() && block is SpacerBlock) {
                idx++
                continue
            }

            // If block keeps with next (e.g., SectionHeaderBlock / PartySubHeaderBlock / TableHeaderBlock),
            // check combined height with the following block (and even table header + first row!)
            var requiredHeight = block.heightPt
            if (block.keepWithNext && idx + 1 < elements.size) {
                val nextBlock = elements[idx + 1]
                requiredHeight += nextBlock.heightPt
                if (nextBlock.keepWithNext && idx + 2 < elements.size) {
                    requiredHeight += elements[idx + 2].heightPt
                }
            }

            if (currentBlocks.isNotEmpty() && cursorY + requiredHeight > usableBottom) {
                flushPage()
                if (block is SpacerBlock) {
                    idx++
                    continue
                }
                // If breaking inside the parcel table before a data row, repeat column headers on new page
                if (block.isParcelTableRow) {
                    val repeatedHeader = repeatTableHeaderFactory()
                    currentBlocks.add(PositionedBlock(repeatedHeader, cursorY))
                    cursorY += repeatedHeader.heightPt
                }
            }

            currentBlocks.add(PositionedBlock(block, cursorY))
            cursorY += block.heightPt
            idx++
        }

        flushPage()
        return pages
    }

    fun writePdfToStream(
        context: Context,
        deed: DeedRecord,
        language: AppLanguage,
        outputStream: OutputStream,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): Result<Int> = writePdfToStreamWithBackend(
        context = context,
        deed = deed,
        language = language,
        outputStream = outputStream,
        generatedAtMillis = generatedAtMillis,
        backend = AndroidPdfDocumentBackend
    )

    internal fun writePdfToStreamWithBackend(
        context: Context,
        deed: DeedRecord,
        language: AppLanguage,
        outputStream: OutputStream,
        generatedAtMillis: Long = System.currentTimeMillis(),
        backend: PdfExportBackend = AndroidPdfDocumentBackend
    ): Result<Int> = runCatching {
        val plan = planDocument(
            context = context,
            deed = deed,
            language = language,
            pageWidthPt = A4Spec.WIDTH_PT,
            pageHeightPt = A4Spec.HEIGHT_PT,
            generatedAtMillis = generatedAtMillis
        )
        val allPageIndices = plan.pages.indices.toList()
        backend.writePages(
            plan = plan,
            pageIndices = allPageIndices,
            outputStream = outputStream
        )
        plan.pages.size
    }
}

internal interface PdfExportBackend {
    fun writePages(
        plan: PaginatedDocumentPlan,
        pageIndices: List<Int>,
        outputStream: OutputStream,
        isCancelled: () -> Boolean = { false }
    ): Boolean
}

internal object AndroidPdfDocumentBackend : PdfExportBackend {
    override fun writePages(
        plan: PaginatedDocumentPlan,
        pageIndices: List<Int>,
        outputStream: OutputStream,
        isCancelled: () -> Boolean
    ): Boolean {
        val pdf = PdfDocument()
        try {
            pageIndices.forEach { pageIndex ->
                if (isCancelled()) return false
                val pageModel = plan.pages[pageIndex]
                val pageInfo = PdfDocument.PageInfo.Builder(
                    plan.pageWidthPt,
                    plan.pageHeightPt,
                    pageModel.pageNumber
                ).create()
                val pdfPage = pdf.startPage(pageInfo)
                plan.drawPage(pdfPage.canvas, pageIndex)
                pdf.finishPage(pdfPage)
            }
            if (isCancelled()) return false
            pdf.writeTo(outputStream)
            outputStream.flush()
            return true
        } finally {
            pdf.close()
        }
    }
}

private fun buildLayout(
    text: String,
    paint: TextPaint,
    widthPx: Int,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
): StaticLayout {
    val safeWidth = widthPx.coerceAtLeast(1)
    return StaticLayout.Builder
        .obtain(text, 0, text.length, paint, safeWidth)
        .setAlignment(alignment)
        .setLineSpacing(0f, 1.22f)
        .setIncludePad(true)
        .build()
}

private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
}
