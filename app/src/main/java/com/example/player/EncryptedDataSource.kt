package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.InputStream
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Custom Media3 DataSource for on-the-fly in-memory decryption of AES-256 encrypted files (.enc).
 * Supports password-based key derivation (PBKDF2WithHmacSHA256) and direct streaming decryption.
 * No decrypted copy is ever written to storage or disk.
 */
class EncryptedDataSource(
    private val context: Context,
    private val passphraseOrKey: String
) : BaseDataSource(/* isNetwork = */ false) {

    private var currentDataSpec: DataSpec? = null
    private var cipherInputStream: InputStream? = null
    private var rawInputStream: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.currentDataSpec = dataSpec
        transferInitializing(dataSpec)

        val uri = dataSpec.uri
        val contentResolver = context.contentResolver
        val rawStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for $uri")
        this.rawInputStream = rawStream

        // Header parsing:
        // Format: [16 bytes SALT] + [16 bytes IV] + [CIPHERTEXT]
        val salt = ByteArray(16)
        val readSalt = rawStream.read(salt)
        if (readSalt < 16) throw IllegalArgumentException("Encrypted file too short for salt")

        val iv = ByteArray(16)
        val readIv = rawStream.read(iv)
        if (readIv < 16) throw IllegalArgumentException("Encrypted file too short for IV")

        // PBKDF2 Key Derivation
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(passphraseOrKey.toCharArray(), salt, 10000, 256)
        val secretKey = factory.generateSecret(spec)
        val keySpec = SecretKeySpec(secretKey.encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        this.cipherInputStream = CipherInputStream(rawStream, cipher)

        // Handle skipping bytes if requested by dataSpec.position
        if (dataSpec.position > 0) {
            var skipped = 0L
            while (skipped < dataSpec.position) {
                val s = cipherInputStream!!.skip(dataSpec.position - skipped)
                if (s <= 0) break
                skipped += s
            }
        }

        opened = true
        transferStarted(dataSpec)

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            C.LENGTH_UNSET.toLong()
        }

        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val stream = cipherInputStream ?: return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }

        val read = stream.read(buffer, offset, bytesToRead)
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }

        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentDataSpec?.uri

    override fun close() {
        if (opened) {
            opened = false
            try {
                cipherInputStream?.close()
                rawInputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cipherInputStream = null
                rawInputStream = null
                transferEnded()
            }
        }
    }

    class Factory(
        private val context: Context,
        private val passphraseOrKey: String
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return EncryptedDataSource(context, passphraseOrKey)
        }
    }
}
