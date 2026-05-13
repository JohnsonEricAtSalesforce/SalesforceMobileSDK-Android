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
package com.salesforce.androidsdk.app

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.MainActivity
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountManagerTest
import com.salesforce.androidsdk.accounts.UserAccountTest
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager
import com.salesforce.androidsdk.ui.LoginActivity
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A class that contains tests for functionality exposed in SalesforceSDKManager.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceSDKManagerTest {

    /**
     * Test for setting the analytics app name before 'SalesforceSDKManager.init()'
     * has been called.
     */
    @Test
    fun testOverrideAiltnAppNameBeforeSDKManagerInit() {
        SalesforceSDKManager.ailtnAppName = TEST_APP_NAME
        SalesforceSDKTestManager.init(getInstrumentation().targetContext, MainActivity::class.java)
        SalesforceSDKTestManager.getInstance().isTestRun = true
        compareAiltnAppNames(TEST_APP_NAME)
    }

    /**
     * Test for setting the analytics app name after 'SalesforceSDKManager.init()'
     * has been called.
     */
    @Test
    fun testOverrideAiltnAppNameAfterSDKManagerInit() {
        SalesforceSDKTestManager.init(getInstrumentation().targetContext, MainActivity::class.java)
        SalesforceSDKManager.ailtnAppName = TEST_APP_NAME
        SalesforceSDKTestManager.getInstance().isTestRun = true
        compareAiltnAppNames(TEST_APP_NAME)
    }

    /**
     * Test for default analytics app name.
     */
    @Test
    fun testDefaultAiltnAppName() {
        SalesforceSDKTestManager.init(getInstrumentation().targetContext, MainActivity::class.java)
        SalesforceSDKTestManager.getInstance().isTestRun = true
        compareAiltnAppNames(getDefaultAppName())
    }

    /**
     * Test for setting an invalid analytics app name.
     */
    @Test
    fun testOverrideInvalidAiltnAppName() {
        SalesforceSDKTestManager.init(getInstrumentation().targetContext, MainActivity::class.java)
        SalesforceSDKManager.ailtnAppName = null
        SalesforceSDKTestManager.getInstance().isTestRun = true
        compareAiltnAppNames(getDefaultAppName())
    }

    /**
     * Test the default theme value.
     */
    @Test
    fun testDefaultTheme() {
        val currentNightMode = getInstrumentation().context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkTheme = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        SalesforceSDKTestManager.init(getInstrumentation().targetContext, MainActivity::class.java)
        Assert.assertEquals(
            "Default theme does not match OS value.", isDarkTheme,
            SalesforceSDKTestManager.getInstance().isDarkTheme
        )
    }

    /**
     * Test setting dark theme.
     */
    @Test
    fun testSetDarkTheme() {
        SalesforceSDKTestManager.init(getInstrumentation().targetContext, MainActivity::class.java)
        SalesforceSDKTestManager.getInstance().theme = SalesforceSDKManager.Theme.DARK
        Assert.assertTrue(
            "Dark theme not successfully set.",
            SalesforceSDKTestManager.getInstance().isDarkTheme
        )
    }

    /**
     * Test changing theme multiple times.
     */
    @Test
    fun testChangingTheme() {
        SalesforceSDKTestManager.init(getInstrumentation().targetContext, MainActivity::class.java)
        SalesforceSDKTestManager.getInstance().theme = SalesforceSDKManager.Theme.DARK
        SalesforceSDKTestManager.getInstance().theme = SalesforceSDKManager.Theme.LIGHT
        Assert.assertFalse(
            "Latest theme value not returned.",
            SalesforceSDKTestManager.getInstance().isDarkTheme
        )
    }

    private fun compareAiltnAppNames(expectedAppName: String?) {
        val userAccMgr = SalesforceSDKTestManager.getInstance().userAccountManager
        val targetContext = getInstrumentation().targetContext
        UserAccountManager.getInstance().createAccount(UserAccountTest.createTestAccount())
        val accMgr = AccountManager.get(targetContext)
        val curUser = userAccMgr.currentUser
        Assert.assertNotNull("Current user should NOT be null", curUser)
        SalesforceAnalyticsManager.reset(curUser)
        val analyticsManager = SalesforceAnalyticsManager.getInstance(userAccMgr.currentUser)
        Assert.assertNotNull("SalesforceAnalyticsManager instance should NOT be null", analyticsManager)
        val manager = analyticsManager.analyticsManager
        Assert.assertNotNull("AnalyticsManager instance should NOT be null", manager)
        val deviceAppAttributes = analyticsManager.analyticsManager.deviceAppAttributes
        Assert.assertNotNull("Device attributes should NOT be null", deviceAppAttributes)
        val ailtnAppName = deviceAppAttributes!!.appName
        Assert.assertEquals("DeviceAppAttributes - App names do NOT match", expectedAppName, ailtnAppName)
        Assert.assertEquals("SalesforceSDKManager - App names do NOT match", expectedAppName, SalesforceSDKManager.ailtnAppName)
        SalesforceAnalyticsManager.reset(curUser)
        for (acc in accMgr.getAccountsByType(UserAccountManagerTest.TEST_ACCOUNT_TYPE)) {
            accMgr.removeAccountExplicitly(acc)
        }
        SalesforceSDKManager.ailtnAppName = null
        SalesforceSDKTestManager.resetInstance()
    }

    private fun getDefaultAppName(): String? {
        var ailtnAppName: String? = null
        try {
            val targetContext = getInstrumentation().targetContext
            val packageInfo = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0)
            ailtnAppName = targetContext.getString(packageInfo.applicationInfo!!.labelRes)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package not found", e)
        }
        return ailtnAppName
    }

    /**
     * Mock version of SalesforceSDKManager.
     */
    private class SalesforceSDKTestManager
    /**
     * Protected constructor.
     *
     * @param context Application context.
     * @param mainActivity Activity to be launched after the login flow.
     * @param loginActivity Login activity.
     */
    protected constructor(
        context: Context,
        mainActivity: Class<out Activity>,
        loginActivity: Class<out Activity>
    ) : SalesforceSDKManager(context, mainActivity, loginActivity) {

        companion object {
            // We don't want to be using INSTANCE defined in SalesforceSDKManager
            // Otherwise tests in other suites could fail after we call resetInstance(...)
            private var TEST_INSTANCE: SalesforceSDKTestManager? = null

            /**
             * Initializes this component.
             *
             * @param context Application context.
             * @param mainActivity Activity to be launched after the login flow.
             */
            fun init(context: Context, mainActivity: Class<out Activity>) {
                if (TEST_INSTANCE == null) {
                    TEST_INSTANCE = SalesforceSDKTestManager(context, mainActivity, LoginActivity::class.java)
                }
                initInternal(context)
            }

            /**
             * Returns a singleton instance of this class.
             *
             * @return Singleton instance of SalesforceSDKManager.
             */
            fun getInstance(): SalesforceSDKManager {
                return TEST_INSTANCE
                    ?: throw RuntimeException("Applications need to call SalesforceSDKManager.init() first.")
            }

            /**
             * Resets the current instance being used.
             * This is meant to be used ONLY by tests.
             */
            fun resetInstance() {
                TEST_INSTANCE = null
            }
        }
    }

    companion object {
        private const val TAG = "SFSDKManagerTest"
        private const val TEST_APP_NAME = "OverridenAppName"
    }
}
