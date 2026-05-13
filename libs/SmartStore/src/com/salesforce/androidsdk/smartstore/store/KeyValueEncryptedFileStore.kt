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
import android.text.TextUtils
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger
import com.salesforce.androidsdk.util.ManagedFilesHelper
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.io.IOException
import java.io.InputStream

/**
 * Key-value store backed by file system. Currently uses an in-memory solution for encryption and
 * decryption. While this solution is not particularly good from a memory standpoint, we will need
 * to employ this as a workaround for now, until we figure out how we can achieve acceptable
 * performance with a streaming solution, since CipherInputStream is a lot slower with AES-GCM.
 */
class KeyValueEncryptedFileStore : KeyValueStore {

    private var encryptionKey: String
    private var kvVersion: Int
    private val storeDir: File

    /**
     * Constructor
     *
     * @param storeName name for key value store
     * @param encryptionKey encryption key for key value store
     */
    constructor(ctx: Context, storeName: String, encryptionKey: String) : this(
        computeParentDir(ctx),
        storeName,
        encryptionKey
    )

    /**
     * Constructor
     *
     * @param parentDir parent directory for key value store
     * @param storeName name for key value store
     * @param encryptionKey encryption key for key value store
     */
    internal constructor(parentDir: File, storeName: String, encryptionKey: String) {
        if (!isValidStoreName(storeName)) {
            throw IllegalArgumentException("Invalid store name: $storeName")
        }
        storeDir = File(parentDir, storeName)
        this.encryptionKey = encryptionKey

        if (!storeDir.exists()) {
            storeDir.mkdirs()
            writeVersion(KV_VERSION)
            kvVersion = KV_VERSION
        } else {
            kvVersion = readVersion()
        }

        if (!storeDir.exists() || !storeDir.isDirectory) {
            throw IllegalArgumentException("Failed to create directory for: $storeName")
        }
    }

    /**
     * Return true if store contains a file for the given key
     * @param key
     * @return
     */
    override fun contains(key: String): Boolean {
        if (!isKeyValid(key, "contains")) {
            return false
        }

        return getKeyFile(key).exists() && getValueFile(key).exists()
    }

    /**
     * Save value for the given key.
     *
     * @param key Unique identifier.
     * @param value Value to be persisted.
     * @return True - if successful, False - otherwise.
     */
    override fun saveValue(key: String, value: String): Boolean {
        if (!isKeyValid(key, "saveValue")) {
            return false
        }
        return try {
            if (kvVersion >= 2) encryptStringToFile(getKeyFile(key), key, encryptionKey)
            encryptStringToFile(getValueFile(key), value, encryptionKey)
            true
        } catch (e: Exception) {
            SmartStoreLogger.e(TAG, "Exception occurred while saving value to filesystem", e)
            false
        }
    }

    /**
     * Save value given as an input stream for the given key.
     * NB: does not close provided input stream
     *
     * @param key Unique identifier.
     * @param stream Stream to be persisted.
     * @return True - if successful, False - otherwise.
     */
    override fun saveStream(key: String, stream: InputStream): Boolean {
        if (!isKeyValid(key, "saveStream")) {
            return false
        }
        return try {
            if (kvVersion >= 2) encryptStringToFile(getKeyFile(key), key, encryptionKey)
            encryptStreamToFile(getValueFile(key), stream, encryptionKey)
            true
        } catch (e: Exception) {
            SmartStoreLogger.e(TAG, "Exception occurred while saving stream to filesystem", e)
            false
        }
    }

    /**
     * Returns value stored for given key.
     *
     * @param key Unique identifier.
     * @return value for given key or null if key not found.
     */
    override fun getValue(key: String): String? {
        if (!isKeyValid(key, "getValue")) {
            return null
        }
        val file = getValueFile(key)
        if (!file.exists()) {
            SmartStoreLogger.w(TAG, "getValue: File does not exist for key: $key")
            return null
        }
        return try {
            decryptFileAsString(file, encryptionKey)
        } catch (e: Exception) {
            SmartStoreLogger.e(TAG, "getValue: Threw exception for key: $key", e)
            null
        }
    }

    /**
     * Returns stream for value of given key.
     *
     * @param key Unique identifier.
     * @return stream to value for given key or null if key not found.
     */
    override fun getStream(key: String): InputStream? {
        if (!isKeyValid(key, "getStream")) {
            return null
        }
        val file = getValueFile(key)
        if (!file.exists()) {
            SmartStoreLogger.w(TAG, "getStream: File does not exist for key: $key")
            return null
        }
        return try {
            decryptFileAsSteam(file, encryptionKey)
        } catch (e: Exception) {
            SmartStoreLogger.e(TAG, "getStream: Threw exception for key: $key", e)
            null
        }
    }

