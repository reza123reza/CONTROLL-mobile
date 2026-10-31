package ir.remotepc

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object Discovery {
    private const val UDP_PORT = 8766
    private const val TCP_PORT = 8765

    fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun wifiBase(ctx: Context): Pair<String, Int>? {
        @Suppress("DEPRECATION")
        val ip = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress
        if (ip == 0) return null
        val b = listOf(ip and 0xff, (ip shr 8) and 0xff, (ip shr 16) and 0xff, (ip shr 24) and 0xff)
        return "${b[0]}.${b[1]}.${b[2]}" to b[3]
    }

    /** UDP broadcast: returns the first "http://ip:port" that answers. */
    fun broadcast(ctx: Context): String? {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wm.createMulticastLock("rc").apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { s ->
                s.broadcast = true; s.soTimeout = 700
                val msg = "RCPC?".toByteArray()
                repeat(2) {
                    try { s.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), UDP_PORT)) } catch (_: Exception) {}
                    try {
                        val buf = ByteArray(512); val pkt = DatagramPacket(buf, buf.size)
                        s.receive(pkt)
                        val j = JSONObject(String(pkt.data, 0, pkt.length))
                        if (j.optString("app") == "remote-pc") return "http://${pkt.address.hostAddress}:${j.optInt("port", TCP_PORT)}"
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) { } finally { try { lock.release() } catch (_: Exception) {} }
        return null
    }

    /** Fallback: probe every address of the phone's /24 on the app port. */
    fun scan(ctx: Context): String? {
        val (prefix, self) = wifiBase(ctx) ?: return null
        val pool = Executors.newFixedThreadPool(48)
        try {
            val tasks = (1..254).filter { it != self }.map { n ->
                Callable { val u = "http://$prefix.$n:$TCP_PORT"; if (Api.ping(u, 600)) u else null }
            }
            for (f in pool.invokeAll(tasks)) { val r = try { f.get() } catch (_: Exception) { null }; if (r != null) return r }
        } finally { pool.shutdownNow() }
        return null
    }
}
