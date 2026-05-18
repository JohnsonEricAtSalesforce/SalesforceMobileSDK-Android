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
package com.salesforce.androidsdk.mobilesync.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert

object JSONTestHelper {

    /**
     * Compare two JSON
     */
    @JvmStatic
    fun assertSameJSON(message: String, expected: Any?, actual: Any?) {
        if (!checkSameJSON(expected, actual)) {
            Assert.fail("$message expected->($expected) actual->($actual)")
        }
    }

    private fun checkSameJSON(expected: Any?, actual: Any?): Boolean {
        // Only one null
        if (actual !== expected && (expected == null || actual == null)) {
            return false
        }

        // One JSONObject.NULL
        if (actual !== expected && (expected === JSONObject.NULL || actual === JSONObject.NULL)) {
            return false
        }

        // Both arrays
        if (expected is JSONArray && actual is JSONArray) {
            return checkSameJSONArray(expected, actual)
        }

        // Both maps
        if (expected is JSONObject && actual is JSONObject) {
            return checkSameJSONObject(expected, actual)
        }

        // Atomic types - comparing string representations
        return expected.toString() == actual.toString()
    }

    /**
     * Compare two JSON arrays
     */
    @JvmStatic
    fun assertSameJSONArray(message: String, expected: JSONArray, actual: JSONArray) {
        if (!checkSameJSONArray(expected, actual)) {
            Assert.fail("$message expected->($expected) actual->($actual)")
        }
    }

    private fun checkSameJSONArray(expected: JSONArray, actual: JSONArray): Boolean {
        if (expected.length() != actual.length()) {
            return false
        }

        if (expected.toString() == actual.toString()) {
            return true
        }

        for (i in 0 until expected.length()) {
            if (!checkSameJSON(expected.get(i), actual.get(i))) {
                return false
            }
        }
        return true
    }

    /**
     * Compare two JSON maps
     */
    @JvmStatic
    fun assertSameJSONObject(message: String, expected: JSONObject, actual: JSONObject) {
        if (!checkSameJSONObject(expected, actual)) {
            Assert.fail("$message expected->($expected) actual->($actual)")
        }
    }

    private fun checkSameJSONObject(expected: JSONObject, actual: JSONObject): Boolean {
        if (expected.length() != actual.length()) {
            return false
        }

        if (expected.toString() == actual.toString()) {
            return true
        }

        val expectedNames = expected.names() ?: return actual.names() == null
        val actualNames = actual.names() ?: return false
        if (expectedNames.length() != actualNames.length()) {
            return false
        }
        val expectedValues = expected.toJSONArray(expectedNames) ?: return false
        val actualValues = actual.toJSONArray(expectedNames) ?: return false
        return checkSameJSONArray(expectedValues, actualValues)
    }
}
