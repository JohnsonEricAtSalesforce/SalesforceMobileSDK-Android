/*
 * Copyright (c) 2021-present, salesforce.com, inc.
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
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.smartstore.tests.R
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/** Tests for MemCachedKeyValueStore */
@RunWith(AndroidJUnit4::class)
class MemCachedKeyValueStoreTest {

    companion object {
        const val TEST_STORE = "TEST_STORE"
        const val NUM_ENTRIES = 25
        const val CACHE_SIZE = 10
    }

    private lateinit var context: Context
    private lateinit var store: KeyValueEncryptedFileStore
    private lateinit var memCachedStore: MemCachedKeyValueStore

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
        store = KeyValueEncryptedFileStore(context, TEST_STORE, SalesforceSDKManager.encryptionKey)
        memCachedStore = MemCachedKeyValueStore(store, CACHE_SIZE)
    }

    @After
    fun tearDown() {
        store.deleteAll()
    }

    @Test
    fun testSaveValueCount() {
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals("Wrong count before save", i, memCachedStore.count())
            Assert.assertEquals("Wrong count before save", i, store.count())
            memCachedStore.saveValue("key$i", "value$i")
            Assert.assertEquals("Wrong count after save", i + 1, memCachedStore.count())
            Assert.assertEquals("Wrong count after save", i + 1, store.count())
        }
    }

    @Test
    fun testSaveStreamCount() {
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals("Wrong count before save", i, memCachedStore.count())
            Assert.assertEquals("Wrong count before save", i, store.count())
            memCachedStore.saveStream("key$i", stringToStream("value$i"))
            Assert.assertEquals("Wrong count after save", i + 1, memCachedStore.count())
            Assert.assertEquals("Wrong count after save", i + 1, store.count())
        }
    }

    @Test
    fun testSaveValueGetWhenMemCacheHits() {
        memCachedStore.saveValue("key1", "value1")
        Assert.assertEquals(1, memCachedStore.memCache.putCount())
        Assert.assertEquals("value1", memCachedStore.getValue("key1"))
        Assert.assertEquals(1, memCachedStore.memCache.hitCount())
        Assert.assertEquals("value1", store.getValue("key1"))
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")))
        Assert.assertEquals(2, memCachedStore.memCache.hitCount())
        Assert.assertEquals("value1", streamToString(store.getStream("key1")))
        Assert.assertEquals("value1", String(memCachedStore.memCache.get("key1"), StandardCharsets.UTF_8))
    }

    @Test
    fun testSaveStreamGetWhenMemCacheHits() {
        memCachedStore.saveStream("key1", stringToStream("value1"))
        Assert.assertEquals(1, memCachedStore.memCache.putCount())
        Assert.assertEquals("value1", memCachedStore.getValue("key1"))
        Assert.assertEquals(1, memCachedStore.memCache.hitCount())
        Assert.assertEquals("value1", store.getValue("key1"))
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")))
        Assert.assertEquals(2, memCachedStore.memCache.hitCount())
        Assert.assertEquals("value1", streamToString(store.getStream("key1")))
        Assert.assertEquals("value1", String(memCachedStore.memCache.get("key1"), StandardCharsets.UTF_8))
    }

    @Test
    fun testSaveValueGetWhenMemCacheMisses() {
        memCachedStore.saveValue("key1", "value1")
        Assert.assertEquals(1, memCachedStore.memCache.putCount())
        memCachedStore.memCache.evictAll()
        Assert.assertEquals("value1", memCachedStore.getValue("key1"))
        Assert.assertEquals(1, memCachedStore.memCache.missCount())
        Assert.assertEquals("value1", store.getValue("key1"))
        memCachedStore.memCache.evictAll()
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")))
        Assert.assertEquals(2, memCachedStore.memCache.missCount())
        Assert.assertEquals("value1", streamToString(store.getStream("key1")))
    }

    @Test
    fun testSaveStreamGetWhenMemCacheMisses() {
        memCachedStore.saveStream("key1", stringToStream("value1"))
        Assert.assertEquals(1, memCachedStore.memCache.putCount())
        memCachedStore.memCache.evictAll()
        Assert.assertEquals("value1", memCachedStore.getValue("key1"))
        Assert.assertEquals(1, memCachedStore.memCache.missCount())
        Assert.assertEquals("value1", store.getValue("key1"))
        memCachedStore.memCache.evictAll()
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")))
        Assert.assertEquals(2, memCachedStore.memCache.missCount())
        Assert.assertEquals("value1", streamToString(store.getStream("key1")))
    }

    @Test
    fun testGetStreamPopulatesMemCache() {
        memCachedStore.saveValue("key1", "value1")
        Assert.assertEquals(1, memCachedStore.memCache.putCount())
        memCachedStore.memCache.evictAll()
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")))
        Assert.assertEquals(1, memCachedStore.memCache.missCount())
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")))
        Assert.assertEquals(1, memCachedStore.memCache.hitCount())
        Assert.assertEquals("value1", String(memCachedStore.memCache.get("key1"), StandardCharsets.UTF_8))
    }

    @Test
    fun testGetValuePopulatesMemCache() {
        memCachedStore.saveValue("key1", "value1")
        Assert.assertEquals(1, memCachedStore.memCache.putCount())
        memCachedStore.memCache.evictAll()
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")))
        Assert.assertEquals(1, memCachedStore.memCache.missCount())
        Assert.assertEquals("value1", streamToString(memCachedStore.getStream("key1")))
        Assert.assertEquals(1, memCachedStore.memCache.hitCount())
        Assert.assertEquals("value1", String(memCachedStore.memCache.get("key1"), StandardCharsets.UTF_8))
    }

    @Test
    fun testSaveValueDelete() {
        for (i in 0 until NUM_ENTRIES) { memCachedStore.saveValue("key$i", "value$i") }
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertNotNull("No value found for key when expected:\$key", memCachedStore.getValue("key$i"))
            Assert.assertEquals("Wrong count before delete", NUM_ENTRIES - i, memCachedStore.count())
            Assert.assertNotNull("No value found for key when expected:\$key", store.getValue("key$i"))
            Assert.assertEquals("Wrong count before delete", NUM_ENTRIES - i, store.count())
            memCachedStore.deleteValue("key$i")
            Assert.assertNull("Value found for key when not expected:\$key", memCachedStore.getValue("key$i"))
            Assert.assertEquals("Wrong count after delete", NUM_ENTRIES - i - 1, memCachedStore.count())
            Assert.assertNull("Value found for key when not expected:\$key", store.getValue("key$i"))
            Assert.assertEquals("Wrong count after delete", NUM_ENTRIES - i - 1, store.count())
        }
    }

    @Test
    fun testSaveValueDeleteAll() {
        for (i in 0 until NUM_ENTRIES) { memCachedStore.saveValue("key$i", "value$i") }
        Assert.assertEquals("Wrong count before deleteAll", NUM_ENTRIES, memCachedStore.count())
        Assert.assertEquals("Wrong count before deleteAll", NUM_ENTRIES, store.count())
        memCachedStore.deleteAll()
        Assert.assertEquals("Wrong count after deleteAll", 0, memCachedStore.count())
        Assert.assertEquals("Wrong count after deleteAll", 0, store.count())
    }

    @Test
    fun testNewLinesPreservedWhenMemCacheHits() {
        val codeBlock = "var fun = function() {\r // comment \n var i = 100; } \r\n // comment"
        memCachedStore.saveStream("js1", stringToStream(codeBlock))
        memCachedStore.saveValue("js2", codeBlock)
        Assert.assertEquals(codeBlock, memCachedStore.getValue("js1"))
        Assert.assertEquals(codeBlock, streamToString(memCachedStore.getStream("js1")))
        Assert.assertEquals(codeBlock, memCachedStore.getValue("js2"))
        Assert.assertEquals(codeBlock, streamToString(memCachedStore.getStream("js2")))
        Assert.assertEquals(codeBlock, store.getValue("js1"))
        Assert.assertEquals(codeBlock, streamToString(store.getStream("js1")))
        Assert.assertEquals(codeBlock, store.getValue("js2"))
        Assert.assertEquals(codeBlock, streamToString(store.getStream("js2")))
    }

    @Test
    fun testNewLinesPreservedWhenMemCacheMisses() {
        val codeBlock = "var fun = function() {\r // comment \n var i = 100; } \r\n // comment"
        memCachedStore.saveStream("js1", stringToStream(codeBlock))
        memCachedStore.saveValue("js2", codeBlock)
        memCachedStore.memCache.evictAll()
        Assert.assertEquals(codeBlock, memCachedStore.getValue("js1"))
        Assert.assertEquals(codeBlock, memCachedStore.getValue("js2"))
        memCachedStore.memCache.evictAll()
        Assert.assertEquals(codeBlock, streamToString(memCachedStore.getStream("js1")))
        Assert.assertEquals(codeBlock, streamToString(memCachedStore.getStream("js2")))
    }

    @Test
    fun testSaveDeleteKeySet() {
        Assert.assertTrue(memCachedStore.keySet().isEmpty())
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals(i, memCachedStore.keySet().size)
            Assert.assertFalse(memCachedStore.keySet().contains("key$i"))
            memCachedStore.saveValue("key$i", "value$i")
            Assert.assertTrue(memCachedStore.keySet().contains("key$i"))
            Assert.assertEquals(i + 1, memCachedStore.keySet().size)
        }
        for (i in 0 until NUM_ENTRIES) {
            Assert.assertEquals(NUM_ENTRIES - i, memCachedStore.keySet().size)
            Assert.assertTrue(memCachedStore.keySet().contains("key$i"))
            memCachedStore.deleteValue("key$i")
            Assert.assertFalse(memCachedStore.keySet().contains("key$i"))
            Assert.assertEquals(NUM_ENTRIES - (i + 1), memCachedStore.keySet().size)
        }
    }

    @Test
    fun testBinaryStorage() {
        memCachedStore.saveStream("icon", getResourceIconStream())
        val savedIconBytes = Encryptor.getByteArrayStreamFromStream(memCachedStore.getStream("icon")!!).toByteArray()
        val resourceIconBytes = Encryptor.getByteArrayStreamFromStream(getResourceIconStream()).toByteArray()
        Assert.assertEquals(resourceIconBytes.size, savedIconBytes.size)
        for (i in resourceIconBytes.indices) { Assert.assertEquals(resourceIconBytes[i], savedIconBytes[i]) }
    }

    @Test
    fun testContains() {
        Assert.assertFalse(memCachedStore.contains("key1"))
        Assert.assertFalse(store.contains("key1"))
        memCachedStore.saveValue("key1", "value1")
        Assert.assertTrue(memCachedStore.contains("key1"))
        Assert.assertTrue(store.contains("key1"))
        Assert.assertFalse(memCachedStore.contains("key2"))
        store.saveValue("key2", "value2")
        Assert.assertTrue(memCachedStore.contains("key2"))
        Assert.assertTrue(store.contains("key2"))
        memCachedStore.saveValue("key3", "value3")
        Assert.assertTrue(memCachedStore.contains("key3"))
        memCachedStore.deleteValue("key1")
        Assert.assertFalse(memCachedStore.contains("key1"))
        Assert.assertFalse(store.contains("key1"))
        memCachedStore.deleteAll()
        Assert.assertFalse(memCachedStore.contains("key2"))
        Assert.assertFalse(memCachedStore.contains("key3"))
        Assert.assertFalse(store.contains("key2"))
        Assert.assertFalse(store.contains("key3"))
    }

    // Helper methods
    private fun getResourceIconStream(): InputStream = context.resources.openRawResource(R.drawable.sf__icon)

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
}
