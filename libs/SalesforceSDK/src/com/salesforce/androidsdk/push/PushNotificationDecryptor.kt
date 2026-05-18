/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.push

import android.text.TextUtils
import android.util.Base64
import com.google.firebase.messaging.RemoteMessage
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.security.KeyStoreWrapper
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import java.security.PrivateKey

/**
 * This class processes incoming push notifications and passes them along to the app.
 * It decrypts the incoming notification if encryption of push notifications is enabled.
 *
 * @author bhariharan
 */
internal class PushNotificationDecryptor private constructor() {

    @Suppress("UNCHECKED_CAST")
    fun onPushMessageReceived(message: RemoteMessage) {
        val data = processNotificationPayload(message.data.toMutableMap())
        if (SalesforceSDKManager.hasInstance()) {
            val pnInterface = SalesforceSDKManager.getInstance().pushNotificationReceiver
            pnInterface?.onPushMessageReceived(data as Map<String?, String?>)
        }
    }

    private fun processNotificationPayload(data: MutableMap<String, String>): MutableMap<String, String> {

        // Checks if the payload is encrypted.
        val isEncryptedKey = "encrypted"
        val encrypted = data.containsKey(isEncryptedKey)
                && data[isEncryptedKey].toBoolean()
        if (!encrypted) {
            return data
        }

        // Checks if the payload contains a decryption key for the payload.
        val secretKey = "secret"
        val encryptedSecretKey = data[secretKey] ?: return data

        // Removes the decryption key and checks if the bundle contains a payload to decrypt.
        data.remove(secretKey)
        if (!data.containsKey(CONTENT_KEY)) {
            return data
        }
        return decryptPayload(encryptedSecretKey, data)
    }

    private fun decryptPayload(encryptedSecretKey: String, data: MutableMap<String, String>): MutableMap<String, String> {
        val encryptedData = data[CONTENT_KEY] ?: return data
        val privateKey = getRSAPrivateKey() ?: return data
        val symmetricKey = Encryptor.decryptWithRSAMultiCipherNodes(privateKey, encryptedSecretKey)
            ?: return data
        val key = ByteArray(16)
        System.arraycopy(symmetricKey, 0, key, 0, 16)
        val iv = ByteArray(16)
        System.arraycopy(symmetricKey, 16, iv, 0, 16)
        val encryptedPayload = Base64.decode(encryptedData, Base64.DEFAULT) ?: return data
        val decryptedData = Encryptor.decryptBytes(encryptedPayload, key, iv)
        if (decryptedData != null) {
            data[CONTENT_KEY] = decryptedData
        }
        return data
    }

    @Synchronized
    private fun getRSAPrivateKey(): PrivateKey? {
        var rsaPrivateKey: PrivateKey? = null
        val name = SalesforceKeyGenerator.getUniqueId(PushService.PUSH_NOTIFICATION_KEY_NAME)
        val sanitizedName = name?.replace("[^A-Za-z0-9]".toRegex(), "")
        if (!TextUtils.isEmpty(sanitizedName)) {
            rsaPrivateKey = KeyStoreWrapper.getInstance().getRSAPrivateKey(sanitizedName!!)
        }
        return rsaPrivateKey
    }

    companion object {
        private const val CONTENT_KEY = "content"
        private val INSTANCE = PushNotificationDecryptor()

        /**
         * Returns a singleton instance of this class.
         *
         * @return Singleton instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(): PushNotificationDecryptor {
            return INSTANCE
        }
    }
}
