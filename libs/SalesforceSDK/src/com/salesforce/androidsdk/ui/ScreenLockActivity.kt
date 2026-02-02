/*
 * Copyright (c) 2026-present, salesforce.com, inc.
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

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager.ACTION_SET_NEW_PASSWORD
import android.content.Intent
import android.content.pm.PackageManager.FEATURE_FACE
import android.content.pm.PackageManager.FEATURE_IRIS
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.provider.Settings.ACTION_BIOMETRIC_ENROLL
import android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityManager
import android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED
import androidx.biometric.BiometricManager.BIOMETRIC_STATUS_UNKNOWN
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.PaddingValues.Absolute
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight.Companion.Normal
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.salesforce.androidsdk.R.drawable.sf__salesforce_logo
import com.salesforce.androidsdk.R.string.sf__application_icon
import com.salesforce.androidsdk.R.string.sf__logout
import com.salesforce.androidsdk.R.string.sf__screen_lock_auth_error
import com.salesforce.androidsdk.R.string.sf__screen_lock_auth_failed
import com.salesforce.androidsdk.R.string.sf__screen_lock_auth_success
import com.salesforce.androidsdk.R.string.sf__screen_lock_error
import com.salesforce.androidsdk.R.string.sf__screen_lock_error_hw_unavailable
import com.salesforce.androidsdk.R.string.sf__screen_lock_retry_button
import com.salesforce.androidsdk.R.string.sf__screen_lock_setup_button
import com.salesforce.androidsdk.R.string.sf__screen_lock_setup_required
import com.salesforce.androidsdk.R.string.sf__screen_lock_subtitle
import com.salesforce.androidsdk.R.string.sf__screen_lock_title
import com.salesforce.androidsdk.R.style.SalesforceSDK_ScreenLock
import com.salesforce.androidsdk.R.style.SalesforceSDK_ScreenLock_Dark
import com.salesforce.androidsdk.app.SalesforceSDKManager.Companion.getInstance
import com.salesforce.androidsdk.auth.OAuth2.LogoutReason.USER_LOGOUT
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.MOBILE_POLICY_PREF
import com.salesforce.androidsdk.security.ScreenLockManager.Companion.SCREEN_LOCK
import com.salesforce.androidsdk.ui.ScreenLockViewModel.Companion.Factory
import com.salesforce.androidsdk.ui.theme.hintTextColor
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import com.salesforce.androidsdk.util.test.ExcludeFromJacocoGeneratedReport

/**
 * An activity that locks the app behind the operating system's provided
 * authentication.
 */
internal class ScreenLockActivity : FragmentActivity() {

    /** The displayed name of the app */
    private val appName = runCatching { getInstance().appName }.getOrNull() ?: "App"

    /** View model */
    private val viewModel: ScreenLockViewModel
            by viewModels { Factory }

    @OptIn(ExperimentalMaterial3Api::class)
    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Protect against screenshots.
        window.setFlags(FLAG_SECURE, FLAG_SECURE)

        val isDarkTheme = getInstance().isDarkTheme
        setTheme(if (isDarkTheme) SalesforceSDK_ScreenLock_Dark else SalesforceSDK_ScreenLock)

        // Makes the navigation bar visible on light themes.
        getInstance().setViewNavigationVisibility(this)

