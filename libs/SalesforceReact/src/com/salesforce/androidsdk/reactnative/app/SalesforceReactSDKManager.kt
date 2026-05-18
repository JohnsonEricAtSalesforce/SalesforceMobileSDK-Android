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
package com.salesforce.androidsdk.reactnative.app

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.JavaScriptModule
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.reactnative.bridge.MobileSyncReactBridge
import com.salesforce.androidsdk.reactnative.bridge.SalesforceNetReactBridge
import com.salesforce.androidsdk.reactnative.bridge.SalesforceOauthReactBridge
import com.salesforce.androidsdk.reactnative.bridge.SmartStoreReactBridge
import com.salesforce.androidsdk.reactnative.ui.SalesforceReactActivity
import com.salesforce.androidsdk.ui.LoginActivity
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType

/**
 * SDK Manager for all react native applications
 */
open class SalesforceReactSDKManager protected constructor(
    context: Context,
    mainActivity: Class<out Activity>,
    loginActivity: Class<out Activity>
) : MobileSyncSDKManager(context, mainActivity, loginActivity, null) {

    override val appType: String
        get() = "ReactNative"

    /**
     * Call this method when setting up ReactInstanceManager
     *
     * @return ReactPackage for this application
     */
    fun getReactPackage(): ReactPackage {
        return object : ReactPackage {
            override fun createNativeModules(
                reactContext: ReactApplicationContext
            ): List<NativeModule> {
                val modules = mutableListOf<NativeModule>()
                modules.add(SalesforceOauthReactBridge(reactContext))
                modules.add(SalesforceNetReactBridge(reactContext))
                modules.add(SmartStoreReactBridge(reactContext))
                modules.add(MobileSyncReactBridge(reactContext))
                return modules
            }

            @Suppress("unused")
            fun createJSModules(): List<Class<out JavaScriptModule>> {
                return emptyList()
            }

            override fun createViewManagers(
                reactContext: ReactApplicationContext
            ): List<ViewManager<*, *>> {
                return emptyList()
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    override fun getDevActions(frontActivity: Activity): Map<String, DevActionHandler> {
        val devActions = super.getDevActions(frontActivity).toMutableMap()
        devActions["React Native Dev Support"] = object : DevActionHandler {
            override fun onSelected() {
                (frontActivity as SalesforceReactActivity).showReactDevOptionsDialog()
            }
        }
        return devActions
    }

    companion object {
        private fun init(
            context: Context,
            mainActivity: Class<out Activity>,
            loginActivity: Class<out Activity>
        ) {
            if (!hasInstance()) {
                setInstance(SalesforceReactSDKManager(context, mainActivity, loginActivity))
            }

            // Upgrade to the latest version.
            SalesforceReactUpgradeManager.getInstance().upgrade()
            initInternal(context)
            EventsObservable.get().notifyEvent(EventType.AppCreateComplete)
        }

        /**
         * Initializes components required for this class
         * to properly function. This method should be called
         * by react native apps using the Salesforce Mobile SDK.
         *
         * @param context      Application context.
         * @param mainActivity Activity that should be launched after the login flow.
         */
        @JvmStatic
        fun initReactNative(context: Context, mainActivity: Class<out Activity>) {
            init(context, mainActivity, LoginActivity::class.java)
        }

        /**
         * Initializes components required for this class
         * to properly function. This method should be called
         * by react native apps using the Salesforce Mobile SDK.
         *
         * @param context       Application context.
         * @param mainActivity  Activity that should be launched after the login flow.
         * @param loginActivity Login activity.
         */
        @JvmStatic
        @Suppress("unused")
        fun initReactNative(
            context: Context,
            mainActivity: Class<out Activity>,
            loginActivity: Class<out Activity>
        ) {
            init(context, mainActivity, loginActivity)
        }

        /**
         * Returns a singleton instance of this class.
         *
         * @return Singleton instance of SalesforceReactSDKManager.
         */
        @JvmStatic
        fun getInstance(): SalesforceReactSDKManager {
            return SalesforceSDKManager.getInstance() as SalesforceReactSDKManager
        }
    }
}
