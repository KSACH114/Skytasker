package com.Skyhelp.tasker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.Skyhelp.tasker.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 点下按钮后等多少秒再发按键，留给用户切回游戏的时间 */
    private val SEND_DELAY_SEC = 5
    private val REQ_SHIZUKU = 1001

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pendingStart = false

    // registerForActivityResult 必须在 Activity 创建前注册（字段初始化即可）
    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val ok = Settings.canDrawOverlays(this)
            android.util.Log.i("SkyUhid", "[PERM] 悬浮窗授权返回，canDrawOverlays=$ok")
            if (ok) {
                pendingStart = false
                startUhidService()
            } else {
                toast("未授权悬浮窗，改用 ① 倒计时触发")
            }
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            android.util.Log.i("SkyUhid", "[PERM] POST_NOTIFICATIONS granted=$granted")
        }

    /** Shizuku 授权结果回调（requestPermission 弹的是 Shizuku 那边的对话框） */
    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        android.util.Log.i("SkyUhid", "[SHZ] 授权返回 requestCode=$requestCode granted=$granted")
        runOnUiThread {
            appendLog("Shizuku 授权结果: " + if (granted) "✅ 已授权" else "❌ 被拒绝（可在 SUI 里重新允许）")
            toast(if (granted) "Shizuku 已授权，可以测试发键了" else "Shizuku 授权被拒绝")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Shizuku.addRequestPermissionResultListener(shizukuPermListener)

        binding.btnShizuku.setOnClickListener { onShizukuClicked() }
        binding.btnVkbd.setOnClickListener { onVkbdTestClicked() }
        binding.btnOverlay.setOnClickListener { onStartKeyboardClicked() }
        binding.btnStop.setOnClickListener { onStopKeyboardClicked() }
    }

    override fun onResume() {
        super.onResume()
        // HyperOS 的悬浮窗授权页可能不回传 resultCode，回来时在这里复查补启动
        if (pendingStart && Settings.canDrawOverlays(this)) {
            pendingStart = false
            startUhidService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermListener)
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    // ---------------------------------------------------------------- 按钮⓪ 连接 Shizuku

    private fun onShizukuClicked() {
        appendLog("=== Shizuku 连接检测 ===")
        if (!ShizukuBridge.isAlive()) {
            appendLog("❌ Shizuku 服务未运行。\n" +
                    "请先：\n" +
                    "  1. 启动 SUI / Shizuku 服务\n" +
                    "  2. 回到这里再点一次")
            toast("Shizuku 未运行，看日志指引")
            return
        }
        if (ShizukuBridge.isPreV11()) {
            appendLog("❌ Shizuku 版本过旧（< v11），请升级。")
            toast("Shizuku 版本太旧")
            return
        }
        if (ShizukuBridge.hasPermission()) {
            appendLog("✅ Shizuku 已连接且已授权，跑环境检测…")
            executor.execute {
                // shell/root 域身份 + /dev/uhid 权限 + vkbd 路径，一次性看全
                val probe = ShizukuBridge.execAndWait(
                    arrayOf("/system/bin/sh", "-c",
                        "id; echo ---; ls -laZ /dev/uhid; echo ---; ls -l " +
                                ShizukuBridge.vkbdPath(this)),
                    8L
                )
                mainHandler.post {
                    appendLog(probe)
                    toast("环境检测完成，看日志")
                }
            }
            return
        }
        appendLog("Shizuku 已连接但未授权，发起授权请求…\n" +
                "（弹出的对话框来自 SUI / Shizuku，点「允许」）")
        try {
            ShizukuBridge.requestPermission(REQ_SHIZUKU)
        } catch (t: Throwable) {
            appendLog("授权请求异常: $t")
        }
    }

    // ---------------------------------------------------------------- 按钮① 发键测试

    private fun onVkbdTestClicked() {
        if (!ShizukuBridge.isAlive()) {
            appendLog("Shizuku 未运行，先点「⓪ 连接 Shizuku」。")
            toast("先连 Shizuku")
            return
        }
        if (!ShizukuBridge.hasPermission()) {
            appendLog("Shizuku 未授权，先点「⓪ 连接 Shizuku」。")
            toast("先完成授权")
            return
        }
        binding.btnVkbd.isEnabled = false
        appendLog("=== $SEND_DELAY_SEC 秒后通过 Shizuku 发送左 Shift ===")
        appendLog("请立刻切到《光·遇》，保持游戏在前台且屏幕已解锁。")
        countdown(SEND_DELAY_SEC) {
            toast("正在通过 Shizuku 发送 ⇧…")
            executor.execute {
                val session = ShizukuBridge.startVkbdSession(this@MainActivity)
                if (session == null) {
                    mainHandler.post {
                        binding.btnVkbd.isEnabled = true
                        appendLog("启动常驻 vkbd 失败（Shizuku 未连/未授权？）")
                        toast("启动失败，看日志")
                    }
                    return@execute
                }
                // 等键盘挂载稳定（create + START + 空报告 + 游戏枚举）
                Thread.sleep(600)
                val ok = session.sendKey()
                // 等按键完成（按住 500ms + 缓冲）
                Thread.sleep(800)
                session.close()
                mainHandler.post {
                    binding.btnVkbd.isEnabled = true
                    appendLog(if (ok) "发键成功（测试会话已关闭）" else "发键失败（写 stdin 异常）")
                    toast(if (ok) "✅ 已发键，看游戏反应" else "❌ 发送失败，看日志")
                }
            }
        }
    }

    private fun countdown(remain: Int, task: () -> Unit) {
        if (remain <= 0) {
            task()
            return
        }
        toast(remain.toString())
        mainHandler.postDelayed({ countdown(remain - 1, task) }, 1000L)
    }

    // ---------------------------------------------------------------- 按钮② 开启悬浮键盘 / ③ 停止

    private fun onStartKeyboardClicked() {
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.i("SkyUhid", "[PERM] 无悬浮窗权限，跳转授权页")
            pendingStart = true
            try {
                val i = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + packageName)
                )
                overlayLauncher.launch(i)
            } catch (t: Throwable) {
                android.util.Log.e("SkyUhid", "[PERM] 无法跳转悬浮窗授权页", t)
                pendingStart = false
                toast("系统不支持悬浮窗授权页，请手动在设置里开启")
            }
            return
        }
        startUhidService()
    }

    private fun startUhidService() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val i = Intent(this, UhidService::class.java).setAction(UhidService.ACTION_START)
        try {
            startForegroundService(i)
            android.util.Log.i("SkyUhid", "[UI] startForegroundService 已调用")
            appendLog("已开启悬浮键盘，切到《光·遇》点悬浮 ⇧ 按钮即可。")
        } catch (t: Throwable) {
            android.util.Log.e("SkyUhid", "[UI] startForegroundService 抛异常", t)
            toast("服务启动失败，看日志")
        }
    }

    private fun onStopKeyboardClicked() {
        stopService(Intent(this, UhidService::class.java))
        appendLog("已停止悬浮键盘服务。")
        toast("已停止")
    }

    // ---------------------------------------------------------------- 工具

    private fun appendLog(text: String) {
        binding.tvLog.append("\n" + text)
        binding.tvLog.post {
            val parent = binding.tvLog.parent
            if (parent is android.widget.ScrollView) {
                parent.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        android.util.Log.i("SkyUhid", "[UI] $msg")
    }
}
