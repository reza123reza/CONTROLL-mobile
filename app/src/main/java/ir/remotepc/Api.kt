package ir.remotepc

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    class LoginResult(val token: String?, val error: String?)

    private fun open(url: String, timeout: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout; readTimeout = timeout; useCaches = false
        }

    /** True if [base] is a Remote-PC server. */
    fun ping(base: String, timeout: Int = 1500): Boolean = try {
        val c = open("$base/api/ping", timeout)
        val ok = c.responseCode == 200 &&
            JSONObject(c.inputStream.bufferedReader().readText()).optString("app") == "remote-pc"
        c.disconnect(); ok
    } catch (e: Exception) { false }

    fun login(base: String, pin: String): LoginResult = try {
        val c = open("$base/api/login", 6000)
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(JSONObject().put("pin", pin).put("app", true).toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        val j = try { JSONObject(txt) } catch (e: Exception) { JSONObject() }
        when {
            code == 200 && j.optString("token").isNotEmpty() -> LoginResult(j.getString("token"), null)
            code == 429 -> LoginResult(null, "چند بار رمز اشتباه زده شد؛ کمی صبر کن")
            code == 401 || code == 403 -> LoginResult(null, "رمز اشتباه است")
            else -> LoginResult(null, "خطا ($code)")
        }
    } catch (e: Exception) { LoginResult(null, "اتصال برقرار نشد") }

    fun postFrame(base: String, token: String, jpeg: ByteArray, w: Int, h: Int): Boolean = try {
        val c = open("$base/api/phone/frame", 4000)
        c.requestMethod = "POST"; c.doOutput = true
        c.setFixedLengthStreamingMode(jpeg.size)
        c.setRequestProperty("Content-Type", "image/jpeg")
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("X-Frame-W", w.toString()); c.setRequestProperty("X-Frame-H", h.toString())
        c.outputStream.use { it.write(jpeg) }
        val ok = c.responseCode == 200
        c.disconnect(); ok
    } catch (e: Exception) { false }

    fun stop(base: String, token: String) {
        try {
            val c = open("$base/api/phone/stop", 3000)
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write("{}".toByteArray()) }
            c.responseCode; c.disconnect()
        } catch (_: Exception) {}
    }
}
