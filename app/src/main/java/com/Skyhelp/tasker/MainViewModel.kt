package com.Skyhelp.tasker

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import com.Skyhelp.tasker.core.AppLog
import com.Skyhelp.tasker.core.ServiceState
import com.Skyhelp.tasker.permission.PermUi
import com.Skyhelp.tasker.permission.readPerms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

data class ShizukuState(
    val alive: Boolean = false,
    val granted: Boolean = false
) {
    /** 服务在跑 + 已授权 = 可用 */
    val connected: Boolean get() = alive && granted
}

/**
 * 主界面 / OOBE 的状态源：Shizuku 状态、权限快照、悬浮键盘服务开关。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val REQ_SHIZUKU = 1001
    }

    private val _shizuku = MutableStateFlow(ShizukuState())
    val shizuku: StateFlow<ShizukuState> = _shizuku.asStateFlow()

    private val _perms = MutableStateFlow(PermUi(false, false, false, false))
    val perms: StateFlow<PermUi> = _perms.asStateFlow()

    /** 悬浮键盘服务是否在跑（真实状态，由 UhidService 在 onCreate/onDestroy 里写） */
    val overlayRunning: StateFlow<Boolean> = ServiceState.running

    // ---- Shizuku 状态监听 ----
    private val onBinderReceived = Shizuku.OnBinderReceivedListener {
        AppLog.i("Shizuku 服务已连接")
        refresh()
    }
    private val onBinderDead = Shizuku.OnBinderDeadListener {
        AppLog.w("Shizuku 服务已断开")
        refresh()
    }
    private val onPermResult = Shizuku.OnRequestPermissionResultListener { _, result ->
        val granted = result == PackageManager.PERMISSION_GRANTED
        if (granted) {
            AppLog.i("Shizuku 授权结果：已授权")
        } else {
            // 两种常见成因给不同提示，否则用户无从下手
            AppLog.e(
                if (ShizukuBridge.isBlockedByUser())
                    "Shizuku 授权被拒绝。系统此前已拦截过授权弹窗，请重启 Shizuku 服务或重启手机后再试"
                else
                    "Shizuku 授权被拒绝。若设备装有 SUI，请到 KernelSU / ReSukiSU 管理器里给本 App 授权，SUI 不会弹出系统授权框"
            )
        }
        refresh()
    }

    init {
        // sticky：注册时立刻回填一次当前状态，避免界面初始为「未连接」
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermResult)
        refresh()
    }

    /** 刷新 Shizuku + 全部权限状态（Activity.onResume 也应调用一次） */
    fun refresh() {
        _shizuku.value = ShizukuState(
            alive = ShizukuBridge.isAlive(),
            granted = ShizukuBridge.hasPermission()
        )
        _perms.value = readPerms(getApplication())
    }

    /** 发起 Shizuku 授权请求（结果由 onPermResult 刷新） */
    fun requestShizukuPermission() {
        if (ShizukuBridge.isPreV11()) {
            AppLog.e("Shizuku 版本过旧（低于 v11），请升级 Shizuku App")
            return
        }
        if (!ShizukuBridge.isAlive()) {
            AppLog.e("Shizuku 服务未运行：请先打开 Shizuku App 启动服务")
            return
        }
        if (ShizukuBridge.isBlockedByUser()) {
            // 「永久拒绝」状态：再点也不会弹窗（HyperOS/ColorOS 上点一次拒绝就进入此状态）
            AppLog.e("授权已被系统阻止（此前拒绝过）：请重启 Shizuku 服务或重启手机后再试")
        }
        try {
            ShizukuBridge.requestPermission(REQ_SHIZUKU)
            AppLog.i("已发送授权请求，请在弹窗中选「允许」")
        } catch (t: Throwable) {
            AppLog.e("请求 Shizuku 授权失败：$t")
        }
    }

    /** 开启悬浮键盘（前台服务） */
    fun startOverlay() {
        val ctx = getApplication<Application>()
        try {
            ctx.startForegroundService(
                Intent(ctx, UhidService::class.java).setAction(UhidService.ACTION_START)
            )
            AppLog.i("已请求开启悬浮键盘")
        } catch (t: Throwable) {
            AppLog.e("开启悬浮键盘失败：$t")
        }
    }

    /** 关闭悬浮键盘 */
    fun stopOverlay() {
        val ctx = getApplication<Application>()
        try {
            ctx.stopService(Intent(ctx, UhidService::class.java))
            AppLog.i("已请求关闭悬浮键盘")
        } catch (t: Throwable) {
            AppLog.e("关闭悬浮键盘失败：$t")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(onBinderReceived)
        Shizuku.removeBinderDeadListener(onBinderDead)
        Shizuku.removeRequestPermissionResultListener(onPermResult)
    }
}
