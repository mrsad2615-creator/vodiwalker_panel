// NEUROX offline compile stubs - android.os.ParcelFileDescriptor.
package android.os

/** On Android `ParcelFileDescriptor` extends `Closeable`, so the stdlib `use` applies. */
class ParcelFileDescriptor private constructor() : java.io.Closeable {
    val statSize: Long get() = -1L
    override fun close() {}
}
