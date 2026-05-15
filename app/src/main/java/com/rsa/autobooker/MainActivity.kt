package com.rsa.autobooker

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.AnimationUtils
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var settingsBtn: ImageButton
    private lateinit var playBtn: ImageButton
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var glassStatsBar: CardView
    private lateinit var glassBottomNav: CardView
    private lateinit var modeBadge: TextView

    private lateinit var statChecks: TextView
    private lateinit var statSlots: TextView
    private lateinit var statBooked: TextView

    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navAlerts: LinearLayout
    private lateinit var navProfile: LinearLayout

    private var isLoggedIn = false
    private var isMonitoring = false
    private var checkCount = 0
    private var slotsFound = 0
    private var bookedCount = 0
    private var consecutiveErrors = 0
    private val handler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var settings: AppSettings
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val RSA_URL = "https://www.myroadsafety.ie/"
        private const val CHANNEL_ID = "rsa_slot_alerts"
        private const val NOTIF_ID = 1
        private const val MAX_ERRORS_BEFORE_STOP = 5
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = AppSettings(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        initViews()
        setupWebView()
        setupClickListeners()
        applyAnimations()
        updateStats()
        updateNavState()
        updateModeBadge()
    }

    private fun initViews() {
        webView = findViewById(R.id.webview)
        settingsBtn = findViewById(R.id.settingsBtn)
        playBtn = findViewById(R.id.playBtn)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        glassStatsBar = findViewById(R.id.glassStatsBar)
        glassBottomNav = findViewById(R.id.glassBottomNav)
        modeBadge = findViewById(R.id.modeBadge)

        statChecks = findViewById(R.id.statChecks)
        statSlots = findViewById(R.id.statSlots)
        statBooked = findViewById(R.id.statBooked)

        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)
        navAlerts = findViewById(R.id.navAlerts)
        navProfile = findViewById(R.id.navProfile)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        }

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidApp")
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadingOverlay.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingOverlay.visibility = View.GONE
                detectLoginState(url)
                detectRateLimit(url)
                if (isMonitoring) injectMonitorScript()
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                consecutiveErrors++
                if (consecutiveErrors >= MAX_ERRORS_BEFORE_STOP) {
                    showSnackbar("⚠️ Too many errors — stopping monitor")
                    if (isMonitoring) toggleMonitoring()
                    consecutiveErrors = 0
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(RSA_URL)
    }

    private fun setupClickListeners() {
        settingsBtn.setOnClickListener {
            pulseView(it)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        playBtn.setOnClickListener {
            pulseView(it)
            toggleMonitoring()
        }

        navHome.setOnClickListener {
            pulseView(it)
            webView.loadUrl(RSA_URL)
            showSnackbar("🏠 Home")
        }

        navHistory.setOnClickListener {
            pulseView(it)
            if (!isLoggedIn) showLoginRequired("History")
            else showSnackbar("📜 History — Coming Soon")
        }

        navAlerts.setOnClickListener {
            pulseView(it)
            if (!isLoggedIn) showLoginRequired("Alerts")
            else showSnackbar("🔔 Alerts — Coming Soon")
        }

        navProfile.setOnClickListener {
            pulseView(it)
            if (!isLoggedIn) showLoginRequired("Profile")
            else showSnackbar("👤 Profile — Coming Soon")
        }
    }

    private fun getScanInterval(): Long {
        return when (settings.scanMode) {
            ScanMode.STEALTH -> 25000L + Random.nextLong(15000)
            ScanMode.NORMAL -> 12000L + Random.nextLong(8000)
            ScanMode.AGGRESSIVE -> 5000L + Random.nextLong(3000)
        }
    }

    private fun getModeLabel(): String {
        return when (settings.scanMode) {
            ScanMode.STEALTH -> "🛡️ STEALTH"
            ScanMode.NORMAL -> "⚡ NORMAL"
            ScanMode.AGGRESSIVE -> "🔥 AGGRESSIVE"
        }
    }

    private fun updateModeBadge() {
        modeBadge.text = getModeLabel()
        modeBadge.setTextColor(when (settings.scanMode) {
            ScanMode.STEALTH -> ContextCompat.getColor(this, R.color.irish_green)
            ScanMode.NORMAL -> ContextCompat.getColor(this, R.color.irish_gold)
            ScanMode.AGGRESSIVE -> ContextCompat.getColor(this, R.color.irish_orange)
        })
    }

    private fun toggleMonitoring() {
        if (!isLoggedIn) {
            showSnackbar("⚠️ Log in to MyRoadSafety first")
            vibrateError()
            return
        }

        isMonitoring = !isMonitoring

        if (isMonitoring) {
            playBtn.setImageResource(R.drawable.ic_stop)
            playBtn.background = ContextCompat.getDrawable(this, R.drawable.circle_stop)
            statusIndicator.setBackgroundResource(R.drawable.pulse_green)
            statusText.text = "● AUTO-BOOKING ACTIVE"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.irish_green))
            showSnackbar("🚀 ${getModeLabel()} mode — scanning started")
            consecutiveErrors = 0
            startMonitoringLoop()
        } else {
            playBtn.setImageResource(R.drawable.ic_play)
            playBtn.background = ContextCompat.getDrawable(this, R.drawable.circle_play)
            statusIndicator.setBackgroundResource(R.drawable.dot_grey)
            statusText.text = "○ IDLE"
            statusText.setTextColor(ContextCompat.getColor(this, R.color.irish_white_dim))
            showSnackbar("⏹ Auto-booking stopped")
            stopMonitoringLoop()
            removeMonitorScript()
        }
    }

    private fun startMonitoringLoop() {
        monitorRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                checkCount++
                updateStats()

                webView.evaluateJavascript(
                    """
                    (function() {
                        var slots = document.querySelectorAll('.available, .slot-available, [class*="available"], td.available, .booking-slot:not(.booked), [data-available="true"], .date-cell.available, .slot.open');
                        var btns = document.querySelectorAll('button[class*="book"], input[value*="Book"], .btn-book, [id*="book"], .confirm-btn');
                        var result = { slots: slots.length, buttons: btns.length, url: location.href, title: document.title };
                        if (slots.length > 0 && ${if (settings.autoClick) "true" else "false"}) {
                            slots[0].click();
                            result.clicked = true;
                        }
                        return JSON.stringify(result);
                    })();
                    """.trimIndent(),
                    { result -> handleMonitorResult(result) }
                )

                val interval = getScanInterval()
                handler.postDelayed(this, interval)
            }
        }
        handler.post(monitorRunnable!!)
    }

    private fun stopMonitoringLoop() {
        monitorRunnable?.let { handler.removeCallbacks(it) }
        monitorRunnable = null
    }

    private fun handleMonitorResult(result: String) {
        try {
            val clean = result.replace("\"", """).trim('"')
            if (clean.contains("slots")) {
                val found = clean.substringAfter(""slots":").substringBefore(",").trim().toIntOrNull() ?: 0
                if (found > 0) {
                    slotsFound += found
                    updateStats()
                    consecutiveErrors = 0

                    if (settings.soundEnabled) playAlertSound()
                    if (settings.vibrateEnabled) vibrateSuccess()

                    sendSlotNotification(found)
                    showSnackbar("🎉 $found SLOT(S) FOUND! Auto-booking...")

                    if (settings.autoBook) {
                        handler.postDelayed({ attemptAutoBook() }, 800)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun attemptAutoBook() {
        webView.evaluateJavascript(
            """
            (function() {
                var btn = document.querySelector('button[type="submit"], .btn-primary, [value="Confirm"], [value="Book Now"], .confirm-booking, .book-btn, #bookButton');
                if (btn) { btn.click(); return "CLICKED"; }
                var next = document.querySelector('.next-step, .continue-btn, [class*="next"]');
                if (next) { next.click(); return "NEXT"; }
                return "NO_BUTTON";
            })();
            """.trimIndent(),
            { result ->
                if (result.contains("CLICKED") || result.contains("NEXT")) {
                    bookedCount++
                    updateStats()
                    showSnackbar("✅ Auto-book triggered!")
                    sendBookingNotification()
                    if (settings.autoBook) {
                        handler.postDelayed({ toggleMonitoring() }, 2000)
                    }
                }
            }
        )
    }

    private fun injectMonitorScript() {
        webView.evaluateJavascript(
            """
            (function() {
                var el = document.getElementById('rsa-auto-indicator');
                if (!el) {
                    el = document.createElement('div');
                    el.id = 'rsa-auto-indicator';
                    el.style.cssText = 'position:fixed;top:70px;right:12px;z-index:99999;background:rgba(0,200,83,0.85);color:#fff;padding:8px 16px;border-radius:24px;font:bold 12px sans-serif;backdrop-filter:blur(8px);box-shadow:0 4px 20px rgba(0,200,83,0.4);border:1px solid rgba(255,255,255,0.2);';
                    el.innerText = '🔍 RSA Auto-Scanning (${getModeLabel().replace("🛡️ ","").replace("⚡ ","").replace("🔥 ","")})';
                    document.body.appendChild(el);
                }
            })();
            """.trimIndent(), null
        )
    }

    private fun removeMonitorScript() {
        webView.evaluateJavascript(
            """
            (function() {
                var el = document.getElementById('rsa-auto-indicator');
                if (el) el.remove();
            })();
            """.trimIndent(), null
        )
    }

    private fun detectLoginState(url: String?) {
        url ?: return
        val wasLoggedIn = isLoggedIn
        isLoggedIn = when {
            url.contains("dashboard", true) -> true
            url.contains("booking", true) -> true
            url.contains("home", true) -> true
            url.contains("login", true) -> false
            url.contains("myroadsafety.ie/") && !url.endsWith("myroadsafety.ie/") -> true
            else -> isLoggedIn
        }
        if (isLoggedIn && !wasLoggedIn) {
            showSnackbar("✅ Logged in detected")
            updateNavState()
        }
    }

    private fun detectRateLimit(url: String?) {
        url ?: return
        if (url.contains("captcha", true) || url.contains("rate-limit", true) ||
            url.contains("blocked", true) || url.contains("too-many", true)) {
            showSnackbar("🚫 Rate limited! Switching to STEALTH mode")
            settings.scanMode = ScanMode.STEALTH
            updateModeBadge()
            if (isMonitoring) {
                stopMonitoringLoop()
                startMonitoringLoop()
            }
        }
    }

    // ===== NOTIFICATIONS =====
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RSA Slot Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant alerts when driving test slots become available"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendSlotNotification(count: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shamrock)
            .setContentTitle("🎉 SLOT FOUND!")
            .setContentText("$count available test slot(s) detected on MyRoadSafety")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setLights(ContextCompat.getColor(this, R.color.irish_green), 500, 500)
            .build()

        notificationManager.notify(NOTIF_ID, notification)
    }

    private fun sendBookingNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shamrock)
            .setContentTitle("✅ AUTO-BOOK ATTEMPTED")
            .setContentText("Booking process initiated — check MyRoadSafety to confirm")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIF_ID + 1, notification)
    }

    // ===== UI UPDATES =====
    private fun updateNavState() {
        val active = ContextCompat.getColor(this, R.color.irish_gold)
        val dim = ContextCompat.getColor(this, R.color.irish_white_dim)
        val historyIcon = navHistory.findViewWithTag<ImageView>("icon") ?: navHistory.getChildAt(0) as ImageView
        val alertsIcon = navAlerts.findViewWithTag<ImageView>("icon") ?: navAlerts.getChildAt(0) as ImageView
        val profileIcon = navProfile.findViewWithTag<ImageView>("icon") ?: navProfile.getChildAt(0) as ImageView
        val historyText = navHistory.findViewWithTag<TextView>("label") ?: navHistory.getChildAt(1) as TextView
        val alertsText = navAlerts.findViewWithTag<TextView>("label") ?: navAlerts.getChildAt(1) as TextView
        val profileText = navProfile.findViewWithTag<TextView>("label") ?: navProfile.getChildAt(1) as TextView

        historyIcon.setColorFilter(if (isLoggedIn) active else dim)
        historyText.setTextColor(if (isLoggedIn) active else dim)
        alertsIcon.setColorFilter(if (isLoggedIn) active else dim)
        alertsText.setTextColor(if (isLoggedIn) active else dim)
        profileIcon.setColorFilter(if (isLoggedIn) active else dim)
        profileText.setTextColor(if (isLoggedIn) active else dim)
    }

    private fun updateStats() {
        statChecks.text = checkCount.toString()
        statSlots.text = slotsFound.toString()
        statBooked.text = bookedCount.toString()
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show()
    }

    private fun showLoginRequired(feature: String) {
        AlertDialog.Builder(this, R.style.GlassDialog)
            .setTitle("🔒 Login Required")
            .setMessage("Log in to MyRoadSafety to access $feature.")
            .setPositiveButton("Got it") { d, _ -> d.dismiss() }
            .show()
        vibrateError()
    }

    private fun pulseView(v: View) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        }.start()
    }

    private fun applyAnimations() {
        findViewById<LinearLayout>(R.id.appBar).startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
        glassStatsBar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
        glassBottomNav.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up))
    }

    // ===== ALERTS =====
    private fun playAlertSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.alert_chime).apply {
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (_: Exception) {}
    }

    private fun vibrateSuccess() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50, 200, 100, 300), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 100, 50, 100, 50, 200, 100, 300), -1)
        }
    }

    private fun vibrateError() {
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(200)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoringLoop()
        mediaPlayer?.release()
        webView.destroy()
    }

    // ===== JS INTERFACE =====
    class WebAppInterface(private val activity: MainActivity) {
        @JavascriptInterface
        fun onSlotFound(count: Int) {
            activity.runOnUiThread {
                activity.slotsFound += count
                activity.updateStats()
                if (activity.settings.soundEnabled) activity.playAlertSound()
                if (activity.settings.vibrateEnabled) activity.vibrateSuccess()
                activity.sendSlotNotification(count)
            }
        }
        @JavascriptInterface
        fun onBookingConfirmed() {
            activity.runOnUiThread {
                activity.bookedCount++
                activity.updateStats()
                activity.showSnackbar("🎉 BOOKING CONFIRMED!")
                activity.sendBookingNotification()
                activity.toggleMonitoring()
            }
        }
        @JavascriptInterface
        fun log(msg: String) { android.util.Log.d("RSA", "JS: $msg") }
    }
}
