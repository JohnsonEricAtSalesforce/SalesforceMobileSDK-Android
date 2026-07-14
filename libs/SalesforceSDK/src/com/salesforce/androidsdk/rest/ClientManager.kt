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
package com.salesforce.androidsdk.rest

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.AuthenticatorService
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.Companion.CLIENT_BLOCKED_ERROR
import com.salesforce.androidsdk.auth.OAuth2.Companion.CLIENT_BLOCKED_RETRY_ERROR
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.CLIENT_BLOCKED
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.REFRESH_TOKEN_EXPIRED
import com.salesforce.androidsdk.rest.RestClient.ClientInfo
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.net.URI
import java.net.URISyntaxException

/**
 * ClientManager is a factory class for RestClient which stores OAuth credentials in the AccountManager.
 * If no account is found, it kicks off the login flow which creates a new account if successful.
 */
class ClientManager(
    ctx: Context,
    private val accountType: String,
    private val revokedTokenShouldLogout: Boolean
) {

    val accountManager: AccountManager = AccountManager.get(ctx)

    /**
     * Method to create a RestClient asynchronously. It is intended to be used by code on the UI thread.
     *
     * If no accounts are found, it will kick off the login flow which will create a new account if successful.
     * After the account is created or if an account already existed, it creates a RestClient and returns it through restClientCallback.
     *
     * Note: The work is actually being done by the service registered to handle authentication for this application account type.
     * @see AuthenticatorService
     *
     * @param activityContext        current activity
     * @param restClientCallback     callback invoked once the RestClient is ready
     */
    fun getRestClient(activityContext: Activity, restClientCallback: RestClientCallback) {
        val acc = getAccount()

        // No account found - let's add one - the AuthenticatorService add account method will start the login activity using either the default login URL or the Salesforce SDK manager's front door URL for Salesforce Identity API UI Bridge
        if (acc == null) {
            SalesforceSDKLogger.i(TAG, "No account of type $accountType found")
            val i = Intent(activityContext,
                SalesforceSDKManager.getInstance().loginActivityClass)
            i.setPackage(activityContext.packageName)
            i.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

            /*
             * Special Note: `LoginActivity` does not actually return a result.
             * However, it does start broadcast intents that need to be received
             * by the starting activity.  Since login activity is started in a
             * new task, the starting activity would become available to be
             * destroyed which unregisters its broadcast intent receivers.
             *
             * Using `startActivityForResult` starts a new task with the
             * starting activity as the "base" intent with login activity as its
             * "visible" sub-activity.  This keeps the starting activity from
             * being eagerly destroyed and sets it as the activity to be started
             * if the user returns the this task after it may have been fully
             * destroyed due to memory pressure.
             *
             * TODO: This short term solution will be replaced in a future release.
             */
            @Suppress("DEPRECATION")
            activityContext.startActivityForResult(i, 0)
        }
        // Account found
        else {
            SalesforceSDKLogger.i(TAG, "Found account of type $accountType")
            val cachedRestClient = peekRestClient()
            restClientCallback.authenticatedRestClient(cachedRestClient)
        }
    }

    /**
     * Method to created an unauthenticated RestClient asynchronously
     * @param activityContext
     * @param restClientCallback
     */
    fun getUnauthenticatedRestClient(activityContext: Activity, restClientCallback: RestClientCallback) {
        restClientCallback.authenticatedRestClient(peekUnauthenticatedRestClient())
    }

    /**
     * Method to create an unauthenticated RestClient.
     * @return
     */
    fun peekUnauthenticatedRestClient(): RestClient {
        return RestClient(RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT!!, null)
    }

    fun peekRestClient(): RestClient {
        return peekRestClient(getAccount())
    }

    /**
     * Method to create RestClient synchronously. It is intended to be used by code not on the UI thread (e.g. ContentProvider).
     *
     * If there is no account, it will throw an exception.
     *
     * @return
     */
    fun peekRestClient(user: UserAccount): RestClient {
        return peekRestClient(getAccountByName(user.accountName!!))
    }

    fun peekRestClient(acc: Account?): RestClient {
        if (acc == null) {
            val e = AccountInfoNotFoundException("No user account found")
            SalesforceSDKLogger.i(TAG, "No user account found", e)
            throw e
        }
        if (SalesforceSDKManager.getInstance().isLoggingOut) {
            val e = AccountInfoNotFoundException("User is logging out")
            SalesforceSDKLogger.i(TAG, "User is logging out", e)
            throw e
        }
        val userAccount = UserAccountManager.getInstance().buildUserAccount(acc)
            ?: throw AccountInfoNotFoundException("Unable to build user account")

        if (userAccount.authToken == null) {
            throw AccountInfoNotFoundException(AccountManager.KEY_AUTHTOKEN)
        }
        if (userAccount.instanceServer == null) {
            throw AccountInfoNotFoundException(AuthenticatorService.KEY_INSTANCE_URL)
        }
        if (userAccount.userId == null) {
            throw AccountInfoNotFoundException(AuthenticatorService.KEY_USER_ID)
        }
        if (userAccount.orgId == null) {
            throw AccountInfoNotFoundException(AuthenticatorService.KEY_ORG_ID)
        }

        try {
            val authTokenProvider = AccMgrAuthTokenProvider(
                this,
                userAccount.instanceServer, userAccount.authToken, userAccount.refreshToken
            )
            val clientInfo = ClientInfo(
                URI(userAccount.instanceServer),
                URI(userAccount.loginServer), URI(userAccount.idUrl), userAccount.accountName, userAccount.username,
                userAccount.userId, userAccount.orgId, userAccount.communityId, userAccount.communityUrl,
                userAccount.firstName, userAccount.lastName, userAccount.displayName, userAccount.email, userAccount.photoUrl, userAccount.thumbnailUrl, userAccount.additionalOauthValues,
                userAccount.lightningDomain, userAccount.lightningSid, userAccount.vfDomain, userAccount.vfSid, userAccount.contentDomain, userAccount.contentSid, userAccount.csrfToken
            )
            return RestClient(clientInfo, userAccount.authToken, HttpAccess.DEFAULT!!, authTokenProvider)
        } catch (e: URISyntaxException) {
            SalesforceSDKLogger.w(TAG, "Invalid server URL", e)
            throw AccountInfoNotFoundException("invalid server url", e)
        }
    }

    /**
     * Invalidate current auth token. The next call to [getRestClient] will do a refresh.
     */
    fun invalidateToken(lastNewAuthToken: String?) {
        accountManager.invalidateAuthToken(getAccountType(), lastNewAuthToken)
    }

    /**
     * Returns the user account that is currently active.
     *
     * @return The current user account.
     */
    fun getAccount(): Account? {
        return SalesforceSDKManager.getInstance().userAccountManager.currentAccount
    }

    /**
     * @param name The name associated with the account.
     * @return The account with the application account type and the given name.
     */
    fun getAccountByName(name: String): Account? {
        val accounts = accountManager.getAccountsByType(getAccountType())
        for (account in accounts) {
            if (account.name == name) {
                return account
            }
        }
        return null
    }

    /**
     * @return All of the accounts found for this application account type.
     */
    fun getAccounts(): Array<Account> {
        return accountManager.getAccountsByType(getAccountType())
    }

    /**
     * Remove all of the accounts passed in.
     *
     * @param accounts The array of accounts to remove.
     */
    fun removeAccounts(accounts: Array<Account>?) {
        if (accounts != null && accounts.isNotEmpty()) {
            for (account in accounts) {
                removeAccount(account)
            }
        }
    }

    /**
     * Creates a new account and returns the parameters as a Bundle.
     */
    fun createNewAccount(userAccount: UserAccount): Bundle {
        return SalesforceSDKManager.getInstance().userAccountManager.createAccount(userAccount)
    }

    /**
     * Creates a new account and returns the parameters as a Bundle.
     *
     * @param accountName Account name
     * @param username Username.
     * @param refreshToken Refresh token.
     * @param authToken Access token.
     * @param instanceUrl Instance URL.
     * @param loginUrl Login URL.
     * @param idUrl Identity URL.
     * @param clientId Client ID.
     * @param orgId Org ID.
     * @param userId User ID.
     * @param communityId Community ID.
     * @param communityUrl Community URL.
     * @param firstName First name.
     * @param lastName Last name.
     * @param displayName Display name.
     * @param email Email.
     * @param photoUrl Photo URL.
     * @param thumbnailUrl Thumbnail URL.
     * @param additionalOauthValues Additional OAuth values.
     * @return Account info.
     *
     * @Deprecated will be removed in Mobile SDK 14.0 - please use createNewAccount(UserAccount userAccount)
     */
    @Deprecated("Use createNewAccount(UserAccount) instead", ReplaceWith("createNewAccount(userAccount)"))
    fun createNewAccount(
        accountName: String, username: String, refreshToken: String,
        authToken: String, instanceUrl: String, loginUrl: String, idUrl: String,
        clientId: String, orgId: String, userId: String, communityId: String?, communityUrl: String?,
        firstName: String?, lastName: String?, displayName: String?, email: String?, photoUrl: String?,
        thumbnailUrl: String?, additionalOauthValues: Map<String, String>?,
        lightningDomain: String?, lightningSid: String?, vfDomain: String?, vfSid: String?,
        contentDomain: String?, contentSid: String?, csrfToken: String?, nativeLogin: Boolean?,
        language: String?, locale: String?
    ): Bundle {
        val userAccount = UserAccountBuilder.getInstance()
            .accountName(accountName).username(username).refreshToken(refreshToken)
            .authToken(authToken).instanceServer(instanceUrl).loginServer(loginUrl).idUrl(idUrl)
            .clientId(clientId).orgId(orgId).userId(userId).communityId(communityId).communityUrl(communityUrl)
            .firstName(firstName).lastName(lastName).displayName(displayName).email(email).photoUrl(photoUrl)
            .thumbnailUrl(thumbnailUrl).additionalOauthValues(additionalOauthValues)
            .lightningDomain(lightningDomain).lightningSid(lightningSid).vfDomain(vfDomain).vfSid(vfSid)
            .contentDomain(contentDomain).contentSid(contentSid).csrfToken(csrfToken).nativeLogin(nativeLogin ?: false)
            .language(language).locale(locale)
            .build()

        return createNewAccount(userAccount)
    }

    /**
     * Should match the value in authenticator.xml.12
     * @return The account type for this application.
     */
    fun getAccountType(): String {
        return accountType
    }

    /**
     * Removes the user account from the account manager. This is safe to call from main thread.
     *
     * @param acc Account to be removed.
     */
    fun removeAccount(acc: Account?) {
        if (acc != null) {
            accountManager.removeAccountExplicitly(acc)
        }
    }

    /**
     * RestClientCallback interface.
     * You must provide an implementation of this interface when calling
     * [ClientManager.getRestClient].
     */
    fun interface RestClientCallback {
        fun authenticatedRestClient(client: RestClient?)
    }

    /**
     * AuthTokenProvider implementation that calls out to the AccountManager to get a new access token.
     * The AccountManager calls AuthenticatorService to do the actual refresh.
     * @see AuthenticatorService
     */
    class AccMgrAuthTokenProvider(
        private val clientManager: ClientManager,
        instanceUrl: String?,
        authToken: String?,
        private var refreshToken: String?
    ) : RestClient.AuthTokenProvider {

        private var gettingAuthToken = false
        private val lock = Object()
        private var lastNewAuthToken: String? = authToken
        private var lastNewInstanceUrl: String? = instanceUrl
        private var lastRefreshTime: Long = -1 /* never refreshed */

        /**
         * Fetch a new access token from the account manager.  If another thread
         * is already in the process of doing this, we'll just wait for it to finish and use that access token.
         * @return The auth token, or null if we can't get a new access token for any reason.
         */
        override fun getNewAuthToken(): String? {
            SalesforceSDKLogger.i(TAG, "Need new access token")

            // Wait if another thread is already fetching an access token
            synchronized(lock) {
                if (gettingAuthToken) {
                    try {
                        (lock as Object).wait()
                    } catch (e: InterruptedException) {
                        SalesforceSDKLogger.w(TAG, "Exception thrown while getting new auth token", e)
                    }
                    return lastNewAuthToken
                }
                gettingAuthToken = true
            }

            var newAuthToken: String? = null
            var newInstanceUrl: String? = null
            var shouldUpdateCache = false
            var accounts: Array<Account>? = null
            var matchingAccount: Account? = null

            try {
                // Only check for matching account inside synchronized thread that
                // is actually getting the new auth token.
                val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
                accounts = clientManager.getAccounts()

                if (refreshToken != null) {
                    for (account in accounts) {
                        val user = userAccountManager.buildUserAccount(account)
                        if (user != null && refreshToken == user.refreshToken) {
                            matchingAccount = account
                            break
                        }
                    }
                }

                // Fail early to ensure we don't logout the current user below by sending null.
                if (matchingAccount == null) {
                    return null
                }

                // We found a matching account, so we'll attempt a refresh and should update the cache.
                shouldUpdateCache = true

                /*
                 * Invalidate current auth token. After a prior
                 * client_blocked_retry the cached token is null because
                 * that path clears it without logging out.
                 * AccountManager.invalidateAuthToken is a no-op for
                 * null, but guarding here avoids a wasteful call whose
                 * frequency increases with retriable attestation
                 * errors.
                 */
                if (lastNewAuthToken != null) {
                    clientManager.invalidateToken(lastNewAuthToken)
                }
                val userAccount = refreshStaleToken(matchingAccount)

                // Defensive: refreshStaleToken is non-null, but guard anyway (mirrors upstream's
                // //noinspection ConstantValue check) so a null slipping through is treated as a
                // terminal, revocable error rather than a NullPointerException.
                @Suppress("SENSELESS_COMPARISON")
                if (userAccount == null) {
                    throw MalformedTokenException("refreshStaleToken returned null")
                }

                newAuthToken = userAccount.authToken
                newInstanceUrl = userAccount.instanceServer

                val broadcastIntent: Intent
                if (newInstanceUrl != null && !newInstanceUrl.equals(lastNewInstanceUrl, ignoreCase = true)) {

                    // Broadcasts an intent that the instance server has changed (implicitly token refreshed too).
                    broadcastIntent = Intent(INSTANCE_URL_UPDATE_INTENT)
                } else {

                    // Broadcasts an intent that the access token has been refreshed.
                    broadcastIntent = Intent(ACCESS_TOKEN_REFRESH_INTENT)
                    EventBuilderHelper.createAndStoreEvent("tokenRefresh", null, TAG, null)
                }
                broadcastIntent.setPackage(SalesforceSDKManager.getInstance().appContext.packageName)
                SalesforceSDKManager.getInstance().appContext.sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                if (e is OAuth2.OAuthFailedException || e is MalformedTokenException) {
                    /*
                     * OAuthFailedException: token endpoint returned
                     * an error (e.g. client_blocked,
                     * client_blocked_retry, invalid_grant).
                     *
                     * MalformedTokenException: token endpoint returned
                     * success but the response lacked an access token.
                     *
                     * Common action: broadcast ACCESS_TOKEN_REVOKE_INTENT
                     * and, for terminal errors, logout the user.
                     */
                    val errorType: String?
                    val errorDesc: String?
                    if (e is OAuth2.OAuthFailedException) {
                        val tokenError = e.tokenErrorResponse
                        errorType = tokenError.error
                        errorDesc = tokenError.errorDescription
                    } else {
                        errorType = null
                        errorDesc = null
                    }

                    if (CLIENT_BLOCKED_RETRY_ERROR != errorType) {
                        // Terminal error (client_blocked, invalid_grant, malformed token, etc.) — logout.
                        if (clientManager.revokedTokenShouldLogout) {
                            if (Looper.myLooper() == null) {
                                Looper.prepare()
                            }
                            val showLoginPage = (accounts?.size ?: 0) == 1
                            val reason = if (CLIENT_BLOCKED_ERROR == errorType) {
                                CLIENT_BLOCKED
                            } else {
                                REFRESH_TOKEN_EXPIRED
                            }
                            // Note: As of writing (2024) this call will never succeed because revoke API is an
                            // authenticated endpoint.  However, there is no harm in attempting and the debug logs
                            // produced may help developers better understand the state of their app.
                            SalesforceSDKManager.getInstance()
                                .logout(matchingAccount, null, showLoginPage, reason)
                        }
                    }

                    // Broadcast revoke intent with error details when available.
                    val broadcastIntent = Intent(ACCESS_TOKEN_REVOKE_INTENT)
                    if (errorType != null) {
                        broadcastIntent.putExtra(EXTRA_TOKEN_ERROR, errorType)
                    }
                    if (errorDesc != null) {
                        broadcastIntent.putExtra(EXTRA_TOKEN_ERROR_DESCRIPTION, errorDesc)
                    }
                    broadcastIntent.setPackage(SalesforceSDKManager.getInstance().appContext.packageName)
                    SalesforceSDKManager.getInstance().appContext.sendBroadcast(broadcastIntent)
                } else {
                    SalesforceSDKLogger.w(TAG, "Exception thrown while getting auth token", e)
                }
            } finally {
                synchronized(lock) {
                    gettingAuthToken = false
                    if (shouldUpdateCache) {
                        lastNewAuthToken = newAuthToken
                        lastNewInstanceUrl = newInstanceUrl
                        lastRefreshTime = System.currentTimeMillis()
                    }
                    (lock as Object).notifyAll()
                }
            }
            return newAuthToken
        }

        override fun getRefreshToken(): String? {
            return refreshToken
        }

        override fun getLastRefreshTime(): Long {
            return lastRefreshTime
        }

        override fun getInstanceUrl(): String? {
            return lastNewInstanceUrl
        }

        @Throws(NetworkErrorException::class, OAuth2.OAuthFailedException::class, MalformedTokenException::class)
        private fun refreshStaleToken(account: Account): UserAccount {
            val originalUserAccount = UserAccountManager.getInstance().buildUserAccount(account)
                ?: throw MalformedTokenException("Could not build user account for refresh")
            val addlParamsMap = originalUserAccount.additionalOauthValues
            try {
                val tr = OAuth2.refreshAuthToken(
                    HttpAccess.DEFAULT!!,
                    URI(originalUserAccount.loginServer!!), originalUserAccount.clientIdForRefresh!!, refreshToken!!, addlParamsMap
                )

                if (tr.authToken == null) {
                    throw MalformedTokenException("Token endpoint returned null access token")
                }

                val updatedUserAccount = UserAccountBuilder.getInstance()
                    .populateFromUserAccount(originalUserAccount)
                    .allowUnset(false)
                    .populateFromTokenEndpointResponse(tr)
                    .build()

                UserAccountManager.getInstance().updateAccount(account, updatedUserAccount)
                updatedUserAccount.downloadProfilePhoto()
                UserAccountManager.getInstance().clearCachedCurrentUser()

                // Handle server-side Refresh Token Rotation: if the response contained a new refresh token,
                // update this provider's cached copy.
                if (tr.refreshToken != null && tr.refreshToken != refreshToken) {
                    refreshToken = tr.refreshToken
                }

                return updatedUserAccount
            } catch (ofe: OAuth2.OAuthFailedException) {
                SalesforceSDKLogger.i(
                    TAG, "Token endpoint error: (Error: " +
                            ofe.tokenErrorResponse.error + ", Status Code: " +
                            ofe.httpStatusCode + ")", ofe
                )
                throw ofe
            } catch (mte: MalformedTokenException) {
                throw mte
            } catch (e: Exception) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while getting new auth token", e)
                throw NetworkErrorException(e)
            }
        }
    }

    /**
     * Exception thrown when a token refresh response is malformed (e.g. missing access_token).
     */
    internal class MalformedTokenException(msg: String) : Exception(msg)

    /**
     * Exception thrown when no account could be found (during a
     * [ClientManager.peekRestClient] call)
     */
    class AccountInfoNotFoundException : RuntimeException {

        constructor(msg: String) : super(msg)

        constructor(msg: String, cause: Throwable) : super(msg, cause)

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        const val ACCESS_TOKEN_REVOKE_INTENT: String = "access_token_revoked"
        const val ACCESS_TOKEN_REFRESH_INTENT: String = "access_token_refeshed"
        const val INSTANCE_URL_UPDATE_INTENT: String = "instance_url_updated"

        /** Intent extra: the `error` value from the token endpoint response (e.g. "client_blocked", "invalid_grant"). */
        const val EXTRA_TOKEN_ERROR: String = "token_error"

        /** Intent extra: the `error_description` value from the token endpoint response. */
        const val EXTRA_TOKEN_ERROR_DESCRIPTION: String = "token_error_description"

        private const val TAG = "ClientManager"
    }
}
