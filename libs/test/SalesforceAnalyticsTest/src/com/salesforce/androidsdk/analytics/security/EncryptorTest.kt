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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException

/**
 * Tests for Encryptor.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class EncryptorTest {

    /**
     * Test to make sure that encrypt does nothing when given a null key.
     */
    @Test
    fun testEncryptWithNullKey() {
        for (data in TEST_DATA) {
            Assert.assertEquals(
                "Encrypt should have left the string unchanged", data,
                Encryptor.encrypt(data, null)
            )
        }
    }

    /**
     * Test to make sure that decrypt does nothing when given a null key.
     */
    @Test
    fun testDecryptWithNullKey() {
        for (data in TEST_DATA) {
            Assert.assertEquals(
                "Decrypt should have left the string unchanged", data,
                Encryptor.decrypt(data, null)
            )
        }
    }

    /**
     * Test to ensure encryption and decryption work as expected.
     */
    @Test
    fun testEncryptDecrypt() {
        for (key in TEST_KEYS) {
            for (data in TEST_DATA) {
                val encryptedData = Encryptor.encrypt(data, key)
                val decryptedData = Encryptor.decrypt(encryptedData, key)
                Assert.assertEquals("Decrypt should restore original", data, decryptedData)
            }
        }
    }

    /**
     * Test to make sure encrypt returns a string different from the original
     * and that decrypt restores the original.
     */
    @Test
    fun testEncryptDecryptWithDifferentData() {
        val key = makeKey("123456")
        for (data in TEST_DATA) {
            Assert.assertFalse(
                "Encrypted string should be different from original",
                data == Encryptor.encrypt(data, key)
            )
            Assert.assertEquals(
                "Decrypt should restore original", data,
                Encryptor.decrypt(Encryptor.encrypt(data, key), key)
            )
            for (otherData in TEST_DATA) {
                val encryptedA = Encryptor.encrypt(data, key)
                val decryptedA = Encryptor.decrypt(encryptedA, key)
                val encryptedB = Encryptor.encrypt(otherData, key)
                val decryptedB = Encryptor.decrypt(encryptedB, key)
                val sameDecrypted = decryptedA == decryptedB
                val sameData = data == otherData
                Assert.assertEquals(
                    "Decrypted strings '" +
                            decryptedA + "','" + decryptedB +
                            "'  should be different for different strings '" +
                            data + "','" + otherData + "'",
                    sameDecrypted, sameData
                )
            }
        }
    }

    /**
     * Test to make sure that encrypting with different keys produces different results.
     */
    @Test
    fun testEncryptDecryptWithDifferentKeys() {
        val data = "fake-token"
        for (key in TEST_KEYS) {
            Assert.assertEquals(
                "Decrypt should restore original", data,
                Encryptor.decrypt(Encryptor.encrypt(data, key), key)
            )
            for (otherKey in TEST_KEYS) {
                val sameKey = (key == null && otherKey == null) || (key != null && key == otherKey)
                if (!sameKey) {
                    val encryptedA = Encryptor.encrypt(data, key)
                    val decryptedA = Encryptor.decrypt(encryptedA, key)
                    val encryptedB = Encryptor.encrypt(data, otherKey)
                    val decryptedB = Encryptor.decrypt(encryptedB, otherKey)
                    Assert.assertEquals("Decrypted values should be the same", decryptedA, decryptedB)
                    val sameEncrypted = encryptedA == encryptedB
                    Assert.assertEquals(
                        "Encrypted strings '" +
                                encryptedA + "','" + encryptedB +
                                "'  should be different for different keys '" +
                                key + "','" + otherKey + "'",
                        sameEncrypted, sameKey
                    )
                }
            }
        }
    }

    /**
     * Check cipher returned by Encryptor.getEncryptingCipher
     */
    @Test
    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class)
    fun testGetEncryptingCipher() {
        val cipher = Encryptor.getEncryptingCipher(makeKey("my-key")!!)
        Assert.assertEquals("Wrong algorithm", "AES/GCM/NoPadding", cipher.algorithm)
        Assert.assertEquals("Wrong iv length", 12, cipher.iv.size)
        Assert.assertEquals("Wrong mode", 16, cipher.blockSize)
    }

    /**
     * Check cipher returned by Encryptor.getDecryptingCipher
     */
    @Test
    @Throws(InvalidAlgorithmParameterException::class, InvalidKeyException::class)
    fun testGetDecryptingCipher() {
        val cipher = Encryptor.getDecryptingCipher(makeKey("my-key")!!, ByteArray(12))
        Assert.assertEquals("Wrong algorithm", "AES/GCM/NoPadding", cipher.algorithm)
        Assert.assertEquals("Wrong iv length", 12, cipher.iv.size)
        Assert.assertEquals("Wrong mode", 16, cipher.blockSize)
    }

    /**
     * Encrypting/decrypting data with ciphers returned by Encryptor.getEncryptingCipher and
     * Encryptor.getDecryptingCipher.
     */
    @Test
    @Throws(
        InvalidAlgorithmParameterException::class, InvalidKeyException::class,
        BadPaddingException::class, IllegalBlockSizeException::class
    )
    fun testEncryptDecryptWithCipher() {
        val key = makeKey("test-key")!!
        val originalText = "abcdefghijklmnopqrstuvwxyz"
        val encryptingCipher = Encryptor.getEncryptingCipher(key)
        val decryptingCipher = Encryptor.getDecryptingCipher(key, encryptingCipher.iv)
        val originalBytes = originalText.toByteArray(StandardCharsets.UTF_8)
        val encryptedBytes = encryptingCipher.doFinal(originalBytes)
        val decryptedBytes = decryptingCipher.doFinal(encryptedBytes)
        val recoveredText = String(decryptedBytes, StandardCharsets.UTF_8)
        Assert.assertNotEquals("Bytes should have encrypted", encryptedBytes, originalBytes)
        Assert.assertNotEquals("Bytes should have been decrypted", decryptedBytes, encryptedBytes)
        Assert.assertEquals("Recovered text should match original", originalText, recoveredText)
    }

    /**
     * Encrypting/decrypting data with ciphers returned by Encryptor.encryptWithoutBase64Encoding and
     * Encryptor.decryptWithoutBase64Encoding.
     */
    @Test
    fun testEncryptDecryptWithoutBase64Encoding() {
        for (key in TEST_KEYS) {
            for (data in TEST_DATA) {
                val dataBytes = data.toByteArray()
                val encryptedData = Encryptor.encryptWithoutBase64Encoding(dataBytes, key)
                val decryptedData = Encryptor.decryptWithoutBase64Encoding(encryptedData, key)
                Assert.assertArrayEquals(
                    "Decrypt should restore original",
                    dataBytes, decryptedData
                )
            }
        }
    }

    companion object {
        private val TEST_KEYS = arrayOf(
            null,
            makeKey("test1234"),
            makeKey("123456")
        )
        private val TEST_DATA = arrayOf(
            "hello world",
            "fake-token"
        )

        private fun makeKey(passcode: String): String? {
            return Encryptor.hash(passcode, "hashing-key")
        }
    }
}
