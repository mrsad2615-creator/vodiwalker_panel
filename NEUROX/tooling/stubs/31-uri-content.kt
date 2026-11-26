// NEUROX offline compile stubs - android.net.Uri and the ContentResolver surface
// used by SAF backup file I/O. Shapes verified against the documented API.
package android.net

import java.io.InputStream
import java.io.OutputStream

class Uri private constructor() {
    override fun toString(): String = ""
    fun getLastPathSegment(): String? = null

    companion object {
        fun parse(value: String): Uri = Uri()
        fun fromFile(path: java.io.File): Uri = Uri()
    }
}
