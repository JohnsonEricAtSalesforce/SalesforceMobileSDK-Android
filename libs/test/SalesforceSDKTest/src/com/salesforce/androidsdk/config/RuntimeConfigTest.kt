/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.config

import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.util.JSONTestHelper
import org.json.JSONArray
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for RuntimeConfig
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class RuntimeConfigTest {

    private lateinit var testContext: Context

    @Before
    fun setUp() {
        testContext = InstrumentationRegistry.getInstrumentation().context
    }

    @After
    fun tearDown() {
    }

    @Test
    fun testGetMissingConfig() {
        val config = RuntimeConfig(testContext)
        config.setRestrictions(Bundle())
        Assert.assertFalse(config.getBoolean(RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts))
        Assert.assertNull(config.getString(RuntimeConfig.ConfigKey.ManagedAppCallbackURL))
        Assert.assertNull(config.getStringArray(RuntimeConfig.ConfigKey.AppServiceHostLabels))
        Assert.assertNull(config.getStringArrayStoredAsArrayOrCSV(RuntimeConfig.ConfigKey.AppServiceHostLabels))
    }

    @Test
    fun testGetString() {
        val config = RuntimeConfig(testContext)
        val b = Bundle()
        b.putString(RuntimeConfig.ConfigKey.ManagedAppCallbackURL.name, "sfdc://test")
        b.putString(RuntimeConfig.ConfigKey.ManagedAppCertAlias.name, "alias")
        config.setRestrictions(b)
        Assert.assertEquals(
            "getString failed",
            "sfdc://test",
            config.getString(RuntimeConfig.ConfigKey.ManagedAppCallbackURL)
        )
        Assert.assertEquals("getString failed", "alias", config.getString(RuntimeConfig.ConfigKey.ManagedAppCertAlias))
    }

    @Test
    fun testGetBoolean() {
        val config = RuntimeConfig(testContext)
        val b = Bundle()
        b.putBoolean(RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts.name, true)
        b.putBoolean(RuntimeConfig.ConfigKey.RequireCertAuth.name, false)
        config.setRestrictions(b)
        Assert.assertEquals(
            "getBoolean failed",
            true,
            config.getBoolean(RuntimeConfig.ConfigKey.OnlyShowAuthorizedHosts)
        )
        Assert.assertEquals("getBoolean failed", false, config.getBoolean(RuntimeConfig.ConfigKey.RequireCertAuth))
    }

    @Test
    fun testGetStringArray() {
        val config = RuntimeConfig(testContext)
        val b = Bundle()
        b.putStringArray(RuntimeConfig.ConfigKey.AppServiceHosts.name, arrayOf("host1", "host2"))
        b.putStringArray(RuntimeConfig.ConfigKey.AppServiceHostLabels.name, arrayOf("label1", "label2"))
        config.setRestrictions(b)
        JSONTestHelper.assertSameJSONArray(
            "getStringArray failed",
            JSONArray(arrayOf("host1", "host2")),
            JSONArray(config.getStringArray(RuntimeConfig.ConfigKey.AppServiceHosts))
        )
        JSONTestHelper.assertSameJSONArray(
            "getStringArray failed",
            JSONArray(arrayOf("label1", "label2")),
            JSONArray(config.getStringArray(RuntimeConfig.ConfigKey.AppServiceHostLabels))
        )
    }

    @Test
    fun testGetStringArrayStoredAsArrayOrCSVSingleValue() {
        val config = RuntimeConfig(testContext)
        val b = Bundle()
        b.putStringArray(RuntimeConfig.ConfigKey.AppServiceHosts.name, arrayOf("host1"))
        b.putString(RuntimeConfig.ConfigKey.AppServiceHostLabels.name, "label1")
        config.setRestrictions(b)
        JSONTestHelper.assertSameJSONArray(
            "getStringArrayStoredAsArrayOrCSV failed",
            JSONArray(arrayOf("host1")),
            JSONArray(config.getStringArrayStoredAsArrayOrCSV(RuntimeConfig.ConfigKey.AppServiceHosts))
        )
        JSONTestHelper.assertSameJSONArray(
            "getStringArrayStoredAsArrayOrCSV failed",
            JSONArray(arrayOf("label1")),
            JSONArray(config.getStringArrayStoredAsArrayOrCSV(RuntimeConfig.ConfigKey.AppServiceHostLabels))
        )
    }

    @Test
    fun testGetStringArrayStoredAsArrayOrCSVMultipleValues() {
        val config = RuntimeConfig(testContext)
        val b = Bundle()
        b.putStringArray(RuntimeConfig.ConfigKey.AppServiceHosts.name, arrayOf("host1", "host2"))
        b.putString(RuntimeConfig.ConfigKey.AppServiceHostLabels.name, "label1,label2")
        config.setRestrictions(b)
        JSONTestHelper.assertSameJSONArray(
            "getStringArrayStoredAsArrayOrCSV failed",
            JSONArray(arrayOf("host1", "host2")),
            JSONArray(config.getStringArrayStoredAsArrayOrCSV(RuntimeConfig.ConfigKey.AppServiceHosts))
        )
        JSONTestHelper.assertSameJSONArray(
            "getStringArrayStoredAsArrayOrCSV failed",
            JSONArray(arrayOf("label1", "label2")),
            JSONArray(config.getStringArrayStoredAsArrayOrCSV(RuntimeConfig.ConfigKey.AppServiceHostLabels))
        )
    }
}
