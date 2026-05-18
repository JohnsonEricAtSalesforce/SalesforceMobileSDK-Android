/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateException

/**
 * This class provides utilities to interact with the Android KeyStore.
 * For more information on the KeyStore, see [KeyStore].
 *
 * @author bhariharan
 */
class KeyStoreWrapper private constructor() {

    private var keyStore: KeyStore? = null

    /**
     * Delete key if it exists.
     *
     * @param name Name of the key to delete.
     */
    fun deleteKey(name: String) {
        try {
            if (name.isNotEmpty() && keyStore!!.containsAlias(name)) {
                keyStore!!.deleteEntry(name)
            }
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Could not delete key $name", e)
        }
    }

    /**
     * Generates an RSA keypair and returns the public key of length 2048.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA public key.
     */
    fun getRSAPublicKey(name: String): PublicKey? {
        return getRSAPublicKey(name, RSA_KEY_LENGTH)
    }

    /**
     * Generates an RSA keypair and returns the encoded public key string of length 2048.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA public key string.
     */
    fun getRSAPublicString(name: String): String? {
        return getRSAPublicString(name, RSA_KEY_LENGTH)
    }

    /**
     * Generates an RSA keypair and returns the private key of length 2048.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return RSA private key.
     */
    fun getRSAPrivateKey(name: String): PrivateKey? {
        return getRSAPrivateKey(name, RSA_KEY_LENGTH)
    }

    /**
     * Generates an RSA keypair and returns the public key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA public key.
     */
    fun getRSAPublicKey(name: String, length: Int): PublicKey? {
        return getPublicKey(RSA, name, length)
    }

    /**
     * Generates an RSA keypair and returns the encoded public key string.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA public key string.
     */
    fun getRSAPublicString(name: String, length: Int): String? {
        return getPublicKeyString(RSA, name, length)
    }

    /**
     * Generates an RSA keypair and returns the private key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @param length Key length.
     * @return RSA private key.
     */
    fun getRSAPrivateKey(name: String, length: Int): PrivateKey? {
        return getPrivateKey(RSA, name, length)
    }

    /**
     * Generates an EC keypair of length 256, and returns the public key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return EC public key.
     */
    fun getECPublicKey(name: String): PublicKey? {
        return getPublicKey(EC, name, EC_KEY_LENGTH)
    }

    /**
     * Generates an EC keypair of length 256, and returns the encoded public key string.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return EC public key string.
     */
    fun getECPublicString(name: String): String? {
        return getPublicKeyString(EC, name, EC_KEY_LENGTH)
    }

    /**
     * Generates an EC keypair of length 256, and returns the private key.
     *
     * @param name Alias of the entry in which the generated key will appear in Android KeyStore.
     * @return EC private key.
     */
    fun getECPrivateKey(name: String): PrivateKey? {
        return getPrivateKey(EC, name, EC_KEY_LENGTH)
    }

    @Throws(CertificateException::class, NoSuchAlgorithmException::class, IOException::class, KeyStoreException::class)
    private fun loadKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore
    }

    private fun getPublicKey(algorithm: String, name: String, length: Int): PublicKey? {
        var publicKey: PublicKey? = null
        createKeysIfNecessary(algorithm, name, length)
        try {
            publicKey = keyStore!!.getCertificate(name).publicKey
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Could not retrieve public key", e)
        }
        return publicKey
    }

    private fun getPublicKeyString(algorithm: String, name: String, length: Int): String? {
        val publicKey = getPublicKey(algorithm, name, length)
        var publicKeyBase64: String? = null
        if (publicKey != null) {
            publicKeyBase64 = Base64.encodeToString(
                publicKey.encoded,
                Base64.NO_WRAP or Base64.NO_PADDING
            )
        }
        return publicKeyBase64
    }

    private fun getPrivateKey(algorithm: String, name: String, length: Int): PrivateKey? {
        var privateKey: PrivateKey? = null
        createKeysIfNecessary(algorithm, name, length)
        try {
            privateKey = keyStore!!.getKey(name, null) as PrivateKey
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Could not retrieve private key", e)
        }
        return privateKey
    }

    @Synchronized
    private fun createKeysIfNecessary(algorithm: String, name: String, length: Int) {
        try {
            if (!keyStore!!.containsAlias(name)) {

                // Generates a new key pair.
                val kpg = KeyPairGenerator.getInstance(algorithm, ANDROID_KEYSTORE)
                val keyGenParameterSpecBuilder = KeyGenParameterSpec.Builder(
                    name,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(length)
                    .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1, KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)

                /*
                 * Disabling StrongBox based on Google's recommendation - it's not a good
                 * fit for this use case, since the key will need to be retrieved multiple
                 * times. Besides, StrongBox Keymaster is available only on a few devices,
                 * such as the Pixel 3 and Pixel 3 XL at this time.
                 */
                keyGenParameterSpecBuilder.setIsStrongBoxBacked(false)
                kpg.initialize(keyGenParameterSpecBuilder.build())
                kpg.generateKeyPair()
            }
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Could not generate key pair", e)
        }
    }

    /**
     * Checks if the RSA key supports OAEP encryption padding.
     *
     * @param name Alias of the key to check.
     * @return true if the key supports OAEP padding, false otherwise.
     */
    fun keySupportsOAEPPadding(name: String): Boolean {
        try {
            if (keyStore!!.containsAlias(name)) {
                val privateKey = keyStore!!.getKey(name, null) as PrivateKey
                val keyFactory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
                val keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
                val encryptionPaddings = keyInfo.encryptionPaddings

                if (encryptionPaddings != null) {
                    for (padding in encryptionPaddings) {
                        if (KeyProperties.ENCRYPTION_PADDING_RSA_OAEP == padding) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Could not check key padding capabilities", e)
        }
        return false
    }

    // For testing only - create key the way we used to before the 11.1.1 cipher change
    @Synchronized
    internal fun legacyCreateKeysIfNecessary(algorithm: String, name: String, length: Int) {
        try {
            if (!keyStore!!.containsAlias(name)) {

                // Generates a new key pair.
                val kpg = KeyPairGenerator.getInstance(algorithm, ANDROID_KEYSTORE)
                val keyGenParameterSpecBuilder = KeyGenParameterSpec.Builder(
                    name,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(length)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)

                /*
                 * Disabling StrongBox based on Google's recommendation - it's not a good
                 * fit for this use case, since the key will need to be retrieved multiple
                 * times. Besides, StrongBox Keymaster is available only on a few devices,
                 * such as the Pixel 3 and Pixel 3 XL at this time.
                 */
                keyGenParameterSpecBuilder.setIsStrongBoxBacked(false)
                kpg.initialize(keyGenParameterSpecBuilder.build())
                kpg.generateKeyPair()
            }
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Could not generate key pair", e)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val RSA = "RSA"
        private const val EC = "EC"
        private const val EC_KEY_LENGTH = 256
        private const val RSA_KEY_LENGTH = 2048
        private const val TAG = "KeyStoreWrapper"

        private var INSTANCE: KeyStoreWrapper? = null

        /**
         * Returns an instance of this class, after initializing the KeyStore if required.
         *
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(): KeyStoreWrapper {
            if (INSTANCE == null) {
                try {
                    INSTANCE = KeyStoreWrapper()
                    INSTANCE!!.keyStore = INSTANCE!!.loadKeyStore()
                } catch (e: Exception) {
                    SalesforceSDKLogger.e(TAG, "Could not load KeyStore", e)
                }
            }
            return INSTANCE!!
        }
    }
}
