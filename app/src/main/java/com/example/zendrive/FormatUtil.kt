package com.example.zendrive

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtil {

    fun formatCurrency(amount: Double, currencyCode: String): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        return "$currencyCode ${nf.format(amount)}"
    }

    fun formatDistance(value: Double, unit: String = "km"): String {
        val nf = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 0
        }
        return "${nf.format(value)} $unit"
    }

    fun formatDate(epochMillis: Long, pattern: String = "dd MMM yyyy"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }
}
