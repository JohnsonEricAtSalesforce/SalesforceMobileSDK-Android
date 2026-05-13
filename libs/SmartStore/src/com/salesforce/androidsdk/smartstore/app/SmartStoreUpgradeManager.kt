/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.app.SalesforceSDKUpgradeManager

/**
 * This class handles upgrades from one version to another.
 *
 * @author bhariharan
 */
open class SmartStoreUpgradeManager protected constructor() : SalesforceSDKUpgradeManager() {

    override fun upgrade() {
        super.upgrade()
        upgradeSmartStore()
    }

    /**
     * Upgrades smartstore data from existing client
     * version to the current version.
     */
    @Synchronized
    protected fun upgradeSmartStore() {
        val installedVersionStr = installedSmartStoreVersion
        if (installedVersionStr == SalesforceSDKManager.SDK_VERSION) {
            return
        }

        // Update shared preference file to reflect the latest version.
        writeCurVersion(SMART_STORE_KEY, SalesforceSDKManager.SDK_VERSION)

        // Compare SDK versions using SdkVersion class and add upgrade steps here as needed.
    }

    /**
     * Returns the currently installed version of smartstore.
     *
     * @return Currently installed version of smartstore.
     */
    val installedSmartStoreVersion: String
        get() = getInstalledVersion(SMART_STORE_KEY)

    companion object {
        /**
         * Key in shared preference file for smart store version.
         */
        private const val SMART_STORE_KEY = "smart_store_version"
        private const val TAG = "SmartStoreUpgradeManager"

        private var INSTANCE: SmartStoreUpgradeManager? = null

        /**
         * Returns an instance of this class.
         *
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(): SmartStoreUpgradeManager {
            if (INSTANCE == null) {
                INSTANCE = SmartStoreUpgradeManager()
            }
            return INSTANCE!!
        }
    }
}
