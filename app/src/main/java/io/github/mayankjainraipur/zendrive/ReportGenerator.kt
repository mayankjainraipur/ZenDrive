package io.github.mayankjainraipur.zendrive

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Per-vehicle reports, as CSV for a spreadsheet and PDF for handing to someone.
 *
 * PDF uses the platform's own PdfDocument rather than a library — the layout here is a title,
 * a summary block and a table, which is well within what a Canvas can draw directly.
 */
object ReportGenerator {

    /** The figures a report leads with; also what a buyer asks for. */
    data class Summary(
        val vehicle: Vehicle,
        val fromMillis: Long,
        val toMillis: Long,
        val eventCount: Int,
        val totalSpend: Double,
        val spendByCategory: List<CategoryExpense>,
        val distanceCovered: Double?,
        val costPerDistance: Double?,
        val avgMileage: Double?,
        val ownershipDays: Long?,
        val costPerDay: Double?
    )

    suspend fun buildSummary(
        db: AppDatabase,
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long
    ): Summary? = withContext(Dispatchers.IO) {
        val vehicle = db.vehicleDao().getVehicleById(vehicleId) ?: return@withContext null
        val eventDao = db.vehicleEventDao()

        val events = eventDao.getExpensesForVehicleInRange(vehicleId, fromMillis, toMillis)
        val totalSpend = events.sumOf { it.cost ?: 0.0 }
        val categories = eventDao.getExpensesByCategory(vehicleId, fromMillis, toMillis)
            .sortedByDescending { it.totalCost }

        // Distance across the window, taken from the odometer log rather than the vehicle's
        // current reading, so a report for last year is not measured against today.
        val readings = db.odometerLogDao().getForVehicleAsc(vehicleId)
            .filter { it.recordedAt in fromMillis..toMillis }
        val distance = if (readings.size >= 2) {
            (readings.last().reading - readings.first().reading).takeIf { it > 0 }
        } else {
            null
        }

        val ownershipDays = vehicle.purchaseDate?.let {
            TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it).takeIf { d -> d > 0 }
        }

