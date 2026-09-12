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

    /** 常驻 vkbd 进程会话：键盘挂一次，点键只写 stdin，避免游戏反复插拔卡顿 */
    @Volatile
    private var vkbdSession: VkbdSession? = null

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
        ensureKeyboardAsync()
        Log.i(TAG, "[SVC] Shizuku alive=${ShizukuBridge.isAlive()} vkbd=${ShizukuBridge.vkbdPath(this)}")

        if (action == ACTION_SEND_SHIFT) {
            sendShiftAsync("intent")
        }
        return START_STICKY
    }

    // ---------------------------------------------------------------- 键盘常驻

    /** 启动（或复用）常驻 vkbd 进程，键盘挂载一次后不销毁 */
    private fun ensureKeyboardAsync() {
        worker.execute {
            val s = vkbdSession
            if (s != null && s.isAlive()) {
                Log.i(TAG, "[SVC] vkbd 常驻进程已存活，无需重建")
                return@execute
            }
            Log.i(TAG, "[SVC] 启动常驻 vkbd…")
            val session = ShizukuBridge.startVkbdSession(this@UhidService)
            if (session == null) {
                Log.e(TAG, "[SVC] 常驻 vkbd 启动失败（Shizuku 未连/未授权？）")
                main.post {
                    Toast.makeText(
                        this@UhidService,
                        "Shizuku 未连接，无法启动键盘",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@execute
            }
            vkbdSession = session
            Log.i(TAG, "[SVC] 常驻 vkbd 已启动")
        }
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

            // 常驻会话不在（或已死）→ 尝试重建
            var s = vkbdSession
            if (s == null || !s.isAlive()) {
                Log.w(TAG, "[SEND] vkbd 常驻进程不在/已死，重建")
                s = ShizukuBridge.startVkbdSession(this@UhidService)
                vkbdSession = s
            }

            val cur = s
            if (cur == null) {
                Log.e(TAG, "[SEND] 无可用 vkbd 会话（Shizuku 未连/未授权？）")
                main.post {
                    Toast.makeText(
                        this@UhidService,
                        "Shizuku 未连接，请回 App 点「⓪ 连接 Shizuku」",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@execute
            }

            val ok = cur.sendKey()
            Log.i(TAG, "[SEND] sendKey 结果=$ok")
            main.post {
                Toast.makeText(
                    this@UhidService,
                    if (ok) "已发送 ⇧" else "发送失败，看日志",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ---------------------------------------------------------------- 收尾

    override fun onDestroy() {
        Log.i(TAG, "[SVC] onDestroy thread=${Thread.currentThread().name}")

        // 销毁常驻 vkbd 进程（关 stdin → vkbd EOF → destroy 键盘 → 退出）
        vkbdSession?.let { s ->
            Log.i(TAG, "[SVC] 关闭常驻 vkbd 进程")
            try {
                s.close()
            } catch (t: Throwable) {
                Log.e(TAG, "[SVC] 关闭 vkbd 会话异常", t)
            }
        }
        vkbdSession = null

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
