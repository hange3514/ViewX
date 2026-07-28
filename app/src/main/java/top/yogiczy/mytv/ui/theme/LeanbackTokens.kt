package top.yogiczy.mytv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 统一的设计 token：浮层透明度语义。
 * 替换各处裸写的 alpha 魔数，同类浮层必须使用同一语义值
 */
object LeanbackAlpha {
    /** 全屏遮罩（选台面板/快捷面板背景） */
    const val Scrim = 0.5f

    /** 浮层底色（信息卡、错误卡、临时面板、经典面板频道列） */
    const val PanelSurface = 0.8f

    /** 经典面板最左分组列（层叠最深处） */
    const val PanelSurfaceDeep = 0.9f

    /** 经典面板节目单列（层叠最浅处） */
    const val PanelSurfaceShallow = 0.7f

    /** 次级文字 */
    const val ContentHigh = 0.8f

    /** 提示性文字 */
    const val ContentMedium = 0.5f

    /** IPV4/延时等小标签底 */
    const val BadgeBackground = 0.3f

    /** 触摸按钮底（"收藏""退出回放"等） */
    const val TouchBackground = 0.2f
}

/** 聚焦反白语义对：容器反白、内容反色 */
val LeanbackFocusedContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.onBackground

val LeanbackFocusedContentColor: Color
    @Composable get() = MaterialTheme.colorScheme.background

/** 统一尺寸 token */
object LeanbackDimens {
    /** 列表项间距（面板/经典面板/设置页统一） */
    val ListItemSpacing = 10.dp

    /** 文本浮层最大宽度 */
    val OverlayMaxWidth = 500.dp

    /** chip/浮层内边距水平值 */
    val ChipPaddingHorizontal = 8.dp

    /** chip/浮层内边距垂直值 */
    val ChipPaddingVertical = 4.dp
}
