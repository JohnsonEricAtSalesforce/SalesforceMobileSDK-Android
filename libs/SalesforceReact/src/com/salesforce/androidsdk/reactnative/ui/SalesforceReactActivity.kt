/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative.ui

import android.app.AlertDialog
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.bridge.Callback
import com.salesforce.androidsdk.reactnative.R
import com.salesforce.androidsdk.reactnative.app.SalesforceReactSDKManager
import com.salesforce.androidsdk.reactnative.bridge.ReactBridgeHelper
import com.salesforce.androidsdk.reactnative.util.SalesforceReactLogger
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.SalesforceActivityDelegate
import com.salesforce.androidsdk.ui.SalesforceActivityInterface

/**
 * Main activity for a Salesforce ReactNative app.
 */
abstract class SalesforceReactActivity : ReactActivity(), SalesforceActivityInterface {

    private val delegate: SalesforceActivityDelegate = SalesforceActivityDelegate(this)
    private var client: RestClient? = null
    private var clientManager: ClientManager? = null
    private var reactActivityDelegate: SalesforceReactActivityDelegate? = null
    var overlayPermissionRequiredDialog: AlertDialog? = null

    /**
     * Pending callbacks for authentication requests from the React Native bridge.
     *
     * When authenticate() is called from JavaScript:
     * - These callbacks are stored in pending variables
     * - They are invoked once authentication completes (either immediately if already
     *   authenticated, or after OAuth flow completes)
     * - Two code paths can invoke these: authenticatedRestClient() callback (always runs)
     *   or onResume() (only runs after OAuth pause/resume cycle)
     * - Whichever path runs first invokes the callbacks and clears these to null
     * - The other path sees null and does nothing, preventing double invocation
     *
     * See authenticate() and onResume(RestClient) for the coordination logic.
     */
    private var pendingAuthSuccessCallback: Callback? = null
    private var pendingAuthErrorCallback: Callback? = null

    /**
     * Returns if authentication should be performed automatically or not.
     *
     * @return True - if you want login to happen as soon as activity is loaded, False - otherwise.
     */
    open fun shouldAuthenticate(): Boolean {
        return true
    }

    /**
     * Called if shouldAuthenticate() returned true but device is offline.
     */
    open fun onErrorAuthenticateOffline() {
        val t = Toast.makeText(
            this,
            R.string.sf__should_authenticate_but_is_offline, Toast.LENGTH_LONG
        )
        t.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SalesforceReactLogger.i(TAG, "onCreate called")
        super.onCreate(savedInstanceState)
        clientManager = buildClientManager()
        delegate.onCreate()
    }

    override fun onResume() {
        super.onResume()
        delegate.onResume(false)
        loadReactAppOnceIfReady()
    }

    override fun onResume(client: RestClient?) {
        try {
            setRestClient(clientManager!!.peekRestClient())
        } catch (e: ClientManager.AccountInfoNotFoundException) {
            setRestClient(client)
        }

        // Not logged in.
        if (client == null) {
            onResumeNotLoggedIn()
        } else {
            // Logged in.
            SalesforceReactLogger.i(TAG, "onResume - already logged in")

            // If we have pending auth callbacks (from deferred authentication via authenticate()),
            // invoke them now. This handles the OAuth flow scenario where the activity was paused
            // for login and is now resuming.
            //
            // NOTE: This works in coordination with authenticate()'s authenticatedRestClient callback.
            // In the OAuth flow, there's a race condition between onResume() and authenticatedRestClient().
            // Whichever runs first will find pending callbacks non-null, invoke them, and set them to null.
            // The other will find them null and do nothing. This ensures callbacks are invoked exactly once.
            //
            // For the "already authenticated" scenario, authenticatedRestClient() invokes callbacks
            // immediately without any pause/resume cycle, so this code is never reached.
            if (pendingAuthSuccessCallback != null) {
                SalesforceReactLogger.i(TAG, "onResume - invoking pending auth callbacks")
                getAuthCredentials(pendingAuthSuccessCallback, pendingAuthErrorCallback)
                pendingAuthSuccessCallback = null
                pendingAuthErrorCallback = null
            }
        }
    }

    private fun onResumeNotLoggedIn() {
        // Need to be authenticated.
        if (shouldAuthenticate()) {
            // Online.
            if (SalesforceReactSDKManager.getInstance().hasNetwork()) {
                SalesforceReactLogger.i(TAG, "onResumeNotLoggedIn - should authenticate/online - authenticating")
                login()
            } else {
                // Offline.
                SalesforceReactLogger.w(TAG, "onResumeNotLoggedIn - should authenticate/offline - can not proceed")
                onErrorAuthenticateOffline()
            }
        } else {
            // Does not need to be authenticated.
            SalesforceReactLogger.i(TAG, "onResumeNotLoggedIn - should not authenticate")
        }
    }

    override fun onPause() {
        super.onPause()
        delegate.onPause()
    }

    override fun onDestroy() {
        delegate.onDestroy()
        super.onDestroy()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return delegate.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    fun showReactDevOptionsDialog() {
        reactNativeHost.reactInstanceManager.showDevOptionsDialog()
    }

    protected open fun login() {
        SalesforceReactLogger.i(TAG, "login called")
        clientManager!!.getRestClient(this, RestClientCallback { client ->
            if (client == null) {
                SalesforceReactLogger.i(TAG, "login callback triggered with null client")
                logout(null)
            } else {
                SalesforceReactLogger.i(TAG, "login callback triggered with actual client")
                this@SalesforceReactActivity.restartReactNativeApp()
            }
        })
    }

    /**
     * Method called from bridge to logout.
     *
     * @param successCallback Success callback.
     */
    fun logout(successCallback: Callback?) {
        SalesforceReactLogger.i(TAG, "logout called")
        SalesforceReactSDKManager.getInstance().logout(this)
        if (successCallback != null) {
            ReactBridgeHelper.invoke(successCallback, "Logout complete")
        }
    }

    /**
     * Method called from bridge to authenticate.
     *
     * @param successCallback Success callback.
     * @param errorCallback Error callback.
     */
    fun authenticate(successCallback: Callback?, errorCallback: Callback?) {
        SalesforceReactLogger.i(TAG, "authenticate called")

        // Store callbacks in pending variables to handle both authentication scenarios:
        //
        // SCENARIO 1: Already authenticated (no OAuth needed)
        //   - getRestClient() callback is invoked immediately on the same thread
        //   - authenticatedRestClient() below invokes callbacks immediately
        //   - Activity does NOT pause/resume, so onResume() is NOT called again
        //   - Callbacks are successfully invoked
        //
        // SCENARIO 2: OAuth required (activity will pause/resume)
        //   - getRestClient() starts OAuth flow
        //   - Activity pauses (goes to login screen)
        //   - User completes OAuth, activity resumes
        //   - Either authenticatedRestClient() or onResume() runs first (race condition)
        //   - Whichever runs first invokes callbacks and clears pending variables
        //   - The other sees null pending variables and does nothing
        //   - Callbacks are successfully invoked exactly once
        //
        // The key fix: authenticatedRestClient() must ALWAYS invoke and clear callbacks,
        // not defer to onResume(), because onResume() is NOT called when already authenticated.
        pendingAuthSuccessCallback = successCallback
        pendingAuthErrorCallback = errorCallback

        clientManager!!.getRestClient(this, RestClientCallback { client ->
            SalesforceReactLogger.i(TAG, "authenticatedRestClient callback invoked")
            this@SalesforceReactActivity.setRestClient(client)

            // Invoke callbacks immediately now that we have a RestClient.
            // For Scenario 1 (already authenticated): This happens immediately, no pause/resume.
            // For Scenario 2 (OAuth required): This may happen before or after onResume().
            // In both cases, we invoke callbacks and clear pending variables to prevent double invocation.
            if (pendingAuthSuccessCallback != null) {
                SalesforceReactLogger.i(TAG, "authenticatedRestClient - invoking pending callbacks")
                getAuthCredentials(pendingAuthSuccessCallback, pendingAuthErrorCallback)
                pendingAuthSuccessCallback = null
                pendingAuthErrorCallback = null
            }
        })
    }

    /**
     * Method called from bridge to get auth credentials.
     *
     * @param successCallback Success callback.
     * @param errorCallback Error callback.
     */
    fun getAuthCredentials(successCallback: Callback?, errorCallback: Callback?) {
        SalesforceReactLogger.i(TAG, "getAuthCredentials called")
        if (client != null) {
            if (successCallback != null) {
                ReactBridgeHelper.invoke(successCallback, client!!.getJSONCredentials())
            }
        } else {
            errorCallback?.invoke("Not authenticated")
        }
    }

    /**
     * Returns an instance of RestClient.
     *
     * @return An instance of RestClient.
     */
    fun getRestClient(): RestClient? {
        return client
    }

    protected open fun setRestClient(restClient: RestClient?) {
        client = restClient
        if (client != null) {
            loadReactAppOnceIfReady()
        }
    }

    open fun buildClientManager(): ClientManager {
        return SalesforceReactSDKManager.getInstance().clientManager
    }

    override fun onLogoutComplete() {
    }

    override fun onUserSwitched() {
    }

    override fun createReactActivityDelegate(): ReactActivityDelegate {
        reactActivityDelegate = SalesforceReactActivityDelegate(this, mainComponentName)
        return reactActivityDelegate!!
    }

    fun shouldReactBeRunning(): Boolean {
        return !shouldAskOverlayPermission() && (!shouldAuthenticate() || client != null)
    }

    protected open fun restartReactNativeApp() {
        reactNativeHost.reactInstanceManager.destroy()
        if (shouldReactBeRunning()) {
            reactNativeHost.reactInstanceManager.createReactContextInBackground()
        }
    }

    private fun shouldAskOverlayPermission(): Boolean {
        if (reactNativeHost.reactInstanceManager.devSupportManager.devSupportEnabled) {
            if (!Settings.canDrawOverlays(this)) {
                showPermissionWarning()
                return true
            } else {
                hidePermissionWarning()
            }
        }
        return false
    }

    private fun loadReactAppOnceIfReady() {
        reactActivityDelegate?.loadReactAppOnceIfReady(mainComponentName)
    }

    private fun showPermissionWarning() {
        if (overlayPermissionRequiredDialog == null) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Developer mode: Overlay permissions need to be granted")
            builder.setCancelable(false)
            builder.setPositiveButton("Continue") { dialog, _ ->
                dialog.dismiss()
                this@SalesforceReactActivity.recreate()
            }
            overlayPermissionRequiredDialog = builder.create()
        }
        if (overlayPermissionRequiredDialog?.isShowing == false) {
            overlayPermissionRequiredDialog!!.show()
        }
    }

    private fun hidePermissionWarning() {
        overlayPermissionRequiredDialog?.dismiss()
    }

    companion object {
        private const val TAG = "SFReactActivity"
    }
}
