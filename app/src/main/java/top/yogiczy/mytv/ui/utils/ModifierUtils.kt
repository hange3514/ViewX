package top.yogiczy.mytv.ui.utils

import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

fun Modifier.handleLeanbackKeyEvents(
    onKeyTap: Map<Int, () -> Unit> = emptyMap(),
    onKeyLongTap: Map<Int, () -> Unit> = emptyMap(),
    onKeyPressStart: Map<Int, () -> Unit> = emptyMap(),
    onKeyPressEnd: Map<Int, () -> Unit> = emptyMap(),
): Modifier = composed {
    val keyDownMap = remember { mutableMapOf<Int, Boolean>() }

    onPreviewKeyEvent {
        when (it.nativeKeyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (it.nativeKeyEvent.repeatCount == 0) {
                    keyDownMap[it.nativeKeyEvent.keyCode] = true
                    onKeyPressStart[it.nativeKeyEvent.keyCode]?.invoke()
                } else if (it.nativeKeyEvent.repeatCount == 1) {
                    keyDownMap[it.nativeKeyEvent.keyCode] = false
                    onKeyLongTap[it.nativeKeyEvent.keyCode]?.invoke()
                }
            }

            KeyEvent.ACTION_UP -> {
                val wasPressed = keyDownMap.containsKey(it.nativeKeyEvent.keyCode)
                val wasShortPress = keyDownMap.remove(it.nativeKeyEvent.keyCode) == true
                // 焦点在 DOWN 与 UP 之间移走时，UP 会落到没记录过 DOWN 的实例上，
                // 只对确实按下过的键回调 pressEnd，避免虚假事件
                if (wasPressed) onKeyPressEnd[it.nativeKeyEvent.keyCode]?.invoke()
                if (wasShortPress) {
                    onKeyTap[it.nativeKeyEvent.keyCode]?.invoke()
                }
            }
        }

        false
    }
}

fun Modifier.handleLeanbackDragGestures(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
): Modifier {
    val speedThreshold = 100.dp
    val distanceThreshold = 10.dp

    val verticalTracker = VelocityTracker()
    var verticalDragOffset = 0f
    val horizontalTracker = VelocityTracker()
    var horizontalDragOffset = 0f


    return this then pointerInput(Unit) {
        detectVerticalDragGestures(
            // 每次手势开始重置位移与速度采样，避免上一次手势的残留污染本次判定
            onDragStart = {
                verticalDragOffset = 0f
                verticalTracker.resetTracking()
            },
            onDragEnd = {
                if (verticalDragOffset.absoluteValue > distanceThreshold.toPx()) {
                    if (verticalTracker.calculateVelocity().y > speedThreshold.toPx()) {
                        onSwipeDown()
                    } else if (verticalTracker.calculateVelocity().y < -speedThreshold.toPx()) {
                        onSwipeUp()
                    }
                }
            },
        ) { change, dragAmount ->
            verticalDragOffset += dragAmount
            verticalTracker.addPosition(change.uptimeMillis, change.position)
        }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = {
                horizontalDragOffset = 0f
                horizontalTracker.resetTracking()
            },
            onDragEnd = {
                if (horizontalDragOffset.absoluteValue > distanceThreshold.toPx()) {
                    if (horizontalTracker.calculateVelocity().x > speedThreshold.toPx()) {
                        onSwipeRight()
                    } else if (horizontalTracker.calculateVelocity().x < -speedThreshold.toPx()) {
                        onSwipeLeft()
                    }
                }
            },
        ) { change, dragAmount ->
            horizontalDragOffset += dragAmount
            horizontalTracker.addPosition(change.uptimeMillis, change.position)
        }
    }
}

