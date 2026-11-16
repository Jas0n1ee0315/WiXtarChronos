package com.jas0n1ee.wixtar.chronos.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 以 Android Keystore 保護的字串加解密工具（AES-256 / GCM）。
 *
 * - 金鑰在 Android Keystore 內產生並保存，無法從裝置匯出；App 只拿得到「加密後的字串」。
 * - 加密結果格式：Base64( IV(12 bytes) + 密文含 GCM tag )，可直接存進 DataStore。
 * - 解密失敗（例如 App 資料被還原到另一台裝置、金鑰已不存在）時回傳 null，
 *   呼叫端應視為「尚未設定密碼」，請使用者重新輸入。
 *
 * 目前用於 PJM（專案管理系統）密碼；之後 HR 密碼也可以改用此工具保存。
 */
object SecureStringCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "wixtar_chronos_secure_string_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    /** 取得（或第一次時建立）Keystore 內的 AES 金鑰 */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        android.util.Log.i("SecureStringCipher", "Keystore 金鑰不存在，產生新金鑰")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** 加密字串；空字串回傳空字串（代表「未設定」） */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey()) // IV 由 Keystore 隨機產生
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    /** 解密字串；空字串回傳空字串；格式錯誤或金鑰失效回傳 null */
    fun decrypt(encoded: String): String? {
        if (encoded.isEmpty()) return ""
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.size <= IV_LENGTH) return null
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val body = bytes.copyOfRange(IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            // 只記錄例外類型與長度，不記錄任何密碼或密文內容
            android.util.Log.w("SecureStringCipher", "解密失敗：${e.javaClass.simpleName} ${e.message} (encodedLen=${encoded.length})")
            null
        }
    }
}
