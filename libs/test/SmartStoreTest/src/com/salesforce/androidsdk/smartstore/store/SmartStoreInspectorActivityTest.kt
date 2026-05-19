/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.Manifest
import android.content.Context
import android.widget.MultiAutoCompleteTextView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.smartstore.R
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/**
 * Tests for SmartStoreInspectorActivity
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class SmartStoreInspectorActivityTest {
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    companion object {
        private const val TEST_SOUP = "test_soup"
        private const val OTHER_TEST_SOUP = "other_test_soup"
        private const val NUMBER_ROWS_TEST_SOUP = 5
        private const val NUMBER_ROWS_OTHER_TEST_SOUP = 10
    }

    private lateinit var targetContext: Context
    private lateinit var store: SmartStore

    var activityScenario: ActivityScenario<SmartStoreInspectorActivity>? = null
    var smartStoreInspectorActivity: SmartStoreInspectorActivity? = null

    private fun dismissSystemDialog() {
        val device = UiDevice.getInstance(getInstrumentation())
        val okButton = device.findObject(UiSelector().textContains("OK"))
        try { okButton.click() } catch (e: UiObjectNotFoundException) { }
    }

    @Before
    fun setUp() {
        EventBuilderHelper.enableDisable(false)
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        createStore()
        createSoups()
        populateSoup(TEST_SOUP, NUMBER_ROWS_TEST_SOUP)
        populateSoup(OTHER_TEST_SOUP, NUMBER_ROWS_OTHER_TEST_SOUP)
        launchActivityBlocking()
        checkInspectorIsReset()
    }

    @After
    fun tearDown() {
        activityScenario?.close()
        smartStoreInspectorActivity = null
    }

    @Test
    fun testClickingClear() {
        clickButton(R.id.sf__inspector_indices_button)
        Assert.assertNotNull(smartStoreInspectorActivity!!.lastResults)
        clickButton(R.id.sf__inspector_clear_button)
        checkInspectorIsReset()
    }

    @Test
    fun testClickingSoups() {
        clickButton(R.id.sf__inspector_soups_button)
        checkInspectorState(
            "select 'other_test_soup', count(*) from {other_test_soup} union select 'test_soup', count(*) from {test_soup}",
            "", "", null, null,
            "[[\"other_test_soup\",10],[\"test_soup\",5]]")
    }

    @Test
    fun testClickingIndices() {
        clickButton(R.id.sf__inspector_indices_button)
        checkInspectorState(
            "select soupName, path, columnType from soup_index_map", "",
            "", null, null,
            "[[\"test_soup\",\"key\",\"string\"],[\"other_test_soup\",\"key\",\"string\"]]")
    }

    @Test
    fun testClickingRunWithoutQuery() {
        clickButton(R.id.sf__inspector_run_button)
        checkInspectorState("", "", "", null, "No query specified", null)
    }

    @Test
    fun testClickingRunWithInvalidQuery() {
        val query = "SELECT {test_soup:key} FROM {test_soup2}"
        setText(R.id.sf__inspector_query_text, query)
        clickButton(R.id.sf__inspector_run_button)
        checkInspectorState(query, "", "", "SmartSqlException", "Unknown soup test_soup2", null)
    }

    @Test
    fun testClickingRunWithValidQueryNoResults() {
        val query = "SELECT {test_soup:key} FROM {test_soup} WHERE {test_soup:key} == 'non-existent-key'"
        setText(R.id.sf__inspector_query_text, query)
        clickButton(R.id.sf__inspector_run_button)
        checkInspectorState(query, "", "", null, "No rows returned", "[]")
    }

    @Test
    fun testClickingRunWithValidQuery() {
        val query = "SELECT {test_soup:key} FROM {test_soup} WHERE {test_soup:key} == 'k_test_soup_1'"
        setText(R.id.sf__inspector_query_text, query)
        clickButton(R.id.sf__inspector_run_button)
        checkInspectorState(query, "", "", null, null, "[[\"k_test_soup_1\"]]")
    }

    @Test
    fun testClickingRunWithValidQueryAndPageSize() {
        val query = "SELECT {test_soup:key} FROM {test_soup} ORDER BY {test_soup:key}"
        val pageSize = "2"
        setText(R.id.sf__inspector_query_text, query)
        setText(R.id.sf__inspector_pagesize_text, pageSize)
        clickButton(R.id.sf__inspector_run_button)
        checkInspectorState(query, pageSize, "", null, null, "[[\"k_test_soup_0\"],[\"k_test_soup_1\"]]")
    }

    @Test
    fun testClickingRunWithValidQueryAndPageSizeAndPageIndex() {
        val query = "SELECT {test_soup:key} FROM {test_soup} ORDER BY {test_soup:key}"
        val pageSize = "2"
        val pageIndex = "1"
        setText(R.id.sf__inspector_query_text, query)
        setText(R.id.sf__inspector_pagesize_text, pageSize)
        setText(R.id.sf__inspector_pageindex_text, pageIndex)
        clickButton(R.id.sf__inspector_run_button)
        checkInspectorState(query, pageSize, pageIndex, null, null, "[[\"k_test_soup_2\"],[\"k_test_soup_3\"]]")
    }

    @Test
    fun testAutoComplete() {
        val queryTextView: MultiAutoCompleteTextView = smartStoreInspectorActivity!!.findViewById(R.id.sf__inspector_query_text)
        val adapter = queryTextView.adapter
        val values = mutableSetOf<String>()
        for (i in 0 until adapter.count) { values.add(adapter.getItem(i) as String) }
        val expectedValues = arrayOf("select", "from", "where", "group by",
            "order by", "{test_soup}", "{test_soup:key}",
            "{test_soup:_soupEntryId}", "{test_soup:_soup}",
            "{test_soup:_soupLastModifiedDate}", "{other_test_soup}",
            "{other_test_soup:key}", "{other_test_soup:_soupEntryId}",
            "{other_test_soup:_soup}",
            "{other_test_soup:_soupLastModifiedDate}")
        for (expectedValue in expectedValues) {
            Assert.assertTrue("Autocomplete should offer $expectedValue", values.contains(expectedValue))
        }
    }

    private fun createStore() {
        val dbOpenHelper: SQLiteOpenHelper = DBOpenHelper.getOpenHelper("", targetContext, null)
        DBHelper.getInstance(dbOpenHelper.writableDatabase).clearMemoryCache()
        store = SmartStore(dbOpenHelper)
        store.dropAllSoups()
    }

    private fun createSoups() {
        for (soupName in arrayOf(TEST_SOUP, OTHER_TEST_SOUP)) {
            Assert.assertFalse("Soup $soupName should not exist", store.hasSoup(soupName))
            store.registerSoup(soupName, arrayOf(IndexSpec("key", Type.string)))
            Assert.assertTrue("Soup $soupName should now exist", store.hasSoup(soupName))
        }
    }

    private fun populateSoup(soupName: String, numberRows: Int) {
        for (i in 0 until numberRows) {
            val soupElt = JSONObject("{'key':'k_${soupName}_$i', 'value':'v_${soupName}_$i'}")
            store.create(soupName, soupElt)
        }
    }

    private fun launchActivityBlocking() {
        activityScenario = ActivityScenario.launch(SmartStoreInspectorActivity::class.java)
        val latch = CountDownLatch(1)
        activityScenario!!.onActivity { activity ->
            smartStoreInspectorActivity = activity
            latch.countDown()
        }
        try { latch.await() } catch (e: InterruptedException) { Assert.fail("Failed to launch activity") }
        dismissSystemDialog()
    }

    private fun clickButton(id: Int) { onView(withId(id)).perform(click()) }

    private fun checkText(message: String, id: Int, expectedString: String) {
        val view: TextView = smartStoreInspectorActivity!!.findViewById(id)
        Assert.assertNotNull("TextView not found", view)
        Assert.assertEquals(message, expectedString, view.text.toString())
    }

    private fun checkInspectorIsReset() { checkInspectorState("", "", "", null, null, null) }

    private fun checkInspectorState(query: String, pageSize: String, pageIndex: String, expectedAlertTitle: String?, expectedAlertMessageSubstring: String?, expectedResultsAsString: String?) {
        checkText("Wrong query", R.id.sf__inspector_query_text, query)
        checkText("Wrong page size", R.id.sf__inspector_pagesize_text, pageSize)
        checkText("Wrong page index", R.id.sf__inspector_pageindex_text, pageIndex)
        if (expectedResultsAsString == null) {
            Assert.assertNull("Wrong results", smartStoreInspectorActivity!!.lastResults)
        } else {
            Assert.assertEquals("Wrong results", expectedResultsAsString, smartStoreInspectorActivity!!.lastResults.toString())
        }
        Assert.assertEquals("Wrong alert title", expectedAlertTitle, smartStoreInspectorActivity!!.lastAlertTitle)
        val actualAlertMessage = smartStoreInspectorActivity!!.lastAlertMessage
        if (expectedAlertMessageSubstring == null || expectedAlertMessageSubstring.isEmpty()) {
            Assert.assertEquals("Wrong alert message", expectedAlertMessageSubstring, actualAlertMessage)
        } else {
            Assert.assertTrue("Wrong alert message", actualAlertMessage!!.contains(expectedAlertMessageSubstring))
        }
    }

    private fun setText(textViewId: Int, text: String) {
        try {
            onView(withId(textViewId)).perform(replaceText(text), closeSoftKeyboard())
        } catch (t: Throwable) {
            Assert.fail("Failed to set text $text")
        }
    }
}
