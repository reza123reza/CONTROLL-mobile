package ir.remotepc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScreenShareService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private val sender = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private var base = ""; private var token = ""
    private var lastSent = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        val data = intent?.getParcelableExtra<Intent>("data")
        val code = intent?.getIntExtra("code", 0) ?: 0
        base = intent?.getStringExtra("base") ?: ""
        token = intent?.getStringExtra("token") ?: ""
        if (data == null || base.isEmpty() || token.isEmpty()) { stopSelf(); return START_NOT_STICKY }
        startForegroundCompat()
        try { begin(code, data) } catch (e: Exception) { shutdown() }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("share", "اشتراک صفحه", NotificationManager.IMPORTANCE_LOW))
        val stop = Notification.Action.Builder(null, "توقف",
            android.app.PendingIntent.getService(this, 1, Intent(this, ScreenShareService::class.java).setAction(ACTION_STOP),
                android.app.PendingIntent.FLAG_IMMUTABLE)).build()
        val n = Notification.Builder(this, "share").setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("صفحه‌ی گوشی روی لپ‌تاپ نمایش داده می‌شود").setOngoing(true).addAction(stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)
    }

    private fun begin(code: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(code, data)
        projection = mp
        thread = HandlerThread("share").also { it.start() }
        val h = Handler(thread!!.looper)
        mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() { shutdown() } }, h)

        val dm = resources.displayMetrics
        val scale = minOf(1f, 720f / minOf(dm.widthPixels, dm.heightPixels))
        val w = (dm.widthPixels * scale).toInt() and 1.inv()
        val hgt = (dm.heightPixels * scale).toInt() and 1.inv()
        val r = ImageReader.newInstance(w, hgt, PixelFormat.RGBA_8888, 2)
        reader = r
        display = mp.createVirtualDisplay("rc", w, hgt, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, h)
        r.setOnImageAvailableListener({ rd ->
            val img = try { rd.acquireLatestImage() } catch (e: Exception) { null } ?: return@setOnImageAvailableListener
            try {
                val now = System.currentTimeMillis()
                if (now - lastSent < 140 || !busy.compareAndSet(false, true)) return@setOnImageAvailableListener
                lastSent = now
                val p = img.planes[0]
                val rowPad = p.rowStride - p.pixelStride * w
                val bmp = Bitmap.createBitmap(w + rowPad / p.pixelStride, hgt, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(p.buffer)
                val crop = if (bmp.width != w) Bitmap.createBitmap(bmp, 0, 0, w, hgt) else bmp
                val out = ByteArrayOutputStream(64 * 1024)
                crop.compress(Bitmap.CompressFormat.JPEG, 60, out)
                if (crop !== bmp) crop.recycle(); bmp.recycle()
                val bytes = out.toByteArray()
                sender.execute {
                    try { if (!Api.postFrame(base, token, bytes, w, hgt)) failures++ else failures = 0
                          if (failures > 40) shutdown() } finally { busy.set(false) }
                }
            } catch (e: Exception) { busy.set(false) } finally { img.close() }
        }, h)
        running = true
    }

    @Volatile private var failures = 0

    private fun shutdown() {
        if (!running && projection == null) { stopSelf(); return }
        running = false
        try { display?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        projection = null; display = null; reader = null
        thread?.quitSafely()
        val b = base; val t = token
        Thread { if (b.isNotEmpty()) Api.stop(b, t) }.start()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { if (running) shutdown(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "ir.remotepc.STOP"
        @Volatile var running = false
    }
}
