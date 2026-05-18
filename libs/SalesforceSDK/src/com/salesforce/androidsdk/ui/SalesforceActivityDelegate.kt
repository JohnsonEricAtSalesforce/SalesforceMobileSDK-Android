/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.ui

import android.app.Activity
import android.content.IntentFilter
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.LogoutCompleteReceiver
import com.salesforce.androidsdk.util.UserSwitchReceiver

/**
 * Class taking care of common behavior of Salesforce*Activity classes.
 */
open class SalesforceActivityDelegate(private val activity: Activity) {

    private lateinit var userSwitchReceiver: UserSwitchReceiver
    private lateinit var logoutCompleteReceiver: LogoutCompleteReceiver

    fun onCreate() {
        userSwitchReceiver = ActivityUserSwitchReceiver()
        ContextCompat.registerReceiver(
            activity,
            userSwitchReceiver,
            IntentFilter(UserAccountManager.USER_SWITCH_INTENT_ACTION),
            RECEIVER_NOT_EXPORTED
        )
        logoutCompleteReceiver = ActivityLogoutCompleteReceiver()
        ContextCompat.registerReceiver(
            activity,
            logoutCompleteReceiver,
            IntentFilter(SalesforceSDKManager.LOGOUT_COMPLETE_INTENT_ACTION),
            RECEIVER_NOT_EXPORTED
        )

        // Lets observers know that activity creation is complete.
        EventsObservable.get().notifyEvent(EventsObservable.EventType.MainActivityCreateComplete, this)
    }

    /**
     * Brings up ScreenLock if needed.
     * Builds RestClient if requested and then calls activity.onResume(restClient).
     * Otherwise calls activity.onResume(null).
     *
     * @param buildRestClient Whether to build a REST client on resume.
     */
    fun onResume(buildRestClient: Boolean) {
        if (buildRestClient) {
            // Gets login options.
            val accountType = SalesforceSDKManager.getInstance().accountType

            // Gets a rest client.
            ClientManager(
                SalesforceSDKManager.getInstance().appContext,
                accountType,
                SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()
            ).getRestClient(activity, object : ClientManager.RestClientCallback {
                override fun authenticatedRestClient(client: RestClient?) {
                    if (client == null) {
                        SalesforceSDKManager.getInstance()
                            .logout(null, activity, true, OAuth2.LogoutReason.CORRUPT_STATE_MSDK)
                        return
                    }
                    (activity as SalesforceActivityInterface).onResume(client)

                    // Lets observers know that rendition is complete.
                    EventsObservable.get().notifyEvent(EventsObservable.EventType.RenditionComplete)
                }
            })
        } else {
            (activity as SalesforceActivityInterface).onResume(null)
        }
    }

    fun onPause() {}

    fun onDestroy() {
        activity.unregisterReceiver(userSwitchReceiver)
        activity.unregisterReceiver(logoutCompleteReceiver)
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (SalesforceSDKManager.getInstance().isDevSupportEnabled()) {
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                SalesforceSDKManager.getInstance().showDevSupportDialog(activity)
                return true
            }
        }
        return false
    }

    /**
     * Acts on the user switch event.
     */
    private inner class ActivityUserSwitchReceiver : UserSwitchReceiver() {
        override fun onUserSwitch() {
            (activity as SalesforceActivityInterface).onUserSwitched()
        }
    }

    /**
     * Acts on the logout complete event.
     */
    private inner class ActivityLogoutCompleteReceiver : LogoutCompleteReceiver() {
        override fun onLogoutComplete(reason: OAuth2.LogoutReason, userAccount: UserAccount?) {
            (activity as SalesforceActivityInterface).onLogoutComplete()
        }
    }
}
