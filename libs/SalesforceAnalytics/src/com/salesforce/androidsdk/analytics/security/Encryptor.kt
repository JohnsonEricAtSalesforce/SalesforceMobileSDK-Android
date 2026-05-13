/*
 * Copyright (c) 2011-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.analytics.security

import android.text.TextUtils
import android.util.Base64
import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Helper class for encryption/decryption/hash computations.
 */
object Encryptor {

    enum class CipherMode(val fullName: String) {
        AES_CBC_CIPHER("AES/CBC/PKCS5Padding"),
        AES_GCM_CIPHER("AES/GCM/NoPadding"),
        RSA_PKCS1("RSA/ECB/PKCS1Padding"),
        RSA_OAEP_SHA256("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    }

    private const val TAG = "Encryptor"
    private const val MAC_TRANSFORMATION = "HmacSHA256"
    private const val BOUNCY_CASTLE = "BC"

    // This provider was separated out of AndroidKeyStoreProvider to work around the issue
    // that Bouncy Castle provider incorrectly declares that it accepts arbitrary keys (incl. Android
    // KeyStore ones). This causes JCA to select the Bouncy Castle's implementation of JCA crypto
    // operations for Android KeyStore keys unless Android KeyStore's own implementations are installed
    // as higher-priority than Bouncy Castle ones. The purpose of this provider is to do just that: to
    // offer crypto operations operating on Android KeyStore keys and to be installed at higher priority
    // than the Bouncy Castle provider.
    // See https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/keystore/java/android/security/keystore2/AndroidKeyStoreBCWorkaroundProvider.java
    private const val BOUNCY_CASTLE_WORKAROUND = "AndroidKeyStoreBCWorkaround"
    private const val READ_BUFFER_LENGTH = 1024

    /**
     * Returns initialized cipher for encryption with an IV automatically generated.
     *
     * @param encryptionKey Encryption key.
     * @return Initialized cipher.
     */
    @JvmStatic
    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class)
    fun getEncryptingCipher(encryptionKey: String): Cipher {
        val keyBytes = Base64.decode(encryptionKey, Base64.DEFAULT)
        return getEncryptingCipher(keyBytes, generateInitVector())
    }

    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class)
    private fun getEncryptingCipher(keyBytes: ByteArray, iv: ByteArray): Cipher {
        val cipher = getBestCipher(CipherMode.AES_GCM_CIPHER)
        val skeySpec = SecretKeySpec(keyBytes, cipher.algorithm)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec)
        cipher.updateAAD(ivSpec.iv)
        return cipher
    }

    /**
     * Returns initialized cipher for decryption.
     *
     * @param encryptionKey Encryption key.
     * @param iv Initialization vector.
     * @return Initialized cipher.
     */
    @JvmStatic
    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class)
    fun getDecryptingCipher(encryptionKey: String, iv: ByteArray): Cipher {
        val keyBytes = Base64.decode(encryptionKey, Base64.DEFAULT)
        return getDecryptingCipher(keyBytes, iv)
    }

    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class)
    private fun getDecryptingCipher(keyBytes: ByteArray, iv: ByteArray): Cipher {
        val cipher = getBestCipher(CipherMode.AES_GCM_CIPHER)
        val skeySpec = SecretKeySpec(keyBytes, cipher.algorithm)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec)
        cipher.updateAAD(ivSpec.iv)
        return cipher
    }

    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class)
    private fun getAESCBCDecryptingCipher(keyBytes: ByteArray, iv: ByteArray): Cipher {
        val cipher = getBestCipher(CipherMode.AES_CBC_CIPHER)
        val skeySpec = SecretKeySpec(keyBytes, cipher.algorithm)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec)
        return cipher
    }

    /**
     * Decrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @return Decrypted data.
     */
    @JvmStatic
    fun decrypt(data: String?, key: String?): String? {
        return decrypt(data, key, ByteArray(12))
    }

    /**
     * Decrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Key.
     * @return Decrypted data.
     */
    @JvmStatic
    fun decrypt(data: ByteArray?, key: String?): String? {
        return decrypt(data, key, ByteArray(12))
    }

    /**
     * Decrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @param iv Initialization vector.
     * @return Decrypted data.
     */
    @JvmStatic
    fun decrypt(data: String?, key: String?, iv: ByteArray): String? {
        if (TextUtils.isEmpty(key) || data == null) {
            return data
        }
        return decrypt(data.toByteArray(), key, iv)
    }

    /**
     * Decrypts data with key using using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Key.
     * @param iv Initialization vector.
     * @return Decrypted data.
     */
    @JvmStatic
    fun decrypt(data: ByteArray?, key: String?, iv: ByteArray): String? {
        if (TextUtils.isEmpty(key)) {
            return if (data != null) {
                String(data, StandardCharsets.UTF_8)
            } else {
                null
            }
        }
        try {
            // Decodes with Base64.
            val keyBytes = Base64.decode(key, Base64.DEFAULT)
            val dataBytes = Base64.decode(data, Base64.DEFAULT)

            // Decrypts with AES.
            val decryptedData = decrypt(dataBytes, dataBytes.size, keyBytes, iv)
            return String(decryptedData, 0, decryptedData.size, StandardCharsets.UTF_8)
        } catch (ex: Exception) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during decryption", ex)
        }
        return null
    }

    /**
     * Decrypts data with key using using AES/GCM/NoPadding. The data is not Base64 encoded.
     *
     * @param data Data.
     * @param key Key.
     * @return Decrypted data.
     */
    @JvmStatic
    fun decryptWithoutBase64Encoding(data: ByteArray?, key: String?): ByteArray? {
        if (TextUtils.isEmpty(key)) {
            return data
        }
        try {
            // Decodes with Base64.
            val keyBytes = Base64.decode(key, Base64.DEFAULT)

            // Decrypts with AES.
            return decrypt(data!!, data.size, keyBytes, ByteArray(12))
        } catch (ex: Exception) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during decryption", ex)
        }
        return null
    }

    /**
     * Encrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @return Encrypted data.
     */
    @JvmStatic
    fun encrypt(data: String?, key: String?): String? {
        return try {
            encrypt(data, key, generateInitVector())
        } catch (ex: Exception) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex)
            null
        }
    }

    /**
     * Encrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @param iv Initialization vector.
     * @return Encrypted data.
     */
    @JvmStatic
    fun encrypt(data: String?, key: String?, iv: ByteArray): String? {
        if (TextUtils.isEmpty(key) || data == null) {
            return data
        }
        val bytes = encryptBytes(data, key, iv) ?: return null
        return try {
            // Do as Base64.encodeToString does, return US-ASCII string with the already Base64 encoded bytes.
            String(bytes, StandardCharsets.US_ASCII)
        } catch (e: Exception) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", e)
            null
        }
    }

    /**
     * Encrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Key.
     * @return Encrypted data.
     */
    @JvmStatic
    fun encryptBytes(data: String?, key: String?): ByteArray? {
        return try {
            encryptBytes(data, key, generateInitVector())
        } catch (ex: Exception) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex)
            null
        }
    }

    /**
     * Encrypts data with key using AES/GCM/NoPadding.
     *
     * @param data Data.
     * @param key Key.
     * @param iv Initialization vector.
     * @return Encrypted data.
     */
    @JvmStatic
    fun encryptBytes(data: String?, key: String?, iv: ByteArray): ByteArray? {
        if (TextUtils.isEmpty(key)) {
            return data?.toByteArray()
        }
        try {
            // Encrypts with our preferred cipher.
            val keyBytes = Base64.decode(key, Base64.DEFAULT)
            val dataBytes = data!!.toByteArray(StandardCharsets.UTF_8)
            return Base64.encode(encrypt(dataBytes, keyBytes, iv), Base64.DEFAULT)
        } catch (ex: Exception) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex)
        }
        return null
    }

    /**
     * Encrypts data with key using AES/GCM/NoPadding. The data is not Base64 encoded.
     *
     * @param data Data.
     * @param key Key.
     * @return Encrypted data.
     */
    @JvmStatic
    fun encryptWithoutBase64Encoding(data: ByteArray?, key: String?): ByteArray? {
        if (TextUtils.isEmpty(key)) {
            return data
        }
        try {
            // Encrypts with our preferred cipher.
            val keyBytes = Base64.decode(key, Base64.DEFAULT)
            return encrypt(data!!, keyBytes, generateInitVector())
        } catch (ex: Exception) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex)
        }
        return null
    }

    /**
     * Checks if the string is Base64 encoded.
     *
     * @param key String.
     * @return True - if encoded, False - otherwise.
     */
    @JvmStatic
    fun isBase64Encoded(key: String?): Boolean {
        return try {
            Base64.decode(key, Base64.DEFAULT)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Return HMAC SHA-256 hash of data using key.
     *
     * @param data Data.
     * @param key Key.
     * @return Hash.
     */
    @JvmStatic
    fun hash(data: String, key: String): String? {
        return try {
            // Signs with SHA-256.
            val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
            val dataBytes = data.toByteArray(StandardCharsets.UTF_8)
            val sha = Mac.getInstance(MAC_TRANSFORMATION)
            val keySpec = SecretKeySpec(keyBytes, sha.algorithm)
            sha.init(keySpec)
            val sig = sha.doFinal(dataBytes)

            // Encodes with Base64.
            Base64.encodeToString(sig, Base64.NO_WRAP)
        } catch (ex: Exception) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during hashing", ex)
            null
        }
    }

    /**
     * Returns data encrypted with an RSA public key.
     *
     * @param publicKey  RSA public key.
     * @param data       Data to be encrypted.
     * @param cipherMode Cipher mode.
     * @return Encrypted data.
     */
    @JvmStatic
    fun encryptWithRSA(publicKey: PublicKey?, data: String?, cipherMode: CipherMode): String? {
        var encryptedData: String? = null
        val encryptedBytes = encryptWithRSABytes(publicKey, data, cipherMode)
        if (encryptedBytes != null) {
            encryptedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP or Base64.NO_PADDING)
        }
        return encryptedData
    }

    /**
     * Returns data encrypted with an RSA public key.
     *
     * @param publicKey  RSA public key.
     * @param data       Data to be encrypted.
     * @param cipherMode Cipher mode.
     * @return Encrypted data.
     */
    @JvmStatic
    fun encryptWithRSABytes(publicKey: PublicKey?, data: String?, cipherMode: CipherMode): ByteArray? {
        return encryptWithPublicKey(publicKey, data, cipherMode)
    }

    /**
     * Returns data decrypted with an RSA private key.
     *
     * @param privateKey RSA private key.
     * @param data       Data to be decrypted.
     * @param cipherMode Cipher mode.
     * @return Decrypted data.
     */
    @JvmStatic
    fun decryptWithRSA(privateKey: PrivateKey?, data: String?, cipherMode: CipherMode): String? {
        var decryptedData: String? = null
        val decryptedBytes = decryptWithRSABytes(privateKey, data, cipherMode)
        if (decryptedBytes != null) {
            try {
                decryptedData = String(decryptedBytes, 0, decryptedBytes.size, StandardCharsets.UTF_8)
            } catch (e: Exception) {
                SalesforceAnalyticsLogger.e(null, TAG, "Error during asymmetric decryption using RSA", e)
            }
        }
        return decryptedData
    }

    /**
     * Returns data decrypted with an RSA private key.
     *
     * @param privateKey RSA private key.
     * @param data       Data to be decrypted.
     * @param cipherMode Ciper mode.
     * @return Decrypted data.
     */
    @JvmStatic
    fun decryptWithRSABytes(privateKey: PrivateKey?, data: String?, cipherMode: CipherMode): ByteArray? {
        return decryptWithPrivateKey(privateKey, data, cipherMode, logErrorOnFailure = true)
    }

    /**
     * Attempt to decrypt with a RSA private key using different cipher modes:
     * - RSA_OAEP_SHA256
     * - then RSA_PKCS1 (legacy)
     *
     * TODO retire this method when the server only supports RSA_OAEP_SHA256
     *
     * @param privateKey RSA private key.
     * @param data       Data to be decrypted.
     * @return Decrypted data.
     */
    @JvmStatic
    fun decryptWithRSAMultiCipherNodes(privateKey: PrivateKey?, data: String?): ByteArray? {
        var result = decryptWithPrivateKey(privateKey, data, CipherMode.RSA_OAEP_SHA256, logErrorOnFailure = false)
        if (result == null) {
            result = decryptWithPrivateKey(privateKey, data, CipherMode.RSA_PKCS1, logErrorOnFailure = true)
        }
        return result
    }

    /**
     * Decrypts the given bytes using key and IV.
     *
     * @param data Data bytes.
     * @param key Key bytes.
     * @param iv Initialization vector bytes.
     * @return Decrypted data.
     */
    @JvmStatic
    fun decryptBytes(data: ByteArray, key: ByteArray, iv: ByteArray): String? {
        return try {
            val cipher = getAESCBCDecryptingCipher(key, iv)
            val result = cipher.doFinal(data, 0, data.size)
            String(result, 0, result.size, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            SalesforceAnalyticsLogger.e(null, TAG, "Error during symmetric decryption using AES", e)
            null
        }
    }

    /**
     * Retrieves data from an InputStream.  Guaranteed to close the InputStream.
     *
     * @param stream InputStream data.
     * @return Data from the InputStream as a String.
     * @throws IOException Provide log details of this exception in a catch with specifics
     * about the operation this method was called for.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getStringFromStream(stream: InputStream): String {
        val output = getByteArrayStreamFromStream(stream)
        return output.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * Retrieves data from an InputStream.  Guaranteed to close the InputStream.
     *
     * @param stream InputStream data.
     * @return Data from the InputStream as a ByteArrayOutputStream
     * @throws IOException Provide log details of this exception in a catch with specifics
     * about the operation this method was called for.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getByteArrayStreamFromStream(stream: InputStream): ByteArrayOutputStream {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_LENGTH)
        var length: Int
        try {
            while (stream.read(buffer).also { length = it } != -1) {
                output.write(buffer, 0, length)
            }
        } finally {
            stream.close()
        }
        return output
    }

    /**
     * Retrieves data from a File.
     *
     * @param file File object.
     * @return Data from the input File as a String.
     * @throws IOException Provide log details of this exception in a catch with specifics
     * about the operation this method was called for.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun getStringFromFile(file: File): String {
        var stream: FileInputStream? = null
        val output: String
        try {
            stream = FileInputStream(file)
            output = getStringFromStream(stream)
        } finally {
            stream?.close()
        }
        return output
    }

    @JvmStatic
    fun encryptWithPublicKey(publicKey: PublicKey?, data: String?, cipherMode: CipherMode): ByteArray? {
        if (publicKey == null || TextUtils.isEmpty(data)) {
            return null
        }
        return try {
            val cipherInstance = getBestCipher(cipherMode)
            initRSACipher(cipherInstance, Cipher.ENCRYPT_MODE, publicKey, cipherMode)
            cipherInstance.doFinal(data!!.toByteArray())
        } catch (e: Exception) {
            SalesforceAnalyticsLogger.e(null, TAG, "Failed to encrypt with $cipherMode", e)
            null
        }
    }

    private fun decryptWithPrivateKey(
        privateKey: PrivateKey?,
        data: String?,
        cipherMode: CipherMode,
        logErrorOnFailure: Boolean
    ): ByteArray? {
        if (privateKey == null || TextUtils.isEmpty(data)) {
            return null
        }
        return try {
            val cipherInstance = getBestCipher(cipherMode)
            initRSACipher(cipherInstance, Cipher.DECRYPT_MODE, privateKey, cipherMode)
            val decodedBytes = Base64.decode(data!!.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING)
            cipherInstance.doFinal(decodedBytes)
        } catch (e: Exception) {
            if (logErrorOnFailure) {
                SalesforceAnalyticsLogger.e(null, TAG, "Failed to decrypt with $cipherMode", e)
            } else {
                SalesforceAnalyticsLogger.w(null, TAG, "Failed to decrypt with $cipherMode")
            }
            null
        }
    }

    @Throws(InvalidKeyException::class, InvalidAlgorithmParameterException::class)
    private fun initRSACipher(cipherInstance: Cipher, opmode: Int, key: Key, cipherMode: CipherMode) {
        cipherInstance.init(opmode, key)
    }

    private fun generateInitVector(): ByteArray {
        // Create the system recommended secure random number generator provider algorithm.
        val random = SecureRandom()
        val iv = ByteArray(12)
        random.nextBytes(iv)
        return iv
    }

    @Throws(GeneralSecurityException::class)
    private fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = getEncryptingCipher(key, iv)
        val meat = cipher.doFinal(data)

        // Prepends the IV to the encoded data (first 12 bytes for GCM).
        val result = ByteArray(iv.size + meat.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(meat, 0, result, iv.size, meat.size)
        return result
    }

    @Throws(GeneralSecurityException::class)
    private fun decrypt(data: ByteArray, length: Int, key: ByteArray, iv: ByteArray): ByteArray {
        // Grabs the init vector prefix (first 12 bytes for GCM, or first 16 bytes for CBC).
        System.arraycopy(data, 0, iv, 0, iv.size)

        // Grabs the encrypted body after the init vector prefix.
        val meatLen = length - iv.size
        val meatOffset = iv.size
        val meat = ByteArray(meatLen)
        System.arraycopy(data, meatOffset, meat, 0, meatLen)
        val cipher: Cipher

        // AES/GCM has an IV of length 12 bytes, whereas AES/CBC has an IV of length 16 bytes.
        cipher = if (iv.size == 12) {
            getDecryptingCipher(key, iv)
        } else {
            getAESCBCDecryptingCipher(key, iv)
        }
        return cipher.doFinal(meat, 0, meatLen)
    }

    private fun getBestCipher(cipherMode: CipherMode): Cipher {
        var cipher: Cipher? = null
        try {
            cipher = when (cipherMode) {
                CipherMode.AES_GCM_CIPHER, CipherMode.RSA_PKCS1 -> {
                    Cipher.getInstance(cipherMode.fullName)
                }
                CipherMode.RSA_OAEP_SHA256 -> {
                    Cipher.getInstance(cipherMode.fullName, BOUNCY_CASTLE_WORKAROUND)
                }
                CipherMode.AES_CBC_CIPHER -> {
                    Cipher.getInstance(cipherMode.fullName)
                }
            }
        } catch (e: Exception) {
            SalesforceAnalyticsLogger.e(null, TAG, "No cipher transformation available", e)
        }
        return cipher!!
    }
}