fun Modifier.handleLeanbackKeyEvents(
    key: Any = Unit,
    onLeft: () -> Unit = {},
    onLongLeft: () -> Unit = {},
    onLeftDown: () -> Unit = {},
    onLeftUp: () -> Unit = {},
    onRight: () -> Unit = {},
    onLongRight: () -> Unit = {},
    onRightDown: () -> Unit = {},
    onRightUp: () -> Unit = {},
    onUp: () -> Unit = {},
    onLongUp: () -> Unit = {},
    onDown: () -> Unit = {},
    onLongDown: () -> Unit = {},
    onSelect: () -> Unit = {},
    onLongSelect: () -> Unit = {},
    onSettings: () -> Unit = {},
    onNumber: (Int) -> Unit = {},
): Modifier = composed {
    val keyDownMap = remember { mutableMapOf<Int, Boolean>() }

    fun handleTap(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> onLeft()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> onRight()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> onUp()
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> onDown()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> onSelect()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_HELP, KeyEvent.KEYCODE_H -> onSettings()
            KeyEvent.KEYCODE_L -> onLongSelect()
            KeyEvent.KEYCODE_0 -> onNumber(0)
            KeyEvent.KEYCODE_1 -> onNumber(1)
            KeyEvent.KEYCODE_2 -> onNumber(2)
            KeyEvent.KEYCODE_3 -> onNumber(3)
            KeyEvent.KEYCODE_4 -> onNumber(4)
            KeyEvent.KEYCODE_5 -> onNumber(5)
            KeyEvent.KEYCODE_6 -> onNumber(6)
            KeyEvent.KEYCODE_7 -> onNumber(7)
            KeyEvent.KEYCODE_8 -> onNumber(8)
            KeyEvent.KEYCODE_9 -> onNumber(9)
        }
    }

    fun handleLongTap(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> onLongLeft()
            KeyEvent.KEYCODE_DPAD_RIGHT -> onLongRight()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> onLongUp()
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> onLongDown()
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> onLongSelect()
        }
    }

    fun handlePressStart(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> onLeftDown()
            KeyEvent.KEYCODE_DPAD_RIGHT -> onRightDown()
        }
    }

    fun handlePressEnd(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> onLeftUp()
            KeyEvent.KEYCODE_DPAD_RIGHT -> onRightUp()
        }
    }

    onPreviewKeyEvent {
        val nativeEvent = it.nativeKeyEvent
        val keyCode = nativeEvent.keyCode
        when (nativeEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (nativeEvent.repeatCount == 0) {
                    keyDownMap[keyCode] = true
                    handlePressStart(keyCode)
                } else if (nativeEvent.repeatCount == 1) {
                    keyDownMap[keyCode] = false
                    handleLongTap(keyCode)
                }
            }

            KeyEvent.ACTION_UP -> {
                val wasPressed = keyDownMap.containsKey(keyCode)
                val wasShortPress = keyDownMap.remove(keyCode) == true
                if (wasPressed) handlePressEnd(keyCode)
                if (wasShortPress) handleTap(keyCode)
            }
        }

        false
    }
}.pointerInput(key) {
    detectTapGestures(
        onTap = { onSelect() },
        onLongPress = { onLongSelect() },
        onDoubleTap = { onSettings() },
    )
}

fun Modifier.handleLeanbackUserAction(onHandle: () -> Unit) =
    onPreviewKeyEvent { onHandle(); false }
        .pointerInput(Unit) { detectDragGestures { _, _ -> onHandle() } }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onHandle() },
                onDoubleTap = { onHandle() },
                onLongPress = { onHandle() },
                onPress = { onHandle() },
            )
        }

/**
 * tv.material3 组件的 onClick/onLongClick 只响应遥控器按键，不响应触摸。
 * 触摸设备（平板/手机）上用本修饰符补齐点按/长按手势，与遥控器行为一致。
 */
fun Modifier.tvTouchClickable(
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
): Modifier = pointerInput(Unit) {
    detectTapGestures(
        onTap = { onClick() },
        onLongPress = onLongClick?.let { handler -> { handler() } },
    )
}