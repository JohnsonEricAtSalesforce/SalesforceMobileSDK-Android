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
package com.salesforce.androidsdk.util

import android.os.Bundle
import android.text.TextUtils
import org.json.JSONException
import org.json.JSONObject

/**
 * A simple util class that has methods to serialize and deserialize Map and Bundle.
 *
 * @author bhariharan
 */
object MapUtil {

    private const val TAG = "MapUtil"

    /**
     * Adds a map of key-value pairs extracted from a bundle.
     *
     * @param bundle Bundle.
     * @param keys Keys.
     * @param map Map to be added to.
     * @return Map with the additions.
     */
    @JvmStatic
    fun addBundleToMap(
        bundle: Bundle?,
        keys: List<String>?,
        map: MutableMap<String, String>?
    ): Map<String, String>? {
        if (bundle == null || keys == null || bundle.isEmpty || keys.isEmpty()) {
            return map
        }
        val resultMap = map ?: mutableMapOf()
        for (key in keys) {
            if (!TextUtils.isEmpty(key)) {
                resultMap[key] = bundle.getString(key).orEmpty()
            }
        }
        return resultMap
    }

    /**
     * Adds a bundle of key-value pairs extracted from a map.
     *
     * @param map Map.
     * @param keys Keys.
     * @param bundle Bundle to be added to.
     * @return Bundle with the additions.
     */
    @JvmStatic
    fun addMapToBundle(map: Map<String, String>?, keys: List<String>?, bundle: Bundle?): Bundle? {
        if (map == null || keys == null || map.isEmpty() || keys.isEmpty()) {
            return bundle
        }
        val resultBundle = bundle ?: Bundle()
        for (key in keys) {
            if (!TextUtils.isEmpty(key)) {
                resultBundle.putString(key, map[key])
            }
        }
        return resultBundle
    }

    /**
     * Adds a map of key-value pairs extracted from a JSONObject.
     *
     * @param jsonObject JSONObject.
     * @param keys Keys.
     * @param map Map to be added to.
     * @return Map with the additions.
     */
    @JvmStatic
    fun addJSONObjectToMap(
        jsonObject: JSONObject?,
        keys: List<String>?,
        map: MutableMap<String, String>?
    ): Map<String, String>? {
        if (jsonObject == null || keys == null || jsonObject.length() == 0 || keys.isEmpty()) {
            return map
        }
        val resultMap = map ?: mutableMapOf()
        for (key in keys) {
            if (!TextUtils.isEmpty(key)) {
                resultMap[key] = jsonObject.optString(key)
            }
        }
        return resultMap
    }

    /**
     * Adds a JSONObject of key-value pairs extracted from a map.
     *
     * @param map Map.
     * @param keys Keys.
     * @param jsonObject JSONObject to be added to.
     * @return JSONObject with the additions.
     */
    @JvmStatic
    fun addMapToJSONObject(
        map: Map<String, String>?,
        keys: List<String>?,
        jsonObject: JSONObject?
    ): JSONObject? {
        if (map == null || keys == null || map.isEmpty() || keys.isEmpty()) {
            return jsonObject
        }
        val resultJson = jsonObject ?: JSONObject()
        for (key in keys) {
            if (!TextUtils.isEmpty(key)) {
                try {
                    resultJson.put(key, map[key])
                } catch (e: JSONException) {
                    SalesforceSDKLogger.e(TAG, "Exception thrown while creating JSON object", e)
                }
            }
        }
        return resultJson
    }
}
