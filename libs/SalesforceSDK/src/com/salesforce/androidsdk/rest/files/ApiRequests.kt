/*
 * Copyright (c) 2013-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest.files

import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestRequest.RestMethod

/**
 * Base class with helpers for building RestRequests of various types.
 *
 * @author sfell
 */
open class ApiRequests {

    protected fun make(builder: ConnectUriBuilder): RestRequest {
        return RestRequest(RestMethod.GET, builder.toString(), HTTP_HEADERS)
    }

    protected fun base(firstPathSegment: String): ConnectUriBuilder {
        return ConnectUriBuilder().appendPath(firstPathSegment)
    }

    protected fun validateSfdcId(sfdcId: String?) {
        if (sfdcId == null || ConnectUriBuilder.EMPTY == sfdcId) {
            throw IllegalArgumentException("invalid sfdcId")
        }
    }

    protected fun validateSfdcIds(vararg sfdcIds: String) {
        for (i in sfdcIds) {
            validateSfdcId(i)
        }
    }

    protected fun validateSfdcIds(sfdcIds: List<String>) {
        for (i in sfdcIds) {
            validateSfdcId(i)
        }
    }

    companion object {
        @JvmField
        val HTTP_HEADERS: Map<String, String> = mapOf(
            "X-Chatter-Entity-Encoding" to "false"
        )
    }
}
