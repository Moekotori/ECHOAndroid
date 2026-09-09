package app.echo.android.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object EchoSecretKeys {
    const val EchoLinkToken = "echo_link_pc_token"
    const val SubsonicPassword = "subsonic_password"
    const val WebDavPassword = "webdav_password"
    const val LastFmSessionKey = "lastfm_session_key"
    const val LastFmSharedSecret = "lastfm_shared_secret"
    const val JellyfinPassword = "jellyfin_password"
    const val JellyfinAccessToken = "jellyfin_access_token"
    const val ListenBrainzToken = "listenbrainz_token"

    fun echoLinkTokenKey(address: String): String =
        "echo_link_token:${address.trim().trimEnd('/').lowercase()}"
}

class EchoSecretStore internal constructor(
    context: Context,
    private val cipher: EchoSecretCipher,
) {
    constructor(context: Context) : this(context, AndroidKeystoreSecretCipher())
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun get(key: String): String? {
        val packed = prefs.getString(key, null) ?: return null
        return cipher.decrypt(packed)
    }

    fun set(key: String, value: String?) {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val packed = cipher.encrypt(trimmed) ?: return
        prefs.edit().putString(key, packed).apply()
    }

    fun takeIfMigrated(key: String, plaintext: String?): String? {
        get(key)?.let { return it }
        val incoming = plaintext?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        set(key, incoming)
        return incoming
    }

    private companion object {
        const val PrefsName = "echo_secrets"
    }
}

internal interface EchoSecretCipher {
    fun encrypt(plain: String): String?
    fun decrypt(packed: String): String?
}

internal class AndroidKeystoreSecretCipher : EchoSecretCipher {
    override fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.getOrNull()

    override fun decrypt(packed: String): String? = runCatching {
        val parts = packed.split('.', limit = 2)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        generator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "echo.secrets.aes"
        const val Transformation = "AES/GCM/NoPadding"
    }
}