        setContent {
            MaterialTheme(colorScheme = getInstance().colorScheme()) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    ScreenLockView2(
                        appName = appName,
                        innerPadding = innerPadding,
                        appIcon = rememberDrawablePainter(
                            runCatching {
                                packageManager.getApplicationIcon(applicationInfo.packageName)
                            }.getOrNull() ?: ResourcesCompat.getDrawable(
                                resources,
                                sf__salesforce_logo,
                                null
                            )
                        ),
                        action = { logoutScreenLockUsers() },
                        viewModel = viewModel
                    )
                }
            }
        }

        // TODO: Remove this when min API > 33
        if (SDK_INT >= TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                PRIORITY_DEFAULT
            ) { /* Purposefully blank */ }
        }

        presentAuth()
    }

    /** The activity result for the biometric setup */
    val biometricSetupActivityResultLauncher = registerForActivityResult(StartActivityForResult()) {
        /*
         * Present authentication again after the user has come back from
         * security settings to ensure they actually set up a secure lock screen
         * such as pin, pattern, password etc. instead of swipe or none.
         */
        presentAuth()
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // TODO: Resolve deprecation. ECJ20260129
        // Purposefully blank
    }

    private fun presentAuth() {
        val biometricPrompt = getBiometricPrompt()
        val biometricManager = BiometricManager.from(this)

        when (biometricManager.canAuthenticate(getAuthenticators())) {
            BIOMETRIC_ERROR_NO_HARDWARE, BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED, BIOMETRIC_ERROR_UNSUPPORTED, BIOMETRIC_STATUS_UNKNOWN -> {
                // This should never happen.
                val error = getString(sf__screen_lock_error)
                SalesforceSDKLogger.e(TAG, "Biometric manager cannot authenticate. $error")
                setErrorMessage(error)
            }

            BIOMETRIC_ERROR_HW_UNAVAILABLE -> setErrorMessage(getString(sf__screen_lock_error_hw_unavailable))
            BIOMETRIC_ERROR_NONE_ENROLLED -> {
                setErrorMessage(getString(sf__screen_lock_setup_required, appName))

                /*
                 * Prompts the user to setup the operating system screen lock and biometrics.
                 * TODO: Remove when min API > 29.
                 */
                if (SDK_INT >= R) {
                    val biometricIntent = Intent(ACTION_BIOMETRIC_ENROLL)
                    biometricIntent.putExtra(EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, getAuthenticators())
                    viewModel.setupButtonAction.value = { biometricSetupActivityResultLauncher.launch(biometricIntent) }
                } else {
                    val lockScreenIntent = Intent(ACTION_SET_NEW_PASSWORD)
                    viewModel.setupButtonAction.value = { biometricSetupActivityResultLauncher.launch(lockScreenIntent) }
                }
                viewModel.setupButtonLabel.value = getString(sf__screen_lock_setup_button)
                viewModel.setupButtonVisible.value = true
            }

            BIOMETRIC_SUCCESS -> {
                resetUI()
                biometricPrompt.authenticate(getPromptInfo())
            }
        }
    }

    private fun getPromptInfo(): PromptInfo {
        var hasFaceUnlock = false
        if (SDK_INT >= Q) {
            hasFaceUnlock = packageManager.hasSystemFeature(FEATURE_FACE) ||
                    (packageManager.hasSystemFeature(FEATURE_IRIS))
        }

        return PromptInfo.Builder()
            .setTitle(getString(sf__screen_lock_title, appName))
            .setSubtitle(getString(sf__screen_lock_subtitle, appName))
            .setAllowedAuthenticators(getAuthenticators())
            .setConfirmationRequired(hasFaceUnlock)
            .build()
    }

    private fun getBiometricPrompt(): BiometricPrompt {
        @Suppress("CAST_NEVER_SUCCEEDS")
        return BiometricPrompt(
            this as FragmentActivity,
            ContextCompat.getMainExecutor(this),
            object : AuthenticationCallback() {

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onAuthError(errString)
                }

                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    finishSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    setErrorMessage(getString(sf__screen_lock_auth_failed))
                    sendAccessibilityEvent(getString(sf__screen_lock_auth_failed))
                }
            })
    }

    private fun onAuthError(errString: CharSequence) {
        var errString = errString
        val authError = getString(sf__screen_lock_auth_error)

        if (errString.isEmpty()) {
            errString = authError
        }
        setErrorMessage(errString.toString())
        sendAccessibilityEvent(authError)

        viewModel.setupButtonVisible.value = true
        viewModel.setupButtonLabel.value = getString(sf__screen_lock_retry_button)
        viewModel.setupButtonAction.value = { presentAuth() }
    }

    private fun finishSuccess() {
        resetUI()
        sendAccessibilityEvent(getString(sf__screen_lock_auth_success))
        val screenLockManager = getInstance().screenLockManager as ScreenLockManager?
        screenLockManager?.onUnlock()
        finish()
    }

    private fun getAuthenticators(): Int {
        // TODO: Remove when min API > 29.
        return if (SDK_INT >= R)
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }

    private fun logoutScreenLockUsers() {
        val userAccountManager = getInstance().userAccountManager
        val accounts = userAccountManager.getAuthenticatedUsers()
        val context = getInstance().appContext

        accounts?.forEach { account ->
            val accountPreferences = context.getSharedPreferences(
                "$MOBILE_POLICY_PREF${account.getUserLevelFilenameSuffix()}", MODE_PRIVATE
            )
            if (accountPreferences.getBoolean(SCREEN_LOCK, false)) {
                userAccountManager.signoutUser(account, null, true, USER_LOGOUT)
            }
        }

        sendAccessibilityEvent("You are logged out.")
        finish()
    }

    private fun setErrorMessage(message: String?) {
        viewModel.logoutButtonVisible.value = true
        viewModel.setupMessageText.value = message ?: ""
        viewModel.setupMessageVisible.value = true
    }

    private fun resetUI() {
        viewModel.logoutButtonVisible.value = false
        viewModel.setupButtonVisible.value = false
        viewModel.setupMessageVisible.value = false
    }

    private fun sendAccessibilityEvent(text: String?) {
        val am = this.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager?
        if (am != null && am.isEnabled) {
            val event = AccessibilityEvent.obtain()
            event.setEventType(TYPE_WINDOW_STATE_CHANGED)
            event.setClassName(javaClass.getName())
            event.setPackageName(this.packageName)
            event.text.add(text)
            am.sendAccessibilityEvent(event)
        }
    }

    companion object {
        private const val TAG = "ScreenLockActivity"
    }
}