    /**
     * Deletes stored value for given key.
     *
     * @param key Unique identifier.
     * @return True - if successful, False - otherwise.
     */
    @Synchronized
    override fun deleteValue(key: String): Boolean {
        if (!isKeyValid(key, "deleteValue")) {
            return false
        }
        var success = true
        if (kvVersion >= 2) {
            success = getKeyFile(key).delete()
        }
        success = getValueFile(key).delete() && success // NB: delete file even if the other delete failed
        return success
    }

    /**
     * Deletes all stored values.
     * @return true if successful
     */
    override fun deleteAll(): Boolean {
        var success = true
        if (kvVersion == 1) {
            for (file in safeListFiles(null)) {
                SmartStoreLogger.i(TAG, "deleting file :${file.name}")
                success = file.delete() && success // NB: delete file even if the other delete failed
            }
        } else {
            for (file in safeListFiles(KEY_SUFFIX)) {
                SmartStoreLogger.i(TAG, "deleting file :${file.name}")
                success = file.delete() && success // NB: delete file even if the other delete failed
            }
            for (file in safeListFiles(VALUE_SUFFIX)) {
                SmartStoreLogger.i(TAG, "deleting file :${file.name}")
                success = file.delete() && success // NB: delete file even if the other delete failed
            }
        }
        return success
    }

    /**
     * Get all keys.
     * NB: will throw UnsupportedOperationException for a v1 store
     */
    override fun keySet(): Set<String> {
        if (kvVersion == 1) {
            throw UnsupportedOperationException("keySet() not supported on v1 stores")
        }

        val keys = HashSet<String>()
        for (file in safeListFiles(KEY_SUFFIX)) {
            try {
                val key = decryptFileAsString(file, encryptionKey)
                keys.add(key)
            } catch (e: Exception) {
                SmartStoreLogger.e(TAG, "keySet(): Threw exception for:${file.name}", e)
                // skip the bad key but keep going
            }
        }
        return keys
    }

    /** @return number of entries in the store. */
    override fun count(): Int {
        return if (kvVersion == 1) safeListFiles(null /* all */).size else keySet().size
    }

    /** @return True if store is empty. */
    override fun isEmpty(): Boolean {
        return count() == 0
    }

    /**
     * @return store directory
     */
    fun getStoreDir(): File {
        return storeDir
    }

    /**
     * @return store name
     */
    override fun getStoreName(): String {
        return storeDir.name
    }

    /**
     * @return store version
     */
    fun getStoreVersion(): Int {
        return kvVersion
    }

    /**
     * Change encryption key
     * All files are read/decrypted with old key and encrypted/written back with new key
     * @param newEncryptionKey
     * @return true if successful
     */
    fun changeEncryptionKey(newEncryptionKey: String): Boolean {
        val originalStoreDir = storeDir
        val storeName = getStoreName()
        val tmpDir = File(storeDir.parent, "$storeName-tmp")
        tmpDir.mkdirs()
        if (!tmpDir.isDirectory) {
            SmartStoreLogger.e(TAG, "changeKey: Failed to create tmp directory: $tmpDir")
            return false
        }
        // NB: - not allowed for store name so no chances of hitting colliding with existing store
        val originalFiles = originalStoreDir.listFiles() ?: return false
        for (originalFile in originalFiles) {
            try {
                encryptStreamToFile(
                    File(tmpDir, originalFile.name), // tmp file
                    decryptFileAsSteam(originalFile, encryptionKey),   // reading original file
                    newEncryptionKey                        // encrypting with new encryption key
                )
            } catch (e: Exception) {
                SmartStoreLogger.e(TAG, "changeKey: Threw exception for file: $originalFile", e)
                //Failed
                return false
            }
        }
        // Removing old store dir - renaming tmp dir
        ManagedFilesHelper.deleteFile(originalStoreDir)
        tmpDir.renameTo(originalStoreDir)

        // Updating encryption key
        encryptionKey = newEncryptionKey

        // Successful
        return true
    }

    private fun encodeKey(key: String): String {
        return SalesforceKeyGenerator.getSHA256Hash(key) ?: ""
    }

    private fun getKeyFile(key: String): File {
        return File(storeDir, encodeKey(key) + KEY_SUFFIX)
    }

    private fun getValueFile(key: String): File {
        val valueFileName = if (kvVersion == 1) encodeKey(key) else encodeKey(key) + VALUE_SUFFIX
        return File(storeDir, valueFileName)
    }

    private fun getVersionFile(): File {
        return File(storeDir, VERSION_FILE_NAME)
    }

    private fun isKeyValid(key: String, operation: String): Boolean {
        if (TextUtils.isEmpty(key)) {
            SmartStoreLogger.w(TAG, "$operation: Invalid key supplied: $key")
            return false
        }
        return true
    }

