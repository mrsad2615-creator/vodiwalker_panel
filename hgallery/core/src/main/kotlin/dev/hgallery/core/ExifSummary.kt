package dev.hgallery.core

/**
 * The camera facts a photograph carries, as far as the gallery shows them.
 *
 * ## Why so few fields
 *
 * A real EXIF block holds a hundred tags, most of which mean nothing to the person who
 * took the photograph. The gallery shows the six that answer "where and how was this
 * taken": camera, lens, aperture, shutter, ISO and focal length. Everything else is left
 * in the file.
 *
 * ## Why it is a separate value
 *
 * Reading EXIF needs the file stream, which is slow and can fail. Keeping the result in a
 * plain value means the sheet can show what it has while the read is running, and the
 * formatting rules are testable without a device.
 */
data class ExifSummary(
    /** Make and model, joined and cleaned. Empty when the camera wrote neither. */
    val camera: String = "",
    val lens: String = "",
    /** Aperture as an f-number, e.g. "f/1.8". Empty when unknown. */
    val aperture: String = "",
    /** Exposure time, e.g. "1/125 s" or "2 s". Empty when unknown. */
    val shutter: String = "",
    /** Sensitivity, e.g. "ISO 100". Empty when unknown. */
    val iso: String = "",
    /** Focal length, e.g. "26 mm". Empty when unknown. */
    val focalLength: String = "",
    /** Degrees clockwise the viewer must apply. 0, 90, 180 or 270. */
    val orientationDegrees: Int = 0,
) {
    /** True when nothing was read, so the sheet can say so instead of drawing an empty list. */
    val isEmpty: Boolean
        get() = camera.isEmpty() && lens.isEmpty() && aperture.isEmpty() &&
            shutter.isEmpty() && iso.isEmpty() && focalLength.isEmpty() && orientationDegrees == 0
}

/**
 * The EXIF orientation value for a rotation, and the rotation for a value.
 *
 * EXIF stores orientation as one of eight numbers, and only the four rotations are used
 * here. Keeping the mapping in one place means the reader and the rotate action cannot
 * disagree about what orientation 6 means.
 */
object ExifOrientation {
    const val NORMAL = 1
    const val ROTATE_90 = 6
    const val ROTATE_180 = 3
    const val ROTATE_270 = 8

    /** The rotation, in degrees clockwise, that a stored value asks the viewer to apply. */
    fun degrees(value: Int): Int = when (value) {
        ROTATE_90 -> 90
        ROTATE_180 -> 180
        ROTATE_270 -> 270
        else -> 0
    }

    /**
     * Adds [degrees] to a stored value and returns the new EXPOSURE orientation.
     *
     * The result is always a pure rotation: a mirrored value (2, 4, 5, 7) is treated as
     * its rotation, because the viewer does not implement a flip and a value it cannot
     * render would be worse than a rotation it can.
     */
    fun rotate(current: Int, degrees: Int): Int =
        when ((degrees(current) + degrees) % FULL_TURN) {
            90 -> ROTATE_90
            180 -> ROTATE_180
            270 -> ROTATE_270
            else -> NORMAL
        }

    private const val FULL_TURN = 360
}

/**
 * The rules for turning EXIF values into the strings the sheet shows.
 *
 * Each function takes the raw numbers a decoder produces and returns the text a camera
 * would print, or an empty string when the number is not usable. An empty string is the
 * signal to omit the row: a dash in place of a value is noise, and a wrong value is worse.
 */
object ExifFormat {

    /** Below this the focal length is a macro or a rounding artefact, not a lens. */
    private const val MIN_FOCAL_MM = 1.0

    /** At and above this, apertures are printed without a decimal: f/11, not f/11.0. */
    private const val WHOLE_APERTURE_FROM = 10.0

    /** The base the one-decimal helper scales by. */
    private const val DECIMAL_BASE = 10.0

    /**
     * Aperture, from the f-number.
     *
     * One decimal below f/10 and none above, which is how lenses are marked. A value of 0
     * means the camera did not write one.
     */
    fun aperture(fNumber: Double): String = when {
        fNumber <= 0.0 -> ""
        fNumber >= WHOLE_APERTURE_FROM -> "f/${fNumber.toInt()}"
        else -> "f/" + oneDecimal(fNumber)
    }

    /**
     * Shutter speed, from the exposure time in seconds.
     *
     * Long exposures are written as seconds and short ones as a fraction, because
     * "0.008 s" tells a photographer less than "1/125 s" does.
     */
    fun shutter(seconds: Double): String = when {
        seconds <= 0.0 -> ""
        seconds >= 1.0 -> "${trim(seconds)} s"
        else -> "1/${kotlin.math.round(1.0 / seconds).toInt()} s"
    }

    /** Sensitivity, from the ISO value. */
    fun iso(value: Int): String = if (value <= 0) "" else "ISO $value"

    /** Focal length, from millimetres. */
    fun focalLength(millimetres: Double): String = when {
        millimetres < MIN_FOCAL_MM -> ""
        else -> "${trim(millimetres)} mm"
    }

    /**
     * Make and model, joined without repeating the maker.
     *
     * Cameras very often write the maker into the model as well ("Canon" and
     * "Canon EOS R6"), and printing both gives "Canon Canon EOS R6".
     */
    fun camera(make: String, model: String): String {
        val m = make.trim()
        val d = model.trim()
        return when {
            m.isEmpty() -> d
            d.isEmpty() -> m
            d.startsWith(m, ignoreCase = true) -> d
            else -> "$m $d"
        }
    }

    /**
     * Whole numbers print without a decimal point: a 26 mm lens is not "26.0 mm".
     */
    private fun trim(value: Double): String =
        if (value == kotlin.math.floor(value)) value.toInt().toString() else oneDecimal(value)

    private fun oneDecimal(value: Double): String {
        val scaled = kotlin.math.round(value * DECIMAL_BASE).toLong()
        return "${scaled / DECIMAL_BASE.toLong()}.${scaled % DECIMAL_BASE.toLong()}"
    }
}
