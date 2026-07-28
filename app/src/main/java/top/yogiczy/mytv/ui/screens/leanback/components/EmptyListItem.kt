package top.yogiczy.mytv.ui.screens.leanback.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.ui.theme.LeanbackAlpha

/**
 * 统一的空状态占位（不可聚焦、无选中态），
 * 用于"当前频道暂无节目"等空列表提示
 */
@Composable
fun LeanbackEmptyListItem(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = LeanbackAlpha.ContentHigh),
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
