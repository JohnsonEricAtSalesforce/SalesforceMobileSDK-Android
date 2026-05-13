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
package com.salesforce.androidsdk.analytics.model

import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger
import org.json.JSONException
import org.json.JSONObject

/**
 * Represents common attributes specific to this app and device. These
 * attributes will be appended to the events stored using this library.
 *
 * @author bhariharan
 */
class DeviceAppAttributes {

    val appVersion: String?
    val appName: String?
    val osVersion: String?
    val osName: String?
    val nativeAppType: String?
    val mobileSdkVersion: String?
    val deviceModel: String?
    val deviceId: String?
    val clientId: String?

    /**
     * Parameterized constructor.
     *
     * @param appVersion App version.
     * @param appName App name.
     * @param osVersion OS version.
     * @param osName OS name.
     * @param nativeAppType App type.
     * @param mobileSdkVersion Mobile SDK version.
     * @param deviceModel Device model.
     * @param deviceId Device ID.
     * @param clientId Client ID.
     */
    constructor(
        appVersion: String?,
        appName: String?,
        osVersion: String?,
        osName: String?,
        nativeAppType: String?,
        mobileSdkVersion: String?,
        deviceModel: String?,
        deviceId: String?,
        clientId: String?
    ) {
        this.appVersion = appVersion
        this.appName = appName
        this.osVersion = osVersion
        this.osName = osName
        this.nativeAppType = nativeAppType
        this.mobileSdkVersion = mobileSdkVersion
        this.deviceModel = deviceModel
        this.deviceId = deviceId
        this.clientId = clientId
    }

    /**
     * Constructs device app attributes from its JSON representation.
     * This is meant for internal use. Apps should use the other constructor
     * to build DeviceAppAttributes objects.
     *
     * @param json JSON object.
     */
    constructor(json: JSONObject?) {
        appVersion = json?.optString(APP_VERSION_KEY)
        appName = json?.optString(APP_NAME_KEY)
        osVersion = json?.optString(OS_VERSION_KEY)
        osName = json?.optString(OS_NAME_KEY)
        nativeAppType = json?.optString(NATIVE_APP_TYPE_KEY)
        mobileSdkVersion = json?.optString(MOBILE_SDK_VERSION_KEY)
        deviceModel = json?.optString(DEVICE_MODEL_KEY)
        deviceId = json?.optString(DEVICE_ID_KEY)
        clientId = json?.optString(CLIENT_ID_KEY)
    }

    /**
     * Returns a JSON representation of device app attributes.
     *
     * @return JSON object.
     */
    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put(APP_VERSION_KEY, appVersion)
            json.put(APP_NAME_KEY, appName)
            json.put(OS_VERSION_KEY, osVersion)
            json.put(OS_NAME_KEY, osName)
            json.put(NATIVE_APP_TYPE_KEY, nativeAppType)
            json.put(MOBILE_SDK_VERSION_KEY, mobileSdkVersion)
            json.put(DEVICE_MODEL_KEY, deviceModel)
            json.put(DEVICE_ID_KEY, deviceId)
            json.put(CLIENT_ID_KEY, clientId)
        } catch (e: JSONException) {
            SalesforceAnalyticsLogger.e(null, TAG, "Exception thrown while attempting to convert to JSON", e)
        }
        return json
    }

    companion object {
        private const val TAG = "DeviceAppAttributes"
        private const val APP_VERSION_KEY = "appVersion"
        private const val APP_NAME_KEY = "appName"
        private const val OS_VERSION_KEY = "osVersion"
        private const val OS_NAME_KEY = "osName"
        private const val NATIVE_APP_TYPE_KEY = "nativeAppType"
        private const val MOBILE_SDK_VERSION_KEY = "mobileSdkVersion"
        private const val DEVICE_MODEL_KEY = "deviceModel"
        private const val DEVICE_ID_KEY = "deviceId"
        private const val CLIENT_ID_KEY = "clientId"
    }
}
