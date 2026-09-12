package com.Skyhelp.tasker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import java.util.concurrent.Executors

/**
 * 前台服务：持有悬浮 ⇧ 按钮。
 *
 * 悬浮窗用 FLAG_NOT_FOCUSABLE，点击时不会抢走《光·遇》的窗口焦点，
 * 这样通过 Shizuku 发出去的 Shift 键才能被游戏收到。
 */
class UhidService : Service() {

    companion object {
        const val ACTION_START = "com.Skyhelp.tasker.action.START"
        const val ACTION_STOP = "com.Skyhelp.tasker.action.STOP"
        const val ACTION_SEND_SHIFT = "com.Skyhelp.tasker.action.SEND_SHIFT"

        private const val TAG = "SkyUhid"
        private const val CHANNEL_ID = "uhid_fgs"
        private const val NOTIF_ID = 101
        /** 两次发送的最小间隔：vkbd 一次完整执行约 1~2s，间隔太短会前后重叠 */
        private const val DEBOUNCE_MS = 2000L
    }

    private var wm: WindowManager? = null
    private var overlayView: TextView? = null
    private var overlayLp: WindowManager.LayoutParams? = null
    private val overlayBgNormal = GradientDrawable()
    private val overlayBgPressed = GradientDrawable()

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var lastSendAt = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        initOverlayDrawables()
        Log.i(TAG, "[SVC] onCreate thread=${Thread.currentThread().name}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())

        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "[SVC] onStartCommand action=$action startId=$startId")

        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        addOverlayIfNeeded()
        Log.i(TAG, "[SVC] Shizuku alive=${ShizukuBridge.isAlive()} vkbd=${ShizukuBridge.vkbdPath(this)}")

        if (action == ACTION_SEND_SHIFT) {
            sendShiftAsync("intent")
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- 前台服务

    private fun startForegroundCompat(notif: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                Log.i(TAG, "[SVC] startForeground ok type=SPECIAL_USE sdk=${Build.VERSION.SDK_INT}")
            } else {
                startForeground(NOTIF_ID, notif)
                Log.i(TAG, "[SVC] startForeground ok (无类型) sdk=${Build.VERSION.SDK_INT}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "[SVC] startForeground 抛异常！", t)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        ch.description = getString(R.string.notif_channel_desc)
        nm.createNotificationChannel(ch)
        Log.i(TAG, "[SVC] 通知渠道已创建 channelId=$CHANNEL_ID")
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    // ---------------------------------------------------------------- 悬浮窗

    private fun initOverlayDrawables() {
        overlayBgNormal.setColor(0xCC1A1A1A.toInt())
        overlayBgNormal.cornerRadius = 56f
        overlayBgNormal.setStroke(3, 0xFFFFFFFF.toInt())
        overlayBgPressed.setColor(0xE66E4AFF.toInt())
        overlayBgPressed.cornerRadius = 56f
        overlayBgPressed.setStroke(3, 0xFFFFFFFF.toInt())
    }

    private fun addOverlayIfNeeded() {
        if (overlayView != null) {
            Log.i(TAG, "[OVERLAY] 悬浮窗已存在，跳过")
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "[OVERLAY] canDrawOverlays=false，拒绝 addView（未授权）")
            Toast.makeText(this, "未授权悬浮窗，请在设置里开启", Toast.LENGTH_LONG).show()
            return
        }
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager
        if (wm == null) {
            Log.e(TAG, "[OVERLAY] getSystemService(WINDOW_SERVICE) 为 null")
            return
        }
        this.wm = wm

        val themed = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault)
        val b = TextView(themed).apply {
            text = "⇧"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            val d = resources.displayMetrics.density
            val pad = (14 * d).toInt()
            setPadding(pad, pad, pad, pad)
            minWidth = (56 * d).toInt()
            minHeight = (56 * d).toInt()
            background = overlayBgNormal
            setOnTouchListener(DragClickTouchListener())
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val d = resources.displayMetrics.density
            x = (60 * d).toInt()
            y = (400 * d).toInt()
        }

        try {
            wm.addView(b, lp)
            overlayView = b
            overlayLp = lp
            Log.i(TAG, "[OVERLAY] addView 成功 x=${lp.x} y=${lp.y} flags=0x${Integer.toHexString(lp.flags)}")
        } catch (t: Throwable) {
            Log.e(TAG, "[OVERLAY] addView 抛异常", t)
            Toast.makeText(this, "悬浮窗创建失败，看日志", Toast.LENGTH_LONG).show()
        }
    }

    private fun flashOverlay() {
        val v = overlayView ?: return
        v.background = overlayBgPressed
        main.postDelayed({ overlayView?.background = overlayBgNormal }, 150L)
    }

    /** 拖动 + 点击判定：位移超阈值算拖动（移动按钮），否则算点击（发键） */
    private inner class DragClickTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var rawX = 0f
        private var rawY = 0f
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val lp = overlayLp ?: return true
                    startX = lp.x
                    startY = lp.y
                    rawX = e.rawX
                    rawY = e.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val lp = overlayLp ?: return true
                    val dx = (e.rawX - rawX).toInt()
                    val dy = (e.rawY - rawY).toInt()
                    if (abs(dx) > 16 || abs(dy) > 16) dragging = true
                    if (dragging) {
                        lp.x = clampToScreen(startX + dx, isX = true)
                        lp.y = clampToScreen(startY + dy, isX = false)
                        wm?.updateViewLayout(v, lp)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        flashOverlay()
                        sendShiftAsync("overlay")
                    }
                    return true
                }
            }
            return false
        }

        private fun clampToScreen(value: Int, isX: Boolean): Int {
            val screen = if (isX) resources.displayMetrics.widthPixels
            else resources.displayMetrics.heightPixels
            val size = if (isX) overlayView?.width ?: 0 else overlayView?.height ?: 0
            var v = value
            if (v < 0) v = 0
            if (v > screen - size - 20) v = screen - size - 20
            return v
        }
    }

    // ---------------------------------------------------------------- 发送

    private fun sendShiftAsync(source: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastSendAt < DEBOUNCE_MS) {
            Log.w(TAG, "[SEND] 去抖忽略 source=$source 距上次=${now - lastSendAt}ms")
            return
        }
        lastSendAt = now
        worker.execute {
            Log.i(TAG, "[SEND] 开始 source=$source thread=${Thread.currentThread().name}")
            val alive = ShizukuBridge.isAlive()
            val perm = ShizukuBridge.hasPermission()
            if (alive && perm) {
                Log.i(TAG, "[SEND] 走 Shizuku 路径（vkbd exec）")
                val r = ShizukuBridge.runVkbd(this@UhidService)
                Log.i(TAG, "[SEND] Shizuku 结果:\n$r")
                val ok = r.startsWith("VKBD_OK")
                main.post {
                    Toast.makeText(
                        this@UhidService,
                        if (ok) "已通过 Shizuku 发送 ⇧" else "发送失败，看日志",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.w(TAG, "[SEND] Shizuku 不可用 alive=$alive perm=$perm")
                main.post {
                    Toast.makeText(
                        this@UhidService,
                        "Shizuku 未连接，请回 App 点「⓪ 连接 Shizuku」",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ---------------------------------------------------------------- 收尾

    override fun onDestroy() {
        Log.i(TAG, "[SVC] onDestroy thread=${Thread.currentThread().name}")
        overlayView?.let { v ->
            try {
                wm?.removeView(v)
                Log.i(TAG, "[OVERLAY] removeView 成功")
            } catch (t: Throwable) {
                Log.e(TAG, "[OVERLAY] removeView 抛异常", t)
            }
        }
        overlayView = null
        worker.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
