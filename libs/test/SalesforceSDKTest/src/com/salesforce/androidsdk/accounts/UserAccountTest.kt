/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.accounts

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.util.BundleTestHelper
import com.salesforce.androidsdk.util.JSONTestHelper
import com.salesforce.androidsdk.util.MapUtil
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * Tests for [UserAccount]
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class UserAccountTest {

    companion object {
        // test user
        const val TEST_ORG_ID = "test_org_id"
        const val TEST_USER_ID = "test_user_id"
        const val TEST_ACCOUNT_NAME = "test_username (https://cs1.salesforce.com) (SalesforceSDKTest)"
        const val TEST_USERNAME = "test_username"
        const val TEST_LOGIN_URL = "https://test.salesforce.com"
        const val TEST_INSTANCE_URL = "https://cs1.salesforce.com"
        const val TEST_API_INSTANCE_URL = "https://api.salesforce.com"
        const val TEST_IDENTITY_URL = "https://test.salesforce.com/$TEST_ORG_ID/$TEST_USER_ID"
        const val TEST_COMMUNITY_URL = "https://mobilesdk.cs1.my.salesforce.com"
        const val TEST_AUTH_TOKEN = "test_auth_token"
        const val TEST_REFRESH_TOKEN = "test_refresh_token"
        const val TEST_COMMUNITY_ID = "test_community_id"
        const val TEST_FIRST_NAME = "test_first_name"
        const val TEST_LAST_NAME = "test_last_name"
        const val TEST_NICK_NAME = "test_nick_name"
        const val TEST_DISPLAY_NAME = "test_display_name"
        const val TEST_USER_TYPE = "test_user_type"
        const val TEST_LAST_MODIFIED_DATE = "2024-09-18T10:11:12Z"
        const val TEST_EMAIL = "test@email.com"
        const val TEST_PHOTO_URL = "http://some.photo.url"
        const val TEST_THUMBNAIL_URL = "http://some.thumbnail.url"
        const val TEST_CUSTOM_KEY = "test_custom_key"
        const val TEST_CUSTOM_VALUE = "test_custom_value"
        const val TEST_LANGUAGE = "en_US"
        const val TEST_LOCALE = "fr_FR"
        const val TEST_LIGHTNING_DOMAIN = "lightning-domain-value"
        const val TEST_LIGHTNING_SID = "lightning-sid-value"
        const val TEST_VF_DOMAIN = "vf-domain-value"
        const val TEST_VF_SID = "vf-sid-value"
        const val TEST_CONTENT_DOMAIN = "content-domain-value"
        const val TEST_CONTENT_SID = "content-sid-value"
        const val TEST_CSRF_TOKEN = "csrf-token-value"
        const val TEST_NATIVE_LOGIN = false
        const val TEST_COOKIE_CLIENT_SRC = "cookie-client-src-value"
        const val TEST_COOKIE_SID_CLIENT = "cookie-sid-client-value"
        const val TEST_SID_COOKIE_NAME = "sid-cookie-name"
        const val TEST_CLIENT_ID = "test-client-id"
        const val TEST_PARENT_SID = "test-parent-sid"
        const val TEST_TOKEN_FORMAT = "test-token-format"
        const val TEST_BEACON_CHILD_CONSUMER_KEY = "test-beacon-child-consumer-key"
        const val TEST_BEACON_CHILD_CONSUMER_SECRET = "test-beacon-child-consumer-secret"
        const val TEST_SCOPE = "api web openid refresh_token"

        // other user
        const val TEST_ORG_ID_2 = "test_org_id_2"
        const val TEST_USER_ID_2 = "test_user_id_2"
        const val TEST_ACCOUNT_NAME_2 = "test_username_2 (https://cs1.salesforce.com) (SalesforceSDKTest)"
        const val TEST_USERNAME_2 = "test_username_2"
        const val TEST_SCOPE_2 = "api web refresh_token sfap_api"

        /**
         * Create test account
         */
        @JvmStatic
        fun createTestAccount(): UserAccount {
            return UserAccountBuilder.getInstance()
                .authToken(TEST_AUTH_TOKEN)
                .refreshToken(TEST_REFRESH_TOKEN)
                .loginServer(TEST_LOGIN_URL)
                .idUrl(TEST_IDENTITY_URL)
                .instanceServer(TEST_INSTANCE_URL)
                .apiInstanceServer(TEST_API_INSTANCE_URL)
                .orgId(TEST_ORG_ID)
                .userId(TEST_USER_ID)
                .username(TEST_USERNAME)
                .accountName(TEST_ACCOUNT_NAME)
                .communityId(TEST_COMMUNITY_ID)
                .communityUrl(TEST_COMMUNITY_URL)
                .firstName(TEST_FIRST_NAME)
                .lastName(TEST_LAST_NAME)
                .displayName(TEST_DISPLAY_NAME)
                .email(TEST_EMAIL)
                .photoUrl(TEST_PHOTO_URL)
                .thumbnailUrl(TEST_THUMBNAIL_URL)
                .lightningDomain(TEST_LIGHTNING_DOMAIN)
                .lightningSid(TEST_LIGHTNING_SID)
                .vfDomain(TEST_VF_DOMAIN)
                .vfSid(TEST_VF_SID)
                .contentDomain(TEST_CONTENT_DOMAIN)
                .contentSid(TEST_CONTENT_SID)
                .csrfToken(TEST_CSRF_TOKEN)
                .nativeLogin(TEST_NATIVE_LOGIN)
                .language(TEST_LANGUAGE)
                .locale(TEST_LOCALE)
                .cookieClientSrc(TEST_COOKIE_CLIENT_SRC)
                .cookieSidClient(TEST_COOKIE_SID_CLIENT)
                .sidCookieName(TEST_SID_COOKIE_NAME)
                .clientId(TEST_CLIENT_ID)
                .parentSid(TEST_PARENT_SID)
                .tokenFormat(TEST_TOKEN_FORMAT)
                .beaconChildConsumerKey(TEST_BEACON_CHILD_CONSUMER_KEY)
                .beaconChildConsumerSecret(TEST_BEACON_CHILD_CONSUMER_SECRET)
                .scope(TEST_SCOPE)
                .additionalOauthValues(createAdditionalOauthValues())
                .build()
        }

        /**
         * Create other test account
         */
        @JvmStatic
        fun createOtherTestAccount(): UserAccount {
            return UserAccountBuilder.getInstance()
                .populateFromUserAccount(createTestAccount())
                .userId(TEST_USER_ID_2)
                .orgId(TEST_ORG_ID_2)
                .username(TEST_USERNAME_2)
                .accountName(TEST_ACCOUNT_NAME_2)
                .scope(TEST_SCOPE_2)
                .build()
        }

        /**
         * Check the user accounts are the same
         * @param expected Expected UserAccount
         * @param actual Actual UserAccount
         */
        @JvmStatic
        fun checkSameUserAccount(expected: UserAccount, actual: UserAccount) {
            // NB We are comparing every fields (UserAccount's equals method only looks at userId and orgId)
            BundleTestHelper.checkSameBundle("Not the expected user account", expected.toBundle(), actual.toBundle())
        }

        private fun createAdditionalOauthValues(): Map<String, String> {
            return hashMapOf(TEST_CUSTOM_KEY to TEST_CUSTOM_VALUE)
        }
    }

    /**
     * Tests user account to bundle conversion.
     */
    @Test
    fun testConvertAccountToBundle() {
        val account = createTestAccount()
        val actual = account.toBundle(createAdditionalOauthKeys())
        val expected = createTestAccountBundle()
        BundleTestHelper.checkSameBundle("UserAccount bundles do not match", expected, actual)
    }

    @Test
    fun testHasScope() {
        val account = createTestAccount()
        Assert.assertTrue(account.hasScope("api"))
        Assert.assertTrue(account.hasScope("web"))
        Assert.assertTrue(account.hasScope("openid"))
        Assert.assertTrue(account.hasScope("refresh_token"))
        Assert.assertFalse(account.hasScope("unknown"))

        val emptyScope = UserAccountBuilder.getInstance()
            .populateFromUserAccount(account)
            .scope("")
            .build()
        Assert.assertFalse(emptyScope.hasScope("api"))
    }

    /**
     * Tests user account to json conversion.
     */
    @Test
    fun testConvertAccountToJSON() {
        val account = createTestAccount()
        val actual = account.toJson(createAdditionalOauthKeys())
        val expected = createTestAccountJSON()
        JSONTestHelper.assertSameJSONObject("UserAccount JSONs do not match", expected, actual)
    }

    /**
     * Tests creating an account from a bundle.
     */
    @Test
    fun testCreateAccountFromBundle() {
        val testBundle = createTestAccountBundle()
        val account = UserAccount(testBundle, createAdditionalOauthKeys())
        checkTestAccount(account)
    }

    /**
     * Tests creating an account from JSON
     */
    @Test
    fun testCreateAccountFromJSON() {
        val testJSON = createTestAccountJSON()
        val account = UserAccount(testJSON, "SalesforceSDKTest", createAdditionalOauthKeys())
        checkTestAccount(account)
    }

    /**
     * Tests populating account from token end point response and id response
     * Simulating user agent flow
     */
    @Test
    fun testPopulateFromTokenEndpointAndIdServiceLikeUserAgentFlow() {
        val id = createIdServiceResponse()
        val trUserAgentFlow = createTokenEndpointResponseLikeUserAgentFlow()
        checkTestAccount(
            UserAccountBuilder.getInstance()
                .populateFromTokenEndpointResponse(trUserAgentFlow)
                .populateFromIdServiceResponse(id)
                .accountName(TEST_ACCOUNT_NAME)
                .loginServer(TEST_LOGIN_URL)
                .nativeLogin(TEST_NATIVE_LOGIN)
                .clientId(TEST_CLIENT_ID)
                .build(), false /* no beacon child fields during user agent flow */
        )
    }

    /**
     * Tests populating account from token end point response and id response
     * Simulating web server flow
     */
    @Test
    fun testPopulateFromTokenEndpointAndIdServiceLikeWebServerFlow() {
        val id = createIdServiceResponse()
        val trWebServerFlow = createTokenEndpointResponseLikeWebServerFlow()
        checkTestAccount(
            UserAccountBuilder.getInstance()
                .populateFromTokenEndpointResponse(trWebServerFlow)
                .populateFromIdServiceResponse(id)
                .accountName(TEST_ACCOUNT_NAME)
                .loginServer(TEST_LOGIN_URL)
                .nativeLogin(TEST_NATIVE_LOGIN)
                .clientId(TEST_CLIENT_ID)
                .build(), true /* beacon child fields expected with web server flow */
        )
    }

    /**
     * Tests populating account from another user account
     */
    @Test
    fun testPopulateFromUserAccount() {
        val otherUserAccount = UserAccountBuilder.getInstance()
            .populateFromUserAccount(createTestAccount())
            .userId(TEST_USER_ID_2)
            .orgId(TEST_ORG_ID_2)
            .username(TEST_USERNAME_2)
            .accountName(TEST_ACCOUNT_NAME_2)
            .scope(TEST_SCOPE_2)
            .build()
        checkOtherTestAccount(otherUserAccount)
    }

    @Test
    fun testGetClientIdForRefresh() {
        val userWithBeaconChildKey = createTestAccount()
        Assert.assertEquals("Beacon child consumer key should match", TEST_BEACON_CHILD_CONSUMER_KEY, userWithBeaconChildKey.beaconChildConsumerKey)
        Assert.assertEquals("Beacon child consumer secret should match", TEST_BEACON_CHILD_CONSUMER_SECRET, userWithBeaconChildKey.beaconChildConsumerSecret)
        Assert.assertEquals("Client id should match", TEST_CLIENT_ID, userWithBeaconChildKey.clientId)
        Assert.assertEquals("Client id for refresh should be beacon child client id", TEST_BEACON_CHILD_CONSUMER_KEY, userWithBeaconChildKey.clientIdForRefresh)

        val userWithoutBeaconChildKey = UserAccountBuilder.getInstance()
            .populateFromUserAccount(userWithBeaconChildKey)
            .beaconChildConsumerKey(null)
            .beaconChildConsumerSecret(null)
            .build()

        Assert.assertNull("Beacon child consumer key should be null", userWithoutBeaconChildKey.beaconChildConsumerKey)
        Assert.assertNull("Beacon child consumer secret should be null", userWithoutBeaconChildKey.beaconChildConsumerSecret)
        Assert.assertEquals("Client id should match", TEST_CLIENT_ID, userWithoutBeaconChildKey.clientId)
        Assert.assertEquals("Client id for refresh should be client id", TEST_CLIENT_ID, userWithoutBeaconChildKey.clientIdForRefresh)
    }

    /**
     * Tests that allowUnset behaves as expected
     */
    @Test
    fun testAllowUnset() {
        // allow unset true (default)
        Assert.assertEquals(
            "login-server-1", UserAccountBuilder.getInstance()
                .loginServer("login-server-1")
                .build().loginServer
        )

        Assert.assertEquals(
            "login-server-2", UserAccountBuilder.getInstance()
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .build().loginServer
        )

        Assert.assertEquals(
            null, UserAccountBuilder.getInstance()
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .loginServer(null)
                .build().loginServer
        )

        Assert.assertEquals(
            "login-server-3", UserAccountBuilder.getInstance()
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .loginServer(null)
                .loginServer("login-server-3")
                .build().loginServer
        )

        // allow unset false
        Assert.assertEquals(
            "login-server-1", UserAccountBuilder.getInstance()
                .allowUnset(false)
                .loginServer("login-server-1")
                .build().loginServer
        )

        Assert.assertEquals(
            "login-server-2", UserAccountBuilder.getInstance()
                .allowUnset(false)
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .build().loginServer
        )

        Assert.assertEquals(
            "login-server-2", UserAccountBuilder.getInstance()
                .allowUnset(false)
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .loginServer(null)
                .build().loginServer
        )

        Assert.assertEquals(
            "login-server-3", UserAccountBuilder.getInstance()
                .allowUnset(false)
                .loginServer("login-server-1")
                .loginServer("login-server-2")
                .loginServer(null)
                .loginServer("login-server-3")
                .build().loginServer
        )
    }

    /**
     * Tests that allowUnset behaves as expected
     */
    @Test
    fun testAllowUnsetForAdditionalOauthValues() {
        val addtional1 = hashMapOf("custom-1" to "value-1")
        val addtional1upd = hashMapOf("custom-1" to "value-1-upd")
        val addtional2 = hashMapOf("custom-2" to "value-2")
        val addtionalMerge = hashMapOf("custom-1" to "value-1", "custom-2" to "value-2")
        val addtionalMergeUpd = hashMapOf("custom-1" to "value-1-upd", "custom-2" to "value-2")

        // allow unset true (default)
        Assert.assertEquals(
            addtional1, UserAccountBuilder.getInstance()
                .additionalOauthValues(addtional1)
                .build().additionalOauthValues
        )

        Assert.assertEquals(
            addtional2, UserAccountBuilder.getInstance()
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .build().additionalOauthValues
        )

        Assert.assertEquals(
            null, UserAccountBuilder.getInstance()
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .additionalOauthValues(null)
                .build().additionalOauthValues
        )

        Assert.assertEquals(
            addtional1upd, UserAccountBuilder.getInstance()
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .additionalOauthValues(null)
                .additionalOauthValues(addtional1upd)
                .build().additionalOauthValues
        )

        // allow unset false - null won't write over - maps are merged
        Assert.assertEquals(
            addtional1, UserAccountBuilder.getInstance()
                .allowUnset(false)
                .additionalOauthValues(addtional1)
                .build().additionalOauthValues
        )

        Assert.assertEquals(
            addtionalMerge, UserAccountBuilder.getInstance()
                .allowUnset(false)
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .build().additionalOauthValues
        )

        Assert.assertEquals(
            addtionalMerge, UserAccountBuilder.getInstance()
                .allowUnset(false)
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .additionalOauthValues(null)
                .build().additionalOauthValues
        )

        Assert.assertEquals(
            addtionalMergeUpd, UserAccountBuilder.getInstance()
                .allowUnset(false)
                .additionalOauthValues(addtional1)
                .additionalOauthValues(addtional2)
                .additionalOauthValues(null)
                .additionalOauthValues(addtional1upd)
                .build().additionalOauthValues
        )
    }

    /**
     * Creates a test [JSONObject] with all [UserAccount] fields populated
     */
    private fun createTestAccountJSON(): JSONObject {
        var obj = JSONObject()
        obj.put(UserAccount.AUTH_TOKEN, TEST_AUTH_TOKEN)
        obj.put(UserAccount.REFRESH_TOKEN, TEST_REFRESH_TOKEN)
        obj.put(UserAccount.LOGIN_SERVER, TEST_LOGIN_URL)
        obj.put(UserAccount.ID_URL, TEST_IDENTITY_URL)
        obj.put(UserAccount.INSTANCE_SERVER, TEST_INSTANCE_URL)
        obj.put(UserAccount.API_INSTANCE_SERVER, TEST_API_INSTANCE_URL)
        obj.put(UserAccount.ORG_ID, TEST_ORG_ID)
        obj.put(UserAccount.USER_ID, TEST_USER_ID)
        obj.put(UserAccount.USERNAME, TEST_USERNAME)
        obj.put(UserAccount.COMMUNITY_ID, TEST_COMMUNITY_ID)
        obj.put(UserAccount.COMMUNITY_URL, TEST_COMMUNITY_URL)
        obj.put(UserAccount.FIRST_NAME, TEST_FIRST_NAME)
        obj.put(UserAccount.LAST_NAME, TEST_LAST_NAME)
        obj.put(UserAccount.DISPLAY_NAME, TEST_DISPLAY_NAME)
        obj.put(UserAccount.EMAIL, TEST_EMAIL)
        obj.put(UserAccount.LANGUAGE, TEST_LANGUAGE)
        obj.put(UserAccount.LOCALE, TEST_LOCALE)
        obj.put(UserAccount.PHOTO_URL, TEST_PHOTO_URL)
        obj.put(UserAccount.THUMBNAIL_URL, TEST_THUMBNAIL_URL)
        obj.put(UserAccount.LIGHTNING_DOMAIN, TEST_LIGHTNING_DOMAIN)
        obj.put(UserAccount.LIGHTNING_SID, TEST_LIGHTNING_SID)
        obj.put(UserAccount.VF_DOMAIN, TEST_VF_DOMAIN)
        obj.put(UserAccount.VF_SID, TEST_VF_SID)
        obj.put(UserAccount.CONTENT_DOMAIN, TEST_CONTENT_DOMAIN)
        obj.put(UserAccount.CONTENT_SID, TEST_CONTENT_SID)
        obj.put(UserAccount.CSRF_TOKEN, TEST_CSRF_TOKEN)
        obj.put(UserAccount.NATIVE_LOGIN, TEST_NATIVE_LOGIN)
        obj.put(UserAccount.COOKIE_CLIENT_SRC, TEST_COOKIE_CLIENT_SRC)
        obj.put(UserAccount.COOKIE_SID_CLIENT, TEST_COOKIE_SID_CLIENT)
        obj.put(UserAccount.SID_COOKIE_NAME, TEST_SID_COOKIE_NAME)
        obj.put(UserAccount.PARENT_SID, TEST_PARENT_SID)
        obj.put(UserAccount.TOKEN_FORMAT, TEST_TOKEN_FORMAT)
        obj.put(UserAccount.SCOPE, TEST_SCOPE)
        obj.put(UserAccount.BEACON_CHILD_CONSUMER_KEY, TEST_BEACON_CHILD_CONSUMER_KEY)
        obj.put(UserAccount.BEACON_CHILD_CONSUMER_SECRET, TEST_BEACON_CHILD_CONSUMER_SECRET)
        obj = MapUtil.addMapToJSONObject(createAdditionalOauthValues(), createAdditionalOauthKeys(), obj)!!
        return obj
    }

    /**
     * Creates a test [Bundle] with all [UserAccount] fields populated
     */
    private fun createTestAccountBundle(): Bundle {
        var bundle = Bundle()
        bundle.putString(UserAccount.AUTH_TOKEN, TEST_AUTH_TOKEN)
        bundle.putString(UserAccount.REFRESH_TOKEN, TEST_REFRESH_TOKEN)
        bundle.putString(UserAccount.LOGIN_SERVER, TEST_LOGIN_URL)
        bundle.putString(UserAccount.ID_URL, TEST_IDENTITY_URL)
        bundle.putString(UserAccount.INSTANCE_SERVER, TEST_INSTANCE_URL)
        bundle.putString(UserAccount.API_INSTANCE_SERVER, TEST_API_INSTANCE_URL)
        bundle.putString(UserAccount.ORG_ID, TEST_ORG_ID)
        bundle.putString(UserAccount.USER_ID, TEST_USER_ID)
        bundle.putString(UserAccount.USERNAME, TEST_USERNAME)
        bundle.putString(UserAccount.ACCOUNT_NAME, TEST_ACCOUNT_NAME)
        bundle.putString(UserAccount.COMMUNITY_ID, TEST_COMMUNITY_ID)
        bundle.putString(UserAccount.COMMUNITY_URL, TEST_COMMUNITY_URL)
        bundle.putString(UserAccount.FIRST_NAME, TEST_FIRST_NAME)
        bundle.putString(UserAccount.LAST_NAME, TEST_LAST_NAME)
        bundle.putString(UserAccount.DISPLAY_NAME, TEST_DISPLAY_NAME)
        bundle.putString(UserAccount.EMAIL, TEST_EMAIL)
        bundle.putString(UserAccount.LANGUAGE, TEST_LANGUAGE)
        bundle.putString(UserAccount.LOCALE, TEST_LOCALE)
        bundle.putString(UserAccount.PHOTO_URL, TEST_PHOTO_URL)
        bundle.putString(UserAccount.THUMBNAIL_URL, TEST_THUMBNAIL_URL)
        bundle.putString(UserAccount.LIGHTNING_DOMAIN, TEST_LIGHTNING_DOMAIN)
        bundle.putString(UserAccount.LIGHTNING_SID, TEST_LIGHTNING_SID)
        bundle.putString(UserAccount.VF_DOMAIN, TEST_VF_DOMAIN)
        bundle.putString(UserAccount.VF_SID, TEST_VF_SID)
        bundle.putString(UserAccount.CONTENT_DOMAIN, TEST_CONTENT_DOMAIN)
        bundle.putString(UserAccount.CONTENT_SID, TEST_CONTENT_SID)
        bundle.putString(UserAccount.CSRF_TOKEN, TEST_CSRF_TOKEN)
        bundle.putBoolean(UserAccount.NATIVE_LOGIN, TEST_NATIVE_LOGIN)
        bundle.putString(UserAccount.COOKIE_CLIENT_SRC, TEST_COOKIE_CLIENT_SRC)
        bundle.putString(UserAccount.COOKIE_SID_CLIENT, TEST_COOKIE_SID_CLIENT)
        bundle.putString(UserAccount.SID_COOKIE_NAME, TEST_SID_COOKIE_NAME)
        bundle.putString(UserAccount.CLIENT_ID, TEST_CLIENT_ID)
        bundle.putString(UserAccount.PARENT_SID, TEST_PARENT_SID)
        bundle.putString(UserAccount.TOKEN_FORMAT, TEST_TOKEN_FORMAT)
        bundle.putString(UserAccount.BEACON_CHILD_CONSUMER_KEY, TEST_BEACON_CHILD_CONSUMER_KEY)
        bundle.putString(UserAccount.BEACON_CHILD_CONSUMER_SECRET, TEST_BEACON_CHILD_CONSUMER_SECRET)
        bundle.putString(UserAccount.SCOPE, TEST_SCOPE)
        bundle = MapUtil.addMapToBundle(createAdditionalOauthValues(), createAdditionalOauthKeys(), bundle)!!
        return bundle
    }

    private fun createAdditionalOauthKeys(): List<String> {
        return listOf(TEST_CUSTOM_KEY)
    }

    /**
     * Check that the account passed has the test values
     */
    fun checkTestAccount(account: UserAccount) {
        checkTestAccount(account, true)
    }

    /**
     * Check that the account passed has the test values
     */
    fun checkTestAccount(account: UserAccount, expectBeaconChildFields: Boolean) {
        Assert.assertEquals("Auth token should match", TEST_AUTH_TOKEN, account.authToken)
        Assert.assertEquals("Refresh token should match", TEST_REFRESH_TOKEN, account.refreshToken)
        Assert.assertEquals("Login server URL should match", TEST_LOGIN_URL, account.loginServer)
        Assert.assertEquals("Identity URL should match", TEST_IDENTITY_URL, account.idUrl)
        Assert.assertEquals("Instance URL should match", TEST_INSTANCE_URL, account.instanceServer)
        Assert.assertEquals("API instance URL should match", TEST_API_INSTANCE_URL, account.apiInstanceServer)
        Assert.assertEquals("Org ID should match", TEST_ORG_ID, account.orgId)
        Assert.assertEquals("User ID should match", TEST_USER_ID, account.userId)
        Assert.assertEquals("User name should match", TEST_USERNAME, account.username)
        Assert.assertEquals("Account name should match", TEST_ACCOUNT_NAME, account.accountName)
        Assert.assertEquals("Community ID should match", TEST_COMMUNITY_ID, account.communityId)
        Assert.assertEquals("Community URL should match", TEST_COMMUNITY_URL, account.communityUrl)
        Assert.assertEquals("First name should match", TEST_FIRST_NAME, account.firstName)
        Assert.assertEquals("Last name should match", TEST_LAST_NAME, account.lastName)
        Assert.assertEquals("Display name should match", TEST_DISPLAY_NAME, account.displayName)
        Assert.assertEquals("Email should match", TEST_EMAIL, account.email)
        Assert.assertEquals("Photo URL should match", TEST_PHOTO_URL, account.photoUrl)
        Assert.assertEquals("Thumbnail URL should match", TEST_THUMBNAIL_URL, account.thumbnailUrl)
        Assert.assertEquals("Language should match", TEST_LANGUAGE, account.language)
        Assert.assertEquals("Locale should match", TEST_LOCALE, account.locale)
        Assert.assertEquals("Lightning domain should match", TEST_LIGHTNING_DOMAIN, account.lightningDomain)
        Assert.assertEquals("Lightning sid should match", TEST_LIGHTNING_SID, account.lightningSid)
        Assert.assertEquals("Content domain should match", TEST_CONTENT_DOMAIN, account.contentDomain)
        Assert.assertEquals("Content sid should match", TEST_CONTENT_SID, account.contentSid)
        Assert.assertEquals("Vf domain should match", TEST_VF_DOMAIN, account.vfDomain)
        Assert.assertEquals("Vf sid should match", TEST_VF_SID, account.vfSid)
        Assert.assertEquals("Native login should match", TEST_NATIVE_LOGIN, account.nativeLogin)
        Assert.assertEquals("Cookie client src should match", TEST_COOKIE_CLIENT_SRC, account.cookieClientSrc)
        Assert.assertEquals("Cookie sid client should match", TEST_COOKIE_SID_CLIENT, account.cookieSidClient)
        Assert.assertEquals("Sid cookie name should match", TEST_SID_COOKIE_NAME, account.sidCookieName)
        Assert.assertEquals("Parent sid should match", TEST_PARENT_SID, account.parentSid)
        Assert.assertEquals("Token format should match", TEST_TOKEN_FORMAT, account.tokenFormat)
        if (expectBeaconChildFields) {
            Assert.assertEquals("Beacon child consumer key should match", TEST_BEACON_CHILD_CONSUMER_KEY, account.beaconChildConsumerKey)
            Assert.assertEquals("Beacon child consumer secret should match", TEST_BEACON_CHILD_CONSUMER_SECRET, account.beaconChildConsumerSecret)
        } else {
            Assert.assertNull("Beacon child consumer key should be null", account.beaconChildConsumerKey)
            Assert.assertNull("Beacon child consumer secret should be null", account.beaconChildConsumerSecret)
        }
        Assert.assertEquals("Scope should match", TEST_SCOPE, account.scope)
        Assert.assertEquals("Additional OAuth values should match", createAdditionalOauthValues(), account.additionalOauthValues)
    }

    /**
     * Check that the account passed has the test values
     */
    fun checkOtherTestAccount(account: UserAccount) {
        Assert.assertEquals("Auth token should match", TEST_AUTH_TOKEN, account.authToken)
        Assert.assertEquals("Refresh token should match", TEST_REFRESH_TOKEN, account.refreshToken)
        Assert.assertEquals("Login server URL should match", TEST_LOGIN_URL, account.loginServer)
        Assert.assertEquals("Identity URL should match", TEST_IDENTITY_URL, account.idUrl)
        Assert.assertEquals("Instance URL should match", TEST_INSTANCE_URL, account.instanceServer)
        Assert.assertEquals("API instance URL should match", TEST_API_INSTANCE_URL, account.apiInstanceServer)
        Assert.assertEquals("Org ID should match", TEST_ORG_ID_2, account.orgId)
        Assert.assertEquals("User ID should match", TEST_USER_ID_2, account.userId)
        Assert.assertEquals("User name should match", TEST_USERNAME_2, account.username)
        Assert.assertEquals("Account name should match", TEST_ACCOUNT_NAME_2, account.accountName)
        Assert.assertEquals("Community ID should match", TEST_COMMUNITY_ID, account.communityId)
        Assert.assertEquals("Community URL should match", TEST_COMMUNITY_URL, account.communityUrl)
        Assert.assertEquals("First name should match", TEST_FIRST_NAME, account.firstName)
        Assert.assertEquals("Last name should match", TEST_LAST_NAME, account.lastName)
        Assert.assertEquals("Display name should match", TEST_DISPLAY_NAME, account.displayName)
        Assert.assertEquals("Email should match", TEST_EMAIL, account.email)
        Assert.assertEquals("Photo URL should match", TEST_PHOTO_URL, account.photoUrl)
        Assert.assertEquals("Thumbnail URL should match", TEST_THUMBNAIL_URL, account.thumbnailUrl)
        Assert.assertEquals("Language should match", TEST_LANGUAGE, account.language)
        Assert.assertEquals("Locale should match", TEST_LOCALE, account.locale)
        Assert.assertEquals("Lightning domain should match", TEST_LIGHTNING_DOMAIN, account.lightningDomain)
        Assert.assertEquals("Lightning sid should match", TEST_LIGHTNING_SID, account.lightningSid)
        Assert.assertEquals("Content domain should match", TEST_CONTENT_DOMAIN, account.contentDomain)
        Assert.assertEquals("Content sid should match", TEST_CONTENT_SID, account.contentSid)
        Assert.assertEquals("Vf domain should match", TEST_VF_DOMAIN, account.vfDomain)
        Assert.assertEquals("Vf sid should match", TEST_VF_SID, account.vfSid)
        Assert.assertEquals("Native login should match", TEST_NATIVE_LOGIN, account.nativeLogin)
        Assert.assertEquals("Cookie client src should match", TEST_COOKIE_CLIENT_SRC, account.cookieClientSrc)
        Assert.assertEquals("Cookie sid client should match", TEST_COOKIE_SID_CLIENT, account.cookieSidClient)
        Assert.assertEquals("Sid cookie name should match", TEST_SID_COOKIE_NAME, account.sidCookieName)
        Assert.assertEquals("Parent sid should match", TEST_PARENT_SID, account.parentSid)
        Assert.assertEquals("Token format should match", TEST_TOKEN_FORMAT, account.tokenFormat)
        Assert.assertEquals("Beacon child consumer key should match", TEST_BEACON_CHILD_CONSUMER_KEY, account.beaconChildConsumerKey)
        Assert.assertEquals("Beacon child consumer secret should match", TEST_BEACON_CHILD_CONSUMER_SECRET, account.beaconChildConsumerSecret)
        Assert.assertEquals("Scope should match", TEST_SCOPE_2, account.scope)
        Assert.assertEquals("Additional OAuth values should match", createAdditionalOauthValues(), account.additionalOauthValues)
    }

    private fun createTokenEndpointResponseLikeUserAgentFlow(): OAuth2.TokenEndpointResponse {
        val params = createTokenEndpointParams()
        return OAuth2.TokenEndpointResponse(params, createAdditionalOauthKeys())
    }

    private fun createTokenEndpointResponseLikeWebServerFlow(): OAuth2.TokenEndpointResponse {
        val params = createTokenEndpointParams()
        params["auto_installed_app_org_consumer_key"] = TEST_BEACON_CHILD_CONSUMER_KEY
        params["auto_installed_app_org_consumer_secret"] = TEST_BEACON_CHILD_CONSUMER_SECRET
        val responseJson = JSONObject(params as Map<*, *>)
        val mediaType = ("application/json").toMediaType()
        val responseBody = ResponseBody.create(mediaType, responseJson.toString())

        val response = Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://something.salesforce.com").build())
            .body(responseBody)
            .build()

        return OAuth2.TokenEndpointResponse(response, createAdditionalOauthKeys())
    }

    private fun createTokenEndpointParams(): MutableMap<String, String> {
        val params = HashMap<String, String>()

        params["access_token"] = TEST_AUTH_TOKEN
        params["refresh_token"] = TEST_REFRESH_TOKEN
        params["instance_url"] = TEST_INSTANCE_URL
        params["api_instance_url"] = TEST_API_INSTANCE_URL
        params["id"] = TEST_IDENTITY_URL
        params["sfdc_community_id"] = TEST_COMMUNITY_ID
        params["sfdc_community_url"] = TEST_COMMUNITY_URL
        params.putAll(createAdditionalOauthValues())
        params["lightning_domain"] = TEST_LIGHTNING_DOMAIN
        params["lightning_sid"] = TEST_LIGHTNING_SID
        params["visualforce_domain"] = TEST_VF_DOMAIN
        params["visualforce_sid"] = TEST_VF_SID
        params["content_domain"] = TEST_CONTENT_DOMAIN
        params["content_sid"] = TEST_CONTENT_SID
        params["csrf_token"] = TEST_CSRF_TOKEN
        params["cookie-clientSrc"] = TEST_COOKIE_CLIENT_SRC
        params["cookie-sid_Client"] = TEST_COOKIE_SID_CLIENT
        params["sidCookieName"] = TEST_SID_COOKIE_NAME
        params["parent_sid"] = TEST_PARENT_SID
        params["token_format"] = TEST_TOKEN_FORMAT
        params["scope"] = TEST_SCOPE

        return params
    }

    private fun createIdServiceResponse(): OAuth2.IdServiceResponse {
        val response = JSONObject()

        response.put("id", TEST_IDENTITY_URL)
        response.put("username", TEST_USERNAME)
        response.put("email", TEST_EMAIL)
        response.put("first_name", TEST_FIRST_NAME)
        response.put("last_name", TEST_LAST_NAME)
        response.put("nick_name", TEST_NICK_NAME)
        response.put("user_type", TEST_USER_TYPE)
        response.put("display_name", TEST_DISPLAY_NAME)
        response.put("last_modified_date", TEST_LAST_MODIFIED_DATE)
        response.put("user_id", TEST_USER_ID)
        response.put("organization_id", TEST_ORG_ID)
        val photos = JSONObject()
        photos.put("picture", TEST_PHOTO_URL)
        photos.put("thumbnail", TEST_THUMBNAIL_URL)
        response.put("photos", photos)
        response.put("language", TEST_LANGUAGE)
        response.put("locale", TEST_LOCALE)
        return OAuth2.IdServiceResponse(response)
    }
}
