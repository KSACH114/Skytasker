package com.Skyhelp.tasker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.Skyhelp.tasker.ui.theme.LocalSuccessColor

/**
 * 主界面顶部的 Shizuku 状态卡片 —— **左右分区，两个动作分开**：
 * - 左侧主体（圆点 + 文案）：点击 = **获取 Shizuku 授权**（已连接时不再响应）
 * - 右侧箭头按钮：点击 = **跳转教程页**
 *
 * 改动原因（用户反馈）：原来是整卡都跳教程，「授权」和「看教程」混在一起，
 * 未连接时点它只会跳走，拿不到授权入口。
 */
@Composable
fun ShizukuStatusCard(
    connected: Boolean,
    onGrantClick: () -> Unit,
    onGuideClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val success = LocalSuccessColor.current
    val statusColor = if (connected) success else MaterialTheme.colorScheme.outline

    Card(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ---- 主体区：点击 = 授权 ----
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        enabled = !connected,
                        onClickLabel = "获取 Shizuku 授权",
                        role = Role.Button,
                        onClick = onGrantClick
                    )
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (connected) "Shizuku 已连接" else "Shizuku 未连接",
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                    Text(
                        text = if (connected) "已授权，可正常使用" else "点击获取授权",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 箭头区：点击 = 看教程 ----
            IconButton(
                onClick = onGuideClick,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "查看连接教程",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
