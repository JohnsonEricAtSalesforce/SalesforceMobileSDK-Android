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
package com.salesforce.androidsdk.smartstore.store

import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Simple class to represent index spec
 */
class IndexSpec(
    val path: String,
    val type: Type,
    val columnName: String? = null
) {

    @Deprecated("Use primary constructor with nullable columnName")
    constructor(path: String, type: Type) : this(path, type, null)

    override fun hashCode(): Int {
        var result = 17
        result = 31 * result + path.hashCode()
        result = 31 * result + type.hashCode()
        if (columnName != null) {
            result = 31 * result + columnName.hashCode()
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (other !is IndexSpec) return false

        var result = true
        result = result && path == other.path
        result = result && type == other.type
        result = result && if (columnName == null) {
            columnName == other.columnName
        } else {
            columnName == other.columnName
        }

        return result
    }

    /**
     * @return path | type
     */
    fun getPathType(): String {
        return "$path|$type"
    }

    /**
     * @return JSONObject for this IndexSpec
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("path", path)
        json.put("type", type)
        json.put("columnName", columnName)
        return json
    }

    companion object {
        /**
         * @param indexSpecs
         * @return JSONArray for the array of IndexSpec's
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun toJSON(indexSpecs: Array<IndexSpec>): JSONArray {
            val json = JSONArray()
            for (indexSpec in indexSpecs) {
                json.put(indexSpec.toJSON())
            }
            return json
        }

        /**
         * @param jsonArray
         * @return IndexSpec[] from a JSONArray
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSON(jsonArray: JSONArray): Array<IndexSpec> {
            val list = ArrayList<IndexSpec>()
            for (i in 0 until jsonArray.length()) {
                list.add(fromJSON(jsonArray.getJSONObject(i)))
            }
            return list.toTypedArray()
        }

        /**
         * Return IndexSpec given JSONObject
         * @param json
         * @return
         * @throws JSONException
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSON(json: JSONObject): IndexSpec {
            return IndexSpec(
                json.getString("path"),
                Type.valueOf(json.getString("type")),
                json.optString("columnName")
            )
        }

        /**
         * @param indexSpecs
         * @return map index spec path to index spec
         */
        @JvmStatic
        fun mapForIndexSpecs(indexSpecs: Array<IndexSpec>): Map<String, IndexSpec> {
            val map = HashMap<String, IndexSpec>()
            for (indexSpec in indexSpecs) {
                map[indexSpec.path] = indexSpec
            }
            return map
        }

        /**
         * @param indexSpecs
         * @return true if at least one of the indexSpec is of type full_text
         */
        @JvmStatic
        fun hasFTS(indexSpecs: Array<IndexSpec>): Boolean {
            for (indexSpec in indexSpecs) {
                if (indexSpec.type == Type.full_text) {
                    return true
                }
            }
            return false
        }

        /**
         * @param indexSpecs
         * @return true if at least one of the indexSpec is of type json1
         */
        @JvmStatic
        fun hasJSON1(indexSpecs: Array<IndexSpec>): Boolean {
            for (indexSpec in indexSpecs) {
                if (indexSpec.type == Type.json1) {
                    return true
                }
            }
            return false
        }
    }
}
