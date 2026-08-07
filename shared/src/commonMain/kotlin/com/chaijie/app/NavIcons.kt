package com.chaijie.app

import androidx.compose.runtime.Composable
import com.tencent.kuikly.compose.foundation.Canvas
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.geometry.Rect
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.graphics.Path
import com.tencent.kuikly.compose.ui.graphics.drawscope.Stroke
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.unit.dp

/**
 * 底部导航 / 控制条图标。原型 home.html / yearflow_v1.html 用内联 SVG（24×24 viewBox），
 * Kuikly 无 SVG 组件，这里用 Canvas + Path 按原坐标等比缩放绘制，保证与原型视觉一致。
 */
enum class NavIconName { HOME, YEAR, PLACE, VIDEO, PLUS, PLAY, PAUSE }

@Composable
fun NavIcon(name: NavIconName, color: Color, sizeDp: Dp = 23.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val u = size.width / 24f
        val w = size.width / 13f
        val stroke = Stroke(width = w)
        when (name) {
            NavIconName.HOME -> {
                val p = Path().apply {
                    moveTo(3f * u, 11f * u); lineTo(12f * u, 3f * u); lineTo(21f * u, 11f * u)
                    moveTo(5f * u, 10f * u); lineTo(5f * u, 20f * u); lineTo(19f * u, 20f * u); lineTo(19f * u, 10f * u)
                }
                drawPath(p, color, style = stroke)
            }
            NavIconName.YEAR -> {
                val p = Path().apply {
                    addOval(Rect(3f * u, 3f * u, 21f * u, 21f * u))
                    moveTo(12f * u, 7f * u); lineTo(12f * u, 12f * u); lineTo(15.5f * u, 14f * u)
                }
                drawPath(p, color, style = stroke)
            }
            NavIconName.PLACE -> {
                val p = Path().apply {
                    addOval(Rect(8f * u, 4f * u, 16f * u, 12f * u))
                    moveTo(12f * u, 22f * u); lineTo(8f * u, 12.5f * u); lineTo(16f * u, 12.5f * u); close()
                }
                drawPath(p, color, style = stroke)
            }
            NavIconName.VIDEO -> {
                val p = Path().apply {
                    addRect(Rect(3f * u, 6f * u, 16f * u, 18f * u))
                    moveTo(16f * u, 10f * u); lineTo(21f * u, 7f * u); lineTo(21f * u, 17f * u); lineTo(16f * u, 14f * u); close()
                }
                drawPath(p, color, style = stroke)
            }
            NavIconName.PLUS -> {
                val p = Path().apply {
                    moveTo(12f * u, 5f * u); lineTo(12f * u, 19f * u)
                    moveTo(5f * u, 12f * u); lineTo(19f * u, 12f * u)
                }
                drawPath(p, color, style = stroke)
            }
            NavIconName.PLAY -> {
                val p = Path().apply {
                    moveTo(8f * u, 5f * u); lineTo(19f * u, 12f * u); lineTo(8f * u, 19f * u); close()
                }
                drawPath(p, color)
            }
            NavIconName.PAUSE -> {
                val p = Path().apply {
                    addRect(Rect(9f * u, 5f * u, 11f * u, 19f * u))
                    addRect(Rect(14f * u, 5f * u, 16f * u, 19f * u))
                }
                drawPath(p, color)
            }
        }
    }
}
