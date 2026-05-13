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

import android.app.Application
import android.app.Instrumentation
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.TestForceApp
import com.salesforce.androidsdk.analytics.security.Encryptor
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

/**
 * Tests for [KeyStoreWrapper].
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class KeyStoreWrapperTest {

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val app = Instrumentation.newApplication(
            TestForceApp::class.java,
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        keyStoreWrapper.deleteKey(KEY_1)
        keyStoreWrapper.deleteKey(KEY_2)
        keyStoreWrapper.deleteKey(KEY_OAEP_TEST)
    }

    @Test
    fun testGetRSAPublicString() {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper)
        val key1 = keyStoreWrapper.getRSAPublicString(KEY_1, RSA_LENGTH)
        val key1Again = keyStoreWrapper.getRSAPublicString(KEY_1, RSA_LENGTH)
        val key2 = keyStoreWrapper.getRSAPublicString(KEY_2, RSA_LENGTH)
        Assert.assertEquals("Public keys with the same name should be the same", key1, key1Again)
        Assert.assertNotSame("Public keys with different names should be different", key1, key2)
    }

    @Test
    fun testGetRSAPrivateKey() {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper)
        val key1 = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH)
        val key1Again = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH)
        Assert.assertEquals("Private keys with the same name should be the same", key1, key1Again)
    }

    @Test
    fun testRSAPKCS1EncryptDecrypt() {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper)
        val privateKey = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH)
        val publicKey = keyStoreWrapper.getRSAPublicKey(KEY_1, RSA_LENGTH)
        val data = "Test data for encryption"
        val encryptedData = Encryptor.encryptWithRSA(publicKey, data, Encryptor.CipherMode.RSA_PKCS1)
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData)
        val decryptedData = Encryptor.decryptWithRSA(privateKey, encryptedData, Encryptor.CipherMode.RSA_PKCS1)
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData)
    }

    @Test
    fun testRSAOAEPSHA256EncryptDecrypt() {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper)
        val privateKey = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH)
        val publicKey = keyStoreWrapper.getRSAPublicKey(KEY_1, RSA_LENGTH)
        val data = "Test data for encryption"
        val encryptedData = Encryptor.encryptWithRSA(publicKey, data, Encryptor.CipherMode.RSA_OAEP_SHA256)
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData)
        val decryptedData = Encryptor.decryptWithRSA(privateKey, encryptedData, Encryptor.CipherMode.RSA_OAEP_SHA256)
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData)
    }


    @Test
    fun testGetECPublicString() {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper)
        val key1 = keyStoreWrapper.getECPublicString(KEY_1)
        val key1Again = keyStoreWrapper.getECPublicString(KEY_1)
        val key2 = keyStoreWrapper.getECPublicString(KEY_2)
        Assert.assertEquals("Public keys with the same name should be the same", key1, key1Again)
        Assert.assertNotSame("Public keys with different names should be different", key1, key2)
    }

    @Test
    fun testGetECPrivateKey() {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper)
        val key1 = keyStoreWrapper.getECPrivateKey(KEY_1)
        val key1Again = keyStoreWrapper.getECPrivateKey(KEY_1)
        Assert.assertEquals("Private keys with the same name should be the same", key1, key1Again)
    }

    // RSA is used to encrypt push notifications.
    // 1. Client generates key pair, stores keys to key store and sends public key to server.
    // 2. Server uses public key to encrypt part of push notification.
    // 3. Client uses private key stored in key store to decrypt push notification.
    //
    // In 12.0 client will use a new RSA cipher mode
    // In 250 server will use the new RSA cipher mode

    /**
     * New client against new server
     * 1. Client generates key pair with new code
     * 2. Server encrypts push notification using new RSA cipher mode
     * 3. Client tries to decrypt push notification
     */
    @Test
    fun testDecryptDataEncryptedWithNewRSACipher() {
        tryNewOrUpgradedClientAgainstNewOrOldServer(true, true, false)
    }

    /**
     * New client against old server
     * 1. Client generates key pair with new code
     * 2. Server has not been upgraded yet and encrypts push notification using old RSA cipher mode
     * 3. Client tries to decrypt push notification
     */
    @Test
    fun testDecryptDataEncryptedWithLegacyRSACipher() {
        tryNewOrUpgradedClientAgainstNewOrOldServer(true, false, false)
    }

    /**
     * Upgraded client against new server
     * 1. Client generated key pair before upgrading to 11.1.1
     * 2. Server encrypts push notification using new RSA cipher mode
     * 3. Client tries to decrypt push notification
     */
    @Test
    fun testDecryptDataEncryptedWithNewRSACipherForKeyCreatedBeforeUpgrade() {
        // NB: only works with upgrade step (which regenerates the key)
        tryNewOrUpgradedClientAgainstNewOrOldServer(false, true, true)
    }

    /**
     * Upgraded client against old server
     * 1. Client generates key pair before upgrading to 11.1.1
     * 2. Server has not been upgraded yet and encrypts push notification using old RSA cipher mode
     * 3. Client tries to decrypt push notification
     */
    @Test
    fun testDecryptDataEncryptedWithLegacyRSACipherForKeyCreatedBeforeUpgrade() {
        // With upgrade step (which regenerates the key)
        tryNewOrUpgradedClientAgainstNewOrOldServer(false, false, true)

        // Also works without the upgrade step
        tryNewOrUpgradedClientAgainstNewOrOldServer(false, false, false)
    }

    @Test
    fun testKeySupportsOAEPPadding() {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper)

        // Create a legacy key pair without OAEP padding support
        keyStoreWrapper.legacyCreateKeysIfNecessary("RSA", KEY_OAEP_TEST, RSA_LENGTH)

        // Verify the legacy key does NOT support OAEP padding
        Assert.assertFalse(
            "Legacy key should not support OAEP padding",
            keyStoreWrapper.keySupportsOAEPPadding(KEY_OAEP_TEST)
        )

        // Delete the legacy key and create a modern key pair
        keyStoreWrapper.deleteKey(KEY_OAEP_TEST)
        keyStoreWrapper.getRSAPublicKey(KEY_OAEP_TEST, RSA_LENGTH) // This creates the key with modern spec

        // Verify the modern key DOES support OAEP padding
        Assert.assertTrue(
            "Modern key should support OAEP padding",
            keyStoreWrapper.keySupportsOAEPPadding(KEY_OAEP_TEST)
        )
    }

    /**
     * Helper method for tests for RSA cipher mode change
     * @param newClient true means new client (key generated with new code), false means upgraded client (key generated the old way)
     * @param newServer true means new server (using new cipher mode), false means old server (using old cipher mode)
     * @param simulateUpgradeStep true means run the code that SalesforceSDKUpgradeManager would run if coming from an older version (one with the old cipher mode)
     */
    private fun tryNewOrUpgradedClientAgainstNewOrOldServer(newClient: Boolean, newServer: Boolean, simulateUpgradeStep: Boolean) {
        val keyStoreWrapper = KeyStoreWrapper.getInstance()
        Assert.assertNotNull("KeyStoreWrapper instance should not be null", keyStoreWrapper)
        if (!newClient) {
            // Simulating upgraded client / generating key the old way
            keyStoreWrapper.legacyCreateKeysIfNecessary("RSA", KEY_1, RSA_LENGTH)
            if (simulateUpgradeStep) {
                // Simulating the upgrade step which should run when app first run
                KeyStoreWrapper.getInstance().deleteKey(KEY_1)
            }
        }
        val privateKey = keyStoreWrapper.getRSAPrivateKey(KEY_1, RSA_LENGTH)
        val publicKey = keyStoreWrapper.getRSAPublicKey(KEY_1, RSA_LENGTH)
        val data = "Test data for encryption"
        // Simulating server
        val encryptedBytes = Encryptor.encryptWithPublicKey(
            publicKey, data,
            if (newServer)
                Encryptor.CipherMode.RSA_OAEP_SHA256 // new server / cipher mode
            else
                Encryptor.CipherMode.RSA_PKCS1       // old server / cipher mode
        )

        val encryptedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP or Base64.NO_PADDING)
        Assert.assertNotSame("Encrypted data should not match original data", data, encryptedData)
        val decryptedData = String(Encryptor.decryptWithRSAMultiCipherNodes(privateKey, encryptedData)!!, StandardCharsets.UTF_8)
        Assert.assertEquals("Decrypted data should match original data", data, decryptedData)
    }

    companion object {
        private const val KEY_1 = "key_1"
        private const val KEY_2 = "key_2"
        private const val KEY_OAEP_TEST = "key_oaep_test"
        private const val RSA_LENGTH = 2048
    }
}
