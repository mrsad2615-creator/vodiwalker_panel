package dev.hgallery.core

/**
 * The rows of a file's details sheet, as pairs rather than sentences.
 *
 * The formatting lives here so it can be tested: a details panel that prints
 * "2.00 MB" next to "1920 x 1080" is easy to get subtly wrong, and a wrong byte
 * count is the kind of error a user only notices when it matters.
 *
 * Every value is derived from fields the media cursor already returned, so the
 * sheet needs no extra permission and no second query.
 */
data class DetailRow(val label: String, val value: String)

/**
 * Builds the rows for one item.
 *
 * @param resolutionFormatter turns width and height into the form people expect.
 *   Passed in because the separator is a locale decision and this module has no
 *   Android dependencies.
 */
fun detailRows(item: MediaItem, resolutionFormatter: (Int, Int) -> String): List<DetailRow> {
    val rows = mutableListOf<DetailRow>()

    rows += DetailRow("Name", item.displayName)
    rows += DetailRow("Size", ByteFormatter.format(item.sizeBytes))

    if (item.width > 0 && item.height > 0) {
        rows += DetailRow("Dimensions", resolutionFormatter(item.width, item.height))
        megapixels(item).takeIf { it.isNotEmpty() }?.let { rows += DetailRow("Megapixels", it) }
    }

    if (item.isVideo && item.durationMs > 0) {
        rows += DetailRow("Duration", DurationFormatter.detail(item.durationMs))
    }

    // RELATIVE_PATH already ends in a slash, as in "DCIM/Camera/"; the row is the
    // folder the file is in, and a trailing slash on its own reads as a typo.
    if (item.relativePath.isNotBlank()) {
        rows += DetailRow("Folder", item.relativePath.trimEnd('/'))
    }

    if (item.bucketName.isNotBlank()) {
        rows += DetailRow("Album", item.bucketName)
    }

    rows += DetailRow("Type", typeLabel(item.mimeType))
    return rows
}

/**
 * Megapixels to one decimal, or the empty string when the pixel count is not
 * positive: "0.0 MP" is worse than saying nothing.
 */
fun megapixels(item: MediaItem): String {
    if (item.width <= 0 || item.height <= 0) return ""
    val millions = item.width.toLong() * item.height.toLong() / MEGAPIXEL_DIVISOR
    val text = tenths(millions)
    // A small icon rounds to "0.0", which is not a measurement. The test that
    // catches this is the reason the rule is here rather than in the caller.
    return if (text == ZERO_TENTHS) "" else "$text MP"
}

/** Integer division with one decimal place, without pulling in a formatter. */
private fun tenths(millionths: Double): String {
    val scaled = kotlin.math.round(millionths * DECIMAL_BASE).toLong()
    return "${scaled / 10L}.${scaled % 10L}"
}

/**
 * The MIME subtype in the terms the reference uses: "JPEG image", "MP4 video".
 *
 * The subtype is upper-cased because that is how these formats are written
 * everywhere else, and the kind is appended because "image/heic" tells most people
 * less than "HEIC image".
 */
fun typeLabel(mimeType: String): String {
    val parts = mimeType.split('/')
    if (parts.size != 2) return mimeType
    val (kind, subtype) = parts
    val readableSubtype = subtype.substringBefore('+').uppercase()
    val readableKind = when (kind) {
        "image" -> "image"
        "video" -> "video"
        "audio" -> "audio"
        else -> kind
    }
    return "$readableSubtype $readableKind"
}

private const val MEGAPIXEL_DIVISOR = 1_000_000.0

/** What a count that rounds away to nothing looks like. */
private const val ZERO_TENTHS = "0.0"

/** Multiplier for one decimal place. */
private const val DECIMAL_BASE = 10.0
