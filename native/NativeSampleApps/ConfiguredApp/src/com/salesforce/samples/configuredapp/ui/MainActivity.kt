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
package com.salesforce.samples.configuredapp.ui

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.salesforce.androidsdk.R as SdkR
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.SalesforceActivity
import com.salesforce.samples.configuredapp.R
import org.json.JSONException

/**
 * Main activity.
 */
class MainActivity : SalesforceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme
        setTheme(if (isDarkTheme) SdkR.style.SalesforceSDK_Dark else SdkR.style.SalesforceSDK)
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this)
        setContentView(R.layout.main)
        var bootconfig = ""
        try {
            bootconfig = BootConfig.getBootConfig(this).asJSON().toString(4)
        } catch (e: JSONException) {
            Log.e("MainActivity.onCreate", "Could not serialize bootconfig", e)
        }
        findViewById<TextView>(R.id.bootconfig).text = bootconfig

        // Fix UI being drawn behind status and navigation bars on Android 15+
        if (SDK_INT > UPSIDE_DOWN_CAKE) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
                val mInsets = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            and WindowInsetsCompat.Type.displayCutout()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                val outValue = TypedValue()
                theme.resolveAttribute(android.R.attr.actionBarSize, outValue, true)
                val actionBarHeight = TypedValue.complexToDimensionPixelSize(
                    outValue.data, resources.displayMetrics
                )
                v.setPadding(mInsets.left, mInsets.top + actionBarHeight, mInsets.right, mInsets.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    override fun onResume(client: RestClient?) {
        // No-op
    }
}
