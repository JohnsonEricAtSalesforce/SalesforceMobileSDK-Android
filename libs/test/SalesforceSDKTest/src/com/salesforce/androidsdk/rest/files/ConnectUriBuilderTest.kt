/*
 * Copyright (c) 2013-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest.files

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ApiVersionStrings
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ConnectUriBuilderTest {

    lateinit var connectPath: String

    @Before
    fun setUp() {
        connectPath = "/services/data/" + ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().appContext) + "/chatter/"
    }

    @Test
    fun testBasePath() {
        Assert.assertEquals(connectPath, ConnectUriBuilder().toString())
    }

    @Test
    fun testAppendPath() {
        Assert.assertEquals(connectPath + "foo", ConnectUriBuilder().appendPath("foo").toString())
        Assert.assertEquals(connectPath + "foo/bar", ConnectUriBuilder().appendPath("foo").appendPath("bar").toString())
        Assert.assertEquals(connectPath + "foo/bar", ConnectUriBuilder().appendPath("foo/bar").toString())
    }

    @Test
    fun testAppendUserId() {
        val userId = "005T0000000ABCD"
        Assert.assertEquals(connectPath + "users/me", ConnectUriBuilder().appendPath("users").appendUserId(null).toString())
        Assert.assertEquals(connectPath + "users/$userId", ConnectUriBuilder().appendPath("users").appendUserId(userId).toString())
        try {
            ConnectUriBuilder().appendPath("users").appendUserId("")
            Assert.fail("empty user id didn't raise exception as expected")
        } catch (e: IllegalArgumentException) { /* expected */
        }
    }

    @Test
    fun testAppendQueryParam() {
        Assert.assertEquals(connectPath + "?user=jjiang", ConnectUriBuilder().appendQueryParam("user", "jjiang").toString())
        Assert.assertEquals(
            connectPath + "?password=123456",
            ConnectUriBuilder().appendQueryParam("password", Integer.valueOf(123456)).toString()
        )
        val nullString: String? = null
        val nullInt: Int? = null
        Assert.assertEquals(connectPath, ConnectUriBuilder().appendQueryParam("user", nullString).toString())
        Assert.assertEquals(connectPath, ConnectUriBuilder().appendQueryParam("user", "").toString())
        Assert.assertEquals(connectPath, ConnectUriBuilder().appendQueryParam("user", nullInt).toString())
        Assert.assertEquals(connectPath, ConnectUriBuilder().appendQueryParam(null, "jjiang").toString())
        Assert.assertEquals(connectPath, ConnectUriBuilder().appendQueryParam(null, Integer.valueOf(123456)).toString())
        Assert.assertEquals(
            connectPath + "?" + Uri.encode("user_name") + "=" + Uri.encode("jiahan jjiang"),
            ConnectUriBuilder().appendQueryParam("user_name", "jiahan jjiang").toString()
        )
    }

    @Test
    fun testAppendPageNum() {
        val filePath = "files/06930000001LkwtAAC/rendition"
        Assert.assertEquals(connectPath + filePath, ConnectUriBuilder().appendPath(filePath).appendPageNum(null).toString())
        Assert.assertEquals(connectPath + filePath + "?page=0", ConnectUriBuilder().appendPath(filePath).appendPageNum(0).toString())
        Assert.assertEquals(connectPath + filePath + "?page=5", ConnectUriBuilder().appendPath(filePath).appendPageNum(5).toString())
        try {
            ConnectUriBuilder().appendPath(filePath).appendPageNum(-3)
            Assert.fail("negative page number didn't raise exception as expected")
        } catch (e: IllegalArgumentException) { /* expected */
        }
    }

    @Test
    fun testAppendVersionNum() {
        val filePath = "files/06930000001LkwtAAC/rendition"
        Assert.assertEquals(
            connectPath + filePath + "?versionNumber=3",
            ConnectUriBuilder().appendPath(filePath).appendVersionNum("3").toString()
        )
        try {
            ConnectUriBuilder().appendPath(filePath).appendVersionNum("0")
            Assert.fail("version 0 didn't raise exception as expected")
        } catch (e: IllegalArgumentException) { /* expected */
        }
        try {
            ConnectUriBuilder().appendPath(filePath).appendVersionNum("abc")
            Assert.fail("invalid version format didn't raise exception as expected")
        } catch (e: IllegalArgumentException) { /* expected */
        }
        try {
            ConnectUriBuilder().appendPath(filePath).appendVersionNum("")
            Assert.fail("empty version string didn't raise exception as expected")
        } catch (e: IllegalArgumentException) { /* expected */
        }
        try {
            ConnectUriBuilder().appendPath(filePath).appendVersionNum("-3")
            Assert.fail("negative version number didn't raise exception as expected")
        } catch (e: IllegalArgumentException) { /* expected */
        }
    }
}
