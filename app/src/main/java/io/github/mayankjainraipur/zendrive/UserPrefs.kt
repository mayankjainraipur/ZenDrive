package io.github.mayankjainraipur.zendrive

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Synchronous mirror of the display preferences held in [UserProfile].
 *
 * The profile row stays the source of truth — it is what gets backed up — but reading it means
 * touching Room on a coroutine, and two callers can't wait for that: the theme has to be applied
 * before the first activity inflates, or the app flashes the wrong palette, and [FormatUtil] is
 * called from adapter bind methods on the main thread. So every write to the profile also lands
 * here, and everything that formats or themes reads from here.
 */
object UserPrefs {

    private const val FILE = "zendrive_prefs"

    private const val KEY_CURRENCY = "currencyCode"
    private const val KEY_DISTANCE = "distanceUnit"
    private const val KEY_DATE_FORMAT = "dateFormatPattern"
    private const val KEY_THEME = "themeMode"
    private const val KEY_LEAD_DAYS = "reminderLeadDays"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    val currencyCode: String
        get() = prefs?.getString(KEY_CURRENCY, null) ?: "INR"

    val distanceUnit: String
        get() = prefs?.getString(KEY_DISTANCE, null) ?: "km"

    val dateFormatPattern: String
        get() = prefs?.getString(KEY_DATE_FORMAT, null) ?: "dd MMM yyyy"

    val themeMode: String
        get() = prefs?.getString(KEY_THEME, null) ?: THEME_DARK

    val reminderLeadDays: Int
        get() = prefs?.getInt(KEY_LEAD_DAYS, 3) ?: 3

    /** Copies the profile's display preferences across. Call after every profile write. */
    fun mirror(profile: UserProfile) {
        prefs?.edit()
            ?.putString(KEY_CURRENCY, profile.preferredCurrencyCode)
            ?.putString(KEY_DISTANCE, profile.distanceUnit)
            ?.putString(KEY_DATE_FORMAT, profile.dateFormatPattern)
            ?.putString(KEY_THEME, profile.themeMode)
            ?.putInt(KEY_LEAD_DAYS, profile.reminderLeadDays)
            ?.apply()
    }

    fun applyTheme() {
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
