package ua.pastushak.tvlauncher

import android.content.Context

/** Збережені налаштування лаунчера. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** Пакети улюблених застосунків у тому порядку, як вони стоять у ряду. */
    var favorites: List<String>
        get() = (sp.getString(KEY_FAVORITES, "") ?: "").split('|').filter { it.isNotEmpty() }
        set(value) = sp.edit().putString(KEY_FAVORITES, value.joinToString("|")).apply()

    var hidden: Set<String>
        get() = HashSet(sp.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet())
        set(value) = sp.edit().putStringSet(KEY_HIDDEN, HashSet(value)).apply()

    var backgroundIndex: Int
        get() = sp.getInt(KEY_BACKGROUND, 0)
        set(value) = sp.edit().putInt(KEY_BACKGROUND, value).apply()

    var columns: Int
        get() = sp.getInt(KEY_COLUMNS, 5).coerceIn(3, 8)
        set(value) = sp.edit().putInt(KEY_COLUMNS, value).apply()

    var initialized: Boolean
        get() = sp.getBoolean(KEY_INITIALIZED, false)
        set(value) = sp.edit().putBoolean(KEY_INITIALIZED, value).apply()

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_BACKGROUND = "background"
        const val KEY_COLUMNS = "columns"
        const val KEY_INITIALIZED = "initialized"
    }
}
