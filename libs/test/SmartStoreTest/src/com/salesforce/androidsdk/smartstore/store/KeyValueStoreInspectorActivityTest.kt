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

import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.smartstore.R
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.smartstore.ui.KeyValueStoreInspectorActivity
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/**
 * Tests for KeyValueStoreInspectorActivity
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class KeyValueStoreInspectorActivityTest {
    private val STORE_1 = "store1"
    private val STORE_2 = "store2"
    private val KEY_1 = "firstKey"
    private val KEY_2 = "secondKey"
    private val KEY_3 = "keyThree"
    private val VALUE_1 = "this is a value in "
    private val VALUE_2 = "this is a different value in "
    private val VALUE_3 = "this is a third value in "
    private val GLOBAL_STORE_TEXT = KeyValueStoreInspectorActivity.GLOBAL_STORE

    var activityScenario: ActivityScenario<KeyValueStoreInspectorActivity>? = null
    var keyValueStoreInspectorActivity: KeyValueStoreInspectorActivity? = null

    // Dismissing system dialog if shown
    // See https://stackoverflow.com/questions/39457305/android-testing-waited-for-the-root-of-the-view-hierarchy-to-have-window-focus
    private fun dismissSystemDialog() {
        val device = UiDevice.getInstance(getInstrumentation())
        val okButton = device.findObject(UiSelector().textContains("OK"))
        if (okButton.exists()) {
            try {
                okButton.click()
            } catch (e: UiObjectNotFoundException) {
                // Nothing to do
            }
        }
    }

    @Before
    fun setUp() {
        EventBuilderHelper.enableDisable(false)
        SmartStoreSDKManager.getInstance().removeAllGlobalKeyValueStores()
    }

    @After
    fun tearDown() {
        activityScenario?.close()
        keyValueStoreInspectorActivity = null
    }

    private fun launchActivityBlocking() {
        activityScenario = ActivityScenario.launch(KeyValueStoreInspectorActivity::class.java)
        val latch = CountDownLatch(1)
        activityScenario?.onActivity { activity ->
            keyValueStoreInspectorActivity = activity
            latch.countDown()
        }
        try {
            latch.await() // Wait for the activity to be set
        } catch (e: InterruptedException) {
            Assert.fail("Failed to launch activity")
        }

        dismissSystemDialog()
    }

    /**
     * Test no KeyValueEncryptedFileStore
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testNoStore() {
        launchActivityBlocking()
        Assert.assertEquals("Incorrect message for no store.",
                KeyValueStoreInspectorActivity.NO_STORE, getCurrentStoreName())
        Assert.assertFalse("Get Value Button should be disabled.", isGetValueButtonEnabled())
    }

    /**
     * Test single KeyValueEncryptedFileStore
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testSingleStore() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        Assert.assertEquals("Wrong Store name shown.",
                STORE_1 + GLOBAL_STORE_TEXT, getCurrentStoreName())
        Assert.assertTrue("Get Value Button should be enabled.", isGetValueButtonEnabled())
        writeKey(KEY_1)
        tapGetValueButton()
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1)
        writeKey(KEY_2)
        tapGetValueButton()
        checkKeyValuePairShown(KEY_2, VALUE_2 + STORE_1)
    }

    /**
     * Test changing stores.
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testChangingStores() {
        createKeyValueStore(STORE_1)
        createKeyValueStore(STORE_2)
        launchActivityBlocking()
        Assert.assertEquals("Wrong Store name shown.",
                STORE_1 + GLOBAL_STORE_TEXT, getCurrentStoreName())
        changeStore(STORE_2)
        Assert.assertEquals("Wrong Store name shown.",
                STORE_2 + GLOBAL_STORE_TEXT, getCurrentStoreName())
        writeKey(KEY_1)
        tapGetValueButton()
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_2)
    }

    /**
     * Test key not found.
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testKeyNotFound() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        writeKey("badKey")
        tapGetValueButton()
        checkForKeyNotFoundDialog()
    }

    /**
     * Test * query
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testStarQuery() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        writeKey("*")
        tapGetValueButton()
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1)
        checkKeyValuePairShown(KEY_2, VALUE_2 + STORE_1)
        checkKeyValuePairShown(KEY_3, VALUE_3 + STORE_1)
    }

    /**
     * Test query ending with * matching one
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testQueryEndingWithStarMatchingOne() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        writeKey("f*")
        tapGetValueButton()
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1)
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1)
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1)
    }

    /**
     * Test query ending with * matching none
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testQueryEndingWithStarMatchingNone() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        writeKey("x*")
        tapGetValueButton()
        checkForKeyNotFoundDialog()
        checkKeyValuePairNotShown(KEY_1, VALUE_1 + STORE_1)
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1)
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1)
    }

    /**
     * Test query starting with * matching two
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testQueryStartingWithStarMatchingTwo() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        writeKey("*Key")
        tapGetValueButton()
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1)
        checkKeyValuePairShown(KEY_2, VALUE_2 + STORE_1)
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1)
    }

    /**
     * Test query ending with * matching two
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testQueryEndingWithStarMatchingTwo() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        writeKey("*x")
        tapGetValueButton()
        checkForKeyNotFoundDialog()
        checkKeyValuePairNotShown(KEY_1, VALUE_1 + STORE_1)
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1)
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1)
    }

    /**
     * Test query starting and ending with * matching two
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testQueryStartingAndEndingWithStarMatchingTwo() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        writeKey("*r*")
        tapGetValueButton()
        checkKeyValuePairShown(KEY_1, VALUE_1 + STORE_1)
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1)
        checkKeyValuePairShown(KEY_3, VALUE_3 + STORE_1)
    }

    /**
     * Test query starting and ending with * matching none
     */
    @Ignore("Suspected production bug: KeyValueStoreInspectorActivity fails to reach RESUMED state — NoActivityResumedException at runtime")
    @Test
    fun testQueryStartingAndEndingWithStarMatchingNone() {
        createKeyValueStore(STORE_1)
        launchActivityBlocking()
        writeKey("*zz*")
        tapGetValueButton()
        checkForKeyNotFoundDialog()
        checkKeyValuePairNotShown(KEY_1, VALUE_1 + STORE_1)
        checkKeyValuePairNotShown(KEY_2, VALUE_2 + STORE_1)
        checkKeyValuePairNotShown(KEY_3, VALUE_3 + STORE_1)
    }

    private fun createKeyValueStore(storeName: String) {
        val store = SmartStoreSDKManager.getInstance().getGlobalKeyValueStore(storeName)
        store.saveValue(KEY_1, VALUE_1 + storeName)
        store.saveValue(KEY_2, VALUE_2 + storeName)
        store.saveValue(KEY_3, VALUE_3 + storeName)
    }

    private fun getCurrentStoreName(): String {
        val textView = keyValueStoreInspectorActivity
                ?.findViewById<AutoCompleteTextView>(R.id.sf__inspector_stores_dropdown)
        return textView?.text.toString()
    }

    private fun isGetValueButtonEnabled(): Boolean {
        val getValueButton = keyValueStoreInspectorActivity
                ?.findViewById<Button>(R.id.sf__inspector_get_value_button)
        return getValueButton?.isEnabled ?: false
    }

    private fun writeKey(key: String) {
        onView(withId(R.id.sf__inspector_key_text)).perform(replaceText(key))
    }

    private fun tapGetValueButton() {
        onView(withId(R.id.sf__inspector_get_value_button)).perform(click())
    }

    private fun checkKeyValuePairShown(key: String, value: String) {
        onView(allOf(withId(R.id.sf__inspector_key), withText(key))).check(matches(isDisplayed()))
        onView(allOf(withId(R.id.sf__inspector_value), withText(value))).check(matches(isDisplayed()))
    }

    private fun checkKeyValuePairNotShown(key: String, value: String) {
        onView(allOf(withId(R.id.sf__inspector_key), withText(key))).check(doesNotExist())
        onView(allOf(withId(R.id.sf__inspector_value), withText(value))).check(doesNotExist())
    }

    private fun changeStore(newStore: String) {
        onView(withId(R.id.sf__inspector_stores_dropdown)).perform(click())
        onView(withText(newStore + GLOBAL_STORE_TEXT)).inRoot(RootMatchers.isPlatformPopup()).perform(click())
    }

    private fun checkForKeyNotFoundDialog() {
        onView(withText(KeyValueStoreInspectorActivity.ERROR_DIALOG_TITLE)).check(matches(isDisplayed()))
        onView(withText(KeyValueStoreInspectorActivity.ERROR_DIALOG_MESSAGE)).check(matches(isDisplayed()))
    }
}
