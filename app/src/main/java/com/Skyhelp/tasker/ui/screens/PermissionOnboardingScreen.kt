package com.Skyhelp.tasker.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Skyhelp.tasker.MainViewModel
import com.Skyhelp.tasker.core.AppLog
import com.Skyhelp.tasker.ui.components.PermissionTodoItem

/**
 * OOBE 第 2 步：权限引导（也是主页「返回获取权限」进的那一页）。
 *
 * - 每项整条可点 = 直接去申请该权限（没有行内「去授权」小按钮）
 * - 「完成」仅在必需项（Shizuku + 悬浮窗）都拿到时才可点，否则置灰
 * - 底部留一个弱化的「暂不设置，先进主界面」出口，避免没装 Shizuku 的用户被永久卡死
 * - 提示一律走日志（不弹 Toast）
 */
@Composable
fun PermissionOnboardingScreen(
    vm: MainViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val perms by vm.perms.collectAsStateWithLifecycle()
    val shizukuState by vm.shizuku.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showGuide by remember { mutableStateOf(false) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refresh() }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refresh() }

    if (showGuide) {
        ShizukuGuideScreen(onBack = {
            showGuide = false
            vm.refresh()
        })
        return
    }

    // 「完成」的置灰条件：Shizuku + 悬浮窗（通知属可选，不算必需）
    val canFinish = perms.shizuku && perms.overlay

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "需要获取一些权限来提升体验",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "点击下面的条目即可授权，完成后会显示 ✔",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        PermissionTodoItem(
            title = "Shizuku",
            description = "免 root 获得系统能力，用于创建虚拟键盘",
            granted = perms.shizuku,
            onGrant = {
                // 服务活着才能申请授权；没跑就只记日志
                if (shizukuState.alive) {
                    vm.requestShizukuPermission()
                } else {
                    AppLog.e("未连接 Shizuku：请先安装并启动 Shizuku / SUI 服务")
                }
            }
        )

        TextButton(onClick = { showGuide = true }) {
            Text("什么是 Shizuku？")
        }

        PermissionTodoItem(
            title = "悬浮窗",
            description = "在游戏画面上方显示 ⇧ 按钮",
            granted = perms.overlay,
            onGrant = {
                overlayLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        PermissionTodoItem(
            title = "通知（可选）",
            description = "显示键盘常驻的运行状态",
            granted = perms.notif,
            onGrant = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    AppLog.i("当前系统（Android 12 及以下）无需申请通知权限")
                }
            }
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onFinish,
            enabled = canFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("完成")
        }

        // 弱化出口：避免没装 Shizuku 的用户永远进不去主界面
        TextButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("暂不设置，先进主界面")
        }
    }
}