    /**
     * Get array of Files in storeDir that ends with suffix
     * Returns all files if suffix is null
     * Returns empty array if storeDir does not exist
     */
    private fun safeListFiles(suffix: String?): Array<File> {
        val filter = FilenameFilter { _, name ->
            suffix?.let { name.endsWith(it) } ?: true
        }
        val files = storeDir.listFiles(filter)
        return files ?: emptyArray()
    }

    @Throws(IOException::class)
    internal fun decryptFileAsString(file: File, encryptionKey: String): String {
        return Encryptor.getStringFromStream(decryptFileAsSteam(file, encryptionKey))
    }

    @Throws(IOException::class)
    internal fun decryptFileAsSteam(file: File, encryptionKey: String): InputStream {
        var f: FileInputStream? = null
        try {
            f = FileInputStream(file)
            val data = DataInputStream(f)
            val bytes = ByteArray(file.length().toInt())
            data.readFully(bytes)
            val decryptedBytes = Encryptor.decryptWithoutBase64Encoding(bytes, encryptionKey)
            return if (decryptedBytes != null) {
                ByteArrayInputStream(decryptedBytes)
            } else {
                throw IOException("Decryption returned null")
            }
        } finally {
            f?.close()
        }
    }

    @Throws(IOException::class)
    internal fun encryptStringToFile(file: File, content: String, encryptionKey: String) {
        encryptBytesToFile(file, content.toByteArray(), encryptionKey)
    }

    @Throws(IOException::class)
    internal fun encryptStreamToFile(file: File, stream: InputStream, encryptionKey: String) {
        val content = Encryptor.getByteArrayStreamFromStream(stream).toByteArray()
        encryptBytesToFile(file, content, encryptionKey)
    }

    @Throws(IOException::class)
    internal fun encryptBytesToFile(file: File, content: ByteArray, encryptionKey: String) {
        var f: FileOutputStream? = null
        try {
            val encryptedContent = Encryptor.encryptWithoutBase64Encoding(content, encryptionKey)
            f = FileOutputStream(file)
            if (encryptedContent != null) {
                f.write(encryptedContent)
            }
        } finally {
            f?.close()
        }
    }

    internal fun writeVersion(kvVersion: Int) {
        try {
            encryptStringToFile(getVersionFile(), kvVersion.toString(), encryptionKey)
        } catch (e: Exception) {
            SmartStoreLogger.e(TAG, "Failed to store version", e)
            // What now ??
        }
    }

    internal fun readVersion(): Int {
        return try {
            decryptFileAsString(getVersionFile(), encryptionKey).toInt()
        } catch (e: Exception) {
            if (e !is FileNotFoundException) {
                // Version 1 did not have a version file - no need to log an error
                SmartStoreLogger.e(TAG, "Failed to retrieve version", e)
            }
            1
        }
    }

    companion object {
        // 1 --> 9.0 (no version files, only values are stored in files named hash(key)
        // 2 --> starting at 9.1 (version file, keys stored in files named <hash(key)>.key and values stored in files named <hash(key)>.value
        const val KV_VERSION = 2

        private val TAG = KeyValueEncryptedFileStore::class.java.simpleName
        const val MAX_STORE_NAME_LENGTH = 96
        const val KEY_SUFFIX = ".key"
        const val VALUE_SUFFIX = ".value"
        const val VERSION_FILE_NAME = "version"
        const val KEY_VALUE_STORES = "keyvaluestores"

        /**
         * Return boolean indicating if a key value store with the given (full) name already exists
         * @param ctx
         * @param storeName full store name
         * @return True - if store was found
         */
        @JvmStatic
        fun hasKeyValueStore(ctx: Context, storeName: String): Boolean {
            return File(computeParentDir(ctx), storeName).exists()
        }

        /**
         * Remove key value store with given (full) name
         * @param ctx
         * @param storeName full store name
         */
        @JvmStatic
        fun removeKeyValueStore(ctx: Context, storeName: String) {
            ManagedFilesHelper.deleteFile(File(computeParentDir(ctx), storeName))
        }

        /**
         * Return parent directory for all key stores
         * @param ctx
         * @return File for parent directory
         */
        @JvmStatic
        fun computeParentDir(ctx: Context): File {
            return File(ctx.applicationInfo.dataDir, KEY_VALUE_STORES)
        }

        /**
         * Store name can only contain letters, digits and _ and cannot exceed 96 characters
         * @param storeName
         * @return True if the name provided is valid for a store
         */
        @JvmStatic
        fun isValidStoreName(storeName: String?): Boolean {
            return storeName != null && storeName.isNotEmpty() && storeName.length <= MAX_STORE_NAME_LENGTH &&
                    storeName.matches(Regex("^[a-zA-Z0-9_]*$"))
        }
    }
}
