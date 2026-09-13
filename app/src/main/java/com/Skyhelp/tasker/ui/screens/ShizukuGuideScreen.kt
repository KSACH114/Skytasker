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
                text = "Shizuku 是一个免 root 就能调用系统（shell / ADB）能力的工具。" +
                        "有了它，我们不用清空手机、不用 root，就能创建虚拟键盘，" +
                        "让游戏把按键当成真实键盘。",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "怎么用？",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "1. 安装 Shizuku（或 SUI）\n" +
                        "2. 按 App 内指引启动服务（Android 11+ 可用「无线调试」启动，无需电脑）\n" +
                        "3. 回到本 App 点「去授权」，允许即可",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "提示：手机重启后需要重新启动一次 Shizuku 服务，这是它的正常机制。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
