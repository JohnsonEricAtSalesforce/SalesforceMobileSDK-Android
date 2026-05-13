/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.security

import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator

/**
 * This class provides methods to generate a unique ID that can be used as an encryption
 * key. The key is derived from an AES-256 base using SecureRandom or AES-128 base using UUID.
 *
 * @author bhariharan
 */
object SalesforceKeyGenerator {

    private const val TAG = "SalesforceKeyGenerator"
    private const val SHARED_PREF_FILE = "identifier.xml"
    private const val ENCRYPTED_ID_SHARED_PREF_KEY = "encrypted_%s"
    private const val ID_PREFIX = "id_"
    internal const val LEGACY_KEYPAIR_ALIAS = "com.salesforce.androidsdk.security.KEYPAIR"
    internal const val MSDK_KEYPAIR_ALIAS = "com.salesforce.androidsdk.security.MSDK_KEYPAIR"
    private const val SHA256 = "SHA-256"
    private const val AES = "AES"

    private val CACHED_ENCRYPTION_KEYS: MutableMap<String, String> = ConcurrentHashMap()

    /**
     * Returns the unique ID being used. The default key length is 256 bits.
     *
     * @param name Unique name associated with this unique ID.
     * @return Unique ID.
     */
    @JvmStatic
    fun getUniqueId(name: String): String {
        return getUniqueId(name, 256)
    }

    /**
     * Returns the unique ID being used based on the key length.
     *
     * @param name Unique name associated with this unique ID.
     * @param length Key length.
     * @return Unique ID.
     */
    @JvmStatic
    fun getUniqueId(name: String, length: Int): String {
        return generateUniqueIdIfNoneStored(name, length)
    }

    /**
     * Returns the encryption key being used.
     *
     * @param name Unique name associated with this encryption key.
     * @return Encryption key.
     */
    @JvmStatic
    fun getEncryptionKey(name: String?): String? {
        if (TextUtils.isEmpty(name)) {
            return null
        }
        var encryptionKey = CACHED_ENCRYPTION_KEYS[name]
        if (encryptionKey == null) {
            encryptionKey = generateEncryptionKey(name!!)
            if (encryptionKey != null) {
                CACHED_ENCRYPTION_KEYS[name] = encryptionKey
            }
        }
        return encryptionKey
    }

    /**
     * Returns a randomly generated 128-byte key that's URL safe.
     *
     * @return Random 128-byte key.
     */
    @JvmStatic
    fun getRandom128ByteKey(): String {
        val secureRandom = SecureRandom()
        val random = ByteArray(128)
        secureRandom.nextBytes(random)
        return Base64.encodeToString(random, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }

    /**
     * Returns the SHA-256 hashed value of the supplied private key.
     *
     * @param privateKey Private key.
     * @return SHA-256 hash.
     */
    @JvmStatic
    fun getSHA256Hash(privateKey: String): String? {
        var hashedString: String? = null
        val privateKeyBytes = privateKey.toByteArray(StandardCharsets.US_ASCII)
        try {
            val digest = MessageDigest.getInstance(SHA256)
            val hash = digest.digest(privateKeyBytes)
            hashedString = Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while generating SHA-256 hash", e)
        }
        return hashedString
    }

    @Synchronized
    private fun generateEncryptionKey(name: String): String? {
        var encryptionKey: String? = null
        try {
            val keyString = getUniqueId(name)
            var secretKey = keyString.toByteArray(StandardCharsets.UTF_8)
            val md = MessageDigest.getInstance(SHA256)
            secretKey = md.digest(secretKey)
            val dest = ByteArray(32)
            System.arraycopy(secretKey, 0, dest, 0, 32)
            encryptionKey = Base64.encodeToString(dest, Base64.NO_WRAP)
        } catch (ex: Exception) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while getting encryption key", ex)
        }
        return encryptionKey
    }

