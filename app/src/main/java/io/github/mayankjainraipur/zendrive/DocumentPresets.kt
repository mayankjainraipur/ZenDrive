package io.github.mayankjainraipur.zendrive

import java.util.Calendar

/**
 * The documents an Indian vehicle owner actually keeps, with the renewal cycle each one runs on.
 *
 * Picking a type used to mean typing a free-text label and then working out the expiry by hand.
 * These carry their own cycle, so choosing "PUC" can offer a date six months out rather than
 * leaving the field blank.
 *
 * [defaultMonths] is the common case, not a rule: insurance is usually annual but multi-year
 * policies exist, and a new car's PUC runs a year before falling to six months. The suggestion is
 * always editable.
 */
enum class DocumentPreset(
    val key: String,
    val labelRes: Int,
    val defaultMonths: Int?,
    val hasExpiry: Boolean
) {
    REGISTRATION("registration", R.string.doc_preset_rc, null, false),
    INSURANCE("insurance", R.string.doc_preset_insurance, 12, true),
    PUC("puc", R.string.doc_preset_puc, 6, true),
    FITNESS("fitness", R.string.doc_preset_fitness, 24, true),
    PERMIT("permit", R.string.doc_preset_permit, 60, true),
    ROAD_TAX("road_tax", R.string.doc_preset_road_tax, 12, true),
    DRIVING_LICENCE("driving_licence", R.string.doc_preset_dl, 240, true),
    INVOICE("invoice", R.string.doc_preset_invoice, null, false),
    WARRANTY("warranty", R.string.doc_preset_warranty, 36, true),
    OTHER("other", R.string.doc_preset_other, null, false);

    /** A suggested expiry [defaultMonths] out from [from], or null when the type doesn't expire. */
    fun suggestedExpiry(from: Long = System.currentTimeMillis()): Long? {
        val months = defaultMonths ?: return null
        return Calendar.getInstance().apply {
            timeInMillis = from
            add(Calendar.MONTH, months)
        }.timeInMillis
    }

    companion object {
        fun fromKey(key: String?): DocumentPreset? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }

        /**
         * Registration and a driving licence belong to the owner or survive the vehicle's whole
         * life, so they are not per-vehicle renewals in the way the rest are.
         */
        fun renewable(): List<DocumentPreset> = entries.filter { it.hasExpiry }
    }
}
