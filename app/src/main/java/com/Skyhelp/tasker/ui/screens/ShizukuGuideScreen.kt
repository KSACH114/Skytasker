package com.Skyhelp.tasker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/** 「什么是 Shizuku」讲解页（OOBE 与教程页共用） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuGuideScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("什么是 Shizuku") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Shizuku 是什么？",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Shizuku 是一个调用系统（shell / ADB）能力的工具。" +
                        "在PC版光遇中，电脑的shift键可以在不影响蜡烛的情况下独立地调出任务面板。" +
                        "本 App 会创建一个虚拟键盘，并且只保留 Shift 键，让光遇进入键盘模式，从而认可 Shift 的操作（大概是这么个意思）",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "怎么用？",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "1. 安装 Shizuku\n" +
                        "2. 打开Shizuku，找到分步骤指南，按提示完成配对并启动，或在视频平台搜“Shizuku安装教程”\n" +
                        "3. 回到本 App 点「去授权」，允许即可",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "下载地址（长按可复制）",
                style = MaterialTheme.typography.titleMedium
            )
            // SelectionContainer：让链接文本可长按选中 / 复制
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. https://apt.izzysoft.de/fdroid/index/apk/moe.shizuku.privileged.api",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "2. https://shizuku.rikka.app/zh-hans/download/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "提示：手机重启后需要重新启动一次 Shizuku 服务，这是它的正常机制。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
