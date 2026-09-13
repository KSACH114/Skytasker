package com.Skyhelp.tasker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Skyhelp.tasker.MainViewModel
import com.Skyhelp.tasker.core.AppLog
import com.Skyhelp.tasker.ui.components.LogPanel
import com.Skyhelp.tasker.ui.components.ShizukuStatusCard

/**
 * 主界面，自上而下：Shizuku 状态 → 悬浮窗开关 → 返回获取权限 → 日志。
 *
 * 悬浮窗开关是**一个按钮**：文字随真实运行状态在「显示悬浮窗 / 关闭悬浮窗」之间切换，
 * 按钮下方小字标明当前状态（状态来自 ServiceState，不是本地 UI 状态）。
 *
 * 提示一律走日志（不弹 Toast）。
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onGoTutorial: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shizuku by vm.shizuku.collectAsStateWithLifecycle()
    val overlayRunning by vm.overlayRunning.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Shizuku 状态（主体=授权，箭头=教程）----
        ShizukuStatusCard(
            connected = shizuku.connected,
            onGrantClick = {
                when {
                    // 已连接时卡片主体不可点，这里只是兜底
                    shizuku.connected -> Unit
                    // 服务在跑、只是本 App 未授权 → 弹系统授权框
                    shizuku.alive -> vm.requestShizukuPermission()
                    // 服务根本没跑 → 只记日志，不跳转、不弹窗
                    else -> AppLog.e("未连接 Shizuku：请先安装并启动 Shizuku / SUI 服务")
                }
            },
            onGuideClick = onGoTutorial
        )

        // ---- 悬浮窗开关（单按钮 + 状态小字）----
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = { if (overlayRunning) vm.stopOverlay() else vm.startOverlay() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (overlayRunning) "关闭悬浮窗" else "显示悬浮窗")
            }
            Text(
                text = if (overlayRunning) "当前悬浮窗已开启" else "当前悬浮窗已关闭",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ---- 手动回权限页（用户自己决定补权限）----
        OutlinedButton(
            onClick = onOpenPermissions,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("返回获取权限")
        }

        Text(
            text = "运行日志",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 日志区占据剩余空间（约一半页面）
        LogPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
