package io.github.mayankjainraipur.zendrive

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Hand-drawn charts.
 *
 * A charting library would be a large dependency for four small charts in an app that
 * deliberately keeps its dependency list short, and none of these need interaction — they are
 * read, not explored.
 */

/** One labelled value; what every chart here consumes. */
data class ChartEntry(val label: String, val value: Double)

private fun Paint.pxText(context: Context, sp: Float) {
    textSize = sp * context.resources.displayMetrics.scaledDensity
}

private fun View.dp(value: Float) = value * resources.displayMetrics.density

/**
 * Vertical bars with value labels — for spend per month.
 *
 * Bars are drawn against the largest value rather than a fixed ceiling, so a quiet month is still
 * legible next to an expensive one.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var entries: List<ChartEntry> = emptyList()
    private var valueFormatter: (Double) -> String = { it.toInt().toString() }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }

    fun setData(data: List<ChartEntry>, formatter: (Double) -> String = valueFormatter) {
        entries = data
        valueFormatter = formatter
        applyThemeColours()
        requestLayout()
        invalidate()
    }

    private fun applyThemeColours() {
        barPaint.color = context.getColor(R.color.accent)
        labelPaint.color = context.getColor(R.color.text_hint)
        valuePaint.color = context.getColor(R.color.text_secondary)
        gridPaint.color = context.getColor(R.color.divider)
        labelPaint.pxText(context, 10f)
        valuePaint.pxText(context, 10f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, dp(180f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return
        applyThemeColours()

        val labelBand = dp(18f)
        val valueBand = dp(14f)
        val plotBottom = height - labelBand
        val plotTop = valueBand
        val plotHeight = plotBottom - plotTop
        if (plotHeight <= 0) return

        canvas.drawLine(0f, plotBottom, width.toFloat(), plotBottom, gridPaint)

        val maxValue = entries.maxOf { it.value }.takeIf { it > 0 } ?: return
        val slot = width.toFloat() / entries.size
        val barWidth = min(slot * 0.55f, dp(36f))
        val radius = dp(4f)

        entries.forEachIndexed { index, entry ->
            val centre = slot * index + slot / 2
            val barHeight = (entry.value / maxValue * plotHeight).toFloat()
            val top = plotBottom - barHeight

            // A zero month should still read as a month, so give it a visible stub.
            val drawTop = if (barHeight < dp(2f)) plotBottom - dp(2f) else top
            canvas.drawRoundRect(
                RectF(centre - barWidth / 2, drawTop, centre + barWidth / 2, plotBottom),
                radius, radius, barPaint
            )

            if (entry.value > 0) {
                canvas.drawText(
                    valueFormatter(entry.value), centre, max(drawTop - dp(4f), valuePaint.textSize),
                    valuePaint
                )
            }
            canvas.drawText(entry.label, centre, height - dp(4f), labelPaint)
        }
    }
}

/**
 * A horizontal proportion bar with a legend — for the category split.
 *
 * Chosen over a pie: comparing lengths is easier than comparing angles, and it degrades gracefully
 * when one category dominates.
 */
class CategoryBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var entries: List<ChartEntry> = emptyList()
    private var valueFormatter: (Double) -> String = { it.toInt().toString() }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }

    /** Distinguishable without relying on hue alone being meaningful; order is stable. */
    private val palette = listOf(
        "#80DBC4", "#74B9FF", "#FFC857", "#FF8A80", "#B39DDB", "#A8CDDC", "#C5E1A5"
    ).map { Color.parseColor(it) }

    fun setData(data: List<ChartEntry>, formatter: (Double) -> String = valueFormatter) {
        entries = data.filter { it.value > 0 }.sortedByDescending { it.value }
        valueFormatter = formatter
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = entries.size
        setMeasuredDimension(width, (dp(28f) + rows * dp(24f)).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        textPaint.color = context.getColor(R.color.text_secondary)
        valuePaint.color = context.getColor(R.color.text_primary)
        textPaint.pxText(context, 12f)
        valuePaint.pxText(context, 12f)

        val total = entries.sumOf { it.value }
        if (total <= 0) return

        val barHeight = dp(14f)
        val radius = dp(7f)
        var x = 0f

        // The proportion bar.
        canvas.save()
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, width.toFloat(), barHeight), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clip)
        entries.forEachIndexed { index, entry ->
            segmentPaint.color = palette[index % palette.size]
            val segmentWidth = (entry.value / total * width).toFloat()
            canvas.drawRect(x, 0f, x + segmentWidth, barHeight, segmentPaint)
            x += segmentWidth
        }
        canvas.restore()

        // The legend, which is what actually makes the bar readable.
        var y = barHeight + dp(20f)
        entries.forEachIndexed { index, entry ->
            segmentPaint.color = palette[index % palette.size]
            canvas.drawCircle(dp(5f), y - dp(4f), dp(4f), segmentPaint)
            canvas.drawText(entry.label, dp(16f), y, textPaint)
            canvas.drawText(valueFormatter(entry.value), width.toFloat(), y, valuePaint)
            y += dp(24f)
        }
    }
}

/**
 * A line with an area fill — for fuel price and mileage over time.
 *
 * The y-axis spans the data's own range rather than starting at zero: these series move within a
 * narrow band, and anchoring at zero would flatten every real change into a straight line.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Double> = emptyList()
    private var valueFormatter: (Double) -> String = { it.toInt().toString() }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setData(data: List<Double>, formatter: (Double) -> String = valueFormatter) {
        values = data
        valueFormatter = formatter
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(140f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return

        val accent = context.getColor(R.color.accent)
        linePaint.color = accent
        linePaint.strokeWidth = dp(2f)
        dotPaint.color = accent
        fillPaint.color = Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent))
        boundPaint.color = context.getColor(R.color.text_hint)
        boundPaint.pxText(context, 10f)

        val labelBand = dp(16f)
        val top = dp(10f)
        val bottom = height - labelBand
        val plotHeight = bottom - top
        val maxValue = values.max()
        val minValue = values.min()
        // A flat series has no range to scale by; centre it rather than dividing by zero.
        val span = (maxValue - minValue).takeIf { it > 0.0 }

        val stepX = width.toFloat() / (values.size - 1)
        fun yFor(value: Double): Float =
            if (span == null) top + plotHeight / 2
            else (bottom - ((value - minValue) / span * plotHeight)).toFloat()

        val line = Path()
        val area = Path()
        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = yFor(value)
            if (index == 0) {
                line.moveTo(x, y)
                area.moveTo(x, bottom)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(stepX * (values.size - 1), bottom)
        area.close()

        canvas.drawPath(area, fillPaint)
        canvas.drawPath(line, linePaint)

        // Emphasise the latest point — the one the reader is looking for.
        val lastX = stepX * (values.size - 1)
        canvas.drawCircle(lastX, yFor(values.last()), dp(3.5f), dotPaint)

        canvas.drawText(valueFormatter(maxValue), 0f, top - dp(1f) + boundPaint.textSize / 2, boundPaint)
        canvas.drawText(valueFormatter(minValue), 0f, height - dp(3f), boundPaint)
    }
}
