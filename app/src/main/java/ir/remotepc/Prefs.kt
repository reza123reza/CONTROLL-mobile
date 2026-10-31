package ir.remotepc

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("rc", Context.MODE_PRIVATE)
    var pin: String get() = p.getString("pin", "") ?: ""; set(v) = p.edit().putString("pin", v).apply()
    var lastUrl: String get() = p.getString("last", "") ?: ""; set(v) = p.edit().putString("last", v).apply()
    var remoteUrl: String get() = p.getString("remote", "") ?: ""; set(v) = p.edit().putString("remote", v).apply()
    var baleLink: String get() = p.getString("bale", "") ?: ""; set(v) = p.edit().putString("bale", v).apply()
    var token: String get() = p.getString("token", "") ?: ""; set(v) = p.edit().putString("token", v).apply()
}
