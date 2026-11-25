// NEUROX offline compile stubs - Android keystore (used only by the security module).
package android.security.keystore

object KeyProperties {
    const val KEY_ALGORITHM_AES = "AES"
    const val BLOCK_MODE_GCM = "GCM"
    const val ENCRYPTION_PADDING_NONE = "NoPadding"
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2
}

class KeyGenParameterSpec {
    class Builder(alias: String, purposes: Int) {
        fun setBlockModes(vararg modes: String): Builder = this
        fun setEncryptionPaddings(vararg paddings: String): Builder = this
        fun setKeySize(size: Int): Builder = this
        fun setUserAuthenticationRequired(required: Boolean): Builder = this
        fun build(): KeyGenParameterSpec = KeyGenParameterSpec()
    }
}
