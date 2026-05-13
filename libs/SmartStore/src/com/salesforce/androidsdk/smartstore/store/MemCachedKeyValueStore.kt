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

import android.util.LruCache
import androidx.annotation.VisibleForTesting
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Key value store that keeps recently accessed values in a in-memory lru cache for faster access
 */
class MemCachedKeyValueStore(
    private val keyValueStore: KeyValueStore,
    cacheSize: Int
) : KeyValueStore {

    @VisibleForTesting
    internal val memCache: LruCache<String, ByteArray> = LruCache(cacheSize)

    override fun contains(key: String): Boolean {
        return memCache.get(key) != null || keyValueStore.contains(key)
    }

    override fun getValue(key: String): String? {
        val stream = getStream(key) ?: return null
        return try {
            Encryptor.getStringFromStream(stream)
        } catch (e: IOException) {
            SmartStoreLogger.e(TAG, "getValue(\"$key\") could not convert stream to string", e)
            null
        }
    }

    override fun getStream(key: String): InputStream? {
        val bytesFromMemCache = memCache.get(key)
        return if (bytesFromMemCache == null) {
            val streamFromStore = keyValueStore.getStream(key) ?: return null
            try {
                val bytesFromStore = Encryptor.getByteArrayStreamFromStream(streamFromStore).toByteArray()
                memCache.put(key, bytesFromStore)
                ByteArrayInputStream(bytesFromStore)
            } catch (e: IOException) {
                SmartStoreLogger.e(TAG, "getStream(\"$key\") could not read stream", e)
                null
            }
        } else {
            ByteArrayInputStream(bytesFromMemCache)
        }
    }

    override fun saveValue(key: String, value: String): Boolean {
        return if (keyValueStore.saveValue(key, value)) {
            memCache.put(key, value.toByteArray(StandardCharsets.UTF_8))
            true
        } else {
            false
        }
    }

    @Throws(IOException::class)
    override fun saveStream(key: String, stream: InputStream): Boolean {
        val bytes = Encryptor.getByteArrayStreamFromStream(stream).toByteArray()
        return if (keyValueStore.saveStream(key, ByteArrayInputStream(bytes))) {
            memCache.put(key, bytes)
            true
        } else {
            false
        }
    }

    override fun deleteValue(key: String): Boolean {
        return if (keyValueStore.deleteValue(key)) {
            memCache.remove(key)
            true
        } else {
            false
        }
    }

    override fun deleteAll(): Boolean {
        memCache.evictAll()
        return keyValueStore.deleteAll()
    }

    override fun keySet(): Set<String> {
        return keyValueStore.keySet()
    }

    override fun count(): Int {
        return keyValueStore.count()
    }

    override fun isEmpty(): Boolean {
        return keyValueStore.isEmpty()
    }

    override fun getStoreName(): String {
        return keyValueStore.getStoreName()
    }

    companion object {
        private val TAG = MemCachedKeyValueStore::class.java.simpleName
    }
}
