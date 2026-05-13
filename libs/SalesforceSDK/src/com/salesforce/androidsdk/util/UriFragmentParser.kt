/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.util

import android.net.Uri
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

/**
 * This parses a Uri fragment that uses a queryString style foo=bar&bar=foo
 * parameter passing (e.g. OAuth2)
 */
object UriFragmentParser {

    private const val TAG = "UriFragmentParser"

    /**
     * look for # error fragments and standard url param errors, like the
     * user clicked deny on the auth page
     *
     * @param uri
     * @return
     */
    @JvmStatic
    fun parse(uri: Uri): Map<String, String> {
        var retval = parse(uri.encodedFragment)
        if (retval.isEmpty()) {
            retval = parse(uri.encodedQuery)
        }
        return retval
    }

    @JvmStatic
    fun parse(fragmentString: String?): Map<String, String> {
        val res = mutableMapOf<String, String>()
        if (fragmentString == null) {
            return res
        }
        val trimmedFragment = fragmentString.trim()
        if (trimmedFragment.isEmpty()) {
            return res
        }
        val params = trimmedFragment.split("&")
        for (param in params) {
            val parts = param.split("=")
            try {
                res[URLDecoder.decode(parts[0], "UTF-8")] =
                    if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            } catch (e: UnsupportedEncodingException) {
                SalesforceSDKLogger.e(TAG, "Unsupported encoding", e)
            }
        }
        return res
    }
}
