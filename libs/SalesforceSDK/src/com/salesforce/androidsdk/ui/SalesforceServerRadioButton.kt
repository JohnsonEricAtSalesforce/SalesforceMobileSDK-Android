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
package com.salesforce.androidsdk.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.TextAppearanceSpan
import android.widget.RadioButton
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager

/**
 * Custom radio button implementation to represent a Salesforce
 * custom server endpoint. Classes using this radio button should use
 * the custom 'setText()' method to display text in this radio button.
 *
 * @author bhariharan
 */
@SuppressLint("AppCompatCustomView")
class SalesforceServerRadioButton(
    context: Context,
    private val name: String,
    private val url: String,
    private val isCustom: Boolean
) : RadioButton(context) {

    private val context: Context = context

    init {
        setText()
    }

    /**
     * Formats the text the right way, before displaying it.
     */
    fun setText() {
        val result = SpannableStringBuilder()
        if (name != null && url != null) {
            val titleSpan = SpannableString(name)
            val isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme
            val titleStyle = if (isDarkTheme) R.style.SalesforceSDK_RadialButtonTitle_Dark else R.style.SalesforceSDK_RadialButtonTitle
            val urlStyle = if (isDarkTheme) R.style.SalesforceSDK_RadialButtonUrl_Dark else R.style.SalesforceSDK_RadialButtonUrl
            titleSpan.setSpan(
                TextAppearanceSpan(context, titleStyle), 0, name.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val urlSpan = SpannableString(url)
            urlSpan.setSpan(
                TextAppearanceSpan(context, urlStyle), 0, url.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            result.append(titleSpan)
            result.append(System.getProperty("line.separator"))
            result.append(urlSpan)
        }
        super.setText(result, BufferType.SPANNABLE)
    }

    /**
     * Returns the server name.
     *
     * @return Server name.
     */
    fun getName(): String {
        return name
    }

    /**
     * Returns the server URL.
     *
     * @return Server URL.
     */
    fun getUrl(): String {
        return url
    }

    /**
     * Returns whether this is a custom server or not.
     *
     * @return True - if custom server, False - otherwise.
     */
    fun isCustom(): Boolean {
        return isCustom
    }
}
