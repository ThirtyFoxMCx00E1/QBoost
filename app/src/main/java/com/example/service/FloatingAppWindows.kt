package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** An app that can float over the game as a small window. */
data class FloatingApp(
    val id: String,
    val title: String,
    val url: String,
    val desktopSite: Boolean,
    val packages: List<String>
)

object FloatingApps {
    /** Order = order in the dock. */
    val all = listOf(
        FloatingApp("youtube", "YouTube", "https://m.youtube.com/", false, listOf("com.google.android.youtube")),
        FloatingApp("spotify", "Spotify", "https://open.spotify.com/", true, listOf("com.spotify.music")),
        FloatingApp("discord", "Discord", "https://discord.com/app", true, listOf("com.discord")),
        FloatingApp("facebook", "Facebook", "https://m.facebook.com/", false, listOf("com.facebook.katana")),
        FloatingApp("whatsapp", "WhatsApp", "https://web.whatsapp.com/", true, listOf("com.whatsapp")),
        FloatingApp("tiktok", "TikTok", "https://www.tiktok.com/", false, listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
        FloatingApp("instagram", "Instagram", "https://www.instagram.com/", false, listOf("com.instagram.android")),
        FloatingApp("messenger", "Messenger", "https://www.messenger.com/", true, listOf("com.facebook.orca")),
        FloatingApp("telegram", "Telegram", "https://web.telegram.org/a/", true, listOf("org.telegram.messenger")),
        FloatingApp("x", "X", "https://x.com/", false, listOf("com.twitter.android")),
        FloatingApp("twitch", "Twitch", "https://m.twitch.tv/", false, listOf("tv.twitch.android.app")),
        FloatingApp("reddit", "Reddit", "https://www.reddit.com/", false, listOf("com.reddit.frontpage")),
        FloatingApp("netflix", "Netflix", "https://www.netflix.com/", false, listOf("com.netflix.mediaclient")),
        FloatingApp("browser", "Browser", "https://www.google.com/", false, listOf("com.android.chrome"))
    )
}

/**
 * Floating windows for multitasking while you play: drag by the title bar, resize with the corner
 * grip, "Aa" turns the keyboard on (it is off by default so the game keeps focus), "-" minimizes to
 * a small tab and "x" closes.
 *
 * Android does not let an app put another app's own screen in a window, so each window shows the
 * web version of the service (you log in once inside the window). Hold an app in the dock to open
 * the real app instead.
 */
class FloatingAppWindows(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val windows = ArrayList<AppWindow>()

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    fun open(app: FloatingApp) {
        val existing = windows.firstOrNull { it.app.id == app.id }
        if (existing != null) {
            existing.restore()
            existing.bringToFront()
            return
        }
        try {
            windows.add(AppWindow(app, windows.size))
        } catch (_: Exception) {
        }
    }

    fun bringAllToFront() {
        windows.toList().forEach { it.bringToFront() }
    }

    /** After the phone is flipped or rotated: keep every window on the screen. */
    fun clampAll() {
        windows.toList().forEach { it.clampToScreen() }
    }

    fun closeAll() {
        windows.toList().forEach { it.close() }
    }

    val isEmpty: Boolean
        get() = windows.isEmpty()

    private inner class AppWindow(val app: FloatingApp, index: Int) {

        private var minimized = false
        private var keyboardOn = false
        private var savedWidth = 0
        private var savedHeight = 0
        private var gripView: View? = null
        private var keysView: TextView? = null

        private val web: WebView = createWebView()

        private val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
            minOf(dp(340), screenWidth() - dp(24)),
            minOf(dp(232), screenHeight() - dp(24)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dp(76) + index * dp(24)).coerceAtMost(maxOf(0, screenWidth() - width))
            y = (dp(28) + index * dp(24)).coerceAtMost(maxOf(0, screenHeight() - height))
        }

        private val root: FrameLayout = buildRoot()

        init {
            windowManager.addView(root, params)
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun createWebView(): WebView {
            val view = WebView(context)
            val s = view.settings
            s.javaScriptEnabled = true
            s.domStorageEnabled = true
            s.mediaPlaybackRequiresUserGesture = false
            s.loadWithOverviewMode = true
            s.useWideViewPort = true
            s.setSupportZoom(true)
            s.builtInZoomControls = true
            s.displayZoomControls = false
            if (app.desktopSite) s.userAgentString = DESKTOP_USER_AGENT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val scheme = request?.url?.scheme ?: return false
                    // keep web pages inside the window, ignore app links (intent:// and similar)
                    return scheme != "http" && scheme != "https"
                }
            }
            view.webChromeClient = WebChromeClient()
            view.loadUrl(app.url)
            return view
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun buildRoot(): FrameLayout {
            val card = FrameLayout(context)
            val cardBackground = GradientDrawable()
            cardBackground.setColor(Color.parseColor("#F2101828"))
            cardBackground.cornerRadius = dp(14).toFloat()
            card.background = cardBackground
            card.clipToOutline = true
            card.outlineProvider = ViewOutlineProvider.BACKGROUND

            val column = LinearLayout(context)
            column.orientation = LinearLayout.VERTICAL

            // ---- title bar: drag here ----
            val bar = LinearLayout(context)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.gravity = Gravity.CENTER_VERTICAL
            bar.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF1F3358"), Color.parseColor("#FF142038"))
            )
            bar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30))

            val title = TextView(context)
            title.text = app.title
            title.setTextColor(Color.WHITE)
            title.textSize = 11f
            title.typeface = Typeface.DEFAULT_BOLD
            title.maxLines = 1
            title.ellipsize = TextUtils.TruncateAt.END
            title.setPadding(dp(12), 0, dp(4), 0)
            title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            bar.addView(title)

            val keys = actionText("Aa") { toggleKeyboard() }
            keysView = keys
            bar.addView(keys)
            bar.addView(actionText("–") { toggleMinimize() })
            bar.addView(actionText("×") { close() })

            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            bar.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (startX + (event.rawX - touchX).toInt())
                            .coerceIn(0, maxOf(0, screenWidth() - params.width))
                        params.y = (startY + (event.rawY - touchY).toInt())
                            .coerceIn(0, maxOf(0, screenHeight() - params.height))
                        windowManager.updateViewLayout(card, params)
                        true
                    }
                    else -> true
                }
            }
            column.addView(bar)
            column.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            card.addView(
                column,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )

            // ---- resize grip (bottom-right corner) ----
            val grip = GripView(context)
            gripView = grip
            card.addView(
                grip,
                FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM or Gravity.END)
            )
            var startW = 0
            var startH = 0
            grip.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startW = params.width
                        startH = params.height
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.width = (startW + (event.rawX - touchX).toInt())
                            .coerceIn(dp(180), maxOf(dp(180), screenWidth() - params.x))
                        params.height = (startH + (event.rawY - touchY).toInt())
                            .coerceIn(dp(120), maxOf(dp(120), screenHeight() - params.y))
                        windowManager.updateViewLayout(card, params)
                        true
                    }
                    else -> true
                }
            }
            return card
        }

        private fun actionText(label: String, onClick: () -> Unit): TextView {
            val view = TextView(context)
            view.text = label
            view.setTextColor(Color.parseColor("#C5CBD8"))
            view.textSize = 14f
            view.typeface = Typeface.DEFAULT_BOLD
            view.gravity = Gravity.CENTER
            view.setPadding(dp(11), 0, dp(11), 0)
            view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            view.setOnClickListener { onClick() }
            return view
        }

        private fun toggleKeyboard() {
            keyboardOn = !keyboardOn
            if (keyboardOn) {
                // The window has to take focus for the keyboard to type into it (the game pauses while it does)
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                windowManager.updateViewLayout(root, params)
                web.requestFocus()
            } else {
                try {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(web.windowToken, 0)
                } catch (_: Exception) {
                }
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(root, params)
            }
            keysView?.setTextColor(Color.parseColor(if (keyboardOn) "#4D9BFF" else "#C5CBD8"))
        }

        private fun toggleMinimize() {
            if (!minimized) {
                savedWidth = params.width
                savedHeight = params.height
                web.visibility = View.GONE
                gripView?.visibility = View.GONE
                params.width = dp(160)
                params.height = dp(30)
                minimized = true
                windowManager.updateViewLayout(root, params)
            } else {
                restore()
            }
        }

        fun restore() {
            if (!minimized) return
            web.visibility = View.VISIBLE
            gripView?.visibility = View.VISIBLE
            params.width = savedWidth
            params.height = savedHeight
            minimized = false
            windowManager.updateViewLayout(root, params)
        }

        fun bringToFront() {
            try {
                windowManager.removeView(root)
                windowManager.addView(root, params)
            } catch (_: Exception) {
            }
        }

        fun clampToScreen() {
            try {
                if (!minimized) {
                    params.width = minOf(params.width, screenWidth())
                    params.height = minOf(params.height, screenHeight())
                }
                params.x = params.x.coerceIn(0, maxOf(0, screenWidth() - params.width))
                params.y = params.y.coerceIn(0, maxOf(0, screenHeight() - params.height))
                windowManager.updateViewLayout(root, params)
            } catch (_: Exception) {
            }
        }

        fun close() {
            windows.remove(this)
            try {
                web.stopLoading()
                (web.parent as? ViewGroup)?.removeView(web)
                web.destroy()
            } catch (_: Exception) {
            }
            try {
                windowManager.removeView(root)
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
