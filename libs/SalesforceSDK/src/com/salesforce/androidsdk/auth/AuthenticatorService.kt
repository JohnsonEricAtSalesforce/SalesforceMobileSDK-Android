/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2.OAuthFailedException
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * The service used for taking care of authentication for a Salesforce-based application.
 * See [AbstractAccountAuthenticator](http://developer.android.com/reference/android/accounts/AbstractAccountAuthenticator.html).
 */
open class AuthenticatorService : Service() {

    private fun getAuthenticator(): Authenticator {
        if (AUTHENTICATOR == null) {
            AUTHENTICATOR = Authenticator(this)
        }
        return AUTHENTICATOR!!
    }

    override fun onBind(intent: Intent): IBinder? {
        return if (AccountManager.ACTION_AUTHENTICATOR_INTENT == intent.action) {
            getAuthenticator().iBinder
        } else {
            null
        }
    }

    private class Authenticator(private val context: Context) : AbstractAccountAuthenticator(context) {

        override fun addAccount(
            response: AccountAuthenticatorResponse,
            accountType: String,
            authTokenType: String?,
            requiredFeatures: Array<String>?,
            options: Bundle?
        ): Bundle {
            return makeAuthIntentBundle(response, options)
        }

        @Throws(NetworkErrorException::class)
        override fun getAuthToken(
            response: AccountAuthenticatorResponse,
            account: Account,
            authTokenType: String,
            options: Bundle?
        ): Bundle {
            val originalUserAccount = UserAccountManager.getInstance().buildUserAccount(account)
                ?: return makeAuthIntentBundle(response, options)

            try {
                val addlParamsMap = originalUserAccount.additionalOauthValues
                val tokenServer = OAuth2.overrideLoginServerIfNeeded(originalUserAccount)
                SalesforceSDKLogger.i(TAG, "Initiating token refresh to host: " + tokenServer.host)
                val tr = OAuth2.refreshAuthToken(
                    HttpAccess.DEFAULT,
                    tokenServer,
                    originalUserAccount.clientIdForRefresh!!,
                    originalUserAccount.refreshToken!!,
                    addlParamsMap
                )

                val updatedUserAccount = UserAccountBuilder.getInstance()
                    .populateFromUserAccount(originalUserAccount)
                    .allowUnset(false)
                    .populateFromTokenEndpointResponse(tr)
                    .build()

                val resBundle = UserAccountManager.getInstance().updateAccount(account, updatedUserAccount)
                updatedUserAccount.downloadProfilePhoto()
                UserAccountManager.getInstance().clearCachedCurrentUser()

                return resBundle
            } catch (ofe: OAuthFailedException) {
                SalesforceSDKLogger.i(
                    TAG, "Token endpoint error: (Error: " +
                            ofe.tokenErrorResponse.error + ", Status Code: " + ofe.httpStatusCode + ")", ofe
                )

                // Terminal errors (except retriable attestation) redirect to login.
                if (ofe.tokenErrorResponse.errorCode != OAuthErrorCode.APP_ATTESTATION_FAILED_RETRY && ofe.isRefreshTokenInvalid) {
                    return makeAuthIntentBundle(response, options)
                }

                val resBundle = Bundle()
                resBundle.putString(AccountManager.KEY_ERROR_CODE, ofe.tokenErrorResponse.error)
                resBundle.putString(AccountManager.KEY_ERROR_MESSAGE, ofe.tokenErrorResponse.errorDescription)
                return resBundle
            } catch (e: Exception) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while getting new auth token", e)
                throw NetworkErrorException(e)
            }
        }

        private fun makeAuthIntentBundle(response: AccountAuthenticatorResponse, options: Bundle?): Bundle {
            val reply = Bundle()
            val i = Intent(context, SalesforceSDKManager.getInstance().loginActivityClass)
            i.setPackage(context.packageName)
            i.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            if (options != null) {
                i.putExtras(options)
            }
            reply.putParcelable(AccountManager.KEY_INTENT, i)
            return reply
        }

        override fun updateCredentials(
            response: AccountAuthenticatorResponse,
            account: Account,
            authTokenType: String?,
            options: Bundle?
        ): Bundle? = null

        override fun confirmCredentials(
            response: AccountAuthenticatorResponse,
            account: Account,
            options: Bundle?
        ): Bundle? = null

        override fun editProperties(
            response: AccountAuthenticatorResponse,
            accountType: String
        ): Bundle? = null

        override fun getAuthTokenLabel(authTokenType: String): String? = null

        override fun hasFeatures(
            response: AccountAuthenticatorResponse,
            account: Account,
            features: Array<String>
        ): Bundle? = null
    }

    companion object {
        // Keys to extra info in the account.
        const val KEY_LOGIN_URL = "loginUrl"
        const val KEY_INSTANCE_URL = "instanceUrl"
        const val KEY_API_INSTANCE_URL = "apiInstanceUrl"
        const val KEY_USER_ID = "userId"
        const val KEY_CLIENT_ID = "clientId"
        const val KEY_ORG_ID = "orgId"
        const val KEY_USERNAME = "username"
        const val KEY_ID_URL = "id"
        const val KEY_COMMUNITY_ID = "communityId"
        const val KEY_COMMUNITY_URL = "communityUrl"
        const val KEY_EMAIL = "email"
        const val KEY_FIRST_NAME = "first_name"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PHOTO_URL = "photoUrl"
        const val KEY_THUMBNAIL_URL = "thumbnailUrl"
        const val KEY_LIGHTNING_DOMAIN = "lightningDomain"
        const val KEY_LIGHTNING_SID = "lightningSid"
        const val KEY_VF_DOMAIN = "vfDomain"
        const val KEY_VF_SID = "vfSid"
        const val KEY_CONTENT_DOMAIN = "contentDomain"
        const val KEY_CONTENT_SID = "contentSid"
        const val KEY_CSRF_TOKEN = "csrfToken"
        const val KEY_NATIVE_LOGIN = "nativeLogin"
        const val KEY_LANGUAGE = "language"
        const val KEY_LOCALE = "locale"
        const val KEY_COOKIE_CLIENT_SRC = "cookie-clientSrc"
        const val KEY_COOKIE_SID_CLIENT = "cookie-sid_Client"
        const val KEY_SID_COOKIE_NAME = "sidCookieName"
        const val KEY_PARENT_SID = "parentSid"
        const val KEY_TOKEN_FORMAT = "tokenFormat"
        const val KEY_BEACON_CHILD_CONSUMER_KEY = "auto_installed_app_org_consumer_key"
        const val KEY_BEACON_CHILD_CONSUMER_SECRET = "auto_installed_app_org_consumer_secret"
        const val KEY_SCOPE = "scope"
        const val KEY_FEATURE_FLAGS = "feature_flags"

        private const val TAG = "AuthenticatorService"

        private var AUTHENTICATOR: Authenticator? = null
    }
}