@Composable
internal fun ScreenLockView2(
    action: () -> Unit,
    appIcon: Painter,
    appName: String,
    innerPadding: PaddingValues,
    viewModel: ScreenLockViewModel,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {

        // Log out button.
        Button(
            colors = ButtonColors(
                containerColor = Transparent,
                contentColor = colorScheme.hintTextColor,
                disabledContainerColor = Transparent,
                disabledContentColor = colorScheme.surfaceVariant,
            ),
            enabled = true,
            contentPadding = PaddingValues(PADDING_SIZE.dp),
            modifier = Modifier.padding(PADDING_SIZE.dp),
            onClick = {
                action()
            },
            shape = RoundedCornerShape(CORNER_RADIUS.dp),
        ) {
            Text(
                color = colorScheme.primary, // TODO: Review. ECJ20260129
                fontWeight = Normal, // TODO: Review. ECJ20260129
                fontSize = 17.sp, // TODO: Review. ECJ20260129
                text = stringResource(sf__logout),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Application icon.
        Image(
            modifier = Modifier
                .align(CenterHorizontally)
                .size(150.dp),
            contentDescription = stringResource(sf__application_icon),
            painter = appIcon,
        )

        Spacer(modifier = Modifier.weight(1f))

        // Set up message text.
        Text(
            fontSize = 14.sp, // TODO: Review. ECJ20260129
            modifier = Modifier.align(CenterHorizontally),
            text = stringResource(sf__screen_lock_setup_required, appName),
        )

        // Setup action button.
        if (viewModel.setupButtonVisible.value) {
            Button(
                colors = ButtonColors(
                    containerColor = colorScheme.tertiary,
                    contentColor = colorScheme.tertiary,
                    disabledContainerColor = colorScheme.surfaceVariant,
                    disabledContentColor = colorScheme.surfaceVariant,
                ),
                contentPadding = PaddingValues(PADDING_SIZE.dp),
                enabled = true,
                onClick = {
                    viewModel.setupButtonAction.value()
                },
                modifier = Modifier
                    .align(CenterHorizontally)
                    .padding(PADDING_SIZE.dp),
                shape = RoundedCornerShape(CORNER_RADIUS.dp),
            ) {
                Text(
                    color = colorScheme.onPrimary,
                    fontSize = 14.sp, // TODO: Review. ECJ20260129
                    fontWeight = Normal, // TODO: Review. ECJ20260129
                    text = viewModel.setupButtonLabel.value,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
fun ScreenLockView2Preview() {
    val testIconPainter = painterResource(id = sf__salesforce_logo)
    ScreenLockView2(
        appName = "App",
        innerPadding = Absolute(0.dp),
        appIcon = testIconPainter,
        viewModel = viewModel(),
        action = {},
    )
}