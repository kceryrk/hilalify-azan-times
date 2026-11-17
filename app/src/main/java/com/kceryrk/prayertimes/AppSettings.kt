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

    fun defaultMethod(): Int =
        if (android.content.res.Resources.getSystem().configuration.locales[0].language == "tr") 13 else 3

    private fun sysProp(key: String): String = runCatching {
        val sp = Class.forName("android.os.SystemProperties")
        val get = sp.getMethod("get", String::class.java, String::class.java)
        get.invoke(null, key, "") as? String ?: ""
    }.getOrDefault("")

    fun isHyperOs(): Boolean =
        sysProp("ro.mi.os.version.name").isNotEmpty() || sysProp("ro.mi.os.version.code").isNotEmpty()

    fun isHyperOs3Plus(): Boolean {
        val code = sysProp("ro.mi.os.version.code").toIntOrNull()
        if (code != null) return code >= 3
        val name = sysProp("ro.mi.os.version.name").ifEmpty { sysProp("ro.mi.os.version.incremental") }
        return (Regex("""OS(\d+)""").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0) >= 3
    }

    fun isSamsung(): Boolean = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    fun method(ctx: Context): Int = int(ctx, "method", defaultMethod())
    fun refreshDays(ctx: Context): Int = int(ctx, "refresh_days", 10).coerceIn(1, 10)
    fun alarmOn(ctx: Context, p: Prayer): Boolean = bool(ctx, "alarm_${p.name}")

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
