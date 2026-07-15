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
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.AuthenticatorService
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuthErrorCode
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.CLIENT_BLOCKED
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.REFRESH_TOKEN_EXPIRED
import com.salesforce.androidsdk.rest.RestClient.ClientInfo
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import androidx.annotation.VisibleForTesting
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentHashMap

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

        private var lastNewAuthToken: String? = authToken
        private var lastNewInstanceUrl: String? = instanceUrl
        private var lastRefreshTime: Long = -1 /* never refreshed */

        /**
         * App-global, per-account refresh coordination state.
         *
         * Many subsystems each hold their own [RestClient] and therefore their own
         * `AccMgrAuthTokenProvider` instance, each carrying a construction-time refresh-token
         * snapshot. Without app-global serialization, a token-refresh storm (e.g. on resume) could
         * have multiple providers POST in true parallel. With server-side Refresh Token Rotation
         * (RTR) the loser then POSTs an already-rotated refresh token, gets `invalid_grant`, and
         * logs the user out. This per-account state serializes refreshes so exactly one provider
         * (the "winner") performs the network refresh and the others ("losers") adopt its result.
         */
        private class RefreshState {
            // Dedicated monitor for this state's winner/loser coordination. A private lock object
            // (rather than synchronizing on the RefreshState reference itself) makes the intent
            // explicit.
            val lock = Object()
            var refreshing = false
            // Incremented once per SUCCESSFUL publish (never on a failed refresh). A waiting loser
            // snapshots this value before sleeping and treats any change on wakeup as "a fresh
            // result was published while I waited." This is robust to a *subsequent* winner that
            // has already re-set refreshing=true (the consecutive-cycle race) and to spurious
            // wakeups — neither of which the refreshing flag alone can distinguish.
            var publishGeneration: Long = 0
            var newAuthToken: String? = null        // last winner's fresh access token (null on failure)
            var newInstanceUrl: String? = null      // last winner's instance URL (losers need it)
            var rotatedRefreshToken: String? = null // refresh token after rotation, for losers to adopt
            var lastRefreshTime: Long = -1
        }

        companion object {
            private val REFRESH_STATES = ConcurrentHashMap<String, RefreshState>()

            /**
             * Clears the app-global per-account refresh coordination state. Test-only:
             * [REFRESH_STATES] is static and survives across tests, so it must be reset between them.
             */
            @VisibleForTesting
            @JvmStatic
            fun resetRefreshStateForTest() {
                REFRESH_STATES.clear()
            }

            /** Bounded safety-net so a loser never parks forever if a winner is somehow lost. */
            private const val LOSER_WAIT_TIMEOUT_MILLIS = 30_000L

            /**
             * A fresh provider that arrives right after a refresh cycle completed (so it found
             * `refreshing == false`) adopts that just-published token instead of starting a new
             * refresh, as long as the publish is this recent. This closes the consecutive-cycle
             * race for fresh arrivers: it stops a freshly-arriving provider from electing itself a
             * new winner microseconds after another winner published — which under Refresh Token
             * Rotation would mean a redundant POST that rotates the token again and widens the
             * stale-token logout window. Kept small: it only needs to exceed the notify-to-reacquire
             * window, and a shorter window minimizes the time a server-revoked token could be
             * re-handed before the next request's 401 forces a real refresh.
             */
            private const val RECENT_REFRESH_THRESHOLD_MILLIS = 3_000L
        }

        /**
         * Fetch a new access token from the account manager.  If another thread
         * is already in the process of doing this, we'll just wait for it to finish and use that access token.
         * @return The auth token, or null if we can't get a new access token for any reason.
         */
        override fun getNewAuthToken(): String? {
            SalesforceSDKLogger.i(TAG, "Need new access token")

            // The matching loop and the no-match early-out MUST run before any shared-state
            // election so that a no-match path (e.g. account removed during refresh) never
            // marks a RefreshState as refreshing — preserving the no deadlock fix and
            // logout-during-refresh semantics.
            val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
            val accounts = clientManager.getAccounts()
            var matchingAccount: Account? = null
            var stateKey: String? = null

            if (refreshToken != null) {
                for (account in accounts) {
                    val user = userAccountManager.buildUserAccount(account)
                    if (user != null && refreshToken == user.refreshToken) {
                        matchingAccount = account
                        val userId = user.userId
                        val orgId = user.orgId
                        if (userId == null || orgId == null) {
                            SalesforceSDKLogger.w(
                                TAG, "Cannot serialize token refresh: " +
                                        "account is missing userId or orgId"
                            )
                            return null
                        }
                        stateKey = "$userId:$orgId"
                        break
                    }
                }
            }

            // Fail early to ensure we don't logout the current user below by sending null.
            if (matchingAccount == null || stateKey == null) {
                return null
            }

            // Elect winner/loser on the SINGLE coordination primitive (the per-account state).
            // Losers wait (looping on the condition to absorb spurious/lost wakeups) for the
            // winner's published result and adopt it without re-attempting, logging out, or
            // broadcasting.
            val state = REFRESH_STATES.computeIfAbsent(stateKey) { RefreshState() }
            synchronized(state.lock) {
                if (state.refreshing) {
                    // Snapshot the publish generation BEFORE waiting. We adopt on a *generation
                    // change* (an edge), not on observing refreshing==false (a level). This rescues
                    // the consecutive-cycle race: if a subsequent winner has already flipped
                    // refreshing back to true by the time we re-acquire the lock, we still detect
                    // that the prior winner published a result while we waited and adopt it, rather
                    // than re-parking against a deadline that began ticking during an unrelated
                    // earlier cycle.
                    val startGeneration = state.publishGeneration
                    val deadline = System.currentTimeMillis() + LOSER_WAIT_TIMEOUT_MILLIS
                    val published: Boolean
                    try {
                        // Loop until a new result is published (generation advanced) or the
                        // in-flight refresh ends without one. Bounded so a lost winner can't
                        // strand us forever. The generation guard also absorbs spurious/lost
                        // wakeups.
                        while (state.refreshing && state.publishGeneration == startGeneration) {
                            val timeRemaining = deadline - System.currentTimeMillis()
                            if (timeRemaining <= 0) {
                                break
                            }
                            (state.lock as Object).wait(timeRemaining)
                        }
                        published = state.publishGeneration != startGeneration
                    } catch (e: InterruptedException) {
                        SalesforceSDKLogger.w(TAG, "Interrupted while waiting for in-flight token refresh", e)
                        Thread.currentThread().interrupt()
                        // Adopt a result only if one was actually published while we waited.
                        if (state.publishGeneration != startGeneration && state.newAuthToken != null) {
                            adoptWinnerResult(state)
                            return state.newAuthToken
                        }
                        return null
                    }

                    if (published) {
                        adoptWinnerResult(state)
                        return state.newAuthToken
                    }
                    // Timed out waiting for an in-flight refresh on this account. Becoming a
                    // second concurrent refresher would risk a parallel stale refresh-token POST
                    // and a spurious logout, so fail safe: return null rather than refresh
                    // uncoordinated. The caller's request fails and can retry; the in-flight
                    // winner (if merely slow) still completes and serves the next caller.
                    return null
                }

                // Fresh arriver (found refreshing==false). If a winner published very recently,
                // adopt that result instead of starting a redundant refresh — closing the
                // consecutive-cycle race for threads that arrive just after a cycle completes.
                //
                // The freshness window alone is not sufficient: we must also confirm the published
                // token actually differs from the one THIS provider just failed a request with
                // (lastNewAuthToken). Without that difference check we could hand our caller back
                // the very token it just got a 401/403 on (e.g. when this provider was itself the
                // recent winner), causing an immediate repeat 401. This mirrors the recheck-under-
                // lock storage guardrail below, which likewise POSTs a real refresh when storage
                // has NOT advanced past this provider's tokens.
                if (state.newAuthToken != null
                    && state.newAuthToken != lastNewAuthToken
                    && System.currentTimeMillis() - state.lastRefreshTime < RECENT_REFRESH_THRESHOLD_MILLIS
                ) {
                    adoptWinnerResult(state)
                    return state.newAuthToken
                }

                // Become the winner. Note: the previously-published newAuthToken/newInstanceUrl/
                // rotatedRefreshToken are intentionally NOT cleared here. A loser of the prior
                // cycle that is woken after we re-set refreshing=true must still be able to read
                // that last-good result via the publishGeneration edge above; clearing it would
                // re-introduce the consecutive-cycle null-return. The success branch of the finally
                // publish overwrites these fields with our own result anyway.
                state.refreshing = true
            }

            // The winner performs the refresh. The entire body below runs inside one try/finally
            // whose finally ALWAYS publishes (or marks failed) and notifies, so no early return
            // can leave state.refreshing stuck true.
            var newAuthToken: String? = null
            var newInstanceUrl: String? = null

            try {
                /*
                 * Recheck-under-lock guardrail. We hold the per-account refresh slot, but the
                 * 401/403 that sent us here may have been provoked by a token this provider was
                 * still using from BEFORE a concurrent (or earlier) refresh already rotated it.
                 * Re-read the account's current tokens from storage: if EITHER the access token
                 * or the refresh token in storage has advanced past what this provider last used,
                 * someone already refreshed — adopt their tokens and skip a redundant network POST.
                 *
                 * Under Refresh Token Rotation every needless POST rotates the refresh token again
                 * and widens the window for a stale-token logout, so avoiding it is a correctness
                 * guardrail, not an optimization. If the adopted access token is itself stale, the
                 * caller's replayed request 401s again and the next getNewAuthToken() — now holding
                 * the latest tokens — performs a real refresh (self-correcting, never a loop).
                 */
                if (lastNewAuthToken != null) {
                    val currentAccount =
                        UserAccountManager.getInstance().buildUserAccount(matchingAccount)
                    if (currentAccount != null) {
                        val storedAuthToken = currentAccount.authToken
                        val storedRefreshToken = currentAccount.refreshToken
                        val haveLatestTokens =
                            storedAuthToken == lastNewAuthToken && storedRefreshToken == refreshToken
                        if (!haveLatestTokens && storedAuthToken != null) {
                            // Storage advanced past us — adopt without refreshing or broadcasting.
                            SalesforceSDKLogger.i(
                                TAG,
                                "Access/refresh token already advanced in storage; adopting without refresh"
                            )
                            newAuthToken = storedAuthToken
                            newInstanceUrl = currentAccount.instanceServer
                            refreshToken = storedRefreshToken
                            return newAuthToken
                        }
                    }

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
                    val errorCode: OAuthErrorCode
                    if (e is OAuth2.OAuthFailedException) {
                        val tokenError = e.tokenErrorResponse
                        errorType = tokenError.error
                        errorDesc = tokenError.errorDescription
                        errorCode = tokenError.errorCode
                    } else {
                        errorType = null
                        errorDesc = null
                        errorCode = OAuthErrorCode.UNKNOWN
                    }

                    if (errorCode != OAuthErrorCode.APP_ATTESTATION_FAILED_RETRY) {
                        // Terminal error (client_blocked, invalid_grant, malformed token, etc.) — logout.
                        if (clientManager.revokedTokenShouldLogout) {
                            if (Looper.myLooper() == null) {
                                Looper.prepare()
                            }
                            val showLoginPage = accounts.size == 1
                            val reason = if (errorCode == OAuthErrorCode.APP_ATTESTATION_FAILED) {
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
                // Update this instance's own cache so its getters stay correct.
                lastNewAuthToken = newAuthToken
                lastNewInstanceUrl = newInstanceUrl
                lastRefreshTime = System.currentTimeMillis()
                // Publish the result to the per-account state and wake any waiting losers.
                // This is the SINGLE publish path and ALWAYS runs on every winner exit path so
                // losers never wait forever and never wake without a definitive result.
                synchronized(state.lock) {
                    state.refreshing = false
                    if (newAuthToken != null) {
                        state.newAuthToken = newAuthToken
                        state.newInstanceUrl = newInstanceUrl
                        state.rotatedRefreshToken = refreshToken
                        state.lastRefreshTime = System.currentTimeMillis()
                        // Mark a fresh result as available. Bumped ONLY on success so a loser woken
                        // by a failed cycle sees an unchanged generation and correctly returns null
                        // (rather than adopting a non-result), while a loser that started waiting
                        // before an earlier success still adopts that success via the edge.
                        state.publishGeneration++
                    }
                    // On failure we deliberately leave newAuthToken/newInstanceUrl/rotatedRefreshToken
                    // and lastRefreshTime UNCHANGED rather than nulling them. publishGeneration is
                    // the sole adopt signal: a loser of THIS failed cycle sees an unchanged
                    // generation and returns null, while a loser that began waiting before an
                    // EARLIER success must still be able to adopt that success — nulling here would
                    // wipe the last-good result out from under it and re-introduce a spurious-null
                    // (the consecutive-cycle race, success-then-failure variant). Fresh arrivers
                    // cannot wrongly adopt a stale token because the recency window keys off
                    // lastRefreshTime, which only a success advances.
                    (state.lock as Object).notifyAll()
                }
            }
            return newAuthToken
        }

        /**
         * Copies the winner's refresh result from the shared per-account state into this loser
         * instance's cache so that this instance's getters return consistent values.
         *
         * Instance URL and refresh token are only overwritten when the winner actually published a
         * non-null value; otherwise this loser keeps its own constructor values so [getInstanceUrl]
         * stays non-null even when the refresh response carried no instance_url (a valid case — see
         * `RestClient.refreshAccessToken`).
         */
        private fun adoptWinnerResult(state: RefreshState) {
            lastNewAuthToken = state.newAuthToken
            lastRefreshTime = state.lastRefreshTime
            if (state.newInstanceUrl != null) {
                lastNewInstanceUrl = state.newInstanceUrl
            }
            if (state.rotatedRefreshToken != null) {
                refreshToken = state.rotatedRefreshToken
            }
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
            // Refresh with the LIVE persisted refresh token, not this provider's
            // construction-time snapshot. With server-side Refresh Token Rotation (RTR), a prior
            // refresh on another provider may have already rotated the token; reading the current
            // value avoids POSTing a stale token that would fail with invalid_grant.
            val currentRefreshToken = originalUserAccount.refreshToken
            try {
                val tokenServer = OAuth2.overrideLoginServerIfNeeded(originalUserAccount)
                SalesforceSDKLogger.i(TAG, "Initiating token refresh to host: " + tokenServer.host)
                val tr = OAuth2.refreshAuthToken(
                    HttpAccess.DEFAULT!!,
                    tokenServer, originalUserAccount.clientIdForRefresh!!, currentRefreshToken!!, addlParamsMap
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
                    // Surface RTR as a per-user feature flag
                    SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_RTR, updatedUserAccount)
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
