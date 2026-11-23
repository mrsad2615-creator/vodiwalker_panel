package dev.hgallery.app.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.core.DetailRow
import dev.hgallery.app.data.ExifReader
import dev.hgallery.core.ExifSummary
import dev.hgallery.core.MediaItem
import dev.hgallery.core.detailRows

/**
 * The file's own facts: what it is, how big, how many pixels, where it lives.
 *
 * Everything here comes from the row the grid already loaded, so opening this costs
 * one composition and no permission prompt. That is why it could be built while EXIF
 * could not: EXIF needs the file stream, this does not.
 *
 * The rows are built in `core/ItemDetails` and unit-tested, including the decision
 * to omit a row rather than print a placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(item: MediaItem, exifReader: ExifReader, onDismiss: () -> Unit) {
    val palette = hgPalette
    val rows = remember(item) { detailRows(item) { w, h -> "$w × $h" } }
    val sheetState = rememberModalBottomSheetState()

    // EXIF is read while the sheet is already open. The file facts above need no read;
    // the camera block appears when it arrives, and an item with no block never shows an
    // empty section.
    val exif by produceState(initialValue = ExifSummary(), key1 = item.uri) {
        value = exifReader.read(item.uri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.page,
        modifier = Modifier.testTag(HgTestTags.DETAILS_SHEET),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = HgMetrics.Gutter)) {
            Text(
                text = stringResource(R.string.details),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = MAX_SHEET_HEIGHT_DP.dp)) {
                items(rows, key = { it.label }) { row -> DetailLine(row) }
            }

            if (!exif.isEmpty) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.exif_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                exifRows(exif).forEach { row -> DetailLine(row) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One label and its value, on one line where the value is short. */
@Composable
private fun DetailLine(row: DetailRow) {
    val palette = hgPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HgMetrics.MinTouchTarget)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** Tall enough for the seven rows a video can produce, short enough for a phone. */
private const val MAX_SHEET_HEIGHT_DP = 420

/**
 * The camera block as rows, skipping anything the file did not record.
 *
 * Built here rather than in `core` because the labels are user-facing text and belong with
 * the other translated strings; the values themselves were formatted and tested in
 * `core/ExifSummary`.
 */
@Composable
private fun exifRows(exif: ExifSummary): List<DetailRow> = buildList {
    if (exif.camera.isNotEmpty()) add(DetailRow(stringResource(R.string.exif_camera), exif.camera))
    if (exif.lens.isNotEmpty()) add(DetailRow(stringResource(R.string.exif_lens), exif.lens))
    if (exif.aperture.isNotEmpty()) add(DetailRow(stringResource(R.string.exif_aperture), exif.aperture))
    if (exif.shutter.isNotEmpty()) add(DetailRow(stringResource(R.string.exif_shutter), exif.shutter))
    if (exif.iso.isNotEmpty()) add(DetailRow(stringResource(R.string.exif_iso), exif.iso))
    if (exif.focalLength.isNotEmpty()) {
        add(DetailRow(stringResource(R.string.exif_focal), exif.focalLength))
    }
    if (exif.orientationDegrees != 0) {
        add(DetailRow(stringResource(R.string.exif_rotation), "${exif.orientationDegrees}°"))
    }
}
