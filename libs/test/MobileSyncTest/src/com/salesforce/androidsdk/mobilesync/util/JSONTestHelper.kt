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
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert

object JSONTestHelper {

    /**
     * Compare two JSON
     * @param message
     * @param expected
     * @param actual
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun assertSameJSON(message: String, expected: Any?, actual: Any?) {
        if (!checkSameJSON(expected, actual)) {
            Assert.fail("$message expected->($expected) actual->($actual)")
        }
    }

    @Throws(JSONException::class)
    private fun checkSameJSON(expected: Any?, actual: Any?): Boolean {

        // Only one null
        if (actual !== expected && (expected == null || actual == null)) {
            return false
        }

        // One one JSONObject.NULL
        if (actual !== expected && (expected === JSONObject.NULL || actual === JSONObject.NULL)) {
            return false
        }

        // Both arrays
        else if (expected is JSONArray && actual is JSONArray) {
            return checkSameJSONArray(expected, actual)
        }

        // Both maps
        else if (expected is JSONObject && actual is JSONObject) {
            return checkSameJSONObject(expected, actual)
        }

        // Atomic types
        else {

            // Comparing string representations, to avoid things like new Long(n) != new Integer(n)
            return expected.toString() == actual.toString()
        }
    }

    /**
     * Compare two JSON arrays
     * @param message
     * @param expected
     * @param actual
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun assertSameJSONArray(message: String, expected: JSONArray, actual: JSONArray) {
        if (!checkSameJSONArray(expected, actual)) {
            Assert.fail("$message expected->($expected) actual->($actual)")
        }
    }

    @Throws(JSONException::class)
    private fun checkSameJSONArray(expected: JSONArray, actual: JSONArray): Boolean {

        // First compare length
        if (expected.length() != actual.length()) {
            return false
        }

        // If string value match we are done
        if (expected.toString() == actual.toString()) {

            // Done
            return true
        }

        // If string values don't match, it might still be the same object (toString does not sort fields of maps)
        else {

            // Compare values
            for (i in 0 until expected.length()) {
                if (!checkSameJSON(expected.get(i), actual.get(i))) {
                    return false
                }
            }
            return true
        }
    }

    /**
     * Compare two JSON maps
     * @param message
     * @param expected
     * @param actual
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun assertSameJSONObject(message: String, expected: JSONObject, actual: JSONObject) {
        if (!checkSameJSONObject(expected, actual)) {
            Assert.fail("$message expected->($expected) actual->($actual)")
        }
    }

    @Throws(JSONException::class)
    private fun checkSameJSONObject(expected: JSONObject, actual: JSONObject): Boolean {

        // If string value match we are done
        if (expected.toString() == actual.toString()) {
            return true
        }

        // After Kotlin migration, null fields become empty lists/arrays in serialized JSON.
        // Normalize by removing keys from actual that have empty array values and are absent in expected.
        val normalizedActual = if (actual.length() > expected.length()) {
            val normalized = JSONObject(actual.toString())
            val keysToRemove = mutableListOf<String>()
            val iter = normalized.keys()
            while (iter.hasNext()) {
                val key = iter.next()
                if (!expected.has(key)) {
                    val value = normalized.opt(key)
                    if (value is JSONArray && value.length() == 0) {
                        keysToRemove.add(key)
                    }
                }
            }
            for (key in keysToRemove) {
                normalized.remove(key)
            }
            normalized
        } else {
            actual
        }

        // Compare length after normalization
        if (expected.length() != normalizedActual.length()) {
            return false
        }

        // Compare keys / values
        val expectedNames = expected.names() ?: return normalizedActual.length() == 0
        val expectedValues = expected.toJSONArray(expectedNames) ?: return false
        val actualValues = normalizedActual.toJSONArray(expectedNames) ?: return false
        return checkSameJSONArray(expectedValues, actualValues)
    }
}
