/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Tests for AuthConfigUtil.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class AuthConfigUtilTest {

    companion object {
        private const val MY_DOMAIN_ENDPOINT = "https://mobilesdk.my.salesforce.com"
        private const val ALTERNATE_MY_DOMAIN_ENDPOINT = "https://powerofus.salesforce.com"
        private const val LOGIN_URL_FOR_ALTERNATE_MY_DOMAIN = "https://powerofus.salesforce.com/s/login"
        private const val SANDBOX_ENDPOINT = "https://test.salesforce.com"
        private const val FORWARD_SLASH = "/"
    }

    private class TestBroadcastReceiver : BroadcastReceiver() {
        private val intentFuture = CompletableFuture<Intent>()

        override fun onReceive(context: Context, intent: Intent) {
            intentFuture.complete(intent)
        }

        fun getIntent(): Future<Intent> {
            return intentFuture
        }
    }

    @Test
    fun testGetAuthConfigWithoutForwardSlash() {
        val authConfig = AuthConfigUtil.getMyDomainAuthConfig(MY_DOMAIN_ENDPOINT)
        Assert.assertNotNull("Auth config should not be null", authConfig)
        Assert.assertNotNull("Auth config JSON should not be null", authConfig!!.authConfig)
    }

    @Test
    fun testGetAuthConfigWithForwardSlash() {
        val authConfig = AuthConfigUtil.getMyDomainAuthConfig(MY_DOMAIN_ENDPOINT + FORWARD_SLASH)
        Assert.assertNotNull("Auth config should not be null", authConfig)
        Assert.assertNotNull("Auth config JSON should not be null", authConfig!!.authConfig)
    }

    @Test
    fun testBrowserBasedLoginEnabled() {
        val authConfig = AuthConfigUtil.getMyDomainAuthConfig(MY_DOMAIN_ENDPOINT)
        Assert.assertNotNull("Auth config should not be null", authConfig)
        Assert.assertNotNull("Auth config JSON should not be null", authConfig!!.authConfig)
        Assert.assertTrue("Browser based login should be enabled", authConfig.isBrowserLoginEnabled)
    }

    @Test
    fun testGetSSOUrls() {
        val authConfig = AuthConfigUtil.getMyDomainAuthConfig(MY_DOMAIN_ENDPOINT)
        Assert.assertNotNull("Auth config should not be null", authConfig)
        Assert.assertNotNull("Auth config JSON should not be null", authConfig!!.authConfig)
        Assert.assertNotNull("SSO URLs should not be null", authConfig.ssoUrls)
        Assert.assertEquals("SSO URLs should have 2 valid entries", 1, authConfig.ssoUrls!!.size)
    }

    @Test
    fun testGetLoginPageUrl() {
        val authConfig = AuthConfigUtil.getMyDomainAuthConfig(ALTERNATE_MY_DOMAIN_ENDPOINT)
        Assert.assertNotNull("Auth config should not be null", authConfig)
        Assert.assertNotNull("Auth config JSON should not be null", authConfig!!.authConfig)
        Assert.assertNotNull("Login page URL should not be null", authConfig.loginPageUrl)
        Assert.assertTrue(
            "Login page URL should contain correct URL",
            authConfig.loginPageUrl!!.contains(LOGIN_URL_FOR_ALTERNATE_MY_DOMAIN)
        )
    }

    @Test
    fun testGetNoAuthConfig() {
        val authConfig = AuthConfigUtil.getMyDomainAuthConfig(SANDBOX_ENDPOINT)
        Assert.assertNull("Auth config should be null", authConfig)
    }

    @Test(timeout = 30_000)
    fun testBroadcastSucceeds() {
        testBroadcast(MY_DOMAIN_ENDPOINT, true)
    }

    @Test(timeout = 30_000)
    fun testBroadcastFails() {
        testBroadcast(SANDBOX_ENDPOINT, false)
    }

    private fun testBroadcast(endpoint: String, expected: Boolean) {
        // Receive the broadcast on a background HandlerThread rather than the main thread.
        // Other tests in this shard launch Activities on the main thread; by the time
        // sendBroadcast is invoked, the main looper can be backed up enough that the
        // receiver's onReceive doesn't run before the test's timeout, even though the
        // broadcast was dispatched. A dedicated Handler decouples broadcast delivery
        // from main-thread saturation.
        val handlerThread = HandlerThread("AuthConfigUtilTest-receiver")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)
        val receiver = TestBroadcastReceiver()
        ContextCompat.registerReceiver(
            SalesforceSDKManager.getInstance().appContext, receiver,
            IntentFilter(AuthConfigUtil.AUTH_CONFIG_COMPLETE_INTENT_ACTION), null,
            handler, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        try {
            AuthConfigUtil.getMyDomainAuthConfig(endpoint)

            val intent = receiver.getIntent().get(20, TimeUnit.SECONDS)
            Assert.assertTrue("The intent extra should be set", intent.hasExtra(AuthConfigUtil.WAS_REQUEST_SUCCESSFUL_EXTRA))

            val extra = intent.getBooleanExtra(AuthConfigUtil.WAS_REQUEST_SUCCESSFUL_EXTRA, !expected)
            if (expected) {
                Assert.assertTrue("The auth config request should succeed", extra)
            } else {
                Assert.assertFalse("The auth config request should fail", extra)
            }
        } finally {
            SalesforceSDKManager.getInstance().appContext.unregisterReceiver(receiver)
            handlerThread.quitSafely()
        }
    }
}
