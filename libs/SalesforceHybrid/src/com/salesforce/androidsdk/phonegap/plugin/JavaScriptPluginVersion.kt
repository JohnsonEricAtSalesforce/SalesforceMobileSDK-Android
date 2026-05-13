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
package com.salesforce.androidsdk.phonegap.plugin

import com.salesforce.androidsdk.app.SalesforceSDKManager

/**
 * Object that encapsulate the version reported by the javascript side
 */
class JavaScriptPluginVersion(private val version: String) {

    private val comparedToNative: Int = compareVersions(version, SalesforceSDKManager.SDK_VERSION)

    /**
     * @return true if the javascript side of the plugin is from the same SDK version than the native side
     */
    fun isCurrent(): Boolean {
        return comparedToNative == 0
    }

    /**
     * @return true if the javascript side of the plugin is from an older version of the SDK than the native side
     */
    fun isOlder(): Boolean {
        return comparedToNative < 0
    }

    /**
     * @return true if the javascript side of the plugin is from an newer version of the SDK than the native side
     */
    fun isNewer(): Boolean {
        return comparedToNative > 0
    }

    override fun toString(): String {
        return version
    }

    companion object {
        /**
         * @param version1
         * @param version2
         * @return -1/0/1 if version1 is older/same/newer than version2
         * dev version is assumed to precede the corresponding version 2.0.dev is older than 2.0
         */
        @JvmStatic
        fun compareVersions(version1: String, version2: String): Int {
            // If same strings, we are done
            if (version1 == version2) return 0

            // Split
            val version1parts = version1.split("\\.".toRegex()).toTypedArray()
            val version2parts = version2.split("\\.".toRegex()).toTypedArray()
            val minLength = minOf(version1parts.size, version2parts.size)

            // Compare each part
            for (i in 0 until minLength) {
                val version1part = safeParseInt(version1parts[i], -1)
                val version2part = safeParseInt(version2parts[i], -1)
                if (version1part != version2part) {
                    return if (version1part < version2part) -1 else 1
                }
            }

            // If one version is simply the dev form of the other, it's the older one
            if (version1parts.size == minLength + 1 && version1parts[minLength] == "dev") {
                return -1
            }
            if (version2parts.size == minLength + 1 && version2parts[minLength] == "dev") {
                return 1
            }

            // Same up to here, but one is longer, the longer one is a patch on the other one
            return if (version1parts.size > version2parts.size) 1 else -1
        }

        /**
         * @param fallback
         * @return integer contained in string or fallback if not a number
         */
        @JvmStatic
        fun safeParseInt(s: String, fallback: Int): Int {
            return try {
                s.toInt()
            } catch (e: NumberFormatException) {
                fallback
            }
        }
    }
}
