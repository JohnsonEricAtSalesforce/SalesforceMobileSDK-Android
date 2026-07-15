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

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.AuthenticatorService
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.util.SalesforceSDKLogger

/**
 * This class acts as a manager that provides methods to access
 * user accounts that are currently logged in, and can be used
 * to add new user accounts.
 *
 * @author bhariharan
 */
open class UserAccountManager protected constructor() {

    private val context: Context = SalesforceSDKManager.getInstance().appContext
    private val accountManager: AccountManager = AccountManager.get(context)
    private val accountType: String = SalesforceSDKManager.getInstance().accountType
    private var cachedCurrentUserAccount: UserAccount? = null

    /**
     * Stores the current active user's user ID and org ID in a shared preference file.
     *
     * @param userId User ID.
     * @param orgId Org ID.
     */
    fun storeCurrentUserInfo(userId: String?, orgId: String?) {
        clearCachedCurrentUser()
        val sp = context.getSharedPreferences(CURRENT_USER_PREF, Context.MODE_PRIVATE)
        val e = sp.edit()
        e.putString(USER_ID_KEY, userId)
        e.putString(ORG_ID_KEY, orgId)
        e.apply()
    }

    /**
     * Returns the stored user ID.
     *
     * @return User ID.
     */
    val storedUserId: String?
        get() {
            val sp = context.getSharedPreferences(CURRENT_USER_PREF, Context.MODE_PRIVATE)
            return sp.getString(USER_ID_KEY, null)
        }

    /**
     * Returns the stored org ID.
     *
     * @return Org ID.
     */
    val storedOrgId: String?
        get() {
            val sp = context.getSharedPreferences(CURRENT_USER_PREF, Context.MODE_PRIVATE)
            return sp.getString(ORG_ID_KEY, null)
        }

    /**
     * Returns the current user logged in.
     *
     * @return Current user that's logged in.
     */
    val currentUser: UserAccount?
        get() {
            cachedCurrentUserAccount = buildUserAccount(currentAccount)
            return cachedCurrentUserAccount
        }

    /**
     * Returns a cached value of the current user.
     *
     * NB: The oauth tokens might be outdated.
     * Should be used by methods that only care about the current user's identity (org id, user id etc).
     * Is faster than getCurrentUser().
     *
     * @return Current user that's logged in (with potentially outdated oauth tokens).
     */
    val cachedCurrentUser: UserAccount?
        get() = cachedCurrentUserAccount ?: currentUser

    /**
     * Get rid of cached current user account.
     */
    fun clearCachedCurrentUser() {
        cachedCurrentUserAccount = null
    }

    /**
     * Returns the current user logged in.
     *
     * @return Current user that's logged in.
     */
    val currentAccount: Account?
        get() {
            val accounts = accountManager.getAccountsByType(accountType)
            if (accounts.isEmpty()) {
                return null
            }

            // Register feature MU if more than one user
            if (accounts.size > 1) {
                SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_MULTI_USERS)
            } else {
                SalesforceSDKManager.getInstance().unregisterUsedAppFeature(Features.FEATURE_MULTI_USERS)
            }

