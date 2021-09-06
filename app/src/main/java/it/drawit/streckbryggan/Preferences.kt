package it.drawit.streckbryggan

import android.content.Context
import android.content.SharedPreferences

const val fileStrecklistan = "strecklistan"
val prefStrecklistanUrl = Pref("strecklistanUrl", R.string.strecklistan_base_uri)
val prefStrecklistanUser = Pref("strecklistanUser", R.string.strecklistan_http_user)
val prefStrecklistanPass = Pref("strecklistanPass", R.string.strecklistan_http_pass)

data class Pref(val key: String, val resourceStringId: Int?) {
    fun get(prefs: SharedPreferences, context: Context): String {
        return when {
            prefs.contains(key) -> {
                prefs.getString(key, "")!!
            }
            resourceStringId != null -> {
                val default = context.getString(resourceStringId)
                prefs.edit().putString(key, default).apply()
                default
            }
            else -> {
                ""
            }
        }
    }

    fun set(prefs: SharedPreferences, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}