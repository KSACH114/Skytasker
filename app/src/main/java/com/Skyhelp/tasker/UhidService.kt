package com.Skyhelp.tasker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
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
    private var positioner: OverlayPositioner? = null
    private var dragListener: DragClickTouchListener? = null
    private var displayListener: DisplayManager.DisplayListener? = null
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
        registerDisplayListener()
        Log.i(TAG, "[SVC] onCreate thread=${Thread.currentThread().name}")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "[POS] onConfigurationChanged orientation=${newConfig.orientation}")
        applyScreenChange()
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
        val listener = DragClickTouchListener()
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
            setOnTouchListener(listener)
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
            // 坐标系从屏幕左上角起算（具体位置由 OverlayPositioner 按相对比例算）
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            wm.addView(b, lp)
            overlayView = b
            overlayLp = lp
            dragListener = listener

            // 按上次保存的相对位置摆位（无记录则用默认值），不再硬编码绝对像素
            val p = OverlayPositioner(wm, b, lp)
            p.refresh()
            val (rx, ry) = p.loadRel(this)
            val (x0, y0) = p.relToAbs(rx, ry)
            lp.x = x0
            lp.y = y0
            wm.updateViewLayout(b, lp)
            positioner = p

            // 首次布局后 View 尺寸才有效，用真实尺寸再校正一次位置
            var sizeFixed = false
            b.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                if (!sizeFixed && positioner === p && view.width > 0) {
                    sizeFixed = true
                    val (cx, cy) = p.relToAbs(rx, ry)
                    if (cx != lp.x || cy != lp.y) p.applyNow(cx, cy)
                }
            }

            Log.i(TAG, "[OVERLAY] addView 成功 x=$x0 y=$y0 relX=$rx relY=$ry "
                    + "flags=0x${Integer.toHexString(lp.flags)}")
        } catch (t: Throwable) {
            Log.e(TAG, "[OVERLAY] addView 抛异常", t)
            Toast.makeText(this, "悬浮窗创建失败，看日志", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- 屏幕尺寸变化

    /** 兜底监听：捕获折叠屏/多窗口/外接屏的尺寸变化（主路径是 onConfigurationChanged） */
    private fun registerDisplayListener() {
        if (displayListener != null) return
        val dm = getSystemService(DISPLAY_SERVICE) as? DisplayManager ?: return
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) applyScreenChange()
            }
        }
        dm.registerDisplayListener(l, main)
        displayListener = l
        Log.i(TAG, "[POS] DisplayListener 已注册")
    }

    private fun unregisterDisplayListener() {
        val l = displayListener ?: return
        (getSystemService(DISPLAY_SERVICE) as? DisplayManager)?.unregisterDisplayListener(l)
        displayListener = null
        Log.i(TAG, "[POS] DisplayListener 已注销")
    }

    /** 屏幕尺寸/方向变化：按相对比例重算位置并平滑归位 */
    private fun applyScreenChange() {
        val p = positioner ?: return
        // 先按「变化前」的尺寸算出相对比例
        val (rx, ry) = p.absToRel()
        // 刷新屏幕尺寸；没变就直接返回（吸收 config + display 双触发的冗余回调）
        if (!p.refresh()) return
        Log.i(TAG, "[POS] 屏幕尺寸变化 → 按比例归位 relX=$rx relY=$ry")
        dragListener?.resetDragging() // 打断拖动，避免松手时用旧坐标反算比例
        val (x, y) = p.relToAbs(rx, ry)
        p.animateTo(x, y)
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
        var dragging = false
            private set

        /** 供旋转时打断拖动 */
        fun resetDragging() {
            dragging = false
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = positioner ?: return true
            val lp = overlayLp ?: return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    p.cancelAnimation() // 拖动打断归位动画，保证跟手
                    startX = lp.x
                    startY = lp.y
                    rawX = e.rawX
                    rawY = e.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - rawX).toInt()
                    val dy = (e.rawY - rawY).toInt()
                    if (abs(dx) > 16 || abs(dy) > 16) dragging = true
                    if (dragging) {
                        p.applyNow(p.clampX(startX + dx), p.clampY(startY + dy))
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        p.saveRel(this@UhidService) // 拖动结束记住相对位置
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        flashOverlay()
                        sendShiftAsync("overlay")
                    }
                    dragging = false
                    return true
                }
            }
            return false
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

        // 收起悬浮窗相关监听与动画（防泄漏）
        unregisterDisplayListener()
        positioner?.cancelAnimation()
        positioner = null
        dragListener = null

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
