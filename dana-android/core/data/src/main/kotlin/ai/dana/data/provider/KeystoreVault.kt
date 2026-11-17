package ai.dana.data.provider

import ai.dana.domain.ApiCredential
import ai.dana.domain.CredentialStoreUnavailable
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class KeystoreVault(private val alias: String = "dana.credentials.aes.v1") {
    private val lock = Mutex()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    suspend fun seal(id: String, scope: String, credential: ApiCredential): String = secured {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The provider generates a fresh IV; nothing in the caller controls the nonce.
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey(generate = true))
        cipher.updateAAD(aad(id, scope))
        val plaintext = credential.value.encodeToByteArray()
        try {
            val ciphertext = cipher.doFinal(plaintext)
            val iv = cipher.iv
            if (iv == null || iv.size != 12) throw CredentialStoreUnavailable()
            "$VERSION.${encoder.encodeToString(iv)}.${encoder.encodeToString(ciphertext)}"
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun open(id: String, scope: String, envelope: String): ApiCredential = secured {
        if (envelope.length > MAX_ENVELOPE_LENGTH) throw CredentialStoreUnavailable()
        val fields = envelope.split('.', limit = 4)
        if (fields.size != 3 || fields[0] != VERSION) throw CredentialStoreUnavailable()

        val iv = decoder.decode(fields[1])
        val sealed = decoder.decode(fields[2])
        if (iv.size != 12 || sealed.size !in 17..(ApiCredential.LIMIT + 16)) throw CredentialStoreUnavailable()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(generate = false), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(id, scope))
        val plaintext = cipher.doFinal(sealed)
        try {
            ApiCredential.parse(plaintext.decodeToString(throwOnInvalidSequence = true))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun obtainKey(generate: Boolean): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val current = store.getKey(alias, null)
        if (current is SecretKey) return current
        if (current != null || !generate) throw CredentialStoreUnavailable()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    private fun aad(id: String, scope: String) = "dana.credentials\u0000$VERSION\u0000$id\u0000$scope".encodeToByteArray()

    private suspend fun <T> secured(block: () -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                block()
            } catch (error: GeneralSecurityException) {
                throw CredentialStoreUnavailable(error)
            } catch (error: ProviderException) {
                throw CredentialStoreUnavailable(error)
            } catch (error: IOException) {
                throw CredentialStoreUnavailable(error)
            } catch (error: IllegalArgumentException) {
                throw CredentialStoreUnavailable(error)
            }
        }
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION = "v1"
        private const val MAX_ENVELOPE_LENGTH = 3_000
    }
}
