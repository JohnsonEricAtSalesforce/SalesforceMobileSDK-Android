/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap

import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.phonegap.plugin.JavaScriptPluginVersion
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for JavaScriptPluginVersion.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class JavaScriptPluginVersionTest {

    /**
     * Test for safeParseInt
     */
    @Test
    fun testSafeParseInt() {
        Assert.assertEquals(1, JavaScriptPluginVersion.safeParseInt("1", -1))
        Assert.assertEquals(-1, JavaScriptPluginVersion.safeParseInt("not-a-number", -1))
    }

    /**
     * Test for compare versions when the same versions are passed in
     */
    @Test
    fun testCompareVersionsSameVersion() {
        Assert.assertEquals(0, JavaScriptPluginVersion.compareVersions("1", "1"))
        Assert.assertEquals(0, JavaScriptPluginVersion.compareVersions("1.2", "1.2"))
        Assert.assertEquals(0, JavaScriptPluginVersion.compareVersions("2.2.3", "2.2.3"))
    }

    /**
     * Test for compare versions when the same versions are passed in and one is marked as dev
     * dev version is assumed to be older
     */
    @Test
    fun testCompareVersionsSameVersionWithDev() {
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("1.dev", "1"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("1", "1.dev"))
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("1.2.dev", "1.2"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("1.2", "1.2.dev"))
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("2.2.3.dev", "2.2.3"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("2.2.3", "2.2.3.dev"))
    }

    /**
     * Test for compare versions when one version is a patch on the other
     * NB dev is simply ignored
     */
    @Test
    fun testCompareVersionsWithPatch() {
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("1", "1.1"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("1.1", "1"))
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("2.4", "2.4.2"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("2.4.2", "2.4"))
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("3.5.1", "3.5.1.3"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("3.5.1.3", "3.5.1"))
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("3.5", "3.5.1.3"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("3.5.1.3", "3.5"))
    }

    /**
     * Test for compare versions with versions with two digits
     * NB dev is simply ignored
     */
    @Test
    fun testCompareVersionsWithTwoDigits() {
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("9", "14"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("14", "9"))
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("2.9", "2.14"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("2.14", "2.9"))
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("1.2.9", "1.2.14"))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("1.2.14", "1.2.9"))
    }

    /**
     * Create JavaScriptPluginVersion for empty version (always considered older)
     */
    @Test
    fun testJavaScriptPluginVersionsWithNoVersion() {
        Assert.assertEquals(-1, JavaScriptPluginVersion.compareVersions("", "2.0"))
        Assert.assertEquals(0, JavaScriptPluginVersion.compareVersions("", ""))
        Assert.assertEquals(1, JavaScriptPluginVersion.compareVersions("2.0", ""))
    }

    /**
     * Create JavaScriptPluginVersion for old versions and make sure isCurrent/isOlder/isNewer returns the value expected
     */
    @Test
    fun testJavaScriptPluginVersionsWithOldVersions() {
        for (version in arrayOf("1.0", "1.1", "1.2", "1.3")) {
            Assert.assertTrue(JavaScriptPluginVersion(version).isOlder)
            Assert.assertFalse(JavaScriptPluginVersion(version).isCurrent)
            Assert.assertFalse(JavaScriptPluginVersion(version).isNewer)
        }
    }

    /**
     * Create JavaScriptPluginVersion for current version and make sure isCurrent/isOlder/isNewer returns the value expected
     */
    @Test
    fun testJavaScriptPluginVersionsWithCurrentVersion() {
        Assert.assertFalse(JavaScriptPluginVersion(SalesforceSDKManager.SDK_VERSION).isOlder)
        Assert.assertTrue(JavaScriptPluginVersion(SalesforceSDKManager.SDK_VERSION).isCurrent)
        Assert.assertFalse(JavaScriptPluginVersion(SalesforceSDKManager.SDK_VERSION).isNewer)
    }

    /**
     * Create JavaScriptPluginVersion for future versions and make sure isCurrent/isOlder/isNewer returns the value expected
     */
    @Test
    fun testJavaScriptPluginVersionsWithNewVersion() {
        val versionParts = SalesforceSDKManager.SDK_VERSION.split("\\.".toRegex())
        val nextMajor = TextUtils.join(".", arrayOf("" + (versionParts[0].toInt() + 1), versionParts[1], versionParts[2]))
        val nextMinor = TextUtils.join(".", arrayOf(versionParts[0], "" + (versionParts[1].toInt() + 1), versionParts[2]))
        val nextPatch = TextUtils.join(".", arrayOf(versionParts[0], versionParts[1], "" + (versionParts[2].toInt() + 1)))

        for (version in arrayOf(nextMajor, nextMinor, nextPatch)) {
            Assert.assertFalse(JavaScriptPluginVersion(version).isOlder)
            Assert.assertFalse(JavaScriptPluginVersion(version).isCurrent)
            Assert.assertTrue(JavaScriptPluginVersion(version).isNewer)
        }
    }
}
