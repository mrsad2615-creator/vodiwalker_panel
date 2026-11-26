// NEUROX offline compile stubs - androidx.activity.result, the shape
// SafBackupFiles uses: a launcher, two contracts, and registerForActivityResult.
package androidx.activity.result.contract

abstract class ActivityResultContract<I, O> {
    abstract fun createIntent(context: android.content.Context, input: I): android.content.Intent
}

class ActivityResultContracts {
    class CreateDocument(private val mimeType: String) : ActivityResultContract<String, android.net.Uri?>() {
        override fun createIntent(context: android.content.Context, input: String): android.content.Intent =
            android.content.Intent()
    }

    class OpenDocument : ActivityResultContract<Array<String>, android.net.Uri?>() {
        override fun createIntent(
            context: android.content.Context,
            input: Array<String>,
        ): android.content.Intent = android.content.Intent()
    }
}
