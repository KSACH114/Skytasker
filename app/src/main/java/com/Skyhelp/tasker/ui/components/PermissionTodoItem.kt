package com.Skyhelp.tasker.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
 * 权限 todo 项 —— **整条就是一个按钮**：点任意位置即触发 [onGrant]。
 *
 * 改动原因（用户反馈）：原来的行内「去授权」小按钮字样不够明显。
 * 现在整条可点 + 描边提示，行尾那行字只是文字提示（不是独立按钮）。
 *
 * - 未完成：描边（outlineVariant）+ 可点击，行尾提示「点击开启」
 * - 已完成：绿色勾 + 绿字 + 行尾「已开启」，**不再响应点击**
 *
 * 状态不只靠颜色表达（配勾号图标 + 文案），满足无障碍要求。
 */
@Composable
fun PermissionTodoItem(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val success = LocalSuccessColor.current
    val tint = if (granted) success else MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(12.dp)
    val borderColor =
        if (granted) success.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(
                enabled = !granted,
                onClickLabel = "获取「$title」权限",
                role = Role.Button,
                onClick = onGrant
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(2.dp, tint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (granted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已完成",
                    tint = success,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (granted) success else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 纯文字提示（非独立控件）—— 整条都能点，这里只是告诉用户「可以点」
        Text(
            text = if (granted) "已开启" else "点击开启",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) success else MaterialTheme.colorScheme.primary
        )
    }
}
