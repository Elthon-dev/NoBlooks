package com.elthon.optilauncher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The home instrument: time, date, the day's progress, the weekday, and a small
 * battery dial — one card, four reads, no filler.
 *
 * Everything here is real and everything here is measured rather than decorated:
 *  - the *day bar* that separates the card is the actual fraction of the day
 *    elapsed, so a divider that would be dead weight in any other design is doing
 *    the work of a progress meter;
 *  - the *dial* carries the battery level as its arc and walks a marker around the
 *    track once a second, so the card is visibly alive without a permanent 60fps
 *    animation running on a screen that is on all day;
 *  - the status word is read straight off the battery intent, so it can only say
 *    CHARGING when the system says charging.
 *
 * Performance shape: the once-a-second tick is a [State] that is *never read in
 * composition* — the dial's marker reads it inside its own draw lambda and the
 * text is gated behind a per-minute derived value, so a second passing costs one
 * draw pass, not a recomposition of the card and its children.
 *
 * Tapping the card opens the island on the Apps page. It shares the island's
 * inset, corner rhythm and solid surface, so the two surfaces read as one set.
 */
@Composable
fun GlanceWidget(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val colors = LocalNbColors.current
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    val tick = rememberSecondTick()

    // Derived on the whole minute: the text below recomposes 1440 times a day
    // rather than once a second. Reading `tick` here costs a long comparison.
    val minuteBatch by remember { derivedStateOf { tick.value / 60_000L } }
    val zoned = remember(minuteBatch) {
        Instant.ofEpochMilli(minuteBatch * 60_000L).atZone(zone)
    }
    val time = remember(zoned) { zoned.format(TIME_FORMAT) }
    val date = remember(zoned) { zoned.format(DATE_FORMAT).uppercase(Locale.getDefault()) }
    val weekday = remember(zoned) { zoned.dayOfWeek.value } // 1 = Monday
    val dayFraction = remember(zoned) {
        ((zoned.hour * 60 + zoned.minute).toFloat() / MINUTES_PER_DAY).coerceIn(0f, 1f)
    }

    val battery = rememberBatteryState(context)
    val status = when {
        battery.charging -> "CHARGING"
        battery.percent in 0..100 -> "ON BATTERY"
        else -> "BATTERY UNKNOWN"
    }

    Column(
        modifier = modifier
            .width(NbDim.InstrumentWidth)
            .shadow(
                elevation = 18.dp,
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
                contentDescription = buildString {
                    append("Time $time, $date.")
                    if (battery.percent in 0..100) append(" Battery ${battery.percent} percent.")
                    if (battery.charging) append(" Charging.")
                    append(" Activate to open apps.")
                }
            }
            .padding(NbDim.WidgetPadding)
    ) {
        NbText(
            text = time,
            style = NbType.clock,
            color = colors.onSurface,
            maxLines = 1,
            contentDescription = "Current time"
        )
        NbText(
            text = date,
            modifier = Modifier.padding(top = 3.dp),
            style = NbType.clockLabel,
            color = colors.onSurfaceDim,
            maxLines = 1
        )

        // The divider IS the meter: how much of today is gone.
        DayBar(fraction = dayFraction, modifier = Modifier.padding(top = 16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BatteryDial(
                level = battery.percent,
                charging = battery.charging,
                secondProvider = { tick.value % 60_000L / 60_000f },
                modifier = Modifier.size(54.dp)
            )
            NbText(
                text = status,
                modifier = Modifier.weight(1f),
                style = NbType.label,
                color = if (battery.charging) colors.accent else colors.onSurfaceDim,
                maxLines = 1
            )
            WeekdayDots(today = weekday)
        }
    }
}

/**
 * Day-progress bar. Reads a plain value rather than a state: it only changes once
 * a minute, and it is cheap enough that animating it would be motion for motion's
 * sake.
 */
@Composable
private fun DayBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.outline)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(colors.accent)
        )
    }
}

/**
 * Battery arc plus a once-a-second marker walking the same track.
 *
 * The marker's position is read from the lambda inside the draw scope, which is
 * what keeps the tick out of composition. Ticks are quantised by design: a smooth
 * sweep would mean animating 60 frames every second of every day on a screen that
 * never sleeps, and this card is supposed to cost nothing while it sits there.
 */
@Composable
private fun BatteryDial(
    level: Int,
    charging: Boolean,
    secondProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalNbColors.current
    val track = colors.outline
    val accent = colors.accent
    val marker = if (charging) colors.accent else colors.onSurface

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.075f
            val inset = stroke / 2f + size.minDimension * 0.02f
            val diameter = size.minDimension - inset * 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )

            if (level in 0..100) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * level / 100f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            val seconds = secondProvider().coerceIn(0f, 1f)
            val angle = (-90f + 360f * seconds) * PI.toFloat() / 180f
            val radius = diameter / 2f
            drawCircle(
                color = marker,
                radius = stroke * 1.1f,
                center = Offset(
                    x = center.x + radius * cos(angle),
                    y = center.y + radius * sin(angle)
                )
            )
        }
        NbText(
            text = if (level in 0..100) "$level" else "—",
            style = NbType.widgetValue,
            color = if (charging) colors.accent else colors.onSurface,
            maxLines = 1,
            contentDescription = if (level in 0..100) {
                "Battery $level percent${if (charging) ", charging" else ""}"
            } else {
                "Battery level unavailable"
            }
        )
    }
}

@Composable
private fun WeekdayDots(today: Int) {
    val colors = LocalNbColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
 * A system broadcast needs no permission and no special flags beyond the
 * not-exported marker that [ContextCompat.registerReceiver] supplies.
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
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
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

/**
 * One emission per second, aligned to the wall clock so the marker lands exactly
 * on the second instead of drifting by however long the previous frame took.
 */
@Composable
private fun rememberSecondTick(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(1_000L - System.currentTimeMillis() % 1_000L)
    }
}

private const val MINUTES_PER_DAY = 1440f
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
