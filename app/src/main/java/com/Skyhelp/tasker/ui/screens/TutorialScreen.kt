package com.Skyhelp.tasker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 教程页：Shizuku 连接 + 使用步骤 + 常见问题 */
@Composable
fun TutorialScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "使用教程",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(8.dp))

        Section(
            title = "第 1 步 · 连接 Shizuku",
            body = "安装并启动 Shizuku（或 SUI）。回到本 App 主页，点顶部的状态卡片发起授权，" +
                    "在弹窗里点「允许」。授权成功后状态卡片会显示「已连接」。"
        )

        Section(
            title = "第 2 步 · 开启悬浮窗",
            body = "在权限页授权「悬浮窗」后，回到主页会自动开启悬浮窗，屏幕上会出现一个悬浮图标。\n" +
                    "如果没出现，点主页的「显示悬浮窗」按钮手动开启。"
        )

        Section(
            title = "第 3 步 · 在游戏里使用",
            body = "先在游戏里领取任务，然后点一下悬浮图标，即可查看任务列表。\n" +
                    "图标可以拖到任意位置，松手后会记住位置，横竖屏切换也不会跑出屏幕。"
        )

        Section(
            title = "常见问题",
            body = "• 按了没反应？请确认屏幕已解锁、游戏在前台，且已在游戏里领取过任务。\n" +
                    "• 重启手机后失效？重新启动一次 Shizuku 服务即可，这是它的正常机制。\n" +
                    "• 悬浮按钮不见了？回到 App 重新点「显示悬浮窗」。\n" +
                    "• 什么是 Shizuku？一个免 root 调用系统（shell / ADB）能力的工具，不需要 root 手机。"
        )
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}