            // Reads the stored user ID and org ID.
            val sp = context.getSharedPreferences(CURRENT_USER_PREF, Context.MODE_PRIVATE)
            val storedUserId = sp.getString(USER_ID_KEY, "") ?: ""
            val storedOrgId = sp.getString(ORG_ID_KEY, "") ?: ""
            for (account in accounts) {
                if (account != null) {
                    // Reads the user ID and org ID from account manager.
                    val encryptionKey = SalesforceSDKManager.encryptionKey
                    val orgId = SalesforceSDKManager.decrypt(
                        accountManager.getUserData(account, AuthenticatorService.KEY_ORG_ID),
                        encryptionKey
                    )
                    val userId = SalesforceSDKManager.decrypt(
                        accountManager.getUserData(account, AuthenticatorService.KEY_USER_ID),
                        encryptionKey
                    )
                    if (storedUserId.trim() == userId && storedOrgId.trim() == orgId) {
                        return account
                    }
                }
            }
            return null
        }

    /**
     * Returns a list of authenticated users.
     *
     * @return List of authenticated users.
     */
    val authenticatedUsers: List<UserAccount>?
        get() {
            val accounts = accountManager.getAccountsByType(accountType)
            if (accounts.isEmpty()) {
                return null
            }
            val userAccounts = ArrayList<UserAccount>()
            for (account in accounts) {
                val userAccount = buildUserAccount(account)
                if (userAccount != null) {
                    userAccounts.add(userAccount)
                }
            }
            return if (userAccounts.isEmpty()) null else userAccounts
        }

    /**
     * Returns whether the specified user account exists or not.
     *
     * @param account User account.
     * @return True - if it exists, False - otherwise.
     */
    fun doesUserAccountExist(account: UserAccount?): Boolean {
        if (account == null) {
            return false
        }
        val userAccounts = authenticatedUsers ?: return false
        if (userAccounts.isEmpty()) {
            return false
        }
        for (userAccount in userAccounts) {
            if (account == userAccount) {
                return true
            }
        }
        return false
    }

    /**
     * Switches to the specified user account. If the specified user account
     * is invalid/doesn't exist, this method kicks off the login flow
     * for a new user. When the user account switch is complete, it is
     * imperative for the app to update its cached references to RestClient,
     * to avoid holding on to a RestClient from the previous user.
     *
     * @param user User account to switch to.
     */
    fun switchToUser(user: UserAccount?) {
        switchToUser(user, USER_SWITCH_TYPE_DEFAULT, null)
    }

    /**
     * Switches to the specified user account.
     *
     * @param user the user account to switch to.
     * @param userSwitchType a USER_SWITCH_TYPE constant.
     * @param extras an optional Bundle of extras to pass additional
     *        information during user switch.
     *
     * @see switchToUser
     */
    fun switchToUser(user: UserAccount?, userSwitchType: Int, extras: Bundle?) {
        if (user == null || !doesUserAccountExist(user)) {
            switchToNewUser()
            return
        }
        val curUser = currentUser

        /*
         * Checks if we are attempting to switch to the current user.
         * In this case, there's nothing to be done.
         */
        if (user == curUser) {
            return
        }
        val cm = ClientManager(context, accountType, true)
        val account = cm.getAccountByName(user.accountName ?: return)
        storeCurrentUserInfo(user.userId, user.orgId)
        cm.peekRestClient(account)
        sendUserSwitchIntent(userSwitchType, extras)

        // Check if User has ScreenLock or Biometric Auth
        val bioAuthManager =
            SalesforceSDKManager.getInstance().biometricAuthenticationManager as? BiometricAuthenticationManager
        val screenLockManager =
            SalesforceSDKManager.getInstance().screenLockManager as? ScreenLockManager
        if (bioAuthManager != null && bioAuthManager.enabled) {
            bioAuthManager.lock()
        } else if (screenLockManager != null && screenLockManager.enabled) {
            screenLockManager.lock()
        }
    }

    /**
     * Kicks off the login flow to switch to a new user. Once the login
     * flow is complete, the context will automatically become the
     * new user's context and a call to peekRestClient() or getRestClient()
     * in ClientManager will return a RestClient instance for the new user.
     */
    fun switchToNewUser() {
        val options = Bundle()
        val i = Intent(context, SalesforceSDKManager.getInstance().loginActivityClass)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        options.putBoolean(BiometricAuthenticationManager.SHOW_BIOMETRIC, false)
        options.putBoolean(LoginActivity.NEW_USER, true)
        i.putExtras(options)
        context.startActivity(i)
    }

    /**
     * Logs the current user out.
     *
     * @param frontActivity Front activity.
     */
    fun signoutCurrentUser(frontActivity: Activity?) {
        SalesforceSDKManager.getInstance().logout(frontActivity)
    }

    /**
     * Logs the current user out.
     *
     * @param frontActivity Front activity.
     * @param showLoginPage True - if the login page should be shown, False - otherwise.
     */
    fun signoutCurrentUser(frontActivity: Activity?, showLoginPage: Boolean) {
        SalesforceSDKManager.getInstance().logout(frontActivity, showLoginPage)
    }

    /**
     * Logs the current user out.
     *
     * @param frontActivity Front activity.
     * @param showLoginPage True - if the login page should be shown, False - otherwise.
     * @param reason The reason for the logout.
     */
    fun signoutCurrentUser(frontActivity: Activity?, showLoginPage: Boolean, reason: OAuth2.LogoutReason) {
        SalesforceSDKManager.getInstance().logout(null, frontActivity, showLoginPage, reason)
    }

    /**
     * Logs the specified user out. If the user specified is not the current
     * user, push notification un-registration will not take place.
     *
     * @param userAccount User account.
     * @param frontActivity Front activity.
     */
    fun signoutUser(userAccount: UserAccount?, frontActivity: Activity?) {
        val account = buildAccount(userAccount)
        SalesforceSDKManager.getInstance().logout(account, frontActivity)
    }

    /**
     * Logs the specified user out. If the user specified is not the current
     * user, push notification un-registration will not take place.
     *
     * @param userAccount User account.
     * @param frontActivity Front activity.
     * @param showLoginPage True - if the login page should be shown, False - otherwise.
     */
    fun signoutUser(userAccount: UserAccount?, frontActivity: Activity?, showLoginPage: Boolean) {
        val account = buildAccount(userAccount)
        SalesforceSDKManager.getInstance().logout(account, frontActivity, showLoginPage)
    }

    /**
     * Logs the specified user out. If the user specified is not the current
     * user, push notification un-registration will not take place.
     *
     * @param userAccount User account.
     * @param frontActivity Front activity.
     * @param showLoginPage True - if the login page should be shown, False - otherwise.
     * @param reason The reason for the logout.
     */
    fun signoutUser(
        userAccount: UserAccount?,
        frontActivity: Activity?,
        showLoginPage: Boolean,
        reason: OAuth2.LogoutReason
    ) {
        val account = buildAccount(userAccount)
        SalesforceSDKManager.getInstance().logout(account, frontActivity, showLoginPage, reason)
    }

    /**
     * Create AccountManager Account from the given UserAccount.
     *
     * @param userAccount UserAccount object.
     * @return auth bundle with encrypted values.
     */
    fun createAccount(userAccount: UserAccount): Bundle {
        val encryptionKey = SalesforceSDKManager.encryptionKey
        val extras = buildAuthBundle(userAccount)

        val acc = Account(userAccount.accountName ?: "", accountType)
        val password = SalesforceSDKManager.encrypt(userAccount.refreshTokenForPersistence, encryptionKey)
        val success = accountManager.addAccountExplicitly(acc, password, Bundle())

        // Add account will fail if the account already exists, so update refresh token.
        if (!success) {
            accountManager.setPassword(acc, password)
        }

        /*
         * Caching auth token otherwise the first call to 'accountManager.getAuthToken()' will go
         * to the AuthenticatorService which will do a refresh. That is problematic when the
         * refresh token is set to expire immediately.
         */
        accountManager.setAuthToken(
            acc, AccountManager.KEY_AUTHTOKEN,
            SalesforceSDKManager.encrypt(userAccount.authToken, encryptionKey)
        )

        // There is a bug in AccountManager::addAccountExplicitly() that sometimes causes user data to not be
        // saved when the user data is passed in through that method. The work-around is to call setUserData()
        // for all the user data manually after passing in empty user data into addAccountExplicitly().
        for (key in extras.keySet()) {
            // WARNING! This assumes all user data is a String!
            accountManager.setUserData(acc, key, extras.getString(key))
        }

        /*
         * Sets this user as the current user
         */
        storeCurrentUserInfo(userAccount.userId, userAccount.orgId)
        return extras
    }

    /**
     * Update AccountManager Account for the given UserAccount.
     *
     * @param account Account object to update.
     * @param userAccount UserAccount object.
     * @return auth bundle with encrypted values.
     */
    fun updateAccount(account: Account, userAccount: UserAccount): Bundle {
        val extras = buildAuthBundle(userAccount)

        for (key in extras.keySet()) {
            accountManager.setUserData(account, key, extras.getString(key))
        }

        // The refresh token is stored as the Account's password (see createAccount), not as user data,
        // so buildAuthBundle does not include it. Persist it explicitly here so that server-side
        // Use the in-memory snapshot rather than getRefreshToken(), which now performs a live lookup
        // against AccountManager and would return the updated value we may be about to write.
        val refreshToken = userAccount.refreshTokenForPersistence
        if (refreshToken != null) {
            val encryptionKey = SalesforceSDKManager.encryptionKey
            accountManager.setPassword(account, SalesforceSDKManager.encrypt(refreshToken, encryptionKey))
        }

        return extras
    }

    /**
     * Builds a UserAccount object from the saved account.
     *
     * @param account Account object.
     * @return UserAccount object.
     */
    fun buildUserAccount(account: Account?): UserAccount? {
        if (account == null) {
            return null
        }

        val encryptionKey = SalesforceSDKManager.encryptionKey
        val accountName = accountManager.getUserData(account, AccountManager.KEY_ACCOUNT_NAME)

        // Maintenance Note: All account values are nullable by default. If a value requires a default value when user's of older versions experience access token refresh, provide that here.
        val refreshToken = SalesforceSDKManager.decrypt(accountManager.getPassword(account), encryptionKey)
        val authToken = decryptUserData(account, AccountManager.KEY_AUTHTOKEN, encryptionKey)
        val loginServer = decryptUserData(account, AuthenticatorService.KEY_LOGIN_URL, encryptionKey)
        val idUrl = decryptUserData(account, AuthenticatorService.KEY_ID_URL, encryptionKey)
        val instanceServer = decryptUserData(account, AuthenticatorService.KEY_INSTANCE_URL, encryptionKey)
        val apiInstanceServer = decryptUserData(account, AuthenticatorService.KEY_API_INSTANCE_URL, encryptionKey)
        val orgId = decryptUserData(account, AuthenticatorService.KEY_ORG_ID, encryptionKey)
        val userId = decryptUserData(account, AuthenticatorService.KEY_USER_ID, encryptionKey)
        val username = decryptUserData(account, AuthenticatorService.KEY_USERNAME, encryptionKey)
        val lastName = decryptUserData(account, AuthenticatorService.KEY_LAST_NAME, encryptionKey)
        val email = decryptUserData(account, AuthenticatorService.KEY_EMAIL, encryptionKey)
        val language = decryptUserData(account, AuthenticatorService.KEY_LANGUAGE, encryptionKey)
        val locale = decryptUserData(account, AuthenticatorService.KEY_LOCALE, encryptionKey)
        val nativeLogin = decryptUserData(account, AuthenticatorService.KEY_NATIVE_LOGIN, encryptionKey).toBoolean()
        val firstName = decryptUserData(account, AuthenticatorService.KEY_FIRST_NAME, encryptionKey)
        val displayName = decryptUserData(account, AuthenticatorService.KEY_DISPLAY_NAME, encryptionKey)
        val photoUrl = decryptUserData(account, AuthenticatorService.KEY_PHOTO_URL, encryptionKey)
        val thumbnailUrl = decryptUserData(account, AuthenticatorService.KEY_THUMBNAIL_URL, encryptionKey)
        val communityId = decryptUserData(account, AuthenticatorService.KEY_COMMUNITY_ID, encryptionKey)
        val communityUrl = decryptUserData(account, AuthenticatorService.KEY_COMMUNITY_URL, encryptionKey)
        val lightningDomain = decryptUserData(account, AuthenticatorService.KEY_LIGHTNING_DOMAIN, encryptionKey)
        val lightningSid = decryptUserData(account, AuthenticatorService.KEY_LIGHTNING_SID, encryptionKey)
        val vfDomain = decryptUserData(account, AuthenticatorService.KEY_VF_DOMAIN, encryptionKey)
        val vfSid = decryptUserData(account, AuthenticatorService.KEY_VF_SID, encryptionKey)
        val contentDomain = decryptUserData(account, AuthenticatorService.KEY_CONTENT_DOMAIN, encryptionKey)
        val contentSid = decryptUserData(account, AuthenticatorService.KEY_CONTENT_SID, encryptionKey)
        val csrfToken = decryptUserData(account, AuthenticatorService.KEY_CSRF_TOKEN, encryptionKey)
        val cookieClientSrc = decryptUserData(account, AuthenticatorService.KEY_COOKIE_CLIENT_SRC, encryptionKey)
        val cookieSidClient = decryptUserData(account, AuthenticatorService.KEY_COOKIE_SID_CLIENT, encryptionKey)
        val sidCookieName = decryptUserData(account, AuthenticatorService.KEY_SID_COOKIE_NAME, encryptionKey)
        val clientId = decryptUserData(account, AuthenticatorService.KEY_CLIENT_ID, encryptionKey)
        val parentSid = decryptUserData(account, AuthenticatorService.KEY_PARENT_SID, encryptionKey)
        val tokenFormat = decryptUserData(account, AuthenticatorService.KEY_TOKEN_FORMAT, encryptionKey)
        val beaconChildConsumerKey = decryptUserData(account, AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_KEY, encryptionKey)
        val beaconChildConsumerSecret = decryptUserData(account, AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_SECRET, encryptionKey)
        val scope = decryptUserData(account, AuthenticatorService.KEY_SCOPE, encryptionKey)
        val featureFlagsRaw = decryptUserData(account, AuthenticatorService.KEY_FEATURE_FLAGS, encryptionKey)

        var additionalOauthValues: Map<String, String>? = null
        val additionalOauthKeys = SalesforceSDKManager.getInstance().additionalOauthKeys

        if (additionalOauthKeys != null && additionalOauthKeys.isNotEmpty()) {
            val oauthMap = HashMap<String, String>()
            for (key in additionalOauthKeys) {
                if (!TextUtils.isEmpty(key)) {
                    val value = decryptUserData(account, key, encryptionKey)
                    if (value != null) {
                        oauthMap[key] = value
                    }
                }
            }
            additionalOauthValues = oauthMap
        }

        return if (authToken == null || instanceServer == null || userId == null || orgId == null) {
            null
        } else {
            val userAccount = UserAccountBuilder.getInstance()
                .authToken(authToken)
                .refreshToken(refreshToken)
                .loginServer(loginServer)
                .idUrl(idUrl)
                .instanceServer(instanceServer)
                .apiInstanceServer(apiInstanceServer)
                .orgId(orgId)
                .userId(userId)
                .username(username)
                .accountName(accountName)
                .communityId(communityId)
                .communityUrl(communityUrl)
                .firstName(firstName)
                .lastName(lastName)
                .displayName(displayName)
                .email(email).photoUrl(photoUrl)
                .thumbnailUrl(thumbnailUrl)
                .lightningDomain(lightningDomain)
                .lightningSid(lightningSid)
                .vfDomain(vfDomain)
                .vfSid(vfSid)
                .contentDomain(contentDomain)
                .contentSid(contentSid)
                .csrfToken(csrfToken)
                .nativeLogin(nativeLogin)
                .language(language)
                .locale(locale)
                .cookieClientSrc(cookieClientSrc)
                .cookieSidClient(cookieSidClient)
                .sidCookieName(sidCookieName)
                .clientId(clientId)
                .parentSid(parentSid)
                .tokenFormat(tokenFormat)
                .beaconChildConsumerKey(beaconChildConsumerKey)
                .beaconChildConsumerSecret(beaconChildConsumerSecret)
                .scope(scope)
                .additionalOauthValues(additionalOauthValues)
                .build()
            if (!TextUtils.isEmpty(featureFlagsRaw)) {
                userAccount.featureFlags = HashSet(featureFlagsRaw!!.split(",").dropLastWhile { it.isEmpty() })
            }
            userAccount
        }
    }

    /**
     * Builds an Account object from the user account passed in.
     *
     * @param userAccount UserAccount object.
     * @return Account object.
     */
    fun buildAccount(userAccount: UserAccount?): Account? {
        val accounts = accountManager.getAccountsByType(accountType)
        if (userAccount == null) {
            return null
        }
        if (accounts.isEmpty()) {
            return null
        }

        // Reads the user account's user ID and org ID.
        val storedUserId = userAccount.userId ?: ""
        val storedOrgId = userAccount.orgId ?: ""
        for (account in accounts) {
            if (account != null) {
                // Reads the user ID and org ID from account manager.
                val encryptionKey = SalesforceSDKManager.encryptionKey
                val orgId = SalesforceSDKManager.decrypt(
                    accountManager.getUserData(account, AuthenticatorService.KEY_ORG_ID),
                    encryptionKey
                )
                val userId = SalesforceSDKManager.decrypt(
                    accountManager.getUserData(account, AuthenticatorService.KEY_USER_ID),
                    encryptionKey
                )
                if (storedUserId.trim() == userId?.trim()
                    && storedOrgId.trim() == orgId?.trim()
                ) {
                    return account
                }
            }
        }
        return null
    }

    /**
     * Broadcasts an intent that a user switch has occurred.
     *
     * @param userSwitchType A USER_SWITCH_TYPE constant.
     * @param extras An optional Bundle of extras to add to the broadcast intent.
     */
    fun sendUserSwitchIntent(userSwitchType: Int, extras: Bundle?) {
        val intent = Intent(USER_SWITCH_INTENT_ACTION)
        intent.setPackage(context.packageName)
        intent.putExtra(EXTRA_USER_SWITCH_TYPE, userSwitchType)
        if (extras != null) {
            intent.putExtras(extras)
        }
        SalesforceSDKManager.getInstance().appContext.sendBroadcast(intent)
    }

    /**
     * Retrieves a stored user account from org ID and user ID.
     *
     * @param orgId Org ID.
     * @param userId User ID.
     * @return User account.
     */
    fun getUserFromOrgAndUserId(orgId: String?, userId: String?): UserAccount? {
        if (TextUtils.isEmpty(orgId) || TextUtils.isEmpty(userId)) {
            return null
        }
        val userAccounts = authenticatedUsers ?: return null
        if (userAccounts.isEmpty()) {
            return null
        }
        for (userAccount in userAccounts) {
            if (orgId == userAccount.orgId && userId == userAccount.userId) {
                return userAccount
            }
        }
        return null
    }

    /**
     * Attempts to refresh the access token for this user by making an API call
     * to the "/token" endpoint. If the call succeeds, the new token is persisted.
     * If the call fails and the refresh token is no longer valid, the user is logged out.
     * This should NOT be called from the main thread because it makes a network request.
     *
     * @param userAccount User account whose token should be refreshed. Use 'null' for current user.
     */
    @Synchronized
    fun refreshToken(userAccount: UserAccount?) {
        val account = userAccount ?: currentUser ?: return
        try {
            val clientManager = SalesforceSDKManager.getInstance().clientManager
            val authTokenProvider = ClientManager.AccMgrAuthTokenProvider(
                clientManager,
                account.instanceServer, account.authToken, account.refreshToken
            )
            authTokenProvider.getNewAuthToken()
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while attempting to refresh token", e)
        }
    }

    /**
     * Create bundle for authenticator service.
     * - it uses keys understood by authenticator service
     * - it encrypts most values
     */
    private fun buildAuthBundle(userAccount: UserAccount): Bundle {
        val encryptionKey = SalesforceSDKManager.encryptionKey
        val extras = Bundle()
        extras.putString(AccountManager.KEY_ACCOUNT_NAME, userAccount.accountName)
        extras.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType)
        extras.putString(AuthenticatorService.KEY_USERNAME, SalesforceSDKManager.encrypt(userAccount.username, encryptionKey))
        extras.putString(AuthenticatorService.KEY_LOGIN_URL, SalesforceSDKManager.encrypt(userAccount.loginServer, encryptionKey))
        extras.putString(AuthenticatorService.KEY_ID_URL, SalesforceSDKManager.encrypt(userAccount.idUrl, encryptionKey))
        extras.putString(AuthenticatorService.KEY_INSTANCE_URL, SalesforceSDKManager.encrypt(userAccount.instanceServer, encryptionKey))
        extras.putString(AuthenticatorService.KEY_API_INSTANCE_URL, SalesforceSDKManager.encrypt(userAccount.apiInstanceServer, encryptionKey))
        extras.putString(AuthenticatorService.KEY_CLIENT_ID, SalesforceSDKManager.encrypt(userAccount.clientId, encryptionKey))
        extras.putString(AuthenticatorService.KEY_ORG_ID, SalesforceSDKManager.encrypt(userAccount.orgId, encryptionKey))
        extras.putString(AuthenticatorService.KEY_USER_ID, SalesforceSDKManager.encrypt(userAccount.userId, encryptionKey))
        extras.putString(AuthenticatorService.KEY_COMMUNITY_ID, SalesforceSDKManager.encrypt(userAccount.communityId, encryptionKey))
        extras.putString(AuthenticatorService.KEY_COMMUNITY_URL, SalesforceSDKManager.encrypt(userAccount.communityUrl, encryptionKey))
        extras.putString(AccountManager.KEY_AUTHTOKEN, SalesforceSDKManager.encrypt(userAccount.authToken, encryptionKey))
        extras.putString(AuthenticatorService.KEY_FIRST_NAME, SalesforceSDKManager.encrypt(userAccount.firstName, encryptionKey))
        extras.putString(AuthenticatorService.KEY_LAST_NAME, SalesforceSDKManager.encrypt(userAccount.lastName, encryptionKey))
        extras.putString(AuthenticatorService.KEY_DISPLAY_NAME, SalesforceSDKManager.encrypt(userAccount.displayName, encryptionKey))
        extras.putString(AuthenticatorService.KEY_EMAIL, SalesforceSDKManager.encrypt(userAccount.email, encryptionKey))
        extras.putString(AuthenticatorService.KEY_LANGUAGE, SalesforceSDKManager.encrypt(userAccount.language, encryptionKey))
        extras.putString(AuthenticatorService.KEY_LOCALE, SalesforceSDKManager.encrypt(userAccount.locale, encryptionKey))
        extras.putString(AuthenticatorService.KEY_PHOTO_URL, SalesforceSDKManager.encrypt(userAccount.photoUrl, encryptionKey))
        extras.putString(AuthenticatorService.KEY_THUMBNAIL_URL, SalesforceSDKManager.encrypt(userAccount.thumbnailUrl, encryptionKey))
        extras.putString(AuthenticatorService.KEY_LIGHTNING_DOMAIN, SalesforceSDKManager.encrypt(userAccount.lightningDomain, encryptionKey))
        extras.putString(AuthenticatorService.KEY_LIGHTNING_SID, SalesforceSDKManager.encrypt(userAccount.lightningSid, encryptionKey))
        extras.putString(AuthenticatorService.KEY_VF_DOMAIN, SalesforceSDKManager.encrypt(userAccount.vfDomain, encryptionKey))
        extras.putString(AuthenticatorService.KEY_VF_SID, SalesforceSDKManager.encrypt(userAccount.vfSid, encryptionKey))
        extras.putString(AuthenticatorService.KEY_CONTENT_DOMAIN, SalesforceSDKManager.encrypt(userAccount.contentDomain, encryptionKey))
        extras.putString(AuthenticatorService.KEY_CONTENT_SID, SalesforceSDKManager.encrypt(userAccount.contentSid, encryptionKey))
        extras.putString(AuthenticatorService.KEY_CSRF_TOKEN, SalesforceSDKManager.encrypt(userAccount.csrfToken, encryptionKey))
        extras.putString(AuthenticatorService.KEY_NATIVE_LOGIN, SalesforceSDKManager.encrypt(userAccount.nativeLogin.toString(), encryptionKey))
        extras.putString(AuthenticatorService.KEY_COOKIE_SID_CLIENT, SalesforceSDKManager.encrypt(userAccount.cookieSidClient, encryptionKey))
        extras.putString(AuthenticatorService.KEY_COOKIE_CLIENT_SRC, SalesforceSDKManager.encrypt(userAccount.cookieClientSrc, encryptionKey))
        extras.putString(AuthenticatorService.KEY_SID_COOKIE_NAME, SalesforceSDKManager.encrypt(userAccount.sidCookieName, encryptionKey))
        extras.putString(AuthenticatorService.KEY_PARENT_SID, SalesforceSDKManager.encrypt(userAccount.parentSid, encryptionKey))
        extras.putString(AuthenticatorService.KEY_TOKEN_FORMAT, SalesforceSDKManager.encrypt(userAccount.tokenFormat, encryptionKey))
        extras.putString(AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_KEY, SalesforceSDKManager.encrypt(userAccount.beaconChildConsumerKey, encryptionKey))
        extras.putString(AuthenticatorService.KEY_BEACON_CHILD_CONSUMER_SECRET, SalesforceSDKManager.encrypt(userAccount.beaconChildConsumerSecret, encryptionKey))
        extras.putString(AuthenticatorService.KEY_SCOPE, SalesforceSDKManager.encrypt(userAccount.scope, encryptionKey))
        val featureFlags = userAccount.featureFlags
        if (featureFlags.isNotEmpty()) {
            extras.putString(
                AuthenticatorService.KEY_FEATURE_FLAGS,
                SalesforceSDKManager.encrypt(TextUtils.join(",", featureFlags), encryptionKey)
            )
        }

        val additionalOauthKeys = SalesforceSDKManager.getInstance().additionalOauthKeys
        if (additionalOauthKeys != null && additionalOauthKeys.isNotEmpty()) {
            val additionalOauthValues = userAccount.additionalOauthValues
            if (additionalOauthValues != null && additionalOauthValues.isNotEmpty()) {
                for (key in additionalOauthKeys) {
                    val value = additionalOauthValues[key]
                    if (value != null) {
                        extras.putString(key, SalesforceSDKManager.encrypt(value, encryptionKey))
                    }
                }
            }
        }

        return extras
    }

    private fun decryptUserData(account: Account, key: String, encryptionKey: String): String? {
        return SalesforceSDKManager.decrypt(accountManager.getUserData(account, key), encryptionKey)
    }

    /**
     * Clears the stored current user info from shared preferences. This should be called
     * when the last user logs out to ensure no user information remains on the device.
     */
    fun clearStoredCurrentUserInfo() {
        clearCachedCurrentUser()
        val sp = context.getSharedPreferences(CURRENT_USER_PREF, Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        SalesforceSDKLogger.d(TAG, "Cleared current user info from shared preferences")
    }

    companion object {
        private const val CURRENT_USER_PREF = "current_user_info"
        private const val USER_ID_KEY = "user_id"
        private const val ORG_ID_KEY = "org_id"
        private const val TAG = "UserAccountManager"

        const val USER_SWITCH_INTENT_ACTION = "com.salesforce.USERSWITCHED"

        /**
         * Represents how the current user has been switched to, as found in an intent sent to a
         * BroadcastReceiver filtering [USER_SWITCH_INTENT_ACTION]. User switching includes logging
         * in, logging out and switching between authenticated users. For backwards compatibility,
         * the case where the last user has logged out is not included, as this currently does not
         * send a broadcast.
         */
        const val EXTRA_USER_SWITCH_TYPE = "com.salesforce.USER_SWITCH_TYPE"

        /** A switch has occurred between two authenticated users. */
        const val USER_SWITCH_TYPE_DEFAULT = -1

        /** The first user has logged in and is being switched to. There were no users authenticated before this switch. */
        const val USER_SWITCH_TYPE_FIRST_LOGIN = 0

        /** An additional user has logged in and is being switched to. There was at least one user authenticated before this switch. */
        const val USER_SWITCH_TYPE_LOGIN = 1

        /** A user has logged out and another authenticated user is being switched to. */
        const val USER_SWITCH_TYPE_LOGOUT = 2

        private var INSTANCE: UserAccountManager? = null

        /**
         * Returns a singleton instance of this class.
         *
         * @return Instance of this class.
         */
        @JvmStatic
        fun getInstance(): UserAccountManager {
            if (INSTANCE == null) {
                INSTANCE = UserAccountManager()
            }
            return INSTANCE!!
        }
    }
}
