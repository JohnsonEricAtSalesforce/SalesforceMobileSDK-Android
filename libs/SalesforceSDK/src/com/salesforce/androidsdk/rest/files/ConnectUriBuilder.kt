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

import android.net.Uri
import com.salesforce.androidsdk.rest.ApiVersionStrings

/**
 * A URI builder for connect URIs, it handles special cases for userId and for
 * optional parameters.
 *
 * @author sfell
 */
class ConnectUriBuilder {

    private val builder: Uri.Builder

    constructor() : this(Uri.parse(ApiVersionStrings.getBaseChatterPath()).buildUpon())

    constructor(b: Uri.Builder) {
        this.builder = b
    }

    fun appendPath(pathSegment: String): ConnectUriBuilder {
        builder.appendEncodedPath(pathSegment)
        return this
    }

    fun appendUserId(userId: String?): ConnectUriBuilder {
        if (EMPTY == userId) {
            throw IllegalArgumentException("invalid user id")
        }
        return appendPath(if (userId == null) ME else userId)
    }

    fun appendFolderId(folderId: String?): ConnectUriBuilder {
        if (EMPTY == folderId) {
            throw IllegalArgumentException("invalid folder id")
        }
        return appendPath(folderId!!)
    }

    fun appendPageNum(pageNum: Int?): ConnectUriBuilder {
        if (pageNum != null && pageNum < 0) {
            throw IllegalArgumentException("page number cannot be negative")
        }
        return appendQueryParam(PAGE, pageNum)
    }

    fun appendPageSize(pageSize: Int?): ConnectUriBuilder {
        if (pageSize != null && pageSize < 0) {
            throw IllegalArgumentException("page size cannot be negative")
        }
        return appendQueryParam(PAGESIZE, pageSize)
    }

    fun appendVersionNum(version: String?): ConnectUriBuilder {
        if (version != null && (EMPTY == version || Integer.valueOf(version) <= 0)) {
            throw IllegalArgumentException("version number cannot be smaller than 1")
        }
        return appendQueryParam(VERSIONNUMBER, version)
    }

    fun appendQueryParam(key: String?, `val`: Int?): ConnectUriBuilder {
        if (key != null && `val` != null)
            builder.appendQueryParameter(key, `val`.toString())
        return this
    }

    fun appendQueryParam(key: String?, `val`: String?): ConnectUriBuilder {
        if (key != null && `val` != null && EMPTY != `val`)
            builder.appendQueryParameter(key, `val`)
        return this
    }

    fun build(): Uri {
        return builder.build()
    }

    override fun toString(): String {
        return build().toString()
    }

    companion object {
        const val EMPTY: String = ""
        private const val ME = "me"
        private const val PAGE = "page"
        private const val PAGESIZE = "pageSize"
        private const val VERSIONNUMBER = "versionNumber"
    }
}
