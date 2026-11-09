package com.kceryrk.prayertimes

import android.content.Context
import android.content.res.Configuration
import android.view.View
import java.util.Locale

/** User preferences, stored in SharedPreferences "app_settings". */
object AppSettings {
    private const val PREFS = "app_settings"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun str(ctx: Context, key: String, def: String): String = prefs(ctx).getString(key, def) ?: def
    fun bool(ctx: Context, key: String, def: Boolean = true): Boolean = prefs(ctx).getBoolean(key, def)
    fun int(ctx: Context, key: String, def: Int): Int = prefs(ctx).getInt(key, def)

    fun put(ctx: Context, key: String, value: Any) {
        val e = prefs(ctx).edit()
        when (value) {
            is String -> e.putString(key, value)
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
        }
        e.apply()
    }

    fun method(ctx: Context): Int = int(ctx, "method", 13)
    fun refreshHours(ctx: Context): Int = int(ctx, "refresh_hours", 6)
    fun alarmOn(ctx: Context, p: Prayer): Boolean = bool(ctx, "alarm_${p.name}")
    fun updatesEnabled(ctx: Context): Boolean = bool(ctx, "updates_on")

    /** Returns a context using the language chosen in settings (or the system language). */
    fun localized(ctx: Context): Context {
        val tag = str(ctx, "language", "system")
        if (tag == "system") return ctx
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return ctx.createConfigurationContext(config)
    }
}

fun visibleIf(show: Boolean): Int = if (show) View.VISIBLE else View.GONE
