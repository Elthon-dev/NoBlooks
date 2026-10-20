package com.elthon.optilauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Icon vocabulary for the launcher — hand-drawn, monochrome, no icon library. */
enum class Glyph { SEARCH, MIC, MIC_OFF, GRID, CHART, IMAGE, SLIDERS, CLOSE, PIN, CHECK, CHEVRON, SPLIT, WINDOW, REFRESH, TRASH, INFO, OPEN, HOME, DOT, KEYBOARD, WIFI, BLUETOOTH, CELLULAR, AIRPLANE }

@Composable
fun IconGlyph(
    glyph: Glyph,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.7f,
    contentDescription: String? = null
) {
    Canvas(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }
    ) {
        val sw = strokeWidth * size.minDimension / 20f
        val s = size.minDimension
        val pad = s * 0.14f
        val stroke = Stroke(
            width = sw,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        fun px(f: Float) = pad + (s - 2 * pad) * f

        when (glyph) {
            Glyph.SEARCH -> {
                val r = s * 0.26f
                val c = Offset(px(0.44f), px(0.44f))
                drawCircle(tint, r, c, style = stroke)
                val a = 45f * PI / 180
                drawLine(
                    tint,
                    start = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                    end = Offset(c.x + (r + s * 0.20f) * cos(a).toFloat(), c.y + (r + s * 0.20f) * sin(a).toFloat()),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.MIC, Glyph.MIC_OFF -> {
                val capsuleW = s * 0.20f
                val capsuleH = s * 0.36f
                val cx = s / 2f
                val left = cx - capsuleW / 2
                val top = px(0.16f)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = Size(capsuleW, capsuleH),
                    cornerRadius = CornerRadius(capsuleW / 2f),
                    style = stroke
                )
                val baseY = top + capsuleH + s * 0.05f
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - capsuleW * 1.15f, baseY - capsuleW * 0.75f),
                    size = Size(capsuleW * 2.3f, capsuleW * 1.5f),
                    style = stroke
                )
                drawLine(
                    tint,
                    start = Offset(cx, baseY + capsuleW * 0.75f),
                    end = Offset(cx, baseY + capsuleW * 1.35f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(cx - capsuleW * 0.6f, baseY + capsuleW * 1.35f),
                    end = Offset(cx + capsuleW * 0.6f, baseY + capsuleW * 1.35f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                if (glyph == Glyph.MIC_OFF) {
                    drawSlash(tint, s, sw)
                }
            }

            Glyph.GRID -> {
                val cell = (s - 2 * pad) * 0.34f
                val gap = (s - 2 * pad) * 0.10f
                val x0 = pad
                val y0 = pad + (s - 2 * pad - 2 * cell - gap) / 2f
                for (row in 0..1) {
                    for (col in 0..1) {
                        val tl = Offset(x0 + col * (cell + gap), y0 + row * (cell + gap))
                        drawRoundRect(
                            color = tint,
                            topLeft = tl,
                            size = Size(cell, cell),
                            cornerRadius = CornerRadius(cell * 0.32f),
                            style = stroke
                        )
                    }
                }
            }

            Glyph.CHART -> {
                val baseY = px(0.88f)
                val heights = floatArrayOf(0.34f, 0.62f, 0.46f, 0.78f)
                val slot = (s - 2 * pad) / heights.size
                heights.forEachIndexed { i, h ->
                    val x = pad + slot * (i + 0.5f)
                    drawLine(
                        tint,
                        start = Offset(x, baseY),
                        end = Offset(x, baseY - (s - 2 * pad) * h),
                        strokeWidth = sw,
                        cap = StrokeCap.Round
                    )
                }
            }

            Glyph.IMAGE -> {
                val rect = Rect(px(0.12f), px(0.20f), px(0.88f), px(0.80f))
                drawRoundRect(
                    color = tint,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(s * 0.10f),
                    style = stroke
                )
                drawCircle(tint, s * 0.06f, Offset(rect.left + s * 0.18f, rect.top + s * 0.16f), style = stroke)
                val path = Path().apply {
                    moveTo(rect.left + s * 0.10f, rect.bottom - s * 0.06f)
                    lineTo(rect.left + s * 0.34f, rect.bottom - s * 0.30f)
                    lineTo(rect.left + s * 0.52f, rect.bottom - s * 0.14f)
                    lineTo(rect.left + s * 0.68f, rect.bottom - s * 0.26f)
                    lineTo(rect.right - s * 0.08f, rect.bottom - s * 0.06f)
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.SLIDERS -> {
                val rows = 3
                val topPad = pad + s * 0.06f
                val rowGap = (s - 2 * pad - s * 0.12f) / (rows - 1)
                val knobs = floatArrayOf(0.30f, 0.68f, 0.45f)
                for (i in 0 until rows) {
                    val y = topPad + rowGap * i
                    drawLine(
                        tint,
                        start = Offset(pad, y),
                        end = Offset(s - pad, y),
                        strokeWidth = sw,
                        cap = StrokeCap.Round
                    )
                    val kx = pad + (s - 2 * pad) * knobs[i]
                    drawCircle(tint, s * 0.075f, Offset(kx, y), style = stroke)
                    drawCircle(color = tint, radius = s * 0.030f, center = Offset(kx, y))
                }
            }

            Glyph.CLOSE -> {
                drawLine(
                    tint,
                    start = Offset(px(0.28f), px(0.28f)),
                    end = Offset(px(0.72f), px(0.72f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.72f), px(0.28f)),
                    end = Offset(px(0.28f), px(0.72f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.PIN -> {
                drawCircle(tint, s * 0.16f, Offset(s / 2f, px(0.36f)), style = stroke)
                drawLine(
                    tint,
                    start = Offset(s / 2f, px(0.52f)),
                    end = Offset(s / 2f, px(0.84f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.CHECK -> {
                drawLine(
                    tint,
                    start = Offset(px(0.24f), px(0.52f)),
                    end = Offset(px(0.44f), px(0.72f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.44f), px(0.72f)),
                    end = Offset(px(0.78f), px(0.30f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.CHEVRON -> {
                drawLine(
                    tint,
                    start = Offset(px(0.60f), px(0.26f)),
                    end = Offset(px(0.34f), px(0.50f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.34f), px(0.50f)),
                    end = Offset(px(0.60f), px(0.74f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.SPLIT -> {
                val rect = Rect(px(0.14f), px(0.20f), px(0.86f), px(0.80f))
                drawRoundRect(
                    color = tint,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(s * 0.09f),
                    style = stroke
                )
                drawLine(
                    tint,
                    start = Offset(rect.center.x, rect.top + s * 0.04f),
                    end = Offset(rect.center.x, rect.bottom - s * 0.04f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.WINDOW -> {
                val rect = Rect(px(0.16f), px(0.22f), px(0.84f), px(0.78f))
                drawRoundRect(
                    color = tint,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(s * 0.10f),
                    style = stroke
                )
                val inner = Rect(rect.left + s * 0.16f, rect.top + s * 0.16f, rect.right - s * 0.06f, rect.bottom - s * 0.10f)
                drawRoundRect(
                    color = tint,
                    topLeft = inner.topLeft,
                    size = inner.size,
                    cornerRadius = CornerRadius(s * 0.06f),
                    style = stroke
                )
            }

            Glyph.REFRESH -> {
                val cx = s / 2f
                val cy = s / 2f
                val r = s * 0.28f
                drawArc(
                    color = tint,
                    startAngle = -30f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2),
                    style = stroke
                )
                val a = (-30f) * PI / 180
                val tip = Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
                val d = s * 0.12f
                drawLine(
                    tint,
                    start = tip,
                    end = Offset(tip.x - d, tip.y - d * 0.4f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = tip,
                    end = Offset(tip.x - d * 0.3f, tip.y + d),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.TRASH -> {
                val left = px(0.28f)
                val right = px(0.72f)
                val top = px(0.34f)
                val bottom = px(0.82f)
                drawLine(
                    tint,
                    start = Offset(px(0.20f), px(0.26f)),
                    end = Offset(px(0.80f), px(0.26f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.40f), px(0.26f)),
                    end = Offset(px(0.43f), px(0.16f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.60f), px(0.26f)),
                    end = Offset(px(0.57f), px(0.16f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                val body = Path().apply {
                    moveTo(left, top)
                    lineTo(right, top)
                    lineTo(right - s * 0.03f, bottom)
                    lineTo(left + s * 0.03f, bottom)
                    close()
                }
                drawPath(body, tint, style = stroke)
                drawLine(
                    tint,
                    start = Offset(midX(left, right), top + s * 0.07f),
                    end = Offset(midX(left, right), bottom - s * 0.07f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.INFO -> {
                drawCircle(tint, s * 0.32f, Offset(s / 2f, s / 2f), style = stroke)
                drawLine(
                    tint,
                    start = Offset(s / 2f, s * 0.42f),
                    end = Offset(s / 2f, s * 0.66f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawCircle(tint, sw * 0.6f, Offset(s / 2f, s * 0.31f))
            }

            Glyph.OPEN -> {
                drawLine(
                    tint,
                    start = Offset(px(0.30f), px(0.50f)),
                    end = Offset(px(0.72f), px(0.50f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.54f), px(0.32f)),
                    end = Offset(px(0.74f), px(0.50f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.54f), px(0.68f)),
                    end = Offset(px(0.74f), px(0.50f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.HOME -> {
                val path = Path().apply {
                    moveTo(px(0.20f), px(0.52f))
                    lineTo(px(0.50f), px(0.24f))
                    lineTo(px(0.80f), px(0.52f))
                    lineTo(px(0.80f), px(0.78f))
                    lineTo(px(0.20f), px(0.78f))
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(
                    tint,
                    start = Offset(px(0.42f), px(0.78f)),
                    end = Offset(px(0.42f), px(0.60f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.58f), px(0.78f)),
                    end = Offset(px(0.58f), px(0.60f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    start = Offset(px(0.42f), px(0.60f)),
                    end = Offset(px(0.58f), px(0.60f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.KEYBOARD -> {
                val rect = Rect(px(0.02f), px(0.24f), px(0.98f), px(0.76f))
                drawRoundRect(
                    color = tint,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(s * 0.10f),
                    style = stroke
                )
                for (row in 0 until 2) {
                    val y = px(0.38f) + row * (px(0.50f) - px(0.38f))
                    for (col in 0 until 3) {
                        val x = px(0.30f) + col * (px(0.70f) - px(0.30f)) / 2f
                        drawCircle(tint, s * 0.024f, Offset(x, y))
                    }
                }
                drawLine(
                    tint,
                    start = Offset(px(0.36f), px(0.65f)),
                    end = Offset(px(0.64f), px(0.65f)),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }

            Glyph.DOT -> {
                drawCircle(tint, s * 0.30f, Offset(s / 2f, s / 2f))
            }

            Glyph.WIFI -> {
                // Three nested arcs opening upward from a dot at the base, like the
                // radio waves they are describing.
                val cx = s / 2f
                for (i in 1..3) {
                    val r = s * (0.14f + 0.20f * i)
                    drawArc(
                        color = tint,
                        startAngle = 225f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(cx - r, px(0.74f) - r),
                        size = Size(r * 2, r * 2),
                        style = stroke
                    )
                }
                drawCircle(tint, s * 0.05f, Offset(cx, px(0.74f)))
            }

            Glyph.BLUETOOTH -> {
                val midX = s / 2f
                val top = px(0.16f)
                val bot = px(0.84f)
                val left = px(0.30f)
                val right = px(0.70f)
                val midY = (top + bot) / 2f
                val path = Path().apply {
                    moveTo(midX, top)
                    lineTo(right, px(0.34f))
                    lineTo(left, px(0.66f))
                    lineTo(right, px(0.66f))
                    lineTo(midX, bot)
                    lineTo(midX, top)
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.CELLULAR -> {
                // Four ascending bars: signal strength, no number attached.
                val base = px(0.86f)
                val barW = s * 0.10f
                val gap = s * 0.055f
                val heights = listOf(0.22f, 0.36f, 0.50f, 0.64f)
                heights.forEachIndexed { i, h ->
                    val x = px(0.08f) + i * (barW + gap)
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(x, base - s * h),
                        size = Size(barW, s * h),
                        cornerRadius = CornerRadius(barW * 0.4f)
                    )
                }
            }

            Glyph.AIRPLANE -> {
                // A plane seen from above: fuselage + swept wings + tailplane.
                val path = Path().apply {
                    moveTo(s * 0.5f, px(0.10f))
                    lineTo(px(0.56f), px(0.44f))
                    lineTo(px(0.90f), px(0.66f))
                    lineTo(px(0.90f), px(0.74f))
                    lineTo(px(0.56f), px(0.66f))
                    lineTo(px(0.56f), px(0.82f))
                    lineTo(px(0.66f), px(0.90f))
                    lineTo(px(0.66f), px(0.94f))
                    lineTo(s * 0.5f, px(0.88f))
                    lineTo(px(0.34f), px(0.94f))
                    lineTo(px(0.34f), px(0.90f))
                    lineTo(px(0.44f), px(0.82f))
                    lineTo(px(0.44f), px(0.66f))
                    lineTo(px(0.10f), px(0.74f))
                    lineTo(px(0.10f), px(0.66f))
                    lineTo(px(0.44f), px(0.44f))
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}

private fun DrawScope.drawSlash(tint: Color, s: Float, sw: Float) {
    drawLine(
        tint,
        start = Offset(s * 0.22f, s * 0.22f),
        end = Offset(s * 0.78f, s * 0.78f),
        strokeWidth = sw,
        cap = StrokeCap.Round
    )
}

private fun midX(left: Float, right: Float) = (left + right) / 2f

/** Dashed hairline used for section separators inside the island. */
fun DrawScope.dashedLine(color: Color, y: Float, width: Float, strokeWidth: Float) {
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = strokeWidth,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))
    )
}
