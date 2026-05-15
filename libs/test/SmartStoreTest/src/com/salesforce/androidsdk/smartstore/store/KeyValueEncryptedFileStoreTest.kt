/*
 * Copyright (c) 2020-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.store

import com.salesforce.androidsdk.MainActivity
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.smartstore.R
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.util.ManagedFilesHelper
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FilenameFilter
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Random

@RunWith(AndroidJUnit4::class)
class KeyValueEncryptedFileStoreTest {

    private lateinit var context: Context
    private lateinit var keyValueStore: KeyValueEncryptedFileStore

    @Before
    fun setUp() {
        // Throw an exception if stream is not closed
        try {
            Class.forName("dalvik.system.CloseGuard")
                .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                .invoke(null, true)
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }

        context = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext
        SmartStoreSDKManager.initNative(context, MainActivity::class.java)
        keyValueStore = KeyValueEncryptedFileStore(
            context, TEST_STORE, SalesforceSDKManager.encryptionKey
        )
        Assert.assertTrue("Store directory should exist", getStoreDir(TEST_STORE).exists())
        Assert.assertTrue("Store should be empty", keyValueStore.isEmpty())
    }

    @After
    fun tearDown() {
        ManagedFilesHelper.deleteFile(getStoreDir(TEST_STORE))
    }

    /** Test getStoreVersion() */
    @Test
    fun testGetStoreVersion() {
        Assert.assertEquals(
            "Wrong kv store version", KeyValueEncryptedFileStore.KV_VERSION,
            keyValueStore.getStoreVersion()
        )
    }

    /** Test getStoreVersion() when there is on version file (v1 store) */
    @Test
    fun testGetStoreVersionWithoutVersionFile() {
        val versionFile = File(getStoreDir(TEST_STORE), "version")
        Assert.assertTrue(versionFile.exists())
        versionFile.delete()
        val keyValueStoreWithoutVersionFile = KeyValueEncryptedFileStore(
            context, TEST_STORE, SalesforceSDKManager.encryptionKey
        )
        Assert.assertEquals("Wrong kv store version", 1, keyValueStoreWithoutVersionFile.getStoreVersion())
        Assert.assertFalse(versionFile.exists())
    }

    /** Test isValidStoreName() */
    @Test
    fun isValidStoreName() {
        // Basic tests
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName(null))
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName(""))
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc!def"))
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc def"))
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc/def"))
        Assert.assertTrue("Store name is valid", KeyValueEncryptedFileStore.isValidStoreName("abc_def"))
        Assert.assertTrue("Store name is valid", KeyValueEncryptedFileStore.isValidStoreName("abc_def_ABC_DEF_012"))
        var generateStoreName = ""
        // Trying various lengths
        for (i in 0 until KeyValueEncryptedFileStore.MAX_STORE_NAME_LENGTH * 2) {
            generateStoreName += "x"
            Assert.assertEquals(
                "Wrong value returned by isValidStoreName(\"$generateStoreName\")",
                generateStoreName.length <= KeyValueEncryptedFileStore.MAX_STORE_NAME_LENGTH,
                KeyValueEncryptedFileStore.isValidStoreName(generateStoreName)
            )
        }
        // Trying various characters
        for (i in 0..255) {
            generateStoreName = i.toChar().toString()
            Assert.assertEquals(
                "Wrong value returned by isValidStoreName(\"$generateStoreName\")",
                (i >= 'a'.code && i <= 'z'.code) || (i >= 'A'.code && i <= 'Z'.code) || (i >= '0'.code && i <= '9'.code) || i == '_'.code,
                KeyValueEncryptedFileStore.isValidStoreName(generateStoreName)
            )
        }
    }

    /** Test computeParentDir() */
    @Test
    fun testComputeParentDir() {
        Assert.assertEquals(
            "Wrong value returned by computeParentDir()", getStoreDir("").absolutePath,
            KeyValueEncryptedFileStore.computeParentDir(context).absolutePath
        )
    }

    /** Test getStoreDir() */
    @Test
    fun testGetStoreDir() {
        Assert.assertEquals(
            "Wrong value returned by getStoreDir()",
            getStoreDir(TEST_STORE).absolutePath,
            keyValueStore.getStoreDir().absolutePath
        )
    }

    /** Test getStoreName() */
    @Test
    fun testGetStoreName() {
        Assert.assertEquals(
            "Wrong value returned by getStoreDir()", TEST_STORE, keyValueStore.getStoreName()
        )
    }

    /** Test that constructor fails if store name provided is invalid */
    @Test
    fun testFailedCreateBadName() {
        try {
            KeyValueEncryptedFileStore(context, "", "")
            Assert.fail("An exception should have been thrown")
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue("Wrong exception", e.message?.contains("Invalid store name") == true)
        }
    }

    /** Test that constructor fails if a file exists where the store dir should be created */
    @Test
    fun testFailedCreateFileExist() {
        val file = getStoreDir("file")
        file.delete() // starting clean
        Assert.assertTrue("Test file creation failed", file.createNewFile())
        try {
            KeyValueEncryptedFileStore(context, "file", "")
            Assert.fail("An exception should have been thrown")
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue("Wrong exception", e.message?.contains("Failed to create directory") == true)
        }
        file.delete()
    }

    /**
     * Test hasKeyValueStore()
     * Call hasKeyValueStore for existing store and non-existent store
     */
    @Test
    fun testHasKeyValueStore() {
        Assert.assertTrue("Store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, TEST_STORE))
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "non_existent_store"))
    }

    /**
     * Test remove key value store
     * Check new store does not exist, add new store, check it now exists, then remove it, check it no longer exists
     */
    @Test
    fun testRemoveKeyValueStore() {
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"))
        val store = KeyValueEncryptedFileStore(context, "new_store", "")
        Assert.assertTrue("Store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"))
        Assert.assertTrue("Store dir should exist", getStoreDir("new_store").exists())
        KeyValueEncryptedFileStore.removeKeyValueStore(context, "new_store")
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"))
        Assert.assertFalse("Store dir should be gone", getStoreDir("new_store").exists())
    }

    /** Test saving values and counting them */
    @Test
    fun testSaveValueCount() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            Assert.assertEquals("Wrong count before save", i, keyValueStore.count())
            keyValueStore.saveValue(key, value)
            Assert.assertEquals("Wrong count after save", i + 1, keyValueStore.count())
        }
    }

    /** Test saving from streams and counting them */
    @Test
    fun testSaveStreamCount() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val stream = stringToStream("value$i")
            Assert.assertEquals("Wrong count before save", i, keyValueStore.count())
            keyValueStore.saveStream(key, stream)
            Assert.assertEquals("Wrong count after save", i + 1, keyValueStore.count())
        }
    }

    /** Test saving values and getting them back */
    @Test
    fun testSaveValueGetValue() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
        }
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val expectedValue = "value$i"
            Assert.assertEquals(
                "Wrong value for key: $key", expectedValue, keyValueStore.getValue(key)
            )
        }
    }

    /** Test saving from streams and getting them back as values */
    @Test
    fun testSaveStreamGetValue() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val stream = stringToStream("value$i")
            keyValueStore.saveStream(key, stream)
        }
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val expectedValue = "value$i"
            Assert.assertEquals(
                "Wrong value for key: $key", expectedValue, keyValueStore.getValue(key)
            )
        }
    }

    /**
     * Test saving and getting large streams to verify memory efficiency
     */
    @Test
    fun testSaveLargeStreamGetLargeStream() {
        val key = "largeStreamKey"
        val dataSize = 5 * 1024 * 1024 // 5MB

        // Generate and save large stream
        getLargeStringStream(dataSize).use { largeStream ->
            keyValueStore.saveStream(key, largeStream)
        }

        // Retrieve and verify the stream
        keyValueStore.getStream(key).use { retrievedStream ->
            getLargeStringStream(dataSize).use { expectedStream ->
                Assert.assertNotNull("Retrieved stream should not be null", retrievedStream)
                Assert.assertTrue("Streams should be equal", streamsEqual(expectedStream, retrievedStream!!))
            }
        }
    }

    private fun getLargeString(size: Int): String {
        val CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = Random()
        val sb = StringBuilder(size)

        for (i in 0 until size) {
            val randomIndex = random.nextInt(CHARACTERS.length)
            val randomChar = CHARACTERS[randomIndex]
            sb.append(randomChar)
        }

        return sb.toString()
    }

    /** Test saving values and getting them back as streams */
    @Test
    fun testSaveValueGetStream() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
        }
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val expectedValue = "value$i"
            Assert.assertEquals(
                "Wrong value (from stream) for key: $key",
                expectedValue,
                streamToString(keyValueStore.getStream(key))
            )
        }
    }

    /** Test saving from streams and getting them back as streams */
    @Test
    fun testSaveStreamGetStream() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val stream = stringToStream("value$i")
            keyValueStore.saveStream(key, stream)
        }
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val expectedValue = "value$i"
            Assert.assertEquals(
                "Wrong value (from stream) for key: $key",
                expectedValue,
                streamToString(keyValueStore.getStream(key))
            )
        }
    }

    /** Test saving values and deleting them */
    @Test
    fun testSaveValueDelete() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
        }
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            Assert.assertNotNull(
                "No value found for key when expected:$key", keyValueStore.getValue(key)
            )
            Assert.assertEquals(
                "Wrong count before delete", NUM_ENTRIES - i, keyValueStore.count()
            )
            keyValueStore.deleteValue(key)
            Assert.assertEquals(
                "Wrong count after delete", NUM_ENTRIES - (i + 1), keyValueStore.count()
            )
            Assert.assertNull(
                "Value found for key when not expected:$key", keyValueStore.getValue(key)
            )
        }
    }

    /** Test saving values and deleting them all at once */
    @Test
    fun testSaveValueDeleteAll() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
        }
        Assert.assertEquals("Wrong count before deleteAll", NUM_ENTRIES, keyValueStore.count())
        keyValueStore.deleteAll()
        Assert.assertEquals("Wrong count after deleteAll", 0, keyValueStore.count())
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            Assert.assertNull(
                "Value found for key when not expected:$key", keyValueStore.getValue(key)
            )
        }
    }

    /** Test saving values and checking the file system */
    @Test
    fun testSaveValueCheckFiles() {
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list()?.size)
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
            val keyFile = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash(key) + ".key")
            val valueFile = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash(key) + ".value")
            Assert.assertTrue(keyFile.exists())
            Assert.assertTrue(valueFile.exists())
            Assert.assertEquals(1 /* version file */ + 2 * (i + 1), getStoreDir(TEST_STORE).list()?.size)
            Assert.assertEquals("key$i", keyValueStore.decryptFileAsString(keyFile, SalesforceSDKManager.encryptionKey))
            Assert.assertEquals("value$i", keyValueStore.decryptFileAsString(valueFile, SalesforceSDKManager.encryptionKey))
        }
    }

    /** Test saving streams and checking the file system */
    @Test
    fun testSaveStreamsCheckFiles() {
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list()?.size)
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val stream = stringToStream("value$i")
            keyValueStore.saveStream(key, stream)
            val keyFile = File(
                getStoreDir(TEST_STORE),
                SalesforceKeyGenerator.getSHA256Hash(key) + ".key"
            )
            val valueFile = File(
                getStoreDir(TEST_STORE),
                SalesforceKeyGenerator.getSHA256Hash(key) + ".value"
            )
            Assert.assertTrue(keyFile.exists())
            Assert.assertTrue(valueFile.exists())
            Assert.assertEquals(1 /* version file */ + 2 * (i + 1), getStoreDir(TEST_STORE).list()?.size)
            Assert.assertEquals("key$i", keyValueStore.decryptFileAsString(keyFile, SalesforceSDKManager.encryptionKey))
            Assert.assertEquals("value$i", keyValueStore.decryptFileAsString(valueFile, SalesforceSDKManager.encryptionKey))
        }
    }

    /** Test checking file system after saving then deleting values */
    @Test
    fun testSaveDeleteCheckFiles() {
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list()?.size)
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
        }
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val keyFile = File(
                getStoreDir(TEST_STORE),
                SalesforceKeyGenerator.getSHA256Hash(key) + ".key"
            )
            val valueFile = File(
                getStoreDir(TEST_STORE),
                SalesforceKeyGenerator.getSHA256Hash(key) + ".value"
            )
            Assert.assertTrue(keyFile.exists())
            Assert.assertTrue(valueFile.exists())
            Assert.assertEquals(1 /* version file */ + 2 * (NUM_ENTRIES - i), getStoreDir(TEST_STORE).list()?.size)
            keyValueStore.deleteValue(key)
            Assert.assertEquals(1 /* version file */ + 2 * (NUM_ENTRIES - (i + 1)), getStoreDir(TEST_STORE).list()?.size)
            Assert.assertFalse(keyFile.exists())
            Assert.assertFalse(valueFile.exists())
        }
    }

    /** Test checking file system after saving then deleting all values */
    @Test
    fun testSaveDeleteAllCheckFiles() {
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list()?.size)
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
        }
        Assert.assertEquals(1 /* version file */ + 2 * NUM_ENTRIES, getStoreDir(TEST_STORE).list()?.size)
        keyValueStore.deleteAll()
        Assert.assertEquals(1 /* version file */, getStoreDir(TEST_STORE).list()?.size)
    }

    /** Test saving values and calling keySet() */
    @Test
    fun testSaveValueKeySet() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            Assert.assertEquals(i, keyValueStore.keySet().size)
            Assert.assertFalse(keyValueStore.keySet().contains(key))
            keyValueStore.saveValue(key, value)
            Assert.assertTrue(keyValueStore.keySet().contains(key))
            Assert.assertEquals(i + 1, keyValueStore.keySet().size)
        }
    }

    /** Test saving streams and calling keySet() */
    @Test
    fun testSaveStreamKeySet() {
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val stream = stringToStream("value$i")
            Assert.assertEquals(i, keyValueStore.keySet().size)
            Assert.assertFalse(keyValueStore.keySet().contains(key))
            keyValueStore.saveStream(key, stream)
            Assert.assertTrue(keyValueStore.keySet().contains(key))
            Assert.assertEquals(i + 1, keyValueStore.keySet().size)
        }
    }

    /** Test calling keySet() after saving then deleting values */
    @Test
    fun testSaveDeleteKeySet() {
        Assert.assertTrue(keyValueStore.keySet().isEmpty())
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
        }
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            Assert.assertEquals(NUM_ENTRIES - i, keyValueStore.keySet().size)
            Assert.assertTrue(keyValueStore.keySet().contains(key))
            keyValueStore.deleteValue(key)
            Assert.assertFalse(keyValueStore.keySet().contains(key))
            Assert.assertEquals(NUM_ENTRIES - (i + 1), keyValueStore.keySet().size)
        }
    }

    /** Test calling keySet() after saving then deleting all values */
    @Test
    fun testSaveDeleteAllKeySet() {
        Assert.assertTrue(keyValueStore.keySet().isEmpty())
        for (i in 0 until NUM_ENTRIES) {
            val key = "key$i"
            val value = "value$i"
            keyValueStore.saveValue(key, value)
        }
        Assert.assertEquals(NUM_ENTRIES, keyValueStore.keySet().size)
        keyValueStore.deleteAll()
        Assert.assertTrue(keyValueStore.keySet().isEmpty())
    }

    /** Making sure various operations won't NPE if storeDir was deleted */
    @Test
    fun testNoNPEIfStoreDirDeleted() {
        ManagedFilesHelper.deleteFile(keyValueStore.getStoreDir())
        Assert.assertNull("Expected null for files in deleted stored dir", keyValueStore.getStoreDir().listFiles())
        try {
            Assert.assertEquals(TEST_STORE, keyValueStore.getStoreName())
            Assert.assertEquals(null, keyValueStore.getValue("xyz"))
            Assert.assertEquals(false, keyValueStore.saveValue("xyz", "abc"))
            Assert.assertEquals(null, keyValueStore.getValue("xyz"))
            Assert.assertEquals(false, keyValueStore.deleteValue("xyz"))
            Assert.assertEquals(null, keyValueStore.getStream("xyz"))
            Assert.assertEquals(false, keyValueStore.saveStream("xyz", stringToStream("abc")))
            Assert.assertEquals(null, keyValueStore.getStream("xyz"))
            Assert.assertEquals(0, keyValueStore.count())
            Assert.assertEquals(true, keyValueStore.isEmpty())
            keyValueStore.deleteAll()
        } catch (e: NullPointerException) {
            Assert.fail("NPE was not expected")
        }
    }

    /** Test saving value with invalid key */
    @Test
    fun testSaveValueInvalidKey() {
        Assert.assertFalse(
            "Save should have returned false for \"\" key",
            keyValueStore.saveValue("", "value")
        )
        Assert.assertNull("Value found for key when not expected", keyValueStore.getValue(""))
        Assert.assertEquals("Wrong count for store", 0, keyValueStore.count())
    }

    /** Test saving invalid value - with Kotlin non-nullable types, null cannot be passed at compile time.
     *  Testing empty value instead. */
    @Test
    fun testSaveValueInvalidValue() {
        // Kotlin enforces non-null String parameter, so we just verify the store handles empty values
        Assert.assertTrue(
            "Save should have returned true for empty value",
            keyValueStore.saveValue("key", "")
        )
        Assert.assertEquals("Wrong value for key", "", keyValueStore.getValue("key"))
        Assert.assertEquals("Wrong count for store", 1, keyValueStore.count())
    }

    /** Test that data is indeed stored encrypted */
    @Test
    fun testStoreIsEncrypted() {
        // Populate store
        keyValueStore.saveValue("key1", "value1")
        keyValueStore.saveValue("key2", "value2")

        // Look at the raw content of value files
        val valueFiles = keyValueStore.getStoreDir().listFiles(FilenameFilter { file, s ->
            s.endsWith(".value")
        })
        Assert.assertEquals("Wrong number of files", 2, valueFiles?.size)

        // Make sure the actual value can't be found
        Assert.assertFalse("File should have been encrypted", streamToString(FileInputStream(valueFiles!![0]))!!.contains("value"))
        Assert.assertFalse("File should have been encrypted", streamToString(FileInputStream(valueFiles!![1]))!!.contains("value"))

        // Look at the raw content of key files
        val keyFiles = keyValueStore.getStoreDir().listFiles(FilenameFilter { file, s ->
            s.endsWith(".key")
        })
        Assert.assertEquals("Wrong number of files", 2, keyFiles?.size)

        // Make sure the actual key can't be found
        Assert.assertFalse("File should have been encrypted", streamToString(FileInputStream(keyFiles!![0]))!!.contains("key"))
        Assert.assertFalse("File should have been encrypted", streamToString(FileInputStream(keyFiles!![1]))!!.contains("key"))
    }

    /** Test changing encryption key */
    @Test
    fun testChangeEncryptionKey() {
        // Populate store
        keyValueStore.saveValue("key1", "value1")
        keyValueStore.saveValue("key2", "value2")
        // Check store
        Assert.assertEquals("Wrong count", 2, keyValueStore.count())
        Assert.assertEquals("Wrong value for key1", "value1", keyValueStore.getValue("key1"))
        Assert.assertEquals("Wrong value for key2", "value2", keyValueStore.getValue("key2"))
        // Getting raw content of files
        val files = keyValueStore.getStoreDir().listFiles(FilenameFilter { file, s ->
            s.endsWith(".value")
        })
        Assert.assertEquals("Wrong number of files", 2, files?.size)
        val file1raw = streamToString(FileInputStream(files!![0]))
        val file2raw = streamToString(FileInputStream(files[1]))
        // Generate new key
        val newEncryptionKey = SalesforceKeyGenerator.getEncryptionKey("new")
        // Make sure it's a different key
        Assert.assertNotEquals("New encryption key should be different", newEncryptionKey, SalesforceSDKManager.encryptionKey)
        // Change encryption key
        Assert.assertTrue("Changing key should have succeeded", keyValueStore.changeEncryptionKey(newEncryptionKey!!))
        // Make sure we can still read all the values from the store
        Assert.assertEquals("Wrong count", 2, keyValueStore.count())
        Assert.assertEquals("Wrong value for key1", "value1", keyValueStore.getValue("key1"))
        Assert.assertEquals("Wrong value for key2", "value2", keyValueStore.getValue("key2"))
        // Getting raw content of files
        val file1rawAfter = streamToString(FileInputStream(files[0]))
        val file2rawAfter = streamToString(FileInputStream(files[1]))
        Assert.assertNotEquals("Raw content should have changed", file1rawAfter, file1raw)
        Assert.assertNotEquals("Raw content should have changed", file2rawAfter, file2raw)
    }

    /** Test code block with comment and newline */
    @Test
    fun testCodeBlock() {
        val codeBlock = "var fun = function() {" + "\n\t// comment" + "\n\tvar i = 100;\n}"
        val minifiedBlock = "function minified(){var n=Math.floor(Math.random());return n>50?7*n:n/2}"
        keyValueStore.saveValue("js1", codeBlock)
        keyValueStore.saveValue("js2", minifiedBlock)
        Assert.assertEquals("Code block was not retrieved correctly.", codeBlock, keyValueStore.getValue("js1"))
        Assert.assertEquals("Code block was not retrieved correctly.", minifiedBlock, keyValueStore.getValue("js2"))
    }

    /** Test save/get/delete/count with v1 store */
    @Test
    fun testSaveGetDeleteCountOnV1Store() {
        keyValueStore = turnIntoV1Store(keyValueStore)

        // Saving values
        keyValueStore.saveValue("key1", "value1")
        keyValueStore.saveStream("key2", stringToStream("value2"))
        keyValueStore.saveValue("key3", "value3")
        keyValueStore.saveStream("key4", stringToStream("value4"))

        // Getting values back
        Assert.assertEquals("value1", streamToString(keyValueStore.getStream("key1")))
        Assert.assertEquals("value2", keyValueStore.getValue("key2"))
        Assert.assertEquals("value3", keyValueStore.getValue("key3"))
        Assert.assertEquals("value4", streamToString(keyValueStore.getStream("key4")))

        // Checking count
        Assert.assertEquals(4, keyValueStore.count())

        // Checking files
        Assert.assertEquals(4, getStoreDir(TEST_STORE).list()?.size)
        val value1 = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1"))
        val value2 = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key2"))
        val value3 = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key3"))
        val value4 = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key4"))
        Assert.assertTrue(value1.exists())
        Assert.assertTrue(value2.exists())
        Assert.assertTrue(value3.exists())
        Assert.assertTrue(value4.exists())
        Assert.assertEquals("value1", keyValueStore.decryptFileAsString(value1, SalesforceSDKManager.encryptionKey))
        Assert.assertEquals("value2", keyValueStore.decryptFileAsString(value2, SalesforceSDKManager.encryptionKey))
        Assert.assertEquals("value3", keyValueStore.decryptFileAsString(value3, SalesforceSDKManager.encryptionKey))
        Assert.assertEquals("value4", keyValueStore.decryptFileAsString(value4, SalesforceSDKManager.encryptionKey))

        // Deleting one
        keyValueStore.deleteValue("key2")
        Assert.assertNull(keyValueStore.getValue("key2"))
        Assert.assertNull(keyValueStore.getStream("key2"))

        // Checking count
        Assert.assertEquals(3, keyValueStore.count())

        // Checking files
        Assert.assertEquals(3, getStoreDir(TEST_STORE).list()?.size)
        Assert.assertFalse(value2.exists())

        // Deleting all
        keyValueStore.deleteAll()
        Assert.assertNull(keyValueStore.getValue("key1"))
        Assert.assertNull(keyValueStore.getStream("key1"))

        // Checking count
        Assert.assertEquals(0, keyValueStore.count())

        // Checking files
        Assert.assertEquals(0, getStoreDir(TEST_STORE).list()?.size)
        Assert.assertFalse(value1.exists())
        Assert.assertFalse(value2.exists())
        Assert.assertFalse(value3.exists())
        Assert.assertFalse(value4.exists())
    }

    /** Test keySet() with v1 store - should throw exception */
    @Test
    fun testKeySetOnV1Store() {
        keyValueStore = turnIntoV1Store(keyValueStore)
        try {
            keyValueStore.keySet()
            Assert.fail("Exception was expected")
        } catch (e: UnsupportedOperationException) {
            Assert.assertTrue(e.message?.contains("keySet() not supported on v1 stores") == true)
        }
    }

    /**
     * Making sure that keySet(), deleteAll(), count() work even if there is a bad key file
     * Bad key file should not happen unless files were tampered with directly
     * @throws IOException
     */
    @Test
    fun testKeySetCountDeleteAllWithBadKeyFile() {
        keyValueStore.saveValue("key1", "value1")
        keyValueStore.saveValue("key2", "value2")

        // Calling count() -  should return 2
        Assert.assertEquals(2, keyValueStore.count())

        // Getting file objects
        val key1File = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1") + ".key")
        val key2File = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key2") + ".key")
        val value1File = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1") + ".value")
        val value2File = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key2") + ".value")

        // Making sure all 4 files exist
        Assert.assertTrue(key1File.exists())
        Assert.assertTrue(key2File.exists())
        Assert.assertTrue(value1File.exists())
        Assert.assertTrue(value2File.exists())

        // Tampering with one of the key file
        key1File.delete()
        key1File.createNewFile()
        Assert.assertEquals(1, keyValueStore.count())
        Assert.assertTrue(key1File.exists())
        Assert.assertTrue(key2File.exists())
        Assert.assertTrue(value1File.exists())
        Assert.assertTrue(value2File.exists())

        // Calling keySet() - should not return bad key
        val foundKeys = keyValueStore.keySet().toTypedArray()
        Assert.assertEquals(1, foundKeys.size)
        Assert.assertEquals("key2", foundKeys[0])

        // Calling count() -  should return 1
        Assert.assertEquals(1, keyValueStore.count())

        // Calling deleteAll() - should also delete bad key file
        keyValueStore.deleteAll()
        Assert.assertFalse(key1File.exists())
        Assert.assertFalse(key2File.exists())
        Assert.assertFalse(value1File.exists())
        Assert.assertFalse(value2File.exists())

        // Calling count() -  should return 0
        Assert.assertEquals(0, keyValueStore.count())
    }

    /**
     * Read some binary file from assets, save it to the key value store then get it back
     * Make sure it's identical to the original file
     */
    @Test
    fun testBinaryStorage() {
        // Saving resource icon to key value store
        keyValueStore.saveStream("icon", getResourceIconStream())

        // Retrieving icon back from key value store
        val savedIconBytes = Encryptor.getByteArrayStreamFromStream(keyValueStore.getStream("icon")!!).toByteArray()

        // Comparing bytes
        val resourceIconBytes = Encryptor.getByteArrayStreamFromStream(getResourceIconStream()).toByteArray()
        Assert.assertEquals(resourceIconBytes.size, savedIconBytes.size)
        for (i in resourceIconBytes.indices) {
            Assert.assertEquals(resourceIconBytes[i], savedIconBytes[i])
        }
    }

    @Test
    fun testContains() {
        Assert.assertFalse(keyValueStore.contains("key1"))
        Assert.assertFalse(keyValueStore.contains("key2"))
        Assert.assertFalse(keyValueStore.contains("key3"))

        // Save one
        keyValueStore.saveValue("key1", "value1")
        Assert.assertTrue(keyValueStore.contains("key1"))
        Assert.assertFalse(keyValueStore.contains("key2"))
        Assert.assertFalse(keyValueStore.contains("key3"))

        // Save another
        keyValueStore.saveValue("key2", "value2")
        Assert.assertTrue(keyValueStore.contains("key1"))
        Assert.assertTrue(keyValueStore.contains("key2"))
        Assert.assertFalse(keyValueStore.contains("key3"))

        // Save third
        keyValueStore.saveValue("key3", "value3")
        Assert.assertTrue(keyValueStore.contains("key1"))
        Assert.assertTrue(keyValueStore.contains("key2"))
        Assert.assertTrue(keyValueStore.contains("key3"))

        // Delete one
        keyValueStore.deleteValue("key1")
        Assert.assertFalse(keyValueStore.contains("key1"))
        Assert.assertTrue(keyValueStore.contains("key2"))
        Assert.assertTrue(keyValueStore.contains("key3"))

        // Delete all
        keyValueStore.deleteAll()
        Assert.assertFalse(keyValueStore.contains("key1"))
        Assert.assertFalse(keyValueStore.contains("key2"))
        Assert.assertFalse(keyValueStore.contains("key3"))
    }

    //
    // Helper methods
    //
    private fun getResourceIconStream(): InputStream {
        return context.resources.openRawResource(R.drawable.sf__inspector_store_icon)
    }

    private fun turnIntoV1Store(store: KeyValueEncryptedFileStore): KeyValueEncryptedFileStore {
        if (!store.isEmpty()) {
            throw RuntimeException("turnIntoV1Store() should be called on empty store")
        }
        // Delete version file (they did not exist in v1)
        File(store.getStoreDir(), "version").delete()

        val storerV1 = KeyValueEncryptedFileStore(
            context, store.getStoreName(), SalesforceSDKManager.encryptionKey
        )

        Assert.assertEquals(1, keyValueStore.readVersion())
        Assert.assertEquals("Directory should be empty", 0, keyValueStore.getStoreDir().list()?.size)

        return storerV1
    }

    private fun getStoreDir(storeName: String): File {
        return File(context.applicationInfo.dataDir + "/keyvaluestores", storeName)
    }

    private fun stringToStream(value: String): InputStream {
        return ByteArrayInputStream(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun streamToString(inputStream: InputStream?): String? {
        if (inputStream == null) {
            return null
        }
        return try {
            Encryptor.getStringFromStream(inputStream)
        } catch (e: IOException) {
            Assert.fail("Failed to read from stream")
            null
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                Assert.fail("Stream failed to close")
            }
        }
    }

    /**
     * Helper method to create a large string stream without loading all data into memory
     * @param size Size in bytes
     * @return InputStream containing the large string data
     */
    private fun getLargeStringStream(size: Int): InputStream {
        return object : InputStream() {
            private var bytesRead = 0
            private val pattern = "0123456789ABCDEF"
            private val patternBytes = pattern.toByteArray(StandardCharsets.UTF_8)
            private val patternLength = patternBytes.size

            override fun read(): Int {
                if (bytesRead >= size) {
                    return -1 // End of stream
                }
                val b = patternBytes[bytesRead % patternLength]
                bytesRead++
                return b.toInt() and 0xFF
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (bytesRead >= size) {
                    return -1 // End of stream
                }

                val bytesToRead = minOf(length, size - bytesRead)
                for (i in 0 until bytesToRead) {
                    buffer[offset + i] = patternBytes[(bytesRead + i) % patternLength]
                }
                bytesRead += bytesToRead
                return bytesToRead
            }
        }
    }

    /**
     * Helper method to compare two streams for equality without loading all data into memory
     * @param stream1 First stream
     * @param stream2 Second stream
     * @return true if streams contain identical data
     * @throws IOException if there's an error reading the streams
     */
    private fun streamsEqual(stream1: InputStream, stream2: InputStream): Boolean {
        val buffer1 = ByteArray(8192)
        val buffer2 = ByteArray(8192)

        var bytesRead1: Int
        var bytesRead2: Int

        while (true) {
            bytesRead1 = stream1.read(buffer1)
            bytesRead2 = stream2.read(buffer2)

            // Check if both streams reached end
            if (bytesRead1 == -1 && bytesRead2 == -1) {
                return true
            }

            // Check if only one stream reached end
            if (bytesRead1 != bytesRead2) {
                return false
            }

            // Compare the read bytes
            for (i in 0 until bytesRead1) {
                if (buffer1[i] != buffer2[i]) {
                    return false
                }
            }
        }
    }

    companion object {
        const val TEST_STORE = "TEST_STORE"
        const val NUM_ENTRIES = 25
    }
}
