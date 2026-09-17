package com.Skyhelp.tasker.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Skyhelp.tasker.R

/** OOBE 第 1 步：欢迎页 */
@Composable
fun WelcomeScreen(onNext: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 双层图标：底层 #0B3B8C 圆角方块 + 上层插画（与桌面自适应图标同源同比例）
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // 0.79 ≈ 自适应图标可见区 72dp 里内容占 57dp 的比例，保证与桌面图标观感一致
                modifier = Modifier.fillMaxSize(0.79f)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "欢迎使用 Skytasker",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "不用清空蜡烛，也能随时查看《光·遇》任务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("下一步")
        }
    }
}
