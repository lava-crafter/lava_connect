// SPDX-License-Identifier: GPL-3.0-or-later
package com.lavacrafter.connect

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A random core storage secret is persisted only as AES-GCM ciphertext; the wrapping key is non-exportable. */
internal object StorageIdentity {
    private const val ALIAS = "lava-connect-storage-wrap-v1"
    private const val FILE = "core-storage-key.bin"

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(
                        256,
                    ).setBlockModes(
                        KeyProperties.BLOCK_MODE_GCM,
                    ).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    @Synchronized fun getOrCreate(filesDir: File): String {
        val file = File(filesDir, FILE)
        val atomic = android.util.AtomicFile(file)
        val raw =
            if (file.exists() || File(file.path + ".bak").exists()) {
                val all = atomic.readFully()
                require(all.size > 12) { "Stored identity is corrupt" }
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
                    doFinal(all.copyOfRange(12, all.size))
                }
            } else {
                ByteArray(32).also(SecureRandom()::nextBytes).also { bytes ->
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
                    val output = atomic.startWrite()
                    try {
                        output.write(cipher.iv + cipher.doFinal(bytes))
                        atomic.finishWrite(output)
                    } catch (
                        e: Exception,
                    ) {
                        atomic.failWrite(output)
                        throw e
                    }
                }
            }
        require(raw.size == 32) { "Stored identity has invalid length" }
        return raw.joinToString("") { "%02x".format(it) }
    }
}
