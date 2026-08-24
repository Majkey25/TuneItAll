package com.tuneitall.tuner.autoscroll

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tuneitall.tuner.MainActivity
import com.tuneitall.tuner.R
import kotlin.math.abs

class AutoScrollOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: AutoScrollPreferences
    private var panel: View? = null
    private var bubble: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var lastPanelX: Int? = null
    private var lastPanelY: Int? = null
    private var speed = AutoScrollSpeed.DEFAULT_LEVEL

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = AutoScrollPreferences(this)
        speed = preferences.speed
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        speed = preferences.speed
        AutoScrollAccessibilityService.instance?.setSpeed(speed)
        when (intent?.action) {
            ACTION_EXIT -> {
                shutdown()
                return START_NOT_STICKY
            }

            ACTION_STOP -> stopScrolling()
        }
        if (!AutoScrollPermissionState.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.auto_scroll_overlay_missing, Toast.LENGTH_LONG).show()
            shutdown()
            return START_NOT_STICKY
        }
        showPanel()
        if (intent?.action == ACTION_START) startScrolling()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopScrolling()
        remove(panel)
        remove(bubble)
        panel = null
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showPanel() {
        remove(bubble)
        bubble = null
        if (panel == null) panel = inflate(R.layout.auto_scroll_overlay).also(::bindPanel)
        val view = panel ?: return
        val params = panelParams ?: panelLayoutParams().also { panelParams = it }
        params.x = AutoScrollOverlayPositioning.clampX(
            lastPanelX ?: params.x,
            screenSize().x,
            params.width,
            dp(OVERLAY_MARGIN_DP),
        )
        params.y = AutoScrollOverlayPositioning.clampY(
            lastPanelY ?: params.y,
            screenSize().y,
            panelHeight(view),
            dp(OVERLAY_MARGIN_DP),
        )
        if (!view.isAttachedToWindow) {
            windowManager.addView(view, params)
            animateIn(view)
        }
        render()
    }

    private fun bindPanel(view: View) {
        view.findViewById<View>(R.id.autoScrollDragHandle).setOnTouchListener(dragListener(isBubble = false))
        view.findViewById<Button>(R.id.autoScrollMinus).setOnClickListener {
            updateSpeed(AutoScrollSpeed.stepDown(speed))
        }
        view.findViewById<Button>(R.id.autoScrollPlus).setOnClickListener {
            updateSpeed(AutoScrollSpeed.stepUp(speed))
        }
        view.findViewById<Button>(R.id.autoScrollStartStop).setOnClickListener {
            if (AutoScrollAccessibilityService.instance?.isRunning() == true) stopScrolling() else startScrolling()
            render()
        }
        view.findViewById<Button>(R.id.autoScrollHide).setOnClickListener(::collapseToBubble)
        view.findViewById<Button>(R.id.autoScrollClose).setOnClickListener { shutdown() }
    }

    private fun startScrolling() {
        if (!AutoScrollPermissionState.isAccessibilityEnabled(this)) {
            Toast.makeText(this, R.string.auto_scroll_accessibility_missing, Toast.LENGTH_LONG).show()
            return
        }
        val service = AutoScrollAccessibilityService.instance
        if (service == null || !service.start(speed)) {
            Toast.makeText(this, R.string.auto_scroll_start_failed, Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun stopScrolling() {
        AutoScrollAccessibilityService.instance?.stop()
        render()
    }

    private fun updateSpeed(next: Int) {
        speed = AutoScrollSpeed.clamp(next)
        preferences.speed = speed
        AutoScrollAccessibilityService.instance?.setSpeed(speed)
        render()
    }

    private fun render() {
        val view = panel ?: return
        val running = AutoScrollAccessibilityService.instance?.isRunning() == true
        view.findViewById<TextView>(R.id.autoScrollSpeed).text = speed.toString()
        view.findViewById<TextView>(R.id.autoScrollStatus).setText(
            if (running) R.string.auto_scroll_running else R.string.auto_scroll_ready,
        )
        view.findViewById<Button>(R.id.autoScrollStartStop).apply {
            setText(if (running) R.string.auto_scroll_stop else R.string.auto_scroll_start)
            backgroundTintList = ColorStateList.valueOf(
                getColor(if (running) R.color.auto_scroll_surface else R.color.auto_scroll_green),
            )
            setTextColor(getColor(if (running) R.color.auto_scroll_text else R.color.auto_scroll_on_green))
        }
    }

    private fun collapseToBubble(view: View) {
        val params = panelParams ?: return
        lastPanelX = params.x
        lastPanelY = params.y
        remove(view)
        if (bubble == null) {
            bubble = inflate(R.layout.auto_scroll_bubble).apply {
                setOnClickListener { showPanel() }
                setOnTouchListener(dragListener(isBubble = true))
            }
        }
        val target = bubble ?: return
        val bubbleLayout = bubbleParams ?: bubbleLayoutParams().also { bubbleParams = it }
        bubbleLayout.x = AutoScrollOverlayPositioning.snapBubbleToEdge(
            params.x,
            screenSize().x,
            bubbleLayout.width,
        )
        bubbleLayout.y = AutoScrollOverlayPositioning.clampY(
            params.y,
            screenSize().y,
            bubbleLayout.height,
            dp(OVERLAY_MARGIN_DP),
        )
        if (!target.isAttachedToWindow) {
            windowManager.addView(target, bubbleLayout)
            animateIn(target)
        }
    }

    private fun dragListener(isBubble: Boolean): View.OnTouchListener {
        val touchSlop = dp(8)
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        return View.OnTouchListener { view, event ->
            val params = if (isBubble) bubbleParams else panelParams
            val target = if (isBubble) bubble else panel
            if (params == null || target == null) return@OnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - touchX).toInt()
                    val deltaY = (event.rawY - touchY).toInt()
                    dragging = dragging || abs(deltaX) > touchSlop || abs(deltaY) > touchSlop
                    val size = screenSize()
                    val width = params.width
                    val height = if (isBubble) params.height else panelHeight(target)
                    params.x = AutoScrollOverlayPositioning.clampX(
                        startX + deltaX,
                        size.x,
                        width,
                        dp(OVERLAY_MARGIN_DP),
                    )
                    params.y = AutoScrollOverlayPositioning.clampY(
                        startY + deltaY,
                        size.y,
                        height,
                        dp(OVERLAY_MARGIN_DP),
                    )
                    windowManager.updateViewLayout(target, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isBubble) {
                        params.x = AutoScrollOverlayPositioning.snapBubbleToEdge(params.x, screenSize().x, params.width)
                        windowManager.updateViewLayout(target, params)
                    } else {
                        lastPanelX = params.x
                        lastPanelY = params.y
                    }
                    if (!dragging) view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun notification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.auto_scroll_notification_title))
            .setContentText(getString(R.string.auto_scroll_notification_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                getString(R.string.auto_scroll_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun panelLayoutParams(): WindowManager.LayoutParams {
        val width = dp(PANEL_WIDTH_DP)
        val margin = dp(OVERLAY_MARGIN_DP)
        return baseLayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = (screenSize().x - width - margin).coerceAtLeast(margin)
            y = (screenSize().y * 0.18f).toInt()
        }
    }

    private fun bubbleLayoutParams(): WindowManager.LayoutParams =
        baseLayoutParams(dp(BUBBLE_SIZE_DP), dp(BUBBLE_SIZE_DP)).apply {
            x = screenSize().x - width
            y = (screenSize().y * 0.18f).toInt()
        }

    private fun baseLayoutParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun inflate(layout: Int): View {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_TuneItAll)
        return LayoutInflater.from(themedContext).inflate(layout, FrameLayout(themedContext), false)
    }

    private fun animateIn(view: View) {
        view.alpha = 0f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).start()
    }

    private fun remove(view: View?) {
        if (view?.isAttachedToWindow == true) windowManager.removeView(view)
    }

    private fun shutdown() {
        stopScrolling()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun screenSize(): Point = resources.displayMetrics.let { Point(it.widthPixels, it.heightPixels) }

    private fun panelHeight(view: View): Int = view.height.takeIf { it > 0 } ?: dp(PANEL_HEIGHT_DP)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SHOW = "com.tuneitall.tuner.autoscroll.SHOW"
        const val ACTION_START = "com.tuneitall.tuner.autoscroll.START"
        const val ACTION_STOP = "com.tuneitall.tuner.autoscroll.STOP"
        const val ACTION_EXIT = "com.tuneitall.tuner.autoscroll.EXIT"

        private const val NOTIFICATION_CHANNEL = "auto_scroll"
        private const val NOTIFICATION_ID = 2201
        private const val OVERLAY_MARGIN_DP = 12
        private const val PANEL_WIDTH_DP = 248
        private const val PANEL_HEIGHT_DP = 224
        private const val BUBBLE_SIZE_DP = 48
    }
}
