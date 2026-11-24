package com.neurox.app

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import com.neurox.domain.SearchMark

/** The same stroke and color apply to every search stimulus. No target highlighting. */
@Composable
internal fun SearchShape(mark: SearchMark, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val u = size.minDimension / 24f
        rotate((mark.ordinal % 4) * 90f) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
                drawLine(tint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 2f * u, StrokeCap.Square)
            }
            if (mark.ordinal < 4) {
                line(5f, 5f, 19f, 5f)
                line(12f, 5f, 12f, 19f)
            } else {
                line(6f, 5f, 6f, 19f)
                line(6f, 19f, 19f, 19f)
            }
        }
    }
}

@Composable
internal fun searchCellLabel(mark: SearchMark?, cell: Int): String {
    val symbol = when (mark) {
        SearchMark.T_UP -> tr("حرف T ایستاده", "Upright T")
        SearchMark.T_RIGHT -> tr("حرف T چرخیده ۹۰ درجه", "T rotated 90 degrees")
        SearchMark.T_DOWN -> tr("حرف T وارونه", "Upside-down T")
        SearchMark.T_LEFT -> tr("حرف T چرخیده ۲۷۰ درجه", "T rotated 270 degrees")
        SearchMark.L_UP -> tr("حرف L", "L")
        SearchMark.L_RIGHT -> tr("حرف L چرخیده ۹۰ درجه", "L rotated 90 degrees")
        SearchMark.L_DOWN -> tr("حرف L چرخیده ۱۸۰ درجه", "L rotated 180 degrees")
        SearchMark.L_LEFT -> tr("حرف L چرخیده ۲۷۰ درجه", "L rotated 270 degrees")
        null -> tr("خالی", "Empty")
    }
    return tr("خانه ${number(cell + 1)}: $symbol", "Cell ${number(cell + 1)}: $symbol")
}
