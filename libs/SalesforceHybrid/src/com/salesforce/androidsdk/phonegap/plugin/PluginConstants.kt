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
package com.salesforce.androidsdk.phonegap.plugin

import com.salesforce.androidsdk.mobilesync.config.SyncsConfig
import com.salesforce.androidsdk.smartstore.config.StoreConfig

internal object PluginConstants {

    const val TARGET = SyncsConfig.TARGET

    const val OPTIONS = SyncsConfig.OPTIONS

    const val SOUP_SPEC = "soupSpec"

    const val RE_INDEX_DATA = "reIndexData"

    const val CURSOR_ID = "cursorId"

    const val TYPE = "type"

    const val SOUP_NAME = StoreConfig.SOUP_NAME

    const val PATH = "path"

    const val PATHS = "paths"

    const val QUERY_SPEC = "querySpec"

    const val EXTERNAL_ID_PATH = "externalIdPath"

    const val ENTRIES = "entries"

    const val ENTRY_IDS = "entryIds"

    const val INDEX = "index"

    const val INDEXES = StoreConfig.INDEXES

    const val IS_GLOBAL_STORE = "isGlobalStore"

    const val STORE_NAME = "storeName"

    const val SYNC_NAME = SyncsConfig.SYNC_NAME
}
