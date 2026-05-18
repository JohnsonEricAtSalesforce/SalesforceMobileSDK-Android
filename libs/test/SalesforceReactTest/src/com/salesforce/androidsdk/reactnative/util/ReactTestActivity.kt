/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative.util

import com.facebook.react.ReactActivityDelegate
import com.salesforce.androidsdk.reactnative.ui.SalesforceReactActivity
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.util.test.TestCredentials

/**
 * Sub-class of SalesforceReactActivity that authenticates using hard-coded credentials.
 *
 * Also uses ReactActivityTestDelegate as delegate
 */
class ReactTestActivity : SalesforceReactActivity() {

    companion object {
        @JvmStatic var username: String? = TestCredentials.USERNAME
        @JvmStatic var accountName: String? = TestCredentials.ACCOUNT_NAME
        @JvmStatic var refreshToken: String? = TestCredentials.REFRESH_TOKEN
        @JvmStatic var authToken: String? = "--will-be-set-through-refresh--"
        @JvmStatic var identityUrl: String? = TestCredentials.IDENTITY_URL
        @JvmStatic var instanceUrl: String? = TestCredentials.INSTANCE_URL
        @JvmStatic var loginUrl: String? = TestCredentials.LOGIN_URL
        @JvmStatic var orgId: String? = TestCredentials.ORG_ID
        @JvmStatic var userId: String? = TestCredentials.USER_ID
        @JvmStatic var photoUrl: String? = TestCredentials.PHOTO_URL
        @JvmStatic var clientId: String? = TestCredentials.CLIENT_ID
        @JvmStatic var language: String? = TestCredentials.LANGUAGE
        @JvmStatic var locale: String? = TestCredentials.LOCALE
    }

    override fun buildClientManager(): ClientManager {
        val clientManager = super.buildClientManager()
        clientManager.createNewAccount(
            accountName!!, username!!, refreshToken!!, authToken!!, instanceUrl!!,
            loginUrl!!, identityUrl!!, clientId!!, orgId!!, userId!!,
            null, null, null, null, null,
            null, photoUrl, null, null, null,
            null, null, null, null, null, null, false,
            language, locale
        )
        return clientManager
    }

    override fun createReactActivityDelegate(): ReactActivityDelegate {
        return ReactActivityTestDelegate(this, null)
    }
}
