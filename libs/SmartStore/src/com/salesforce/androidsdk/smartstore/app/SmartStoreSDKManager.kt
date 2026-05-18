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
package com.salesforce.androidsdk.smartstore.app

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.smartstore.R
import com.salesforce.androidsdk.smartstore.config.StoreConfig
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper
import com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.smartstore.ui.KeyValueStoreInspectorActivity
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType
import com.salesforce.androidsdk.util.ManagedFilesHelper

/**
 * SDK Manager for all native applications that use SmartStore
 */
open class SmartStoreSDKManager protected constructor(
    context: Context,
    mainActivity: Class<out Activity>?,
    loginActivity: Class<out Activity>?,
    nativeLoginActivity: Class<out Activity>?,
    googleCloudProjectId: Long? = null
) : SalesforceSDKManager(context, mainActivity!!, loginActivity, nativeLoginActivity, googleCloudProjectId) {

    override fun cleanUp(userAccount: UserAccount?) {
        if (userAccount != null) {
            // NB if database file was already deleted, we still need to call DBOpenHelper.deleteDatabase to clean up the DBOpenHelper cache
            DBOpenHelper.deleteAllDatabases(appContext, userAccount)
            removeAllKeyValueStores(userAccount)
        } else {
            DBOpenHelper.deleteAllUserDatabases(appContext)
        }
        super.cleanUp(userAccount)
    }

    /**
     * Return default database used by smart store in the global context
     *
     * @return SmartStore instance
     */
    fun getGlobalSmartStore(): SmartStore {
        return getGlobalSmartStore(null)
    }

    /**
     * Returns the database used by smart store in the global context.
     *
     * @param dbName The database name. This must be a valid file name without a
     *               filename extension such as ".db". Pass 'null' for default.
     * @return SmartStore instance.
     */
    fun getGlobalSmartStore(dbName: String?): SmartStore {
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_SMART_STORE_GLOBAL)
        val effectiveDbName = if (TextUtils.isEmpty(dbName)) DBOpenHelper.DEFAULT_DB_NAME else dbName!!
        val dbOpenHelper = DBOpenHelper.getOpenHelper(
            encryptionKey, context,
            effectiveDbName, null, null
        )
        return SmartStore(dbOpenHelper)
    }

    /**
     * Returns the database used by smart store for the current user.
     *
     * @return SmartStore instance.
     */
    fun getSmartStore(): SmartStore {
        return getSmartStore(userAccountManager.cachedCurrentUser)
    }

    /**
     * Returns the database used by smart store for a specified user.
     *
     * @param account UserAccount instance.
     * @return SmartStore instance.
     */
    fun getSmartStore(account: UserAccount?): SmartStore {
        return getSmartStore(account, null)
    }

    /**
     * Returns the database used by smart store for a specified user in the
     * specified community.
     *
     * @param account     UserAccount instance.
     * @param communityId Community ID.
     * @return SmartStore instance.
     */
    fun getSmartStore(account: UserAccount?, communityId: String?): SmartStore {
        return getSmartStore(DBOpenHelper.DEFAULT_DB_NAME, account, communityId)
    }

    /**
     * Returns the database used by smart store for a specified database name and
     * user in the specified community.
     *
     * @param dbNamePrefix The database name. This must be a valid file name without a
     *                     filename extension such as ".db".
     * @param account      UserAccount instance.
     * @param communityId  Community ID.
     * @return SmartStore instance.
     */
    fun getSmartStore(dbNamePrefix: String?, account: UserAccount?, communityId: String?): SmartStore {
        val effectiveDbName = if (TextUtils.isEmpty(dbNamePrefix)) DBOpenHelper.DEFAULT_DB_NAME else dbNamePrefix!!
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_SMART_STORE_USER)
        val dbOpenHelper = DBOpenHelper.getOpenHelper(
            encryptionKey, context,
            effectiveDbName, account, communityId
        )
        return SmartStore(dbOpenHelper)
    }

    /**
     * Returns whether global smart store is enabled or not.
     */
    fun hasGlobalSmartStore(dbName: String?): Boolean {
        val effectiveDbName = if (TextUtils.isEmpty(dbName)) DBOpenHelper.DEFAULT_DB_NAME else dbName!!
        return DBOpenHelper.smartStoreExists(context, effectiveDbName, null, null)
    }

    /**
     * Returns whether smart store is enabled for the current user or not.
     */
    fun hasSmartStore(): Boolean {
        return hasSmartStore(userAccountManager.cachedCurrentUser, null)
    }

    /**
     * Returns whether smart store is enabled for the specified user or not.
     */
    fun hasSmartStore(account: UserAccount?): Boolean {
        return hasSmartStore(account, null)
    }

    /**
     * Returns whether smart store is enabled for the specified community or not.
     */
    fun hasSmartStore(account: UserAccount?, communityId: String?): Boolean {
        return hasSmartStore(DBOpenHelper.DEFAULT_DB_NAME, account, communityId)
    }

    /**
     * Returns whether smart store is enabled for the specified database or not.
     */
    fun hasSmartStore(dbNamePrefix: String?, account: UserAccount?, communityId: String?): Boolean {
        val effectiveDbName = if (TextUtils.isEmpty(dbNamePrefix)) DBOpenHelper.DEFAULT_DB_NAME else dbNamePrefix!!
        return DBOpenHelper.smartStoreExists(context, effectiveDbName, account, communityId)
    }

    fun removeGlobalSmartStore(dbName: String?) {
        val effectiveDbName = if (TextUtils.isEmpty(dbName)) DBOpenHelper.DEFAULT_DB_NAME else dbName!!
        DBOpenHelper.deleteDatabase(context, effectiveDbName, null, null)
    }

    fun removeSmartStore() {
        removeSmartStore(userAccountManager.cachedCurrentUser)
    }

    fun removeSmartStore(account: UserAccount?) {
        removeSmartStore(account, null)
    }

    fun removeSmartStore(account: UserAccount?, communityId: String?) {
        removeSmartStore(DBOpenHelper.DEFAULT_DB_NAME, account, communityId)
    }

    fun removeSmartStore(dbNamePrefix: String?, account: UserAccount?, communityId: String?) {
        val effectiveDbName = if (TextUtils.isEmpty(dbNamePrefix)) DBOpenHelper.DEFAULT_DB_NAME else dbNamePrefix!!
        DBOpenHelper.deleteDatabase(context, effectiveDbName, account, communityId)
    }

    fun getGlobalStoresPrefixList(): List<String> {
        val userAccount = userAccountManager.cachedCurrentUser
        val communityId = userAccount?.communityId
        return DBOpenHelper.getGlobalDatabasePrefixList(context, userAccountManager.cachedCurrentUser, communityId)
    }

    fun getUserStoresPrefixList(): List<String> {
        return getUserStoresPrefixList(userAccountManager.cachedCurrentUser)
    }

    fun getUserStoresPrefixList(account: UserAccount?): List<String> {
        return if (account != null) {
            DBOpenHelper.getUserDatabasePrefixList(context, account, account.communityId)
        } else {
            ArrayList()
        }
    }

    fun removeAllGlobalStores() {
        val globalDBNames = getGlobalStoresPrefixList()
        for (storeName in globalDBNames) {
            removeGlobalSmartStore(storeName)
        }
    }

    fun removeAllUserStores() {
        removeAllUserStores(userAccountManager.cachedCurrentUser)
    }

    fun removeAllUserStores(account: UserAccount?) {
        DBOpenHelper.deleteAllDatabases(appContext, account)
    }

    /**
     * Setup global store using config found in res/raw/globalstore.json
     */
    fun setupGlobalStoreFromDefaultConfig() {
        SmartStoreLogger.d(TAG, "Setting up global store using config found in res/raw/globalstore.json")
        val config = StoreConfig(context, R.raw.globalstore)
        if (config.hasSoups()) {
            config.registerSoups(getGlobalSmartStore())
        }
    }

    /**
     * Setup user store using config found in res/raw/userstore.json
     */
    fun setupUserStoreFromDefaultConfig() {
        SmartStoreLogger.d(TAG, "Setting up user store using config found in res/raw/userstore.json")
        val config = StoreConfig(context, R.raw.userstore)
        if (config.hasSoups()) {
            config.registerSoups(getSmartStore())
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    override fun getDevActions(frontActivity: Activity): Map<String, DevActionHandler> {
        val devActions = super.getDevActions(frontActivity).toMutableMap()

        devActions["Inspect SmartStore"] = object : DevActionHandler {
            override fun onSelected() {
                frontActivity.startActivity(
                    SmartStoreInspectorActivity.getIntent(
                        frontActivity,
                        false,
                        DBOpenHelper.DEFAULT_DB_NAME
                    )
                )
            }
        }

        devActions["Inspect KeyValue Store"] = object : DevActionHandler {
            override fun onSelected() {
                frontActivity.startActivity(
                    KeyValueStoreInspectorActivity.getIntent(frontActivity)
                )
            }
        }

        return devActions
    }

    override val devSupportInfos: List<String>
        get() {
            val infos = ArrayList(super.devSupportInfos)
            infos.addAll(
                listOf(
                    "SQLCipher version", getSmartStore().getSQLCipherVersion(),
                    "SQLCipher Compile Options", TextUtils.join(", ", getSmartStore().getCompileOptions()),
                    "SQLCipher Runtime Setting", TextUtils.join(", ", getSmartStore().getRuntimeSettings()),
                    "User SmartStores", TextUtils.join(", ", getUserStoresPrefixList()),
                    "Global SmartStores", TextUtils.join(", ", getGlobalStoresPrefixList()),
                    "User Key-Value Stores", TextUtils.join(", ", getKeyValueStoresPrefixList()),
                    "Global Key-Value Stores", TextUtils.join(", ", getGlobalKeyValueStoresPrefixList())
                )
            )
            return infos
        }

    fun getKeyValueStore(storeName: String): KeyValueEncryptedFileStore {
        return getKeyValueStore(storeName, userAccountManager.cachedCurrentUser!!, null)
    }

    fun getKeyValueStore(storeName: String, account: UserAccount): KeyValueEncryptedFileStore {
        return getKeyValueStore(storeName, account, null)
    }

    fun getKeyValueStore(storeName: String, account: UserAccount, communityId: String?): KeyValueEncryptedFileStore {
        val suffix = account.getCommunityLevelFilenameSuffix(communityId)
        return KeyValueEncryptedFileStore(appContext, storeName + suffix, encryptionKey)
    }

    fun hasKeyValueStore(storeName: String): Boolean {
        return hasKeyValueStore(storeName, userAccountManager.cachedCurrentUser!!, null)
    }

    fun hasKeyValueStore(storeName: String, account: UserAccount): Boolean {
        return hasKeyValueStore(storeName, account, null)
    }

    fun hasKeyValueStore(storeName: String, account: UserAccount, communityId: String?): Boolean {
        val suffix = account.getCommunityLevelFilenameSuffix(communityId)
        return KeyValueEncryptedFileStore.hasKeyValueStore(appContext, storeName + suffix)
    }

    fun removeKeyValueStore(storeName: String) {
        removeKeyValueStore(storeName, userAccountManager.cachedCurrentUser!!, null)
    }

    fun removeKeyValueStore(storeName: String, account: UserAccount) {
        removeKeyValueStore(storeName, account, null)
    }

    fun removeKeyValueStore(storeName: String, account: UserAccount, communityId: String?) {
        val suffix = account.getCommunityLevelFilenameSuffix(communityId)
        KeyValueEncryptedFileStore.removeKeyValueStore(appContext, storeName + suffix)
    }

    fun getKeyValueStoresPrefixList(): List<String> {
        return getKeyValueStoresPrefixList(userAccountManager.cachedCurrentUser)
    }

    fun getKeyValueStoresPrefixList(account: UserAccount?): List<String> {
        return if (account == null) {
            ArrayList()
        } else {
            ManagedFilesHelper.getPrefixList(
                appContext, KeyValueEncryptedFileStore.KEY_VALUE_STORES,
                account.communityLevelFilenameSuffix, "", null
            )
        }
    }

    fun removeAllKeyValueStores() {
        removeAllKeyValueStores(userAccountManager.cachedCurrentUser)
    }

    fun removeAllKeyValueStores(account: UserAccount?) {
        if (account != null) {
            ManagedFilesHelper.deleteFiles(
                ManagedFilesHelper.getFiles(
                    appContext, KeyValueEncryptedFileStore.KEY_VALUE_STORES,
                    account.userLevelFilenameSuffix, "", null
                )
            )
        }
    }

    fun getGlobalKeyValueStore(storeName: String): KeyValueEncryptedFileStore {
        return KeyValueEncryptedFileStore(appContext, storeName + GLOBAL_SUFFIX, encryptionKey)
    }

    fun hasGlobalKeyValueStore(storeName: String): Boolean {
        return KeyValueEncryptedFileStore.hasKeyValueStore(appContext, storeName + GLOBAL_SUFFIX)
    }

    fun removeGlobalKeyValueStore(storeName: String) {
        KeyValueEncryptedFileStore.removeKeyValueStore(appContext, storeName + GLOBAL_SUFFIX)
    }

    fun getGlobalKeyValueStoresPrefixList(): List<String> {
        return ManagedFilesHelper.getPrefixList(
            appContext, KeyValueEncryptedFileStore.KEY_VALUE_STORES,
            GLOBAL_SUFFIX, "", null
        )
    }

    fun removeAllGlobalKeyValueStores() {
        ManagedFilesHelper.deleteFiles(
            ManagedFilesHelper.getFiles(
                appContext, KeyValueEncryptedFileStore.KEY_VALUE_STORES, GLOBAL_SUFFIX, "", null
            )
        )
    }

    companion object {
        private const val TAG = "SmartStoreSDKManager"
        const val GLOBAL_SUFFIX = "_global"

        private fun init(
            context: Context,
            mainActivity: Class<out Activity>?,
            loginActivity: Class<out Activity>?,
            nativeLoginActivity: Class<out Activity>?
        ) {
            if (!hasInstance()) {
                setInstance(SmartStoreSDKManager(context, mainActivity, loginActivity, nativeLoginActivity))
            }

            // Upgrade to the latest version.
            SmartStoreUpgradeManager.getInstance().upgrade()
            initInternal(context)
            EventsObservable.get().notifyEvent(EventType.AppCreateComplete)
        }

        /**
         * Initializes components required for this class
         * to properly function. This method should be called
         * by native apps using the Salesforce Mobile SDK.
         *
         * @param context      Application context.
         * @param mainActivity Activity that should be launched after the login flow.
         */
        @JvmStatic
        fun initNative(context: Context, mainActivity: Class<out Activity>) {
            init(context, mainActivity, LoginActivity::class.java, null)
        }

        /**
         * Initializes components required for this class
         * to properly function. This method should be called
         * by native apps using the Salesforce Mobile SDK.
         *
         * @param context       Application context.
         * @param mainActivity  Activity that should be launched after the login flow.
         * @param loginActivity Login activity.
         */
        @JvmStatic
        fun initNative(context: Context, mainActivity: Class<out Activity>, loginActivity: Class<out Activity>?) {
            init(context, mainActivity, loginActivity, null)
        }

        /**
         * Initializes components required for this class
         * to properly function. This method should be called
         * by native apps using the Salesforce Mobile SDK.
         *
         * @param context             Application context.
         * @param mainActivity        Activity that should be launched after the login flow.
         * @param loginActivity       Login activity.
         * @param nativeLoginActivity Native login activity.
         */
        @JvmStatic
        fun initNative(
            context: Context,
            mainActivity: Class<out Activity>,
            loginActivity: Class<out Activity>?,
            nativeLoginActivity: Class<out Activity>?
        ) {
            init(context, mainActivity, loginActivity, nativeLoginActivity)
        }

        /**
         * Returns a singleton instance of this class.
         *
         * @return Singleton instance of SmartStoreSDKManager.
         */
        @JvmStatic
        fun getInstance(): SmartStoreSDKManager {
            return SalesforceSDKManager.getInstance() as SmartStoreSDKManager
        }
    }
}