    @Synchronized
    private fun generateUniqueIdIfNoneStored(name: String, length: Int): String {
        var uniqueId: String? = null
        var storeUniqueId = false

        // Checks if we have a unique identifier stored.
        val encryptedUniqueId = readFromSharedPrefs(ID_PREFIX + name)
        if (encryptedUniqueId != null) {
            val msdkPrivateKey = KeyStoreWrapper.getInstance().getRSAPrivateKey(MSDK_KEYPAIR_ALIAS)
            uniqueId = Encryptor.decryptWithRSA(msdkPrivateKey, encryptedUniqueId, Encryptor.CipherMode.RSA_OAEP_SHA256)
            // Decryption failed - must have been encrypted with legacy key (LEGACY_KEYPAIR_ALIAS)
            if (uniqueId == null) {
                val privateKey = KeyStoreWrapper.getInstance().getRSAPrivateKey(LEGACY_KEYPAIR_ALIAS)
                uniqueId = Encryptor.decryptWithRSA(privateKey, encryptedUniqueId, Encryptor.CipherMode.RSA_OAEP_SHA256)
                // Decryption failed - must have been encrypted with legacy key with old cipher mode
                if (uniqueId == null) {
                    uniqueId = Encryptor.decryptWithRSA(privateKey, encryptedUniqueId, Encryptor.CipherMode.RSA_PKCS1)
                }
                // We need to store it with the new key (MSDK_KEYPAIR_ALIAS)
                storeUniqueId = true
            }
        }

        // Otherwise create a new one and store it
        if (uniqueId == null) {
            uniqueId = createUniqueId(length)
            storeUniqueId = true
        }

        // Encrypt and store unique id if it was just created, or if it had to be decrypted with old key
        if (storeUniqueId) {
            val publicKey = KeyStoreWrapper.getInstance().getRSAPublicKey(MSDK_KEYPAIR_ALIAS)
            val encryptedKey = Encryptor.encryptWithRSA(publicKey, uniqueId, Encryptor.CipherMode.RSA_OAEP_SHA256) ?: ""
            storeInSharedPrefs(ID_PREFIX + name, encryptedKey)
        }

        return uniqueId!!
    }

    private fun createUniqueId(length: Int): String {
        val uniqueId: String
        try {
            // Create the key generator with its recommended secure random number generator provider algorithm.
            val keyGenerator = KeyGenerator.getInstance(AES)
            keyGenerator.init(length)

            // Generates a 256-bit key.
            uniqueId = Base64.encodeToString(keyGenerator.generateKey().encoded, Base64.NO_WRAP)
        } catch (e: NoSuchAlgorithmException) {
            SalesforceSDKLogger.e(TAG, "Security exception thrown", e)

            // Generates a random UUID 128-bit key instead.
            return UUID.randomUUID().toString()
        }
        return uniqueId
    }

    internal fun readFromSharedPrefs(key: String): String? {
        val prefs = SalesforceSDKManager.getInstance().appContext.getSharedPreferences(SHARED_PREF_FILE, 0)
        return prefs.getString(getSharedPrefKey(key), null)
    }

    @Synchronized
    internal fun storeInSharedPrefs(key: String, value: String) {
        val prefs = SalesforceSDKManager.getInstance().appContext.getSharedPreferences(SHARED_PREF_FILE, 0)
        prefs.edit().putString(getSharedPrefKey(key), value).apply()
    }

    private fun getSharedPrefKey(name: String): String {
        val suffix = if (TextUtils.isEmpty(name)) "" else name
        return String.format(Locale.US, ENCRYPTED_ID_SHARED_PREF_KEY, suffix)
    }

    /**
     * Clears all stored identifiers from shared preferences. This should be called
     * when the last user logs out to ensure no encrypted identifiers remain on the device.
     */
    @JvmStatic
    @Synchronized
    fun clearAll() {
        val prefs = SalesforceSDKManager.getInstance().appContext.getSharedPreferences(SHARED_PREF_FILE, 0)
        prefs.edit().clear().apply()
        CACHED_ENCRYPTION_KEYS.clear()
        SalesforceSDKLogger.d(TAG, "Cleared all identifiers from shared preferences")
    }
}
