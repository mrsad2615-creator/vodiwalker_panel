package com.mrsad.ambient.presentation.ambient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsad.ambient.designsystem.MrsadColors
import com.mrsad.ambient.designsystem.NumeralFormatter
import com.mrsad.ambient.domain.model.ClockConfig
import com.mrsad.ambient.domain.model.ClockStyle
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HybridClock(
    config: ClockConfig,
    modifier: Modifier = Modifier
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(config.showSeconds) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(if (config.showSeconds) 1000L else 15000L)
        }
    }

    val cal = remember(nowMillis) { Calendar.getInstance().apply { timeInMillis = nowMillis } }
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val hourDisplay = if (config.use24Hour) hour24 else {
        val h = hour24 % 12
        if (h == 0) 12 else h
    }
    val minute = cal.get(Calendar.MINUTE)
    val second = cal.get(Calendar.SECOND)

    val timeRaw = if (config.showSeconds) {
        "%02d:%02d:%02d".format(hourDisplay, minute, second)
    } else {
        "%02d:%02d".format(hourDisplay, minute)
    }
    val timeFormatted = NumeralFormatter.format(timeRaw, config.usePersianNumerals)

    val ringSize = (176 * config.scale).dp

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (config.style != ClockStyle.MINIMAL_DIGITAL && config.showAnalogTicks) {
            Canvas(modifier = Modifier.size(ringSize)) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = MrsadColors.BorderSubtle,
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                // 12 geometric hour ticks
                for (i in 0 until 12) {
                    val angleRad = Math.toRadians((i * 30 - 90).toDouble())
                    val inner = r * if (i % 3 == 0) 0.86f else 0.92f
                    val outer = r * 0.98f
                    drawLine(
                        color = if (i % 3 == 0) MrsadColors.PureWhite else MrsadColors.GrayMuted,
                        start = Offset(
                            center.x + (inner * cos(angleRad)).toFloat(),
                            center.y + (inner * sin(angleRad)).toFloat()
                        ),
                        end = Offset(
                            center.x + (outer * cos(angleRad)).toFloat(),
                            center.y + (outer * sin(angleRad)).toFloat()
                        ),
                        strokeWidth = if (i % 3 == 0) 2.5f else 1.2f,
                        cap = StrokeCap.Square
                    )
                }

                // Minute indicator dot on the outer ring
                val minAngle = Math.toRadians((minute * 6 - 90).toDouble())
                drawCircle(
                    color = MrsadColors.PureWhite,
                    radius = 3.5f,
                    center = Offset(
                        center.x + (r * 0.98f * cos(minAngle)).toFloat(),
                        center.y + (r * 0.98f * sin(minAngle)).toFloat()
                    )
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeFormatted,
                color = MrsadColors.PureWhite,
                fontSize = (46 * config.scale).sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp
            )
        }
    }
}
