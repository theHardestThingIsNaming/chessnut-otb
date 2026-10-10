package app.chessnut.otb.lichess

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The Lichess login, kept in the Android keystore. A password is never written here. */
class LichessVault(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(): String? = decrypt(prefs.getString(KEY_TOKEN, null))

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, encrypt(token)).apply()
    }

    fun accountId(): String? = prefs.getString(KEY_ID, null)

    fun username(): String? = prefs.getString(KEY_NAME, null)

    fun saveAccount(id: String, username: String) {
        prefs.edit().putString(KEY_ID, id).putString(KEY_NAME, username).apply()
    }

    fun savePending(verifier: String, state: String) {
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
    }

    fun pendingVerifier(): String? = prefs.getString(KEY_VERIFIER, null)

    fun pendingState(): String? = prefs.getString(KEY_STATE, null)

    fun clearPending() {
        prefs.edit().remove(KEY_VERIFIER).remove(KEY_STATE).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        return runCatching {
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, GCM_IV_BYTES)
            val body = payload.copyOfRange(GCM_IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_STORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", ANDROID_STORE)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "lichess_login"
        const val KEY_ALIAS = "lichess_login"
        const val ANDROID_STORE = "AndroidKeyStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val KEY_TOKEN = "token"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_VERIFIER = "verifier"
        const val KEY_STATE = "state"
    }
}
