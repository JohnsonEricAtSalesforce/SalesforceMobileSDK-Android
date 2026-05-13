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
package com.salesforce.androidsdk.app

import android.accounts.Account
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.config.AdminSettingsManager
import com.salesforce.androidsdk.config.LegacyAdminSettingsManager
import com.salesforce.androidsdk.push.PushMessaging
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.MOBILE_POLICY_PREF
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.SCREEN_LOCK
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.SCREEN_LOCK_TIMEOUT
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import java.io.IOException
import java.util.concurrent.Executors

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
open class SalesforceSDKUpgradeManager(
    private val userManager: UserManager = UserManager {
        SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers
    }
) {

    /**
     * Simple interface to improve testability
     */
    fun interface UserManager {
        fun getAuthenticatedUsers(): List<UserAccount>?
    }

    /**
     * Upgrade method.
     */
    open fun upgrade() {
        upgradeAccMgr()
    }

    /**
     * Upgrades account manager data from existing client
     * version to the current version.
     */
    @Synchronized
    @VisibleForTesting
    protected open fun upgradeAccMgr() {
        val installedVersionStr = installedAccMgrVersion
        if (installedVersionStr == SalesforceSDKManager.SDK_VERSION || installedVersionStr.isEmpty()) {
            return
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(ACC_MGR_KEY, SalesforceSDKManager.SDK_VERSION)

        try {
            val installedVersion = SdkVersion.parseFromString(installedVersionStr)
            if (installedVersion.isLessThan(SdkVersion(9, 2, 0, false))) {
                upgradeFromBefore9_2_0To10_1_1PasscodeFixes()
            }
            // Already incorporated into 9.2 upgrade.
            else if (installedVersion.isGreaterThanOrEqualTo(SdkVersion(9, 2, 0, false))
                && installedVersion.isLessThan(SdkVersion(10, 2, 0, false))
            ) {
                upgradeFromVersions9_2_0Thru10_1_1To10_1_1PasscodeFixes()
            }
            if (installedVersion.isLessThan(SdkVersion(11, 0, 0, false))) {
                updateFromBefore11_0_0()
            }
            if (installedVersion.isLessThan(SdkVersion(12, 0, 0, false))) {
                updateFromBefore12_0_0()
            }
            if (installedVersion.isLessThan(SdkVersion(13, 0, 2, false))) {
                updateFromBefore13_0_2()
            }
            if (installedVersion.isLessThan(SdkVersion(15, 0, 0, false))) {
                migrateAccountType()
            }
        } catch (e: Exception) {
            SalesforceSDKLogger.e(
                TAG,
                "Failed to parse installed version. Error message: ${e.message}"
            )
        }
    }

    /**
     * Writes the current version to the shared preference file.
     *
     * @param key Key to update.
     * @param value New version number.
     */
    @Synchronized
    @VisibleForTesting
    protected open fun writeCurVersion(key: String, value: String) {
        val sp = SalesforceSDKManager.getInstance().appContext.getSharedPreferences(VERSION_SHARED_PREF, Context.MODE_PRIVATE)
        sp.edit().putString(key, value).commit()
    }

    /**
     * Returns the currently installed version of account manager.
     *
     * @return Currently installed version of account manager.
     */
    val installedAccMgrVersion: String
        get() = getInstalledVersion(ACC_MGR_KEY)

    /**
     * Returns the currently installed version of the specified key.
     *
     * @return Currently installed version of the specified key.
     */
    @VisibleForTesting
    protected open fun getInstalledVersion(key: String): String {
        val sp = SalesforceSDKManager.getInstance().appContext.getSharedPreferences(
            VERSION_SHARED_PREF,
            Context.MODE_PRIVATE
        )
        return sp.getString(key, "") ?: ""
    }

    // TODO: Remove upgrade step in Mobile SDK 11.0
    private fun upgradeFromBefore9_2_0To10_1_1PasscodeFixes() {
        val KEY_PASSCODE = "passcode"
        val KEY_TIMEOUT = "access_timeout"
        val KEY_PASSCODE_LENGTH = "passcode_length"
        val KEY_FAILED_ATTEMPTS = "failed_attempts"
        val KEY_PASSCODE_LENGTH_KNOWN = "passcode_length_known"
        val KEY_BIOMETRIC_ALLOWED = "biometric_allowed"
        val KEY_BIOMETRIC_ENROLLMENT = "biometric_enrollment"
        val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        val ctx = SalesforceSDKManager.getInstance().appContext

        val globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE)
        if (globalPrefs.contains(KEY_TIMEOUT) && globalPrefs.contains(KEY_PASSCODE_LENGTH)) {
            val globalEditor = globalPrefs.edit()
            // Check that Passcode was enabled
            val timeout = globalPrefs.getInt(KEY_TIMEOUT, 0)
            if (timeout != 0) {
                globalEditor.putBoolean(SCREEN_LOCK, true)
                globalEditor.putInt(SCREEN_LOCK_TIMEOUT, timeout)
            }

            globalEditor.remove(KEY_PASSCODE)
            globalEditor.remove(KEY_TIMEOUT)
            globalEditor.remove(KEY_FAILED_ATTEMPTS)
            globalEditor.remove(KEY_PASSCODE_LENGTH)
            globalEditor.remove(KEY_PASSCODE_LENGTH_KNOWN)
            globalEditor.remove(KEY_BIOMETRIC_ALLOWED)
            globalEditor.remove(KEY_BIOMETRIC_ENROLLMENT)
            globalEditor.remove(KEY_BIOMETRIC_ENABLED)
            globalEditor.apply()

            // Set which users should have screen lock
            val accounts = userManager.getAuthenticatedUsers()

            if (accounts != null) {
                for (account in accounts) {
                    val orgPrefs = ctx.getSharedPreferences(
                        MOBILE_POLICY_PREF + account.getOrgLevelFilenameSuffix(),
                        Context.MODE_PRIVATE
                    )
                    if (orgPrefs.contains(KEY_TIMEOUT) && orgPrefs.contains(KEY_PASSCODE_LENGTH)) {
                        // Check that Passcode was enabled
                        val userTimeout = orgPrefs.getInt(KEY_TIMEOUT, 0)
                        if (userTimeout != 0) {
                            // Set screen lock key at user level
                            val userPrefs = ctx.getSharedPreferences(
                                MOBILE_POLICY_PREF + account.getUserLevelFilenameSuffix(),
                                Context.MODE_PRIVATE
                            )
                            userPrefs.edit().putBoolean(SCREEN_LOCK, true).putInt(SCREEN_LOCK_TIMEOUT, userTimeout).apply()
                        }

                        // Delete passcode keys at org level
                        val orgEditor = orgPrefs.edit()
                        orgEditor.remove(KEY_PASSCODE)
                        orgEditor.remove(KEY_TIMEOUT)
                        orgEditor.remove(KEY_FAILED_ATTEMPTS)
                        orgEditor.remove(KEY_PASSCODE_LENGTH)
                        orgEditor.remove(KEY_PASSCODE_LENGTH_KNOWN)
                        orgEditor.remove(KEY_BIOMETRIC_ALLOWED)
                        orgEditor.remove(KEY_BIOMETRIC_ENROLLMENT)
                        orgEditor.remove(KEY_BIOMETRIC_ENABLED)
                        orgEditor.apply()
                    }
                }
            }
        }
    }

    // TODO: Remove upgrade step in Mobile SDK 12.0
    private fun upgradeFromVersions9_2_0Thru10_1_1To10_1_1PasscodeFixes() {
        val ctx = SalesforceSDKManager.getInstance().appContext
        val globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE)
        if (globalPrefs.contains(SCREEN_LOCK)) {
            val accounts = userManager.getAuthenticatedUsers()
            if (accounts != null) {
                HttpAccess.init(ctx) // only needed because we need to hit the network for this upgrade step.
                Executors.newSingleThreadExecutor().execute {
                    var lowestTimeout = Int.MAX_VALUE

                    // Get and set connected app mobile policy timeout per user.
                    for (account in accounts) {
                        try {
                            val response = OAuth2.callIdentityService(
                                HttpAccess.DEFAULT!!,
                                account.idUrl ?: "",
                                account.authToken ?: ""
                            )

                            if (response.screenLock && response.screenLockTimeout != -1) {
                                val userPrefs = ctx.getSharedPreferences(
                                    MOBILE_POLICY_PREF + account.getUserLevelFilenameSuffix(),
                                    Context.MODE_PRIVATE
                                )
                                val timeoutInMills = response.screenLockTimeout * 1000 * 60
                                userPrefs.edit().putInt(SCREEN_LOCK_TIMEOUT, timeoutInMills).apply()

                                if (lowestTimeout == Int.MAX_VALUE || timeoutInMills < lowestTimeout) {
                                    lowestTimeout = timeoutInMills
                                }
                            }
                        } catch (e: IOException) {
                            SalesforceSDKLogger.e(TAG, "Exception throw retrieving mobile policy", e)
                        }
                    }

                    // Set timeout or remove block.
                    if (lowestTimeout < Int.MAX_VALUE && lowestTimeout > 0) {
                        globalPrefs.edit().putInt(SCREEN_LOCK_TIMEOUT, lowestTimeout).apply()
                    } else {
                        globalPrefs.edit().remove(SCREEN_LOCK).apply()
                    }
                }
            }
        }
    }

    // TODO: Remove upgrade step in Mobile SDK 12.0
    private fun updateFromBefore11_0_0() {
        val legacySettingsManager = LegacyAdminSettingsManager()
        val settingsManager = SalesforceSDKManager.getInstance().adminSettingsManager
        val accounts = userManager.getAuthenticatedUsers()
        if (accounts != null) {
            for (account in accounts) {
                // Copying custom attributes from org level shared prefs to user level shared prefs
                val legacySettings = legacySettingsManager.getPrefs(account)
                if (legacySettings?.isNotEmpty() == true) {
                    settingsManager?.setPrefs(legacySettings, account)
                }
            }
            // Cleaning up legacy settings at the end
            // because there could be multiple users in the same org
            for (account in accounts) {
                // NB: Not using resetAll because it will remove legacy and non-legacy settings
                //     because they share the same prefix
                legacySettingsManager.reset(account)
            }
        }
    }

    private fun updateFromBefore12_0_0() {
        // Re-register all users for push notifications with new keys once push is setup
        PushMessaging.reRegistrationRequested = true
    }

    private fun updateFromBefore13_0_2() {
        // Re-register all users for push notifications with new keys once push is setup
        PushMessaging.reRegistrationRequested = true
    }

    /**
     *  Migrate any accounts with account_type "com.salesforce.androidsdk" to a unique value.
     *
     *  @deprecated Will be removed in Mobile SDK 15.0.0.
     */
    @Deprecated("Will be removed in Mobile SDK 15.0.0")
    @Suppress("DEPRECATION")
    fun migrateAccountType() {
        val LEGACY_ACCOUNT_TYPE = "com.salesforce.androidsdk"
        if (SalesforceSDKManager.getInstance().accountType == LEGACY_ACCOUNT_TYPE) {
            SalesforceSDKLogger.e(
                TAG, "No app specific account type found.  To ensure users " +
                        "can login override the \"account_type\" value in your strings.xml."
            )
            return
        }

        val accountManager = SalesforceSDKManager.getInstance().clientManager.accountManager
        val userAccountManager = SalesforceSDKManager.getInstance().userAccountManager

        for (account in accountManager.getAccountsByType(LEGACY_ACCOUNT_TYPE)) {
            try {
                val userAccount = userAccountManager.buildUserAccount(account)
                if (userAccount == null) {
                    SalesforceSDKLogger.e(TAG, "Unable to build UserAccount from account: ${account.name}")
                    continue
                }

                // Android OS accounts are immutable so we have to remove the account and add a new one.
                accountManager.removeAccountExplicitly(account)
                userAccountManager.createAccount(userAccount)
            } catch (e: Exception) {
                SalesforceSDKLogger.e(TAG, "Failed to migrate account: ${account.name}", e)
            }
        }
    }

    companion object {
        private const val VERSION_SHARED_PREF = "version_info"
        private const val ACC_MGR_KEY = "acc_mgr_version"
        private const val TAG = "SalesforceSDKUpgradeManager"

        private var INSTANCE: SalesforceSDKUpgradeManager? = null

        /**
         * Returns an instance of this class.
         *
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(): SalesforceSDKUpgradeManager {
            if (INSTANCE == null) {
                INSTANCE = SalesforceSDKUpgradeManager()
            }
            return INSTANCE!!
        }
    }
}
