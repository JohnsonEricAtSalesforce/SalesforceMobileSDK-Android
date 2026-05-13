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

package com.salesforce.androidsdk.smartstore.store

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.MainActivity
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.ui.LoginActivity
import org.json.JSONException
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class StoreConfigTest : SmartStoreTestCase() {

    private lateinit var sdkManager: SmartStoreSDKManager
    private lateinit var globalStore: SmartStore
    private lateinit var userStore: SmartStore

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        SmartStoreSDKTestManager.init(InstrumentationRegistry.getInstrumentation().targetContext, store)
        sdkManager = SmartStoreSDKTestManager.getInstance()
        globalStore = sdkManager.getGlobalSmartStore()
        userStore = sdkManager.getSmartStore()
    }

    override val encryptionKey: String
        get() = "test123"

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        sdkManager.removeAllGlobalStores()
        super.tearDown()
    }

    @Test
    @Throws(JSONException::class)
    fun testSetupGlobalStoreFromDefaultConfig() {
        Assert.assertFalse(globalStore.hasSoup("globalSoup1"))
        Assert.assertFalse(globalStore.hasSoup("globalSoup2"))

        // Setting up soup
        sdkManager.setupGlobalStoreFromDefaultConfig()

        // Checking smartstore
        Assert.assertTrue(globalStore.hasSoup("globalSoup1"))
        Assert.assertTrue(globalStore.hasSoup("globalSoup2"))
        val actualSoupNames = globalStore.getAllSoupNames()
        Assert.assertEquals("Wrong soups found", 2, actualSoupNames.size)
        Assert.assertTrue(actualSoupNames.contains("globalSoup1"))
        Assert.assertTrue(actualSoupNames.contains("globalSoup2"))

        // Checking first soup in details
        checkIndexSpecs("globalSoup1", arrayOf(
                IndexSpec("stringField1", SmartStore.Type.string, "TABLE_1_0"),
                IndexSpec("integerField1", SmartStore.Type.integer, "TABLE_1_1"),
                IndexSpec("floatingField1", SmartStore.Type.floating, "TABLE_1_2"),
                IndexSpec("json1Field1", SmartStore.Type.json1, "json_extract(soup, '$.json1Field1')"),
                IndexSpec("ftsField1", SmartStore.Type.full_text, "TABLE_1_4")
        ))

        // Checking second soup in details
        checkIndexSpecs("globalSoup2", arrayOf(
                IndexSpec("stringField2", SmartStore.Type.string, "TABLE_2_0"),
                IndexSpec("integerField2", SmartStore.Type.integer, "TABLE_2_1"),
                IndexSpec("floatingField2", SmartStore.Type.floating, "TABLE_2_2"),
                IndexSpec("json1Field2", SmartStore.Type.json1, "json_extract(soup, '$.json1Field2')"),
                IndexSpec("ftsField2", SmartStore.Type.full_text, "TABLE_2_4")
        ))
    }

    @Test
    @Throws(JSONException::class)
    fun testSetupUserStoreFromDefaultConfig() {
        Assert.assertFalse(userStore.hasSoup("userSoup1"))
        Assert.assertFalse(userStore.hasSoup("userSoup2"))

        // Setting up soup
        sdkManager.setupUserStoreFromDefaultConfig()

        // Checking smartstore
        Assert.assertTrue(userStore.hasSoup("userSoup1"))
        Assert.assertTrue(userStore.hasSoup("userSoup2"))
        val actualSoupNames = userStore.getAllSoupNames()
        Assert.assertEquals("Wrong soups found", 2, actualSoupNames.size)
        Assert.assertTrue(actualSoupNames.contains("userSoup1"))
        Assert.assertTrue(actualSoupNames.contains("userSoup2"))

        // Checking first soup in details
        checkIndexSpecs("userSoup1", arrayOf(
                IndexSpec("stringField1", SmartStore.Type.string, "TABLE_1_0"),
                IndexSpec("integerField1", SmartStore.Type.integer, "TABLE_1_1"),
                IndexSpec("floatingField1", SmartStore.Type.floating, "TABLE_1_2"),
                IndexSpec("json1Field1", SmartStore.Type.json1, "json_extract(soup, '$.json1Field1')"),
                IndexSpec("ftsField1", SmartStore.Type.full_text, "TABLE_1_4")
        ))

        // Checking second soup in details
        checkIndexSpecs("userSoup2", arrayOf(
                IndexSpec("stringField2", SmartStore.Type.string, "TABLE_2_0"),
                IndexSpec("integerField2", SmartStore.Type.integer, "TABLE_2_1"),
                IndexSpec("floatingField2", SmartStore.Type.floating, "TABLE_2_2"),
                IndexSpec("json1Field2", SmartStore.Type.json1, "json_extract(soup, '$.json1Field2')"),
                IndexSpec("ftsField2", SmartStore.Type.full_text, "TABLE_2_4")
        ))
    }

    /**
     * Mock version of SmartStoreSDKManager - that gets passed the current user store in init()
     * That way we don't actually have to setup a user account
     */
    private class SmartStoreSDKTestManager(
        context: Context,
        private val userStore: SmartStore
    ) : SmartStoreSDKManager(context, MainActivity::class.java, LoginActivity::class.java, null) {

        fun getTestSmartStore(): SmartStore {
            return userStore
        }

        companion object {
            // We don't want to be using INSTANCE defined in SmartStoreSDKManager
            // Otherwise tests in other suites could fail after we call resetInstance(...)
            private var TEST_INSTANCE: SmartStoreSDKTestManager? = null

            /**
             * Initializes this component.
             *  @param context      Application context.
             *  @param userStore    The store to return from getSmartStore()
             */
            fun init(context: Context, userStore: SmartStore) {
                if (TEST_INSTANCE == null) {
                    TEST_INSTANCE = SmartStoreSDKTestManager(context, userStore)
                }
                initInternal(context)
            }

            /**
             * Returns a singleton instance of this class.
             *
             * @return Singleton instance of SmartStoreSDKManager.
             */
            fun getInstance(): SmartStoreSDKManager {
                return TEST_INSTANCE
                    ?: throw RuntimeException("Applications need to call SmartStoreSDKManager.init() first.")
            }
        }
    }
}
