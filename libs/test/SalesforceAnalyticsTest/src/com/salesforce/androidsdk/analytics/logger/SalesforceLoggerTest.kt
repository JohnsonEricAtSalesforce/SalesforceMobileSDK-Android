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
package com.salesforce.androidsdk.analytics.logger

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

/**
 * Tests for SalesforceLogger.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SalesforceLoggerTest {

    private lateinit var targetContext: Context
    private val random = Random()

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        SalesforceLogger.flushComponents()
        SalesforceLogger.resetLoggerPrefs(targetContext)
        val components = SalesforceLogger.getComponents()
        Assert.assertNull("No components should be returned", components)
    }

    @After
    fun tearDown() {
        SalesforceLogger.flushComponents()
        SalesforceLogger.resetLoggerPrefs(targetContext)
    }

    /**
     * Test for adding a single component.
     */
    @Test
    fun testAddSingleComponent() {
        val logger = SalesforceLogger.getLogger(TEST_COMPONENT_1, targetContext)
        Assert.assertNotNull("SalesforceLogger instance should not be null", logger)
        val components = SalesforceLogger.getComponents()
        Assert.assertEquals("Number of components should be 1", 1, components?.size)
    }

    /**
     * Test for adding multiple components.
     */
    @Test
    fun testAddMultipleComponents() {
        var logger = SalesforceLogger.getLogger(TEST_COMPONENT_1, targetContext)
        Assert.assertNotNull("SalesforceLogger instance should not be null", logger)
        logger = SalesforceLogger.getLogger(TEST_COMPONENT_2, targetContext)
        Assert.assertNotNull("SalesforceLogger instance should not be null", logger)
        val components = SalesforceLogger.getComponents()
        Assert.assertEquals("Number of components should be 2", 2, components?.size)
        Assert.assertTrue("Component should be present in results", components?.contains(TEST_COMPONENT_1) == true)
        Assert.assertTrue("Component should be present in results", components?.contains(TEST_COMPONENT_2) == true)
    }

    /**
     * Test for setting log level.
     */
    @Test
    fun testSetLogLevel() {
        val logger = SalesforceLogger.getLogger(TEST_COMPONENT_1, targetContext)
        Assert.assertNotNull("SalesforceLogger instance should not be null", logger)
        var logLevel = logger.getLogLevel()
        Assert.assertNotSame("Log levels should not be same", SalesforceLogger.Level.VERBOSE, logLevel)
        logger.setLogLevel(SalesforceLogger.Level.VERBOSE)
        logLevel = logger.getLogLevel()
        Assert.assertEquals("Log levels should be the same", SalesforceLogger.Level.VERBOSE, logLevel)
    }

    /**
     * Test that null input returns null.
     */
    @Test
    fun testRedactNull() {
        Assert.assertNull("Redact of null should return null", SalesforceLogger.redact(null))
    }

    /**
     * Test that an empty string is unchanged.
     */
    @Test
    fun testRedactEmptyString() {
        Assert.assertEquals("Empty string should be unchanged", "", SalesforceLogger.redact(""))
    }

    /**
     * Test that a message without sensitive data is unchanged.
     */
    @Test
    fun testRedactNonSensitiveMessage() {
        val message = "User logged in successfully"
        Assert.assertEquals("Non-sensitive message should be unchanged", message, SalesforceLogger.redact(message))
    }

    /**
     * Test that access_token is redacted in JSON.
     */
    @Test
    fun testRedactAccessToken() {
        val value = randomString(23)
        val input = "{\"access_token\":\"$value\"}"
        val expected = "{\"access_token\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("access_token should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that refresh_token is redacted in JSON.
     */
    @Test
    fun testRedactRefreshToken() {
        val value = randomString(43)
        val input = "{\"refresh_token\":\"$value\"}"
        val expected = "{\"refresh_token\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("refresh_token should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that id_token is redacted in JSON.
     */
    @Test
    fun testRedactIdToken() {
        val value = randomString(51)
        val input = "{\"id_token\":\"$value\"}"
        val expected = "{\"id_token\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("id_token should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that csrf_token is redacted in JSON.
     */
    @Test
    fun testRedactCsrfToken() {
        val value = randomString(10)
        val input = "{\"csrf_token\":\"$value\"}"
        val expected = "{\"csrf_token\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("csrf_token should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that sid is redacted in JSON.
     */
    @Test
    fun testRedactSid() {
        val value = randomString(17)
        val input = "{\"sid\":\"$value\"}"
        val expected = "{\"sid\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("sid should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that lightning_sid is redacted in JSON.
     */
    @Test
    fun testRedactLightningSid() {
        val value = randomString(17)
        val input = "{\"lightning_sid\":\"$value\"}"
        val expected = "{\"lightning_sid\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("lightning_sid should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that visualforce_sid is redacted in JSON.
     */
    @Test
    fun testRedactVisualforceSid() {
        val value = randomString(10)
        val input = "{\"visualforce_sid\":\"$value\"}"
        val expected = "{\"visualforce_sid\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("visualforce_sid should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that content_sid is redacted in JSON.
     */
    @Test
    fun testRedactContentSid() {
        val value = randomString(15)
        val input = "{\"content_sid\":\"$value\"}"
        val expected = "{\"content_sid\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("content_sid should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that parent_sid is redacted in JSON.
     */
    @Test
    fun testRedactParentSid() {
        val value = randomString(14)
        val input = "{\"parent_sid\":\"$value\"}"
        val expected = "{\"parent_sid\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("parent_sid should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that beacon_child_consumer_secret is redacted in JSON.
     */
    @Test
    fun testRedactBeaconChildConsumerSecret() {
        val value = randomString(11)
        val input = "{\"beacon_child_consumer_secret\":\"$value\"}"
        val expected = "{\"beacon_child_consumer_secret\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("beacon_child_consumer_secret should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that multiple sensitive keys in one JSON message are all redacted.
     */
    @Test
    fun testRedactMultipleJsonKeys() {
        val accessValue = randomString(8)
        val refreshValue = randomString(10)
        val input = "{\"access_token\":\"$accessValue\",\"refresh_token\":\"$refreshValue\",\"instance_url\":\"https://na1.salesforce.com\"}"
        val expected = "{\"access_token\":\"${expectedMask(accessValue)}\",\"refresh_token\":\"${expectedMask(refreshValue)}\",\"instance_url\":\"https://na1.salesforce.com\"}"
        Assert.assertEquals("Multiple sensitive keys should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that JSON keys with spaces around colon are redacted.
     */
    @Test
    fun testRedactJsonWithSpaces() {
        val value = randomString(8)
        val input = "{\"access_token\" : \"$value\"}"
        val expected = "{\"access_token\" : \"${expectedMask(value)}\"}"
        Assert.assertEquals("JSON with spaces around colon should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that access_token in a URL query parameter is redacted.
     */
    @Test
    fun testRedactUrlAccessToken() {
        val value = randomString(15)
        val input = "https://instance.salesforce.com/secur/frontdoor.jsp?access_token=$value"
        val expected = "https://instance.salesforce.com/secur/frontdoor.jsp?access_token=${expectedMask(value)}"
        Assert.assertEquals("URL access_token should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that sid in a URL query parameter is redacted.
     */
    @Test
    fun testRedactUrlSid() {
        val value = randomString(12)
        val input = "https://instance.salesforce.com/secur/frontdoor.jsp?sid=$value&retURL=/home"
        val expected = "https://instance.salesforce.com/secur/frontdoor.jsp?sid=${expectedMask(value)}&retURL=/home"
        Assert.assertEquals("URL sid should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that token in a URL query parameter is redacted.
     */
    @Test
    fun testRedactUrlToken() {
        val value = randomString(6)
        val input = "https://example.com/callback?token=$value&state=xyz"
        val expected = "https://example.com/callback?token=${expectedMask(value)}&state=xyz"
        Assert.assertEquals("URL token should be redacted", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that non-sensitive URL parameters are preserved.
     */
    @Test
    fun testRedactUrlPreservesNonSensitive() {
        val input = "https://example.com/path?display=touch&retURL=/home"
        Assert.assertEquals("Non-sensitive URL params should be unchanged", input, SalesforceLogger.redact(input))
    }

    /**
     * Test redaction with a realistic full token endpoint response.
     */
    @Test
    fun testRedactFullTokenResponse() {
        val accessVal = randomString(21)
        val refreshVal = randomString(13)
        val idTokenVal = randomString(20)
        val lightningSidVal = randomString(9)
        val csrfVal = randomString(7)
        val input = "parsedResponse-->{\"access_token\":\"$accessVal\"," +
                "\"refresh_token\":\"$refreshVal\"," +
                "\"instance_url\":\"https://na1.salesforce.com\"," +
                "\"id\":\"https://login.salesforce.com/id/00Dxx/005xx\"," +
                "\"id_token\":\"$idTokenVal\"," +
                "\"lightning_sid\":\"$lightningSidVal\"," +
                "\"csrf_token\":\"$csrfVal\"}"
        val result = SalesforceLogger.redact(input)
        Assert.assertFalse("access_token value should not appear", result?.contains(accessVal) == true)
        Assert.assertFalse("refresh_token value should not appear", result?.contains(refreshVal) == true)
        Assert.assertFalse("id_token value should not appear", result?.contains(idTokenVal) == true)
        Assert.assertFalse("lightning_sid value should not appear", result?.contains(lightningSidVal) == true)
        Assert.assertFalse("csrf_token value should not appear", result?.contains(csrfVal) == true)
        Assert.assertTrue("instance_url value should be preserved", result?.contains("https://na1.salesforce.com") == true)
        Assert.assertTrue("id value should be preserved", result?.contains("https://login.salesforce.com/id/00Dxx/005xx") == true)
        Assert.assertTrue("Masked output should contain stars", result?.contains("***") == true)
    }

    /**
     * Test that mixed JSON and URL content is fully redacted.
     */
    @Test
    fun testRedactMixedContent() {
        val jsonTokenVal = randomString(12)
        val urlSidVal = randomString(10)
        val input = "Response: {\"access_token\":\"$jsonTokenVal\"} from https://example.com?sid=$urlSidVal"
        val result = SalesforceLogger.redact(input)
        Assert.assertFalse("JSON token should not appear", result?.contains(jsonTokenVal) == true)
        Assert.assertFalse("URL sid should not appear", result?.contains(urlSidVal) == true)
    }

    /**
     * Test that values with 4 or fewer characters are fully masked.
     */
    @Test
    fun testRedactShortValue() {
        val value = randomString(2)
        val input = "{\"sid\":\"$value\"}"
        val expected = "{\"sid\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("Short values should be fully masked", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that values with exactly 4 characters are fully masked.
     */
    @Test
    fun testRedactExactly4CharValue() {
        val value = randomString(4)
        val input = "{\"sid\":\"$value\"}"
        val expected = "{\"sid\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("4-char values should be fully masked", expected, SalesforceLogger.redact(input))
    }

    /**
     * Test that values with 5 characters show only the last 4.
     */
    @Test
    fun testRedact5CharValue() {
        val value = randomString(5)
        val input = "{\"sid\":\"$value\"}"
        val expected = "{\"sid\":\"${expectedMask(value)}\"}"
        Assert.assertEquals("5-char values should show last 4", expected, SalesforceLogger.redact(input))
    }

    /**
     * Generates a random alphanumeric string of the given length.
     */
    private fun randomString(length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)])
        }
        return sb.toString()
    }

    companion object {
        private const val TEST_COMPONENT_1 = "TestComponent1"
        private const val TEST_COMPONENT_2 = "TestComponent2"
        private const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private const val VISIBLE_CHARS = 4

        /**
         * Computes the expected masked value: stars replacing all but the last 4 characters.
         * Values of 4 or fewer characters are fully masked with stars.
         */
        private fun expectedMask(value: String): String {
            if (value.length <= VISIBLE_CHARS) {
                return "*".repeat(value.length)
            }
            return "*".repeat(value.length - VISIBLE_CHARS) + value.substring(value.length - VISIBLE_CHARS)
        }
    }
}
