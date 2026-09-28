package top.authorche.agent.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("author_agent_secrets", Context.MODE_PRIVATE)

    fun put(name: String, value: String) {
        if (value.isBlank()) {
            preferences.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = listOf(cipher.iv, encrypted).joinToString(":") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
        preferences.edit().putString(name, encoded).apply()
    }

    fun get(name: String): String {
        val encoded = preferences.getString(name, null) ?: return ""
        return runCatching {
            val (ivText, dataText) = encoded.split(":", limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)),
            )
            String(
                cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "author_ai_agent_master_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
