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

import android.app.Instrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.TestForceApp
import com.salesforce.androidsdk.analytics.security.Encryptor
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [SalesforceKeyGenerator].
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceKeyGeneratorTest {

    companion object {
        private const val KEY_1 = "key_1"
        private const val KEY_2 = "key_2"
        private const val KEY_3 = "key_3"
    }

    @Before
    fun setUp() {
        val app = Instrumentation.newApplication(
            TestForceApp::class.java,
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app)
    }

    @Test
    fun testGetUniqueId() {
        val id1 = SalesforceKeyGenerator.getUniqueId(KEY_1)
        val id1Again = SalesforceKeyGenerator.getUniqueId(KEY_1)
        val id2 = SalesforceKeyGenerator.getUniqueId(KEY_2)

        // Output: 4*Math.Ceiling(((double)bytes.Length/3)))
        // 4*Math.Ceiling(32/3) = 44
        Assert.assertEquals("The encoded string based on an AES-256 key should have 58 characters", 44, id1!!.length)
        Assert.assertEquals("Unique IDs with the same name should be the same", id1Again, id1)
        Assert.assertNotSame("Unique IDs with different names should be different", id2, id1)
        val id3 = SalesforceKeyGenerator.getUniqueId(KEY_3, 128)
        val id3Again = SalesforceKeyGenerator.getUniqueId(KEY_3, 128)

        // 4*Math.Ceiling(16/3) = 24
        Assert.assertEquals("The encoded string based on an AES-128 key should have 38 characters", 24, id3!!.length)
        Assert.assertEquals("Unique IDs with the same name should be the same", id3Again, id3)
    }

    @Test
    fun testGetEncryptionKey() {
        val id1 = SalesforceKeyGenerator.getEncryptionKey(KEY_1)
        val id1Again = SalesforceKeyGenerator.getEncryptionKey(KEY_1)
        val id2 = SalesforceKeyGenerator.getEncryptionKey(KEY_2)
        Assert.assertEquals("Encryption keys with the same name should be the same", id1Again, id1)
        Assert.assertNotSame("Encryption keys with different names should be different", id2, id1)
    }

    @Test
    fun testGetUniqueIdStoredUsingLegacyKeyPairAndOldCipherMode() {
        encryptAndStoreInPrefs("test_name", "test_value", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1)
        Assert.assertEquals("test_value", decryptFromPrefs("test_name", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))

        // Now calling getUniqueId
        Assert.assertEquals("test_value", SalesforceKeyGenerator.getUniqueId("test_name"))

        // The value should have been re-encrypted
        // - it should not be decryptable with the legacy key pair
        // - it should be decryptable with the msdk key pair
        Assert.assertNull(decryptFromPrefs("test_name", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value", decryptFromPrefs("test_name", SalesforceKeyGenerator.MSDK_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))
    }

    @Test
    fun testGetUniqueIdStoredUsingLegacyKeyPairAndNewCipherMode() {
        encryptAndStoreInPrefs("test_name", "test_value", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256)
        Assert.assertEquals("test_value", decryptFromPrefs("test_name", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))

        // Now calling getUniqueId
        Assert.assertEquals("test_value", SalesforceKeyGenerator.getUniqueId("test_name"))

        // The value should have been re-encrypted
        // - it should not be decryptable with the legacy key pair
        // - it should be decryptable with the msdk key pair
        Assert.assertNull(decryptFromPrefs("test_name", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))
        Assert.assertEquals("test_value", decryptFromPrefs("test_name", SalesforceKeyGenerator.MSDK_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))
    }

    @Test
    fun testMultipleGetUniqueIdStoredUsingLegacyKeyPair() {
        encryptAndStoreInPrefs("test_name_1", "test_value_1", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1)
        encryptAndStoreInPrefs("test_name_2", "test_value_2", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1)
        encryptAndStoreInPrefs("test_name_3", "test_value_3", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1)
        Assert.assertEquals("test_value_1", decryptFromPrefs("test_name_1", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_2", decryptFromPrefs("test_name_2", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_3", decryptFromPrefs("test_name_3", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))

        // Now calling getUniqueId for the first one
        Assert.assertEquals("test_value_1", SalesforceKeyGenerator.getUniqueId("test_name_1"))

        // The value should have been re-encrypted
        // - it should not be decryptable with the legacy key pair
        // - it should be decryptable with the msdk key pair
        Assert.assertNull(decryptFromPrefs("test_name_1", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_1", decryptFromPrefs("test_name_1", SalesforceKeyGenerator.MSDK_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))

        // Other values should not have been re-encrypted
        Assert.assertEquals("test_value_2", decryptFromPrefs("test_name_2", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_3", decryptFromPrefs("test_name_3", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))

        // Now calling getUniqueId for the second one
        Assert.assertEquals("test_value_2", SalesforceKeyGenerator.getUniqueId("test_name_2"))

        // The value should have been re-encrypted
        // - it should not be decryptable with the legacy key pair
        // - it should be decryptable with the msdk key pair
        Assert.assertNull(decryptFromPrefs("test_name_2", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_2", decryptFromPrefs("test_name_2", SalesforceKeyGenerator.MSDK_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))

        // The already re-encrypted value should have been left alone
        Assert.assertNull(decryptFromPrefs("test_name_1", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_1", decryptFromPrefs("test_name_1", SalesforceKeyGenerator.MSDK_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))

        // The third one should not have been re-encrypted
        Assert.assertEquals("test_value_3", decryptFromPrefs("test_name_3", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))

        // Now calling getUniqueId for the third one
        Assert.assertEquals("test_value_3", SalesforceKeyGenerator.getUniqueId("test_name_3"))

        // The value should have been re-encrypted
        // - it should not be decryptable with the legacy key pair
        // - it should be decryptable with the msdk key pair
        Assert.assertNull(decryptFromPrefs("test_name_3", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_3", decryptFromPrefs("test_name_3", SalesforceKeyGenerator.MSDK_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))

        // The already re-encrypted values should have been left alone
        Assert.assertNull(decryptFromPrefs("test_name_1", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_1", decryptFromPrefs("test_name_1", SalesforceKeyGenerator.MSDK_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))
        Assert.assertNull(decryptFromPrefs("test_name_2", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1))
        Assert.assertEquals("test_value_2", decryptFromPrefs("test_name_2", SalesforceKeyGenerator.MSDK_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_OAEP_SHA256))
    }

    @Test
    fun testMakeSureLegacyKeyPairNotRecreated() {
        encryptAndStoreInPrefs("test_name", "test_value", SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS, Encryptor.CipherMode.RSA_PKCS1)
        val legacyPublicKey = KeyStoreWrapper.getInstance().getRSAPublicKey(SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS)
        // Now calling getUniqueId
        Assert.assertEquals("test_value", SalesforceKeyGenerator.getUniqueId("test_name"))
        // The legacy key pair should NOT have been deleted or recreated
        Assert.assertEquals(legacyPublicKey.toString(), KeyStoreWrapper.getInstance().getRSAPublicKey(SalesforceKeyGenerator.LEGACY_KEYPAIR_ALIAS).toString())
    }

    private fun encryptAndStoreInPrefs(name: String, value: String, keyPairAlias: String, cipherMode: Encryptor.CipherMode) {
        val publicKey = KeyStoreWrapper.getInstance().getRSAPublicKey(keyPairAlias)
        val encryptedKey = Encryptor.encryptWithRSA(publicKey!!, value, cipherMode)
        SalesforceKeyGenerator.storeInSharedPrefs("id_$name", encryptedKey)
    }

    private fun decryptFromPrefs(name: String, keyPairAlias: String, cipherMode: Encryptor.CipherMode): String? {
        val privateKey = KeyStoreWrapper.getInstance().getRSAPrivateKey(keyPairAlias)
        val encryptedValue = SalesforceKeyGenerator.readFromSharedPrefs("id_$name")
        return Encryptor.decryptWithRSA(privateKey!!, encryptedValue!!, cipherMode)
    }
}
