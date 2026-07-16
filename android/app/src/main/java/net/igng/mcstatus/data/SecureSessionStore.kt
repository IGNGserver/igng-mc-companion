package net.igng.mcstatus.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("igng_mc_auth", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun save(account: SavedAccount) {
        val key = accountKey(account.userId)
        val accountIds = accountIds().toMutableSet().apply { add(account.userId.toString()) }
        preferences.edit()
            .putStringSet(KEY_ACCOUNT_IDS, accountIds)
            .putString(KEY_ACTIVE_USER_ID, account.userId.toString())
            .putString("${key}_token", encrypt(account.token))
            .putString("${key}_username", account.username)
            .putString("${key}_nickname", account.nickname.orEmpty())
            .apply()
    }

    fun activeAccount(): SavedAccount? = activeUserId()?.let(::account)
    fun accounts(): List<SavedAccount> = accountIds().mapNotNull { it.toIntOrNull() }.sorted().mapNotNull(::account)
    fun switchTo(userId: Int): SavedAccount? = account(userId)?.also { preferences.edit().putString(KEY_ACTIVE_USER_ID, userId.toString()).apply() }
    fun removeActive() { activeUserId()?.let(::remove) }

    private fun remove(userId: Int) {
        val key = accountKey(userId)
        val ids = accountIds().toMutableSet().apply { remove(userId.toString()) }
        val editor = preferences.edit().remove("${key}_token").remove("${key}_username").remove("${key}_nickname").putStringSet(KEY_ACCOUNT_IDS, ids)
        if (activeUserId() == userId) editor.remove(KEY_ACTIVE_USER_ID)
        editor.apply()
    }

    private fun account(userId: Int): SavedAccount? {
        val key = accountKey(userId)
        val encrypted = preferences.getString("${key}_token", null) ?: return null
        val token = runCatching { decrypt(encrypted) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return SavedAccount(userId, preferences.getString("${key}_username", "") ?: "", preferences.getString("${key}_nickname", "")?.ifBlank { null }, token)
    }

    private fun accountIds(): Set<String> = preferences.getStringSet(KEY_ACCOUNT_IDS, emptySet()).orEmpty()
    private fun activeUserId(): Int? = preferences.getString(KEY_ACTIVE_USER_ID, null)?.toIntOrNull()
    private fun accountKey(userId: Int) = "account_$userId"
    private fun secretKey(): SecretKey {
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String { val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.ENCRYPT_MODE, secretKey()); return base64(cipher.iv) + "." + base64(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))) }
    private fun decrypt(value: String): String { val parts = value.split("."); require(parts.size == 2); val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, decode(parts[0]))); return String(cipher.doFinal(decode(parts[1])), StandardCharsets.UTF_8) }
    private fun base64(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
    private companion object { const val KEY_ALIAS = "igng_mc_session_key"; const val TRANSFORMATION = "AES/GCM/NoPadding"; const val KEY_ACCOUNT_IDS = "account_ids"; const val KEY_ACTIVE_USER_ID = "active_user_id" }
}
