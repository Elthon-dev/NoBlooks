package com.elthon.optilauncher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.elthon.optilauncher.ui.theme.LocalNbColors
import com.elthon.optilauncher.ui.theme.NbDim
import com.elthon.optilauncher.ui.theme.NbType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The home widget: a live clock card, not a static printout.
 *
 * Three things move on it:
 *  - the time, once a minute;
 *  - a hairline ring that fills smoothly across the current minute, so there is
 *    always evidence the launcher is alive without a spinning ornament;
 *  - the battery readout, from the system's sticky battery broadcast (no
 *    permission involved).
 *
 * Tapping it opens the island on the Apps page. It shares the island's corner
 * rhythm, inset and solid surface, so the two read as one set.
 */
@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val colors = LocalNbColors.current
    val context = LocalContext.current
    val now by rememberSecondTick()
    val zone = remember { ZoneId.systemDefault() }

    val zoned = remember(now) { Instant.ofEpochMilli(now).atZone(zone) }
    val time = remember(zoned) {
        zoned.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    }
    val date = remember(zoned) {
        zoned.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    val weekday = remember(zoned) { zoned.dayOfWeek.value } // 1 = Monday
    val fraction = remember(zoned) { zoned.second / 60f }

    // Restart the sweep when the minute rolls over instead of animating
    // backwards around the whole circle.
    val ring = remember { Animatable(fraction) }
    LaunchedEffect(fraction) {
        if (fraction < ring.value - 0.2f) ring.snapTo(0f)
        ring.animateTo(fraction, animationSpec = tween(1000, easing = LinearEasing))
    }

    val battery = rememberBatteryState(context)

    Column(
        modifier = modifier
            .width(238.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(NbDim.WidgetCorner),
                clip = false,
                ambientColor = colors.shadow,
                spotColor = colors.shadow
            )
            .clip(RoundedCornerShape(NbDim.WidgetCorner))
            .background(colors.surfaceSolid)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = "Clock widget. $time, $date. Activate to open apps."
            }
            .padding(NbDim.WidgetPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.width(238.dp - NbDim.WidgetPadding * 2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NbText(
                    time,
                    style = NbType.clock,
                    color = colors.onSurface,
                    maxLines = 1,
                    contentDescription = "Current time"
                )
                NbText(
                    date,
                    style = NbType.clockLabel,
                    color = colors.onSurfaceDim,
                    maxLines = 1
                )
            }
            MinuteRing(
                fraction = { ring.value },
                batteryPercent = battery.percent,
                charging = battery.charging,
                modifier = Modifier.size(46.dp)
            )
        }

        Box(
            Modifier
                .width(238.dp - NbDim.WidgetPadding * 2)
                .background(colors.outline)
                .padding(top = 1.dp)
        )

        Row(
            modifier = Modifier.width(238.dp - NbDim.WidgetPadding * 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NbText(
                if (battery.charging) "CHARGING" else "ON BATTERY",
                style = NbType.label,
                color = if (battery.charging) colors.accent else colors.onSurfaceDim,
                maxLines = 1
            )
            WeekdayDots(today = weekday)
        }
    }
}

/**
 * Minute ring with the battery percentage in the middle. The sweep value is read
 * inside the draw lambda, so each animation frame only invalidates the draw phase
 * — no recomposition per frame.
 */
@Composable
private fun MinuteRing(
    fraction: () -> Float,
    batteryPercent: Int,
    charging: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val track = colors.outline
    val sweep = if (charging) colors.accent else colors.accent.copy(alpha = 0.92f)
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(46.dp)) {
            val strokeWidth = size.minDimension * 0.085f
            val inset = strokeWidth / 2f
            val diameter = size.minDimension - strokeWidth
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth)
            )
            val value = fraction().coerceIn(0f, 1f)
            drawArc(
                color = sweep,
                startAngle = -90f,
                sweepAngle = 360f * value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            val angle = Math.toRadians((-90f + 360f * value).toDouble())
            val radius = diameter / 2f
            val center = Offset(size.minDimension / 2f, size.minDimension / 2f)
            drawCircle(
                color = sweep,
                radius = strokeWidth * 0.95f,
                center = Offset(
                    center.x + (radius * Math.cos(angle)).toFloat(),
                    center.y + (radius * Math.sin(angle)).toFloat()
                )
            )
        }
        NbText(
            if (batteryPercent in 0..100) "$batteryPercent" else "—",
            style = NbType.widgetValue,
            color = if (charging) colors.accent else colors.onSurface,
            maxLines = 1,
            contentDescription = if (batteryPercent in 0..100) {
                "Battery $batteryPercent percent"
            } else {
                "Battery level unavailable"
            }
        )
    }
}

@Composable
private fun WeekdayDots(today: Int) {
    val colors = LocalNbColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (day in 1..7) {
            val isToday = day == today
            Box(
                Modifier
                    .size(if (isToday) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(if (isToday) colors.accent else colors.outline)
            )
        }
    }
}

private data class BatteryState(val percent: Int, val charging: Boolean)

/**
 * Battery level from the system's sticky ACTION_BATTERY_CHANGED broadcast.
 * Registering for a system broadcast needs no permission and no flags beyond the
 * not-exported marker, which [ContextCompat.registerReceiver] supplies.
 */
@Composable
private fun rememberBatteryState(context: Context): BatteryState {
    var state by remember { mutableStateOf(readBattery(context, null)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivingContext: Context?, intent: Intent?) {
                state = readBattery(context, intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return state
}

private fun readBattery(context: Context, intent: Intent?): BatteryState {
    val resolved = intent ?: runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()
    if (resolved == null) return BatteryState(percent = -1, charging = false)
    val level = resolved.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = resolved.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = resolved.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryState(percent = percent, charging = charging)
}

/** Ticks once a second: cheap, and enough for a smooth minute sweep. */
@Composable
private fun rememberSecondTick(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1000L)
    }
}
