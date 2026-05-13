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
package com.salesforce.androidsdk.util.test

import android.content.Context
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.util.JSONObjectHelper
import com.salesforce.androidsdk.util.ResourceReaderHelper
import org.json.JSONException
import org.json.JSONObject

/**
 * Authentication credentials used to make live server calls in tests
 *
 * To populate test_credentials.json clone SalesforceMobileSDK-Shared and run web app in credsHelper folder
 */
object TestCredentials {

    @JvmStatic
    lateinit var API_VERSION: String
    @JvmStatic
    lateinit var ACCOUNT_TYPE: String
    @JvmStatic
    lateinit var ORG_ID: String
    @JvmStatic
    lateinit var USERNAME: String
    @JvmStatic
    lateinit var ACCOUNT_NAME: String
    @JvmStatic
    lateinit var USER_ID: String
    @JvmStatic
    lateinit var LOGIN_URL: String
    @JvmStatic
    lateinit var INSTANCE_URL: String
    @JvmStatic
    var API_INSTANCE_URL: String? = null
    @JvmStatic
    lateinit var COMMUNITY_URL: String
    @JvmStatic
    lateinit var IDENTITY_URL: String
    @JvmStatic
    lateinit var CLIENT_ID: String
    @JvmStatic
    lateinit var REFRESH_TOKEN: String
    @JvmStatic
    lateinit var PHOTO_URL: String
    @JvmStatic
    lateinit var LANGUAGE: String
    @JvmStatic
    lateinit var LOCALE: String

    @JvmStatic
    fun init(ctx: Context) {
        try {
            val json = JSONObject(ResourceReaderHelper.readAssetFile(ctx, "test_credentials.json")!!)

            API_VERSION = ApiVersionStrings.getVersionNumber(ctx)
            ACCOUNT_TYPE = ctx.getString(R.string.account_type)
            ORG_ID = json.getString("organization_id")
            USERNAME = json.getString("username")
            ACCOUNT_NAME = json.getString("display_name")
            USER_ID = json.getString("user_id")
            LOGIN_URL = json.getString("test_login_domain")
            INSTANCE_URL = json.getString("instance_url")
            API_INSTANCE_URL = JSONObjectHelper.optString(json, "api_instance_url")
            COMMUNITY_URL = json.optString(
                "community_url",
                INSTANCE_URL /* in case the test_credentials.json was obtained for a user / org without community setup */
            )
            IDENTITY_URL = json.getString("identity_url")
            CLIENT_ID = json.getString("test_client_id")
            REFRESH_TOKEN = json.getString("refresh_token")
            PHOTO_URL = json.getString("photo_url")
            LANGUAGE = json.optString("language", "en_US")
            LOCALE = json.optString("locale", "en_US")
        } catch (e: Exception) {
            throw RuntimeException("Failed to read test_credentials.json", e)
        }
    }

    @JvmStatic
    fun init(creds: String, ctx: Context) {
        try {
            val json = JSONObject(creds)
            API_VERSION = ApiVersionStrings.getVersionNumber(ctx)
            ACCOUNT_TYPE = ctx.getString(R.string.account_type)
            ORG_ID = json.getString("organization_id")
            USERNAME = json.getString("username")
            ACCOUNT_NAME = json.getString("display_name")
            USER_ID = json.getString("user_id")
            LOGIN_URL = json.getString("test_login_domain")
            INSTANCE_URL = json.getString("instance_url")
            COMMUNITY_URL = json.optString(
                "community_url",
                INSTANCE_URL /* In case the test_credentials.json was obtained for a user/org without community setup */
            )
            IDENTITY_URL = json.getString("identity_url")
            CLIENT_ID = json.getString("test_client_id")
            REFRESH_TOKEN = json.getString("refresh_token")
            PHOTO_URL = json.getString("photo_url")
            LANGUAGE = json.optString("language", "en_US")
            LOCALE = json.optString("locale", "en_US")
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }
}
