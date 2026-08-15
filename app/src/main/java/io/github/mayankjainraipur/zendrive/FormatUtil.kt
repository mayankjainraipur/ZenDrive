package io.github.mayankjainraipur.zendrive

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Defaults come from [UserPrefs], so a call with no explicit unit or pattern honours whatever the
 * user chose in settings. Pass an argument only to deliberately override that.
 */
object FormatUtil {

    fun formatCurrency(amount: Double, currencyCode: String = UserPrefs.currencyCode): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        return "$currencyCode ${nf.format(amount)}"
    }

    fun formatDistance(value: Double, unit: String = UserPrefs.distanceUnit): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 0
        }
        return "${nf.format(value)} $unit"
    }

    fun formatDate(epochMillis: Long, pattern: String = UserPrefs.dateFormatPattern): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    /** The user's date pattern with a clock time appended — for backup and restore timestamps. */
    fun formatDateTime(epochMillis: Long): String {
        val sdf = SimpleDateFormat("${UserPrefs.dateFormatPattern}, HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }
}
