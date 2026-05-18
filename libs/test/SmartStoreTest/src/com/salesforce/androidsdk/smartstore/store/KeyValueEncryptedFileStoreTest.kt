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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.smartstore.tests.R
import com.salesforce.androidsdk.util.ManagedFilesHelper
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class KeyValueEncryptedFileStoreTest {

    companion object {
        const val TEST_STORE = "TEST_STORE"
        const val NUM_ENTRIES = 25
    }

    private lateinit var context: Context
    private lateinit var keyValueStore: KeyValueEncryptedFileStore

    @Before
    fun setUp() {
        try {
            Class.forName("dalvik.system.CloseGuard")
                .getMethod("setEnabled", Boolean::class.javaPrimitiveType)
                .invoke(null, true)
        } catch (e: ReflectiveOperationException) {
            throw RuntimeException(e)
        }

        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        SmartStoreSDKManager.initNative(context, com.salesforce.androidsdk.MainActivity::class.java)
        keyValueStore = KeyValueEncryptedFileStore(context, TEST_STORE, SalesforceSDKManager.encryptionKey)
        Assert.assertTrue("Store directory should exist", getStoreDir(TEST_STORE).exists())
        Assert.assertTrue("Store should be empty", keyValueStore.isEmpty())
    }

    @After
    fun tearDown() {
        ManagedFilesHelper.deleteFile(getStoreDir(TEST_STORE))
    }

    @Test
    fun testGetStoreVersion() {
        Assert.assertEquals("Wrong kv store version", KeyValueEncryptedFileStore.KV_VERSION, keyValueStore.getStoreVersion())
    }

    @Test
    fun testGetStoreVersionWithoutVersionFile() {
        val versionFile = File(getStoreDir(TEST_STORE), "version")
        Assert.assertTrue(versionFile.exists())
        versionFile.delete()
        val keyValueStoreWithoutVersionFile = KeyValueEncryptedFileStore(context, TEST_STORE, SalesforceSDKManager.encryptionKey)
        Assert.assertEquals("Wrong kv store version", 1, keyValueStoreWithoutVersionFile.getStoreVersion())
        Assert.assertFalse(versionFile.exists())
    }

    @Test
    fun isValidStoreName() {
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName(null))
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName(""))
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc!def"))
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc def"))
        Assert.assertFalse("Store name is invalid", KeyValueEncryptedFileStore.isValidStoreName("abc/def"))
        Assert.assertTrue("Store name is valid", KeyValueEncryptedFileStore.isValidStoreName("abc_def"))
        Assert.assertTrue("Store name is valid", KeyValueEncryptedFileStore.isValidStoreName("abc_def_ABC_DEF_012"))
        var generateStoreName = ""
        for (i in 0 until KeyValueEncryptedFileStore.MAX_STORE_NAME_LENGTH * 2) {
            generateStoreName += "x"
            Assert.assertEquals("Wrong value returned by isValidStoreName(\"$generateStoreName\")",
                generateStoreName.length <= KeyValueEncryptedFileStore.MAX_STORE_NAME_LENGTH,
                KeyValueEncryptedFileStore.isValidStoreName(generateStoreName))
        }
        for (i in 0 until 256) {
            generateStoreName = i.toChar().toString()
            Assert.assertEquals("Wrong value returned by isValidStoreName(\"$generateStoreName\")",
                (i in 'a'.code..'z'.code) || (i in 'A'.code..'Z'.code) || (i in '0'.code..'9'.code) || i == '_'.code,
                KeyValueEncryptedFileStore.isValidStoreName(generateStoreName))
        }
    }

    @Test
    fun testComputeParentDir() {
        Assert.assertEquals("Wrong value returned by computeParentDir()", getStoreDir("").absolutePath, KeyValueEncryptedFileStore.computeParentDir(context).absolutePath)
    }

    @Test
    fun testGetStoreDir() {
        Assert.assertEquals("Wrong value returned by getStoreDir()", getStoreDir(TEST_STORE).absolutePath, keyValueStore.storeDir.absolutePath)
    }

    @Test
    fun testGetStoreName() {
        Assert.assertEquals("Wrong value returned by getStoreDir()", TEST_STORE, keyValueStore.getStoreName())
    }

    @Test
    fun testFailedCreateBadName() {
        try {
            KeyValueEncryptedFileStore(context, "", "")
            Assert.fail("An exception should have been thrown")
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue("Wrong exception", e.message!!.contains("Invalid store name"))
        }
    }

    @Test
    fun testFailedCreateFileExist() {
        val file = getStoreDir("file")
        file.delete()
        Assert.assertTrue("Test file creation failed", file.createNewFile())
        try {
            KeyValueEncryptedFileStore(context, "file", "")
            Assert.fail("An exception should have been thrown")
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue("Wrong exception", e.message!!.contains("Failed to create directory"))
        }
        file.delete()
    }

    @Test
    fun testHasKeyValueStore() {
        Assert.assertTrue("Store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, TEST_STORE))
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "non_existent_store"))
    }

    @Test
    fun testRemoveKeyValueStore() {
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"))
        KeyValueEncryptedFileStore(context, "new_store", "")
        Assert.assertTrue("Store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"))
        Assert.assertTrue("Store dir should exist", getStoreDir("new_store").exists())
        KeyValueEncryptedFileStore.removeKeyValueStore(context, "new_store")
        Assert.assertFalse("No store should have been found", KeyValueEncryptedFileStore.hasKeyValueStore(context, "new_store"))
        Assert.assertFalse("Store dir should be gone", getStoreDir("new_store").exists())
    }

    @Test
    fun testSaveValueCount() {
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals("Wrong count before save", i, keyValueStore.count())
            keyValueStore.saveValue("key$i", "value$i")
            Assert.assertEquals("Wrong count after save", i + 1, keyValueStore.count())
        }
    }

    @Test
    fun testSaveStreamCount() {
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals("Wrong count before save", i, keyValueStore.count())
            keyValueStore.saveStream("key$i", stringToStream("value$i"))
            Assert.assertEquals("Wrong count after save", i + 1, keyValueStore.count())
        }
    }

    @Test
    fun testSaveValueGetValue() {
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveValue("key$i", "value$i") }
        for (i in 0 until NUM_ENTRIES) { Assert.assertEquals("Wrong value for key: key$i", "value$i", keyValueStore.getValue("key$i")) }
    }

    @Test
    fun testSaveStreamGetValue() {
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveStream("key$i", stringToStream("value$i")) }
        for (i in 0 until NUM_ENTRIES) { Assert.assertEquals("Wrong value for key: key$i", "value$i", keyValueStore.getValue("key$i")) }
    }

    @Test
    fun testSaveLargeStreamGetLargeStream() {
        val key = "largeStreamKey"
        val dataSize = 5 * 1024 * 1024
        getLargeStringStream(dataSize).use { largeStream -> keyValueStore.saveStream(key, largeStream) }
        keyValueStore.getStream(key).use { retrievedStream ->
            getLargeStringStream(dataSize).use { expectedStream ->
                Assert.assertNotNull("Retrieved stream should not be null", retrievedStream)
                Assert.assertTrue("Streams should be equal", streamsEqual(expectedStream, retrievedStream!!))
            }
        }
    }

    @Test
    fun testSaveValueGetStream() {
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveValue("key$i", "value$i") }
        for (i in 0 until NUM_ENTRIES) { Assert.assertEquals("Wrong value (from stream) for key: key$i", "value$i", streamToString(keyValueStore.getStream("key$i"))) }
    }

    @Test
    fun testSaveStreamGetStream() {
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveStream("key$i", stringToStream("value$i")) }
        for (i in 0 until NUM_ENTRIES) { Assert.assertEquals("Wrong value (from stream) for key: key$i", "value$i", streamToString(keyValueStore.getStream("key$i"))) }
    }

    @Test
    fun testSaveValueDelete() {
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveValue("key$i", "value$i") }
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertNotNull("No value found for key when expected:key$i", keyValueStore.getValue("key$i"))
            Assert.assertEquals("Wrong count before delete", NUM_ENTRIES - i, keyValueStore.count())
            keyValueStore.deleteValue("key$i")
            Assert.assertEquals("Wrong count after delete", NUM_ENTRIES - (i + 1), keyValueStore.count())
            Assert.assertNull("Value found for key when not expected:key$i", keyValueStore.getValue("key$i"))
        }
    }

    @Test
    fun testSaveValueDeleteAll() {
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveValue("key$i", "value$i") }
        Assert.assertEquals("Wrong count before deleteAll", NUM_ENTRIES, keyValueStore.count())
        keyValueStore.deleteAll()
        Assert.assertEquals("Wrong count after deleteAll", 0, keyValueStore.count())
        for (i in 0 until NUM_ENTRIES) { Assert.assertNull("Value found for key when not expected:key$i", keyValueStore.getValue("key$i")) }
    }

    @Test
    fun testSaveValueCheckFiles() {
        Assert.assertEquals(1, getStoreDir(TEST_STORE).list()!!.size)
        for (i in 0 until NUM_ENTRIES) {
            keyValueStore.saveValue("key$i", "value$i")
            val keyFile = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key$i") + ".key")
            val valueFile = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key$i") + ".value")
            Assert.assertTrue(keyFile.exists())
            Assert.assertTrue(valueFile.exists())
            Assert.assertEquals(1 + 2 * (i + 1), getStoreDir(TEST_STORE).list()!!.size)
            Assert.assertEquals("key$i", keyValueStore.decryptFileAsString(keyFile, SalesforceSDKManager.encryptionKey))
            Assert.assertEquals("value$i", keyValueStore.decryptFileAsString(valueFile, SalesforceSDKManager.encryptionKey))
        }
    }

    @Test
    fun testSaveStreamsCheckFiles() {
        Assert.assertEquals(1, getStoreDir(TEST_STORE).list()!!.size)
        for (i in 0 until NUM_ENTRIES) {
            keyValueStore.saveStream("key$i", stringToStream("value$i"))
            val keyFile = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key$i") + ".key")
            val valueFile = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key$i") + ".value")
            Assert.assertTrue(keyFile.exists())
            Assert.assertTrue(valueFile.exists())
            Assert.assertEquals(1 + 2 * (i + 1), getStoreDir(TEST_STORE).list()!!.size)
            Assert.assertEquals("key$i", keyValueStore.decryptFileAsString(keyFile, SalesforceSDKManager.encryptionKey))
            Assert.assertEquals("value$i", keyValueStore.decryptFileAsString(valueFile, SalesforceSDKManager.encryptionKey))
        }
    }

    @Test
    fun testSaveDeleteCheckFiles() {
        Assert.assertEquals(1, getStoreDir(TEST_STORE).list()!!.size)
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveValue("key$i", "value$i") }
        for (i in 0 until NUM_ENTRIES) {
            val keyFile = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key$i") + ".key")
            val valueFile = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key$i") + ".value")
            Assert.assertTrue(keyFile.exists())
            Assert.assertTrue(valueFile.exists())
            Assert.assertEquals(1 + 2 * (NUM_ENTRIES - i), getStoreDir(TEST_STORE).list()!!.size)
            keyValueStore.deleteValue("key$i")
            Assert.assertEquals(1 + 2 * (NUM_ENTRIES - (i + 1)), getStoreDir(TEST_STORE).list()!!.size)
            Assert.assertFalse(keyFile.exists())
            Assert.assertFalse(valueFile.exists())
        }
    }

    @Test
    fun testSaveDeleteAllCheckFiles() {
        Assert.assertEquals(1, getStoreDir(TEST_STORE).list()!!.size)
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveValue("key$i", "value$i") }
        Assert.assertEquals(1 + 2 * NUM_ENTRIES, getStoreDir(TEST_STORE).list()!!.size)
        keyValueStore.deleteAll()
        Assert.assertEquals(1, getStoreDir(TEST_STORE).list()!!.size)
    }

    @Test
    fun testSaveValueKeySet() {
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals(i, keyValueStore.keySet().size)
            Assert.assertFalse(keyValueStore.keySet().contains("key$i"))
            keyValueStore.saveValue("key$i", "value$i")
            Assert.assertTrue(keyValueStore.keySet().contains("key$i"))
            Assert.assertEquals(i + 1, keyValueStore.keySet().size)
        }
    }

    @Test
    fun testSaveStreamKeySet() {
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals(i, keyValueStore.keySet().size)
            Assert.assertFalse(keyValueStore.keySet().contains("key$i"))
            keyValueStore.saveStream("key$i", stringToStream("value$i"))
            Assert.assertTrue(keyValueStore.keySet().contains("key$i"))
            Assert.assertEquals(i + 1, keyValueStore.keySet().size)
        }
    }

    @Test
    fun testSaveDeleteKeySet() {
        Assert.assertTrue(keyValueStore.keySet().isEmpty())
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveValue("key$i", "value$i") }
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals(NUM_ENTRIES - i, keyValueStore.keySet().size)
            Assert.assertTrue(keyValueStore.keySet().contains("key$i"))
            keyValueStore.deleteValue("key$i")
            Assert.assertFalse(keyValueStore.keySet().contains("key$i"))
            Assert.assertEquals(NUM_ENTRIES - (i + 1), keyValueStore.keySet().size)
        }
    }

    @Test
    fun testSaveDeleteAllKeySet() {
        Assert.assertTrue(keyValueStore.keySet().isEmpty())
        for (i in 0 until NUM_ENTRIES) { keyValueStore.saveValue("key$i", "value$i") }
        Assert.assertEquals(NUM_ENTRIES, keyValueStore.keySet().size)
        keyValueStore.deleteAll()
        Assert.assertTrue(keyValueStore.keySet().isEmpty())
    }

    @Test
    fun testNoNPEIfStoreDirDeleted() {
        ManagedFilesHelper.deleteFile(keyValueStore.storeDir)
        Assert.assertNull("Expected null for files in deleted stored dir", keyValueStore.storeDir.listFiles())
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

    @Test
    fun testSaveValueInvalidKey() {
        Assert.assertFalse("Save should have returned false for \"\" key", keyValueStore.saveValue("", "value"))
        Assert.assertNull("Value found for key when not expected", keyValueStore.getValue(""))
        Assert.assertEquals("Wrong count for store", 0, keyValueStore.count())
        Assert.assertFalse("Save should have returned false for null key", keyValueStore.saveValue(null as String, "value"))
        Assert.assertNull("Value found for key when not expected", keyValueStore.getValue(null as String))
        Assert.assertEquals("Wrong count for store", 0, keyValueStore.count())
    }

    @Test
    fun testSaveValueInvalidValue() {
        Assert.assertFalse("Save should have returned false for null value", keyValueStore.saveValue("key", null as String))
        Assert.assertNull("Value found for key when not expected", keyValueStore.getValue("key"))
        Assert.assertEquals("Wrong count for store", 0, keyValueStore.count())
    }

    @Test
    fun testStoreIsEncrypted() {
        keyValueStore.saveValue("key1", "value1")
        keyValueStore.saveValue("key2", "value2")
        val valueFiles = keyValueStore.storeDir.listFiles { _, s -> s.endsWith(".value") }
        Assert.assertEquals("Wrong number of files", 2, valueFiles!!.size)
        Assert.assertFalse("File should have been encrypted", streamToString(FileInputStream(valueFiles[0]))!!.contains("value"))
        Assert.assertFalse("File should have been encrypted", streamToString(FileInputStream(valueFiles[1]))!!.contains("value"))
        val keyFiles = keyValueStore.storeDir.listFiles { _, s -> s.endsWith(".key") }
        Assert.assertEquals("Wrong number of files", 2, keyFiles!!.size)
        Assert.assertFalse("File should have been encrypted", streamToString(FileInputStream(keyFiles[0]))!!.contains("key"))
        Assert.assertFalse("File should have been encrypted", streamToString(FileInputStream(keyFiles[1]))!!.contains("key"))
    }

    @Test
    fun testChangeEncryptionKey() {
        keyValueStore.saveValue("key1", "value1")
        keyValueStore.saveValue("key2", "value2")
        Assert.assertEquals("Wrong count", 2, keyValueStore.count())
        Assert.assertEquals("Wrong value for key1", "value1", keyValueStore.getValue("key1"))
        Assert.assertEquals("Wrong value for key2", "value2", keyValueStore.getValue("key2"))
        val files = keyValueStore.storeDir.listFiles { _, s -> s.endsWith(".value") }
        Assert.assertEquals("Wrong number of files", 2, files!!.size)
        val file1raw = streamToString(FileInputStream(files[0]))
        val file2raw = streamToString(FileInputStream(files[1]))
        val newEncryptionKey = SalesforceKeyGenerator.getEncryptionKey("new")!!
        Assert.assertNotEquals("New encryption key should be different", newEncryptionKey, SalesforceSDKManager.encryptionKey)
        Assert.assertTrue("Changing key should have succeeded", keyValueStore.changeEncryptionKey(newEncryptionKey))
        Assert.assertEquals("Wrong count", 2, keyValueStore.count())
        Assert.assertEquals("Wrong value for key1", "value1", keyValueStore.getValue("key1"))
        Assert.assertEquals("Wrong value for key2", "value2", keyValueStore.getValue("key2"))
        val file1rawAfter = streamToString(FileInputStream(files[0]))
        val file2rawAfter = streamToString(FileInputStream(files[1]))
        Assert.assertNotEquals("Raw content should have changed", file1rawAfter, file1raw)
        Assert.assertNotEquals("Raw content should have changed", file2rawAfter, file2raw)
    }

    @Test
    fun testCodeBlock() {
        val codeBlock = "var fun = function() {\n\t// comment\n\tvar i = 100;\n}"
        val minifiedBlock = "function minified(){var n=Math.floor(Math.random());return n>50?7*n:n/2}"
        keyValueStore.saveValue("js1", codeBlock)
        keyValueStore.saveValue("js2", minifiedBlock)
        Assert.assertEquals("Code block was not retrieved correctly.", codeBlock, keyValueStore.getValue("js1"))
        Assert.assertEquals("Code block was not retrieved correctly.", minifiedBlock, keyValueStore.getValue("js2"))
    }

    @Test
    fun testSaveGetDeleteCountOnV1Store() {
        keyValueStore = turnIntoV1Store(keyValueStore)
        keyValueStore.saveValue("key1", "value1")
        keyValueStore.saveStream("key2", stringToStream("value2"))
        keyValueStore.saveValue("key3", "value3")
        keyValueStore.saveStream("key4", stringToStream("value4"))
        Assert.assertEquals("value1", streamToString(keyValueStore.getStream("key1")))
        Assert.assertEquals("value2", keyValueStore.getValue("key2"))
        Assert.assertEquals("value3", keyValueStore.getValue("key3"))
        Assert.assertEquals("value4", streamToString(keyValueStore.getStream("key4")))
        Assert.assertEquals(4, keyValueStore.count())
        Assert.assertEquals(4, getStoreDir(TEST_STORE).list()!!.size)
        val value1 = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1"))
        Assert.assertTrue(value1.exists())
        Assert.assertEquals("value1", keyValueStore.decryptFileAsString(value1, SalesforceSDKManager.encryptionKey))
        keyValueStore.deleteValue("key2")
        Assert.assertNull(keyValueStore.getValue("key2"))
        Assert.assertEquals(3, keyValueStore.count())
        keyValueStore.deleteAll()
        Assert.assertNull(keyValueStore.getValue("key1"))
        Assert.assertEquals(0, keyValueStore.count())
        Assert.assertEquals(0, getStoreDir(TEST_STORE).list()!!.size)
    }

    @Test
    fun testKeySetOnV1Store() {
        keyValueStore = turnIntoV1Store(keyValueStore)
        try {
            keyValueStore.keySet()
            Assert.fail("Exception was expected")
        } catch (e: UnsupportedOperationException) {
            Assert.assertTrue(e.message!!.contains("keySet() not supported on v1 stores"))
        }
    }

    @Test
    fun testKeySetCountDeleteAllWithBadKeyFile() {
        keyValueStore.saveValue("key1", "value1")
        keyValueStore.saveValue("key2", "value2")
        Assert.assertEquals(2, keyValueStore.count())
        val key1File = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1") + ".key")
        val key2File = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key2") + ".key")
        val value1File = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key1") + ".value")
        val value2File = File(getStoreDir(TEST_STORE), SalesforceKeyGenerator.getSHA256Hash("key2") + ".value")
        Assert.assertTrue(key1File.exists())
        Assert.assertTrue(key2File.exists())
        Assert.assertTrue(value1File.exists())
        Assert.assertTrue(value2File.exists())
        key1File.delete()
        key1File.createNewFile()
        Assert.assertEquals(1, keyValueStore.count())
        val foundKeys = keyValueStore.keySet().toTypedArray()
        Assert.assertEquals(1, foundKeys.size)
        Assert.assertEquals("key2", foundKeys[0])
        Assert.assertEquals(1, keyValueStore.count())
        keyValueStore.deleteAll()
        Assert.assertFalse(key1File.exists())
        Assert.assertFalse(key2File.exists())
        Assert.assertFalse(value1File.exists())
        Assert.assertFalse(value2File.exists())
        Assert.assertEquals(0, keyValueStore.count())
    }

    @Test
    fun testBinaryStorage() {
        keyValueStore.saveStream("icon", getResourceIconStream())
        val savedIconBytes = Encryptor.getByteArrayStreamFromStream(keyValueStore.getStream("icon")!!).toByteArray()
        val resourceIconBytes = Encryptor.getByteArrayStreamFromStream(getResourceIconStream()).toByteArray()
        Assert.assertEquals(resourceIconBytes.size, savedIconBytes.size)
        for (i in resourceIconBytes.indices) { Assert.assertEquals(resourceIconBytes[i], savedIconBytes[i]) }
    }

    @Test
    fun testContains() {
        Assert.assertFalse(keyValueStore.contains("key1"))
        keyValueStore.saveValue("key1", "value1")
        Assert.assertTrue(keyValueStore.contains("key1"))
        Assert.assertFalse(keyValueStore.contains("key2"))
        keyValueStore.saveValue("key2", "value2")
        Assert.assertTrue(keyValueStore.contains("key2"))
        keyValueStore.deleteValue("key1")
        Assert.assertFalse(keyValueStore.contains("key1"))
        keyValueStore.deleteAll()
        Assert.assertFalse(keyValueStore.contains("key2"))
    }

    // Helper methods
    private fun getResourceIconStream(): InputStream = context.resources.openRawResource(R.drawable.sf__icon)

    private fun turnIntoV1Store(store: KeyValueEncryptedFileStore): KeyValueEncryptedFileStore {
        if (!store.isEmpty()) throw RuntimeException("turnIntoV1Store() should be called on empty store")
        File(store.storeDir, "version").delete()
        val storeV1 = KeyValueEncryptedFileStore(context, store.getStoreName(), SalesforceSDKManager.encryptionKey)
        Assert.assertEquals(1, keyValueStore.readVersion())
        Assert.assertEquals("Directory should be empty", 0, keyValueStore.storeDir.list()!!.size)
        return storeV1
    }

    private fun getStoreDir(storeName: String): File = File(context.applicationInfo.dataDir + "/keyvaluestores", storeName)

    private fun stringToStream(value: String): InputStream = ByteArrayInputStream(value.toByteArray(StandardCharsets.UTF_8))

    private fun streamToString(inputStream: InputStream?): String? {
        if (inputStream == null) return null
        try {
            return Encryptor.getStringFromStream(inputStream)
        } catch (e: Exception) {
            Assert.fail("Failed to read from stream")
            return null
        } finally {
            try { inputStream.close() } catch (e: Exception) { Assert.fail("Stream failed to close") }
        }
    }

    private fun getLargeStringStream(size: Int): InputStream {
        return object : InputStream() {
            private var bytesRead = 0
            private val patternBytes = "0123456789ABCDEF".toByteArray(StandardCharsets.UTF_8)
            private val patternLength = patternBytes.size
            override fun read(): Int {
                if (bytesRead >= size) return -1
                val b = patternBytes[bytesRead % patternLength]
                bytesRead++
                return b.toInt() and 0xFF
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (bytesRead >= size) return -1
                val bytesToRead = minOf(length, size - bytesRead)
                for (i in 0 until bytesToRead) { buffer[offset + i] = patternBytes[(bytesRead + i) % patternLength] }
                bytesRead += bytesToRead
                return bytesToRead
            }
        }
    }

    private fun streamsEqual(stream1: InputStream, stream2: InputStream): Boolean {
        val buffer1 = ByteArray(8192)
        val buffer2 = ByteArray(8192)
        while (true) {
            val bytesRead1 = stream1.read(buffer1)
            val bytesRead2 = stream2.read(buffer2)
            if (bytesRead1 == -1 && bytesRead2 == -1) return true
            if (bytesRead1 != bytesRead2) return false
            for (i in 0 until bytesRead1) { if (buffer1[i] != buffer2[i]) return false }
        }
    }
}
