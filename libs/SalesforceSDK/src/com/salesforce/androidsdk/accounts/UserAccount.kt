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
package com.salesforce.androidsdk.accounts

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.Nullable
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.ScopeParser
import com.salesforce.androidsdk.util.MapUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * This class represents a single user account that is currently
 * logged in against a Salesforce endpoint. It encapsulates data
 * that is used to uniquely identify a single user account.
 *
 * @author bhariharan
 */
class UserAccount internal constructor(
    val authToken: String?,
    val refreshToken: String?,
    val loginServer: String?,
    val idUrl: String?,
    val instanceServer: String?,
    val orgId: String?,
    val userId: String?,
    val username: String?,
    val accountName: String?,
    val communityId: String?,
    val communityUrl: String?,
    val firstName: String?,
    val lastName: String?,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val thumbnailUrl: String?,
    val additionalOauthValues: Map<String, String>?,
    val lightningDomain: String?,
    val lightningSid: String?,
    val vfDomain: String?,
    val vfSid: String?,
    val contentDomain: String?,
    val contentSid: String?,
    val csrfToken: String?,
    val nativeLogin: Boolean?,
    val language: String?,
    val locale: String?,
    val cookieClientSrc: String?,
    val cookieSidClient: String?,
    val sidCookieName: String?,
    val clientId: String?,
    val parentSid: String?,
    val tokenFormat: String?,
    val beaconChildConsumerKey: String?,
    val beaconChildConsumerSecret: String?,
    val apiInstanceServer: String?,
    val scope: String?
) {

    init {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_USER_AUTH)
    }

    /**
     * Construct UserAccount from JSON.
     *
     * @param object JSON object.
     * @param applicationName the application name.
     * @param additionalOauthKeys additional auth keys.
     */
    internal constructor(
        `object`: JSONObject?,
        applicationName: String,
        additionalOauthKeys: List<String>?
    ) : this(
        authToken = `object`?.optString(AUTH_TOKEN, null),
        refreshToken = `object`?.optString(REFRESH_TOKEN, null),
        loginServer = `object`?.optString(LOGIN_SERVER, null),
        idUrl = `object`?.optString(ID_URL, null),
        instanceServer = `object`?.optString(INSTANCE_SERVER, null),
        orgId = `object`?.optString(ORG_ID, null),
        userId = `object`?.optString(USER_ID, null),
        username = `object`?.optString(USERNAME, null),
        accountName = if (`object` != null) {
            val username = `object`.optString(USERNAME, null)
            val instanceServer = `object`.optString(INSTANCE_SERVER, null)
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(instanceServer)) {
                String.format("%s (%s) (%s)", username, instanceServer, applicationName)
            } else null
        } else null,
        communityId = `object`?.optString(COMMUNITY_ID, null),
        communityUrl = `object`?.optString(COMMUNITY_URL, null),
        firstName = `object`?.optString(FIRST_NAME, null),
        lastName = `object`?.optString(LAST_NAME, null),
        displayName = `object`?.optString(DISPLAY_NAME, null),
        email = `object`?.optString(EMAIL, null),
        photoUrl = `object`?.optString(PHOTO_URL, null),
        thumbnailUrl = `object`?.optString(THUMBNAIL_URL, null),
        additionalOauthValues = MapUtil.addJSONObjectToMap(`object`, additionalOauthKeys, null),
        lightningDomain = `object`?.optString(LIGHTNING_DOMAIN, null),
        lightningSid = `object`?.optString(LIGHTNING_SID, null),
        vfDomain = `object`?.optString(VF_DOMAIN, null),
        vfSid = `object`?.optString(VF_SID, null),
        contentDomain = `object`?.optString(CONTENT_DOMAIN, null),
        contentSid = `object`?.optString(CONTENT_SID, null),
        csrfToken = `object`?.optString(CSRF_TOKEN, null),
        nativeLogin = `object`?.optBoolean(NATIVE_LOGIN),
        language = `object`?.optString(LANGUAGE, null),
        locale = `object`?.optString(LOCALE, null),
        cookieClientSrc = `object`?.optString(COOKIE_CLIENT_SRC, null),
        cookieSidClient = `object`?.optString(COOKIE_SID_CLIENT, null),
        sidCookieName = `object`?.optString(SID_COOKIE_NAME, null),
        clientId = `object`?.optString(CLIENT_ID, null),
        parentSid = `object`?.optString(PARENT_SID, null),
        tokenFormat = `object`?.optString(TOKEN_FORMAT, null),
        beaconChildConsumerKey = `object`?.optString(BEACON_CHILD_CONSUMER_KEY, null),
        beaconChildConsumerSecret = `object`?.optString(BEACON_CHILD_CONSUMER_SECRET, null),
        apiInstanceServer = `object`?.optString(API_INSTANCE_SERVER, null),
        scope = `object`?.optString(SCOPE, null)
    )

    /**
     * Construct UserAccount from JSON.
     * @param object
     */
    constructor(`object`: JSONObject) : this(
        `object`,
        SalesforceSDKManager.getInstance().applicationName,
        SalesforceSDKManager.getInstance().additionalOauthKeys
    )

    /**
     * Construct UserAccount from Bundle.
     *
     * @param bundle Bundle.
     * @param additionalOauthKeys additional oauth keys.
     */
    internal constructor(bundle: Bundle?, additionalOauthKeys: List<String>?) : this(
        authToken = bundle?.getString(AUTH_TOKEN),
        refreshToken = bundle?.getString(REFRESH_TOKEN),
        loginServer = bundle?.getString(LOGIN_SERVER),
        idUrl = bundle?.getString(ID_URL),
        instanceServer = bundle?.getString(INSTANCE_SERVER),
        orgId = bundle?.getString(ORG_ID),
        userId = bundle?.getString(USER_ID),
        username = bundle?.getString(USERNAME),
        accountName = bundle?.getString(ACCOUNT_NAME),
        communityId = bundle?.getString(COMMUNITY_ID),
        communityUrl = bundle?.getString(COMMUNITY_URL),
        firstName = bundle?.getString(FIRST_NAME),
        lastName = bundle?.getString(LAST_NAME),
        displayName = bundle?.getString(DISPLAY_NAME),
        email = bundle?.getString(EMAIL),
        photoUrl = bundle?.getString(PHOTO_URL),
        thumbnailUrl = bundle?.getString(THUMBNAIL_URL),
        additionalOauthValues = MapUtil.addBundleToMap(bundle, additionalOauthKeys, null),
        lightningDomain = bundle?.getString(LIGHTNING_DOMAIN),
        lightningSid = bundle?.getString(LIGHTNING_SID),
        vfDomain = bundle?.getString(VF_DOMAIN),
        vfSid = bundle?.getString(VF_SID),
        contentDomain = bundle?.getString(CONTENT_DOMAIN),
        contentSid = bundle?.getString(CONTENT_SID),
        csrfToken = bundle?.getString(CSRF_TOKEN),
        nativeLogin = bundle?.getBoolean(NATIVE_LOGIN),
        language = bundle?.getString(LANGUAGE),
        locale = bundle?.getString(LOCALE),
        cookieClientSrc = bundle?.getString(COOKIE_CLIENT_SRC),
        cookieSidClient = bundle?.getString(COOKIE_SID_CLIENT),
        sidCookieName = bundle?.getString(SID_COOKIE_NAME),
        clientId = bundle?.getString(CLIENT_ID),
        parentSid = bundle?.getString(PARENT_SID),
        tokenFormat = bundle?.getString(TOKEN_FORMAT),
        beaconChildConsumerKey = bundle?.getString(BEACON_CHILD_CONSUMER_KEY),
        beaconChildConsumerSecret = bundle?.getString(BEACON_CHILD_CONSUMER_SECRET),
        apiInstanceServer = bundle?.getString(API_INSTANCE_SERVER),
        scope = bundle?.getString(SCOPE)
    )

    /**
     * Construct UserAccount from Bundle
     *
     * @param bundle
     */
    constructor(bundle: Bundle) : this(
        bundle,
        SalesforceSDKManager.getInstance().additionalOauthKeys
    )

    /**
     * Returns the VF domain for this user.
     *
     * @return VF domain.
     */
    fun getVFDomain(): String? {
        return vfDomain
    }

    /**
     * Returns the VF SID for this user.
     *
     * @return VF SID.
     */
    fun getVFSid(): String? {
        return vfSid
    }

    /**
     * Returns the CSRF token for this user.
     *
     * @return CSRF token.
     */
    fun getCSRFToken(): String? {
        return csrfToken
    }

    /**
     * Returns the oauth client id to use for refresh
     * In the case of beacon app, the beacon child consumer key returned during login should be used instead of the configured consumer key
     * @return client id to use for refresh.
     */
    fun getClientIdForRefresh(): String? {
        return if (!TextUtils.isEmpty(beaconChildConsumerKey)) beaconChildConsumerKey else clientId
    }

    /**
     * Checks whether the provided scope exists in this account's scope list.
     *
     * @param scopeToCheck Scope name to check.
     * @return True if present, false otherwise.
     */
    fun hasScope(scopeToCheck: String?): Boolean {
        return ScopeParser(scope).hasScope(scopeToCheck)
    }

    /**
     * Fetches this user's profile photo from the cache.
     *
     * @return User's profile photo.
     */
    fun getProfilePhoto(): Bitmap? {
        val file = getProfilePhotoFile() ?: return null
        val bitmapOptions = BitmapFactory.Options()
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
    }

    /**
     * Fetches this user's profile photo from the server and stores it in the cache.
     */
    fun downloadProfilePhoto() {
        val file = getProfilePhotoFile()
        if (photoUrl == null || file == null) {
            return
        }
        val srcUri = Uri.parse(photoUrl)
        val destUri = Uri.fromFile(file)
        if (srcUri == null || destUri == null) {
            return
        }

        // Checks if DownloadManager is enabled on the device, to ensure it doesn't crash.
        val pm = SalesforceSDKManager.getInstance().appContext.packageManager
        val state = pm.getApplicationEnabledSetting("com.android.providers.downloads")
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        ) {
            val downloadReq = DownloadManager.Request(srcUri)
            downloadReq.setDestinationUri(destUri)
            downloadReq.addRequestHeader(AUTHORIZATION, BEARER + authToken)
            downloadReq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            downloadReq.setVisibleInDownloadsUi(false)
            val downloadManager =
                SalesforceSDKManager.getInstance().appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.enqueue(downloadReq)
        }
    }

    /**
     * Returns the org level storage path for this user account, relative to
     * the higher level directory of app data. The higher level directory
     * could be 'files'. The output is of the format '/{orgID}/'.
     * This storage path is meant for data that can be shared
     * across multiple users of the same org.
     *
     * @return File storage path.
     */
    fun getOrgLevelStoragePath(): String {
        return "$FORWARD_SLASH$orgId$FORWARD_SLASH"
    }

    /**
     * Returns the user level storage path for this user account, relative to
     * the higher level directory of app data. The higher level directory
     * could be 'files'. The output is of the format '/{orgID}/{userId}/'.
     * This storage path is meant for data that is unique to a particular
     * user in an org, but common across all the communities that the
     * user is a member of within that org.
     *
     * @return File storage path.
     */
    fun getUserLevelStoragePath(): String {
        return "$FORWARD_SLASH$orgId$FORWARD_SLASH$userId$FORWARD_SLASH"
    }

    /**
     * Returns the storage path for this user account, relative to the higher
     * level directory of app data. The higher level directory could be 'files'.
     * The output is of the format '/{orgID}/{userID}/{communityID}/'.
     * If 'communityID' is null or the internal community ID, then the output
     * would be '/{orgID}/{userID}/internal/'. This storage path is meant for
     * data that is unique to a particular user in a specific community.
     *
     * @return File storage path.
     */
    fun getCommunityLevelStoragePath(): String {
        var leafDir = INTERNAL_COMMUNITY_PATH
        if (!TextUtils.isEmpty(communityId) && communityId != INTERNAL_COMMUNITY_ID) {
            leafDir = communityId!!
        }
        return getCommunityLevelStoragePath(leafDir)
    }

    /**
     * Returns the storage path for this user account, relative to the higher
     * level directory of app data. The higher level directory could be 'files'.
     * The output is of the format '/{orgID}/{userID}/{communityID}/'.
     * If 'communityID' is null or the internal community ID, then the output
     * would be '/{orgID}/{userID}/internal/'. This storage path is meant for
     * data that is unique to a particular user in a specific community.
     *
     * @param communityId Community ID. Pass 'null' for internal community.
     * @return File storage path.
     */
    fun getCommunityLevelStoragePath(communityId: String?): String {
        var leafDir = INTERNAL_COMMUNITY_PATH
        if (!TextUtils.isEmpty(communityId) && communityId != INTERNAL_COMMUNITY_ID) {
            leafDir = communityId!!
        }
        return "$FORWARD_SLASH$orgId$FORWARD_SLASH$userId$FORWARD_SLASH$leafDir$FORWARD_SLASH"
    }

    /**
     * Returns a unique suffix for this user account, that can be appended
     * to a file to uniquely identify this account, at an org level.
     * The output is of the format '_{orgID}'. This suffix is meant
     * for data that can be shared across multiple users of the same org.
     *
     * @return Filename suffix.
     */
    fun getOrgLevelFilenameSuffix(): String {
        return "$UNDERSCORE$orgId"
    }

    /**
     * Returns a unique suffix for this user account, that can be appended
     * to a file to uniquely identify this account, at a user level.
     * The output is of the format '_{orgID}_{userID}'. This suffix
     * is meant for data that is unique to a particular user in an org,
     * but common across all the communities that the user is a member
     * of within that org.
     *
     * @return Filename suffix.
     */
    fun getUserLevelFilenameSuffix(): String {
        return "$UNDERSCORE$orgId$UNDERSCORE$userId"
    }

    /**
     * Returns a unique suffix for this user account, that can be appended
     * to a file to uniquely identify this account, at a community level.
     * The output is of the format '_{orgID}_{userID}_{communityID}'.
     * If 'communityID' is null or the internal community ID, then the output
     * would be '_{orgID}_{userID}_internal'. This storage path is meant for
     * data that is unique to a particular user in a specific community.
     *
     * @return Filename suffix.
     */
    fun getCommunityLevelFilenameSuffix(): String {
        var leafDir = INTERNAL_COMMUNITY_PATH
        if (!TextUtils.isEmpty(communityId) && communityId != INTERNAL_COMMUNITY_ID) {
            leafDir = communityId!!
        }
        return getCommunityLevelFilenameSuffix(leafDir)
    }

    /**
     * Returns a unique suffix for this user account, that can be appended
     * to a file to uniquely identify this account, at a community level.
     * The output is of the format '_{orgID}_{userID}_{communityID}'.
     * If 'communityID' is null or the internal community ID, then the output
     * would be '_{orgID}_{userID}_internal'. This storage path is meant for
     * data that is unique to a particular user in a specific community.
     *
     * @param communityId Community ID. Pass 'null' for internal community.
     * @return Filename suffix.
     */
    fun getCommunityLevelFilenameSuffix(communityId: String?): String {
        var leafDir = INTERNAL_COMMUNITY_PATH
        if (!TextUtils.isEmpty(communityId) && communityId != INTERNAL_COMMUNITY_ID) {
            leafDir = communityId!!
        }
        return "$UNDERSCORE$orgId$UNDERSCORE$userId$UNDERSCORE$leafDir"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UserAccount) {
            return false
        }
        if (userId == null || orgId == null || other.userId == null || other.orgId == null) {
            return false
        }
        return other.userId == userId && other.orgId == orgId
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = result xor orgId.hashCode() + result * 37
        return result
    }

    /**
     * Returns a JSON representation of this instance.
     *
     * @param additionalOauthKeys: values to pull from the additionalOauthValues
     * @return JSONObject instance.
     */
    internal fun toJson(additionalOauthKeys: List<String>?): JSONObject {
        var `object` = JSONObject()
        try {
            `object`.put(AUTH_TOKEN, authToken)
            `object`.put(REFRESH_TOKEN, refreshToken)
            `object`.put(LOGIN_SERVER, loginServer)
            `object`.put(ID_URL, idUrl)
            `object`.put(INSTANCE_SERVER, instanceServer)
            `object`.put(API_INSTANCE_SERVER, apiInstanceServer)
            `object`.put(ORG_ID, orgId)
            `object`.put(USER_ID, userId)
            `object`.put(USERNAME, username)
            `object`.put(COMMUNITY_ID, communityId)
            `object`.put(COMMUNITY_URL, communityUrl)
            `object`.put(FIRST_NAME, firstName)
            `object`.put(LAST_NAME, lastName)
            `object`.put(DISPLAY_NAME, displayName)
            `object`.put(EMAIL, email)
            `object`.put(PHOTO_URL, photoUrl)
            `object`.put(THUMBNAIL_URL, thumbnailUrl)
            `object`.put(LIGHTNING_DOMAIN, lightningDomain)
            `object`.put(LIGHTNING_SID, lightningSid)
            `object`.put(VF_DOMAIN, vfDomain)
            `object`.put(VF_SID, vfSid)
            `object`.put(CONTENT_DOMAIN, contentDomain)
            `object`.put(CONTENT_SID, contentSid)
            `object`.put(CSRF_TOKEN, csrfToken)
            `object`.put(NATIVE_LOGIN, nativeLogin)
            `object`.put(LANGUAGE, language)
            `object`.put(LOCALE, locale)
            `object`.put(COOKIE_CLIENT_SRC, cookieClientSrc)
            `object`.put(COOKIE_SID_CLIENT, cookieSidClient)
            `object`.put(SID_COOKIE_NAME, sidCookieName)
            `object`.put(PARENT_SID, parentSid)
            `object`.put(TOKEN_FORMAT, tokenFormat)
            `object`.put(BEACON_CHILD_CONSUMER_KEY, beaconChildConsumerKey)
            `object`.put(BEACON_CHILD_CONSUMER_SECRET, beaconChildConsumerSecret)
            `object`.put(SCOPE, scope)
            `object` = MapUtil.addMapToJSONObject(additionalOauthValues, additionalOauthKeys, `object`) ?: `object`
        } catch (e: JSONException) {
            SalesforceSDKLogger.e(TAG, "Unable to convert to JSON", e)
        }
        return `object`
    }

    /**
     * Returns a JSON representation of this instance.
     *
     * @return JSONObject instance.
     */
    fun toJson(): JSONObject {
        return toJson(SalesforceSDKManager.getInstance().additionalOauthKeys)
    }

    /**
     * Returns a representation of this instance in a bundle.
     *
     * @param additionalOauthKeys: values to pull from the additionalOauthValues
     * @return Bundle instance.
     */
    internal fun toBundle(additionalOauthKeys: List<String>?): Bundle {
        var `object` = Bundle()
        `object`.putString(AUTH_TOKEN, authToken)
        `object`.putString(REFRESH_TOKEN, refreshToken)
        `object`.putString(LOGIN_SERVER, loginServer)
        `object`.putString(ID_URL, idUrl)
        `object`.putString(INSTANCE_SERVER, instanceServer)
        `object`.putString(API_INSTANCE_SERVER, apiInstanceServer)
        `object`.putString(ORG_ID, orgId)
        `object`.putString(USER_ID, userId)
        `object`.putString(USERNAME, username)
        `object`.putString(ACCOUNT_NAME, accountName)
        `object`.putString(COMMUNITY_ID, communityId)
        `object`.putString(COMMUNITY_URL, communityUrl)
        `object`.putString(FIRST_NAME, firstName)
        `object`.putString(LAST_NAME, lastName)
        `object`.putString(DISPLAY_NAME, displayName)
        `object`.putString(EMAIL, email)
        `object`.putString(LANGUAGE, language)
        `object`.putString(LOCALE, locale)
        `object`.putString(PHOTO_URL, photoUrl)
        `object`.putString(THUMBNAIL_URL, thumbnailUrl)
        `object`.putString(LIGHTNING_DOMAIN, lightningDomain)
        `object`.putString(LIGHTNING_SID, lightningSid)
        `object`.putString(VF_DOMAIN, vfDomain)
        `object`.putString(VF_SID, vfSid)
        `object`.putString(CONTENT_DOMAIN, contentDomain)
        `object`.putString(CONTENT_SID, contentSid)
        `object`.putString(CSRF_TOKEN, csrfToken)
        `object`.putBoolean(NATIVE_LOGIN, nativeLogin ?: false)
        `object`.putString(COOKIE_CLIENT_SRC, cookieClientSrc)
        `object`.putString(COOKIE_SID_CLIENT, cookieSidClient)
        `object`.putString(SID_COOKIE_NAME, sidCookieName)
        `object`.putString(CLIENT_ID, clientId)
        `object`.putString(PARENT_SID, parentSid)
        `object`.putString(TOKEN_FORMAT, tokenFormat)
        `object`.putString(BEACON_CHILD_CONSUMER_KEY, beaconChildConsumerKey)
        `object`.putString(BEACON_CHILD_CONSUMER_SECRET, beaconChildConsumerSecret)
        `object`.putString(SCOPE, scope)
        `object` = MapUtil.addMapToBundle(additionalOauthValues, additionalOauthKeys, `object`) ?: `object`
        return `object`
    }

    /**
     * Returns a representation of this instance in a bundle.
     *
     * @return Bundle instance.
     */
    fun toBundle(): Bundle {
        return toBundle(SalesforceSDKManager.getInstance().additionalOauthKeys)
    }

    private fun getProfilePhotoFile(): File? {
        val filename = PROFILE_PHOTO_PATH_PREFIX + getUserLevelFilenameSuffix() + JPG
        val baseDir = SalesforceSDKManager.getInstance().appContext.externalCacheDir
        return if (baseDir != null) File(baseDir, filename) else null
    }

    companion object {
        const val AUTH_TOKEN = "authToken"
        const val REFRESH_TOKEN = "refreshToken"
        const val LOGIN_SERVER = "loginServer"
        const val ID_URL = "idUrl"
        const val INSTANCE_SERVER = "instanceServer"
        const val API_INSTANCE_SERVER = "apiInstanceServer"
        const val ORG_ID = "orgId"
        const val USER_ID = "userId"
        const val USERNAME = "username"
        const val ACCOUNT_NAME = "accountName"
        const val COMMUNITY_ID = "communityId"
        const val COMMUNITY_URL = "communityUrl"
        const val INTERNAL_COMMUNITY_ID = "000000000000000AAA"
        const val INTERNAL_COMMUNITY_PATH = "internal"
        const val EMAIL = "email"
        const val FIRST_NAME = "first_name"
        const val DISPLAY_NAME = "display_name"
        const val LAST_NAME = "last_name"
        const val PHOTO_URL = "photoUrl"
        const val THUMBNAIL_URL = "thumbnailUrl"
        const val LIGHTNING_DOMAIN = "lightningDomain"
        const val LIGHTNING_SID = "lightningSid"
        const val VF_DOMAIN = "vfDomain"
        const val VF_SID = "vfSid"
        const val CONTENT_DOMAIN = "contentDomain"
        const val CONTENT_SID = "contentSid"
        const val CSRF_TOKEN = "csrfToken"
        const val NATIVE_LOGIN = "native_login"
        const val LANGUAGE = "language"
        const val LOCALE = "locale"
        const val COOKIE_CLIENT_SRC = "cookie-clientSrc"
        const val COOKIE_SID_CLIENT = "cookie-sid_Client"
        const val SID_COOKIE_NAME = "sidCookieName"
        const val CLIENT_ID = "clientId"
        const val PARENT_SID = "parentSid"
        const val TOKEN_FORMAT = "tokenFormat"
        const val BEACON_CHILD_CONSUMER_KEY = "beacon_child_consumer_key"
        const val BEACON_CHILD_CONSUMER_SECRET = "beacon_child_consumer_secret"
        const val SCOPE = "scope"

        private const val TAG = "UserAccount"
        private const val FORWARD_SLASH = "/"
        private const val UNDERSCORE = "_"
        private const val PROFILE_PHOTO_PATH_PREFIX = "profile_photo_"
        private const val AUTHORIZATION = "Authorization"
        private const val BEARER = "Bearer "
        private const val JPG = ".jpg"
    }
}
