package com.Skyhelp.tasker.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.Skyhelp.tasker.ShizukuBridge

/** 权限引导页 / 主界面用的权限快照 */
data class PermUi(
    /** Shizuku 服务在跑 且 本 App 已授权 */
    val shizuku: Boolean,
    /** 悬浮窗（SYSTEM_ALERT_WINDOW） */
    val overlay: Boolean,
    /** 通知（Android 13+ 才需要运行时申请） */
    val notif: Boolean,
    /** 设备上是否装了 Shizuku / SUI 管理器 */
    val shizukuInstalled: Boolean
)

/** 可能的 Shizuku 管理器包名（官方 Shizuku / SUI） */
private val SHIZUKU_PKGS = listOf(
    "moe.shizuku.manager",          // 官方 Shizuku
    "moe.shizuku.privileged.api",   // 部分版本
    "com.rikka.sui"                 // SUI
)

fun readPerms(c: Context): PermUi = PermUi(
    shizuku = ShizukuBridge.isAlive() && ShizukuBridge.hasPermission(),
    overlay = Settings.canDrawOverlays(c),
    notif = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
    shizukuInstalled = SHIZUKU_PKGS.any { pkg ->
        runCatching { c.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }
)
