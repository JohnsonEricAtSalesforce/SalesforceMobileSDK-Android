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
package com.salesforce.androidsdk.app

/**
 * Flags for ftr_ field in user agent
 */
object Features {
    const val FEATURE_AILTN_ENABLED = "AI"
    const val FEATURE_APP_IS_IDP = "IP"
    const val FEATURE_APP_IS_SP = "SP"
    const val FEATURE_BROWSER_LOGIN = "BW"
    const val FEATURE_CERT_AUTH = "CT"
    const val FEATURE_LOCALHOST = "LH"
    const val FEATURE_MDM = "MM"
    const val FEATURE_MULTI_USERS = "MU"
    const val FEATURE_PUSH_NOTIFICATIONS = "PN"
    const val FEATURE_USER_AUTH = "UA"
    const val FEATURE_SCREEN_LOCK = "SL"
    const val FEATURE_BIOMETRIC_AUTH = "BA"
    const val FEATURE_NATIVE_LOGIN = "NL"
    const val FEATURE_QR_CODE_LOGIN = "QR"
    const val FEATURE_WELCOME_DISCOVERY_LOGIN = "WD"
    const val FEATURE_RTR = "RT"
}
