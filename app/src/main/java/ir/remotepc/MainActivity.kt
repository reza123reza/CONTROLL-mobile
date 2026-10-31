package ir.remotepc

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var overlay: LinearLayout
    private var base = ""
    private var token = ""
    private var fileCb: ValueCallback<Array<Uri>>? = null
    @Volatile private var connecting = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0f1115")) }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val u = r.url.toString()
                    return if (base.isNotEmpty() && u.startsWith(base)) false
                    else { try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (_: Exception) {}; true }
                }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) showOverlay("اتصال قطع شد…", true)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                    fileCb?.onReceiveValue(null); fileCb = cb
                    return try { startActivityForResult(p.createIntent(), REQ_FILE); true } catch (e: Exception) { fileCb = null; false }
                }
            }
            setDownloadListener { url, ua, cd, mime, _ ->
                try {
                    val name = URLUtil.guessFileName(url, cd, mime)
                    val r = DownloadManager.Request(Uri.parse(url))
                        .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                        .addRequestHeader("User-Agent", ua)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
                    Toast.makeText(this@MainActivity, "دانلود شروع شد: $name", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this@MainActivity, "دانلود ناموفق", Toast.LENGTH_SHORT).show() }
            }
        }
        root.addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER; setPadding(40, 20, 40, 20) }
        val retry = Button(this).apply { text = "تلاش دوباره"; setOnClickListener { connect() } }
        val settingsBtn = Button(this).apply { text = "تنظیمات"; setOnClickListener { settingsDialog() } }
        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.parseColor("#0f1115"))
            addView(TextView(context).apply { text = "💻"; textSize = 56f; gravity = Gravity.CENTER })
            addView(status); addView(retry); addView(settingsBtn)
        }
        root.addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val menu = TextView(this).apply {
            text = "⋮"; textSize = 24f; setTextColor(Color.WHITE); setPadding(28, 6, 28, 6)
            setBackgroundColor(Color.parseColor("#99000000")); setOnClickListener { showMenu(it) }
        }
        root.addView(menu, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply { setMargins(8, 0, 0, 120) })
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        if (prefs.pin.isEmpty()) settingsDialog() else connect()
    }

    private fun showOverlay(msg: String, show: Boolean) = runOnUiThread {
        status.text = msg; overlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun candidates(): List<String> {
        val l = LinkedHashSet<String>()
        if (prefs.lastUrl.isNotEmpty()) l.add(prefs.lastUrl)
        return l.toList()
    }

    private fun connect() {
        if (connecting) return
        connecting = true
        if (prefs.pin.isEmpty()) { connecting = false; settingsDialog(); return }
        showOverlay("در حال پیدا کردن لپ‌تاپ…", true)
        Thread {
            try {
                var found: String? = candidates().firstOrNull { Api.ping(it) }
                val wifi = Discovery.onWifi(this)
                if (found == null && wifi) { showOverlay("جستجو در شبکه‌ی وای‌فای…", true); found = Discovery.broadcast(this) }
                if (found == null && wifi) { showOverlay("جستجوی دقیق‌تر…", true); found = Discovery.scan(this) }
                val remote = prefs.remoteUrl.trim().trimEnd('/')
                if (found == null && remote.isNotEmpty()) { showOverlay("تلاش با آدرس راه دور…", true); if (Api.ping(remote, 5000)) found = remote }
                if (found == null) {
                    showOverlay("لپ‌تاپ پیدا نشد.\nمطمئن شو روشن است و اینترنت دارد.\nاز راه دور: ربات بله را بررسی کن.", true)
                    return@Thread
                }
                showOverlay("در حال ورود…", true)
                val r = Api.login(found, prefs.pin)
                if (r.token == null) { showOverlay(r.error ?: "ورود ناموفق", true); return@Thread }
                base = found; token = r.token; prefs.lastUrl = found
                runOnUiThread {
                    val cm = CookieManager.getInstance(); cm.setAcceptCookie(true)
                    cm.setCookie(base, "rc_token=$token; Path=/")
                    cm.flush()
                    web.loadUrl(base + "/")
                    overlay.visibility = View.GONE
                }
            } finally { connecting = false }
        }.start()
    }

    private fun settingsDialog() {
        val pad = 40
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0) }
        fun field(hint: String, value: String, pass: Boolean = false) = EditText(this).apply {
            this.hint = hint; setText(value); setSingleLine()
            if (pass) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            col.addView(this)
        }
        val pin = field("رمز (همان رمز داشبورد لپ‌تاپ)", prefs.pin, true)
        val remote = field("آدرس راه دور (اختیاری، مثلاً http://100.x.y.z:8765)", prefs.remoteUrl)
        val bale = field("لینک ربات بله (اختیاری)", prefs.baleLink)
        AlertDialog.Builder(this).setTitle("تنظیمات").setView(col).setCancelable(prefs.pin.isNotEmpty())
            .setPositiveButton("ذخیره و اتصال") { _, _ ->
                prefs.pin = pin.text.toString().trim(); prefs.remoteUrl = remote.text.toString().trim()
                prefs.baleLink = bale.text.toString().trim(); prefs.lastUrl = ""; connect()
            }.setNegativeButton("لغو", null).show()
    }

    private fun showMenu(anchor: View) {
        val m = PopupMenu(this, anchor)
        m.menu.add(0, 1, 0, if (ScreenShareService.running) "⏹ توقف نمایش صفحه روی لپ‌تاپ" else "📱→💻 نمایش صفحه‌ی گوشی روی لپ‌تاپ")
        m.menu.add(0, 2, 1, "🔄 اتصال دوباره")
        m.menu.add(0, 3, 2, "💬 ربات بله (وضعیت از راه دور)")
        m.menu.add(0, 4, 3, "⚙️ تنظیمات")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> toggleShare(); 2 -> connect()
                3 -> if (prefs.baleLink.isNotEmpty()) try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(prefs.baleLink))) } catch (_: Exception) {}
                     else Toast.makeText(this, "لینک ربات را در تنظیمات وارد کن", Toast.LENGTH_LONG).show()
                4 -> settingsDialog()
            }; true
        }
        m.show()
    }

    private fun toggleShare() {
        if (ScreenShareService.running) {
            startService(Intent(this, ScreenShareService::class.java).setAction(ScreenShareService.ACTION_STOP)); return
        }
        if (token.isEmpty()) { Toast.makeText(this, "اول به لپ‌تاپ وصل شو", Toast.LENGTH_SHORT).show(); return }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        when (req) {
            REQ_FILE -> { fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); fileCb = null }
            REQ_CAPTURE -> if (res == RESULT_OK && data != null) {
                val i = Intent(this, ScreenShareService::class.java).putExtra("code", res).putExtra("data", data)
                    .putExtra("base", base).putExtra("token", token)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                Toast.makeText(this, "صفحه‌ی گوشی روی لپ‌تاپ نمایش داده می‌شود", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack() && overlay.visibility != View.VISIBLE) web.goBack() else super.onBackPressed() }

    override fun onResume() { super.onResume(); if (base.isNotEmpty()) web.onResume() }
    override fun onPause() { super.onPause(); web.onPause() }

    companion object { const val REQ_FILE = 11; const val REQ_CAPTURE = 12 }
}
