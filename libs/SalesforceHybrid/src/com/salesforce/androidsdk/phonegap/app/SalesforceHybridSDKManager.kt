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
package com.salesforce.androidsdk.phonegap.app

import android.app.Activity
import android.content.Context
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.config.SyncsConfig
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.phonegap.ui.SalesforceDroidGapActivity
import com.salesforce.androidsdk.smartstore.config.StoreConfig
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType

/**
 * SDK Manager for all hybrid applications.
 */
open class SalesforceHybridSDKManager protected constructor(
    context: Context,
    mainActivity: Class<out Activity>,
    loginActivity: Class<out Activity>
) : MobileSyncSDKManager(context, mainActivity, loginActivity, null) {

    override fun getUserAgent(qualifier: String): String {
        val config = BootConfig.getBootConfig(context)
        val updatedQualifier = if (config.isLocal()) {
            "${qualifier}Local"
        } else {
            "${qualifier}Remote"
        }
        return super.getUserAgent(updatedQualifier)
    }

    /**
     * Setup global store using config found in assets/www/globalstore.json
     */
    override fun setupGlobalStoreFromDefaultConfig() {
        SmartStoreLogger.d(TAG, "Setting up global store using config found in ${ConfigAssetPath.GLOBAL_STORE.path}")
        val config = StoreConfig(context, ConfigAssetPath.GLOBAL_STORE.path)
        if (config.hasSoups()) {
            config.registerSoups(getGlobalSmartStore())
        }
    }

    /**
     * Setup user store using config found in assets/www/userstore.json
     */
    override fun setupUserStoreFromDefaultConfig() {
        SmartStoreLogger.d(TAG, "Setting up user store using config found in ${ConfigAssetPath.USER_STORE.path}")
        val config = StoreConfig(context, ConfigAssetPath.USER_STORE.path)
        if (config.hasSoups()) {
            config.registerSoups(getSmartStore())
        }
    }

    /**
     * Setup global syncs using config found in assets/www/globalsyncs.json
     */
    override fun setupGlobalSyncsFromDefaultConfig() {
        MobileSyncLogger.d(TAG, "Setting up global syncs using config found in ${ConfigAssetPath.GLOBAL_SYNCS.path}")
        val config = SyncsConfig(context, ConfigAssetPath.GLOBAL_SYNCS.path)
        if (config.hasSyncs()) {
            config.createSyncs(getGlobalSmartStore())
        }
    }

    /**
     * Setup user syncs using config found in assets/www/usersyncs.json
     */
    override fun setupUserSyncsFromDefaultConfig() {
        MobileSyncLogger.d(TAG, "Setting up global syncs using config found in ${ConfigAssetPath.USER_SYNCS.path}")
        val config = SyncsConfig(context, ConfigAssetPath.USER_SYNCS.path)
        if (config.hasSyncs()) {
            config.createSyncs(getSmartStore())
        }
    }

    /**
     * Paths to the assets files containing configs for SmartStore/MobileSync in hybrid apps.
     */
    private enum class ConfigAssetPath(fileName: String) {
        GLOBAL_STORE("globalstore.json"),
        USER_STORE("userstore.json"),
        GLOBAL_SYNCS("globalsyncs.json"),
        USER_SYNCS("usersyncs.json");

        val path: String = "www${System.getProperty("file.separator")}$fileName"
    }

    companion object {
        private const val TAG = "SalesforceHybridSDKManager"

        private fun init(
            context: Context,
            mainActivity: Class<out Activity>,
            loginActivity: Class<out Activity>
        ) {
            if (!hasInstance()) {
                setInstance(SalesforceHybridSDKManager(context, mainActivity, loginActivity))
            }

            // Upgrade to the latest version.
            SalesforceHybridUpgradeManager.getInstance().upgrade()
            initInternal(context)

            EventsObservable.get().notifyEvent(EventType.AppCreateComplete)
        }

        /**
         * Initializes components required for this class
         * to properly function. This method should be called
         * by hybrid apps using the Salesforce Mobile SDK.
         *
         * @param context Application context.
         */
        @JvmStatic
        fun initHybrid(context: Context) {
            init(context, SalesforceDroidGapActivity::class.java, LoginActivity::class.java)
        }

        /**
         * Initializes components required for this class
         * to properly function. This method should be called
         * by hybrid apps using the Salesforce Mobile SDK.
         *
         * @param context Application context.
         * @param loginActivity Login activity.
         */
        @JvmStatic
        fun initHybrid(context: Context, loginActivity: Class<out Activity>) {
            init(context, SalesforceDroidGapActivity::class.java, loginActivity)
        }

        /**
         * Initializes components required for this class
         * to properly function. This method should be called
         * by hybrid apps that use a subclass of SalesforceDroidGapActivity.
         *
         * @param context Application context.
         * @param mainActivity Main activity.
         * @param loginActivity Login activity.
         */
        @JvmStatic
        fun initHybrid(
            context: Context,
            mainActivity: Class<out SalesforceDroidGapActivity>,
            loginActivity: Class<out Activity>
        ) {
            init(context, mainActivity, loginActivity)
        }

        /**
         * Returns a singleton instance of this class.
         *
         * @return Singleton instance of SalesforceHybridSDKManager.
         */
        @JvmStatic
        fun getInstance(): SalesforceHybridSDKManager {
            return if (hasInstance()) {
                SalesforceSDKManager.getInstance() as SalesforceHybridSDKManager
            } else {
                throw RuntimeException("Applications need to call SalesforceHybridSDKManager.init() first.")
            }
        }
    }
}