        Summary(
            vehicle = vehicle,
            fromMillis = fromMillis,
            toMillis = toMillis,
            eventCount = events.size,
            totalSpend = totalSpend,
            spendByCategory = categories,
            distanceCovered = distance,
            costPerDistance = distance?.let { totalSpend / it },
            avgMileage = null,
            ownershipDays = ownershipDays,
            costPerDay = ownershipDays?.let { totalSpend / it }
        )
    }

    // ─── CSV ─────────────────────────────────────────────────────────────────

    suspend fun writeCsv(
        db: AppDatabase,
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
        out: OutputStream
    ) = withContext(Dispatchers.IO) {
        val events = db.vehicleEventDao()
            .getEventsForVehicle(vehicleId)
            .filter { it.date in fromMillis..toMillis }
            .sortedBy { it.date }

        val unit = UserPrefs.distanceUnit
        val builder = StringBuilder()
        builder.appendLine(
            listOf(
                "Date", "Type", "Title", "Description", "Cost",
                "Odometer ($unit)", "Fuel volume", "Price per unit", "Full tank", "Next due"
            ).joinToString(",")
        )

        for (event in events) {
            builder.appendLine(
                listOf(
                    FormatUtil.formatDate(event.date),
                    event.eventType,
                    event.title,
                    event.description.orEmpty(),
                    event.cost?.toString().orEmpty(),
                    event.odometer?.toString().orEmpty(),
                    event.fuelVolume?.toString().orEmpty(),
                    event.pricePerUnit?.toString().orEmpty(),
                    if (event.isFullTank) "yes" else "",
                    event.nextDueDate?.let { FormatUtil.formatDate(it) }.orEmpty()
                ).joinToString(",") { escapeCsv(it) }
            )
        }

        out.write(builder.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Quotes a field when it contains a comma, quote or newline, doubling any embedded quote.
     * Without this a description containing a comma silently shifts every later column.
     */
    private fun escapeCsv(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    // ─── PDF ─────────────────────────────────────────────────────────────────

    private const val PAGE_WIDTH = 595   // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    suspend fun writePdf(
        db: AppDatabase,
        context: Context,
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
        out: OutputStream
    ) = withContext(Dispatchers.IO) {
        val summary = buildSummary(db, vehicleId, fromMillis, toMillis) ?: return@withContext
        val events = db.vehicleEventDao()
            .getEventsForVehicle(vehicleId)
            .filter { it.date in fromMillis..toMillis }
            .sortedBy { it.date }

        val document = PdfDocument()
        // Black on white regardless of app theme: this is going to a printer or another person.
        val title = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val heading = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 10f }
        val muted = Paint().apply { textSize = 10f; color = android.graphics.Color.DKGRAY }
        val rule = Paint().apply { strokeWidth = 0.5f; color = android.graphics.Color.LTGRAY }

        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var canvas = page.canvas
        var y = MARGIN + 10f

        fun newPageIfNeeded(needed: Float) {
            if (y + needed < PAGE_HEIGHT - MARGIN) return
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            y = MARGIN
        }

        val vehicle = summary.vehicle
        canvas.drawText(vehicle.name, MARGIN, y, title)
        y += 18f
        canvas.drawText(
            "${vehicle.vehicleNumber} · ${vehicle.brand} ${vehicle.model} · ${vehicle.year}",
            MARGIN, y, muted
        )
        y += 14f
        canvas.drawText(
            "${FormatUtil.formatDate(summary.fromMillis)} — ${FormatUtil.formatDate(summary.toMillis)}",
            MARGIN, y, muted
        )
        y += 22f

        canvas.drawText("Summary", MARGIN, y, heading)
        y += 16f

        val unit = UserPrefs.distanceUnit
        val lines = buildList {
            add("Records" to summary.eventCount.toString())
            add("Total spend" to FormatUtil.formatCurrency(summary.totalSpend))
            summary.distanceCovered?.let {
                add("Distance covered" to FormatUtil.formatDistance(it))
            }
            summary.costPerDistance?.let {
                add("Cost per $unit" to FormatUtil.formatCurrency(it))
            }
            summary.costPerDay?.let {
                add("Cost per day owned" to FormatUtil.formatCurrency(it))
            }
            add("Odometer now" to FormatUtil.formatDistance(vehicle.odometerReading))
        }
        for ((label, value) in lines) {
            newPageIfNeeded(14f)
            canvas.drawText(label, MARGIN, y, body)
            canvas.drawText(value, MARGIN + 200f, y, body)
            y += 14f
        }
        y += 10f

        if (summary.spendByCategory.isNotEmpty()) {
            newPageIfNeeded(30f)
            canvas.drawText("By category", MARGIN, y, heading)
            y += 16f
            for (category in summary.spendByCategory) {
                newPageIfNeeded(14f)
                canvas.drawText(
                    category.eventType.replaceFirstChar { it.uppercase() }, MARGIN, y, body
                )
                canvas.drawText(
                    FormatUtil.formatCurrency(category.totalCost), MARGIN + 200f, y, body
                )
                y += 14f
            }
            y += 10f
        }

        newPageIfNeeded(30f)
        canvas.drawText("Records", MARGIN, y, heading)
        y += 16f
        canvas.drawLine(MARGIN, y - 10f, PAGE_WIDTH - MARGIN, y - 10f, rule)

        for (event in events) {
            newPageIfNeeded(14f)
            canvas.drawText(FormatUtil.formatDate(event.date), MARGIN, y, body)
            canvas.drawText(event.eventType, MARGIN + 90f, y, body)
            canvas.drawText(truncate(event.title, 34), MARGIN + 160f, y, body)
            event.cost?.let {
                canvas.drawText(FormatUtil.formatCurrency(it), MARGIN + 380f, y, body)
            }
            event.odometer?.let {
                canvas.drawText(
                    String.format(Locale.getDefault(), "%,.0f", it), MARGIN + 470f, y, muted
                )
            }
            y += 14f
        }

        document.finishPage(page)
        document.writeTo(out)
        document.close()
    }

    private fun truncate(value: String, max: Int) =
        if (value.length <= max) value else value.take(max - 1) + "…"

    /** Start and end of a calendar year. */
    fun yearRange(year: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            add(Calendar.YEAR, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return start.timeInMillis to end.timeInMillis
    }
}
