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

import android.text.TextUtils
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestRequest.RestMethod
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * This defines the HTTP requests in the connect API for files functionality.
 *
 * @author sfell
 */
object FileRequests : ApiRequests() {

    @JvmStatic
    fun getContentDocumentLinkPath(): String {
        return ApiVersionStrings.getBaseSObjectPath() + "ContentDocumentLink"
    }

    /**
     * Build a Request that can fetch a page from the files owned by the specified user.
     *
     * @param userId If null the context user is used, otherwise it should be an Id of a user.
     * @param pageNum If null fetches the first page, otherwise fetches the specified page.
     * @return A new RestRequest that can be used to fetch this data.
     */
    @JvmStatic
    fun ownedFilesList(userId: String?, pageNum: Int?): RestRequest {
        return make(base("connect/files/users").appendUserId(userId).appendPageNum(pageNum))
    }

    /**
     * Build a Request that can fetch a page from the list of files from groups
     * that the user is a member of.
     *
     * @param userId If null the context user is used, otherwise it should be an Id of a user.
     * @param pageNum If null fetches the first page, otherwise fetches the specified page.
     * @return A new RestRequest that can be used to fetch this data.
     */
    @JvmStatic
    fun filesInUsersGroups(userId: String?, pageNum: Int?): RestRequest {
        return make(base("connect/files/users").appendUserId(userId).appendPath("filter/groups").appendPageNum(pageNum))
    }

    /**
     * Build a Request that can fetch a page from the list of files that have
     * been shared with the user.
     *
     * @param userId If null the context user is used, otherwise it should be an Id of a user.
     * @param pageNum If null fetches the first page, otherwise fetches the specified page.
     * @return A new RestRequest that can be used to fetch this data.
     */
    @JvmStatic
    fun filesSharedWithUser(userId: String?, pageNum: Int?): RestRequest {
        return make(base("connect/files/users").appendUserId(userId).appendPath("filter/sharedwithme").appendPageNum(pageNum))
    }

    /**
     * Build a Request that can fetch the file details of a particular version
     * of a file.
     *
     * @param sfdcId The Id of the file.
     * @param version If null fetches the most recent version, otherwise fetches this specific version.
     * @return A new RestRequest that can be used to fetch this data.
     */
    @JvmStatic
    fun fileDetails(sfdcId: String, version: String?): RestRequest {
        validateSfdcId(sfdcId)
        return make(base("connect/files").appendPath(sfdcId).appendVersionNum(version))
    }

    /**
     * Build a request that can fetch the latest file details of one or more
     * files in a single request.
     *
     * @param sfdcIds The list of file Ids to fetch.
     * @return A new RestRequest that can be used to fetch this data.
     */
    @JvmStatic
    fun batchFileDetails(sfdcIds: List<String>): RestRequest {
        validateSfdcIds(sfdcIds)
        val ids = TextUtils.join(",", sfdcIds)
        return make(base("connect/files").appendPath("batch").appendPath(ids))
    }

    /**
     * Build a Request that can fetch the a preview/rendition of a particular
     * page of the file (and version).
     *
     * @param sfdcId The Id of the file.
     * @param version If null fetches the most recent version, otherwise fetches this specific version.
     * @param renditionType What format of rendition do you want to get.
     * @param pageNum Which page to fetch, pages start at 0.
     * @return A new RestRequest that can be used to fetch this data.
     */
    @JvmStatic
    fun fileRendition(sfdcId: String, version: String?, renditionType: RenditionType, pageNum: Int?): RestRequest {
        validateSfdcId(sfdcId)
        return make(
            base("connect/files").appendPath(sfdcId).appendPath("rendition")
                .appendQueryParam("type", renditionType.toString()).appendVersionNum(version).appendPageNum(pageNum)
        )
    }

    /**
     * Builds a request that can fetch the actual binary file contents of this
     * particular file.
     *
     * @param sfdcId The Id of the file.
     * @param version The version of the file.
     * @return A new RestRequest that can be used to fetch this data.
     */
    @JvmStatic
    fun fileContents(sfdcId: String, version: String?): RestRequest {
        validateSfdcId(sfdcId)
        return make(base("connect/files").appendPath(sfdcId).appendPath("content").appendVersionNum(version))
    }

    /**
     * Build a Request that can fetch a page from the list of entities that this
     * file is shared to.
     *
     * @param sfdcId The Id of the file.
     * @param pageNum If null fetches the first page, otherwise fetches the specified page.
     * @return A new RestRequest that can be used to fetch this data.
     */
    @JvmStatic
    fun fileShares(sfdcId: String, pageNum: Int?): RestRequest {
        validateSfdcId(sfdcId)
        return make(base("connect/files").appendPath(sfdcId).appendPath("file-shares").appendPageNum(pageNum))
    }

    /**
     * Build a request that will add a file share for the specified fileId to
     * the specified entityId.
     *
     * @param fileId The Id of the file being shared.
     * @param entityId The Id of the entity to share the file to (e.g. a user or a group).
     * @param shareType The type of share (V - View, C - Collaboration).
     * @return A new RestRequest that be used to create this share.
     */
    @JvmStatic
    fun addFileShare(fileId: String, entityId: String, shareType: String): RestRequest {
        validateSfdcIds(fileId, entityId)
        return RestRequest(RestMethod.POST, getContentDocumentLinkPath(), makeFileShare(fileId, entityId, shareType))
    }

    /**
     * Build a request that will delete the specified file share.
     *
     * @param shareId The Id of the file share record (aka ContentDocumentLink).
     * @return A new RestRequest that be used to create this share.
     */
    @JvmStatic
    fun deleteFileShare(shareId: String): RestRequest {
        validateSfdcId(shareId)
        return RestRequest(RestMethod.DELETE, "${getContentDocumentLinkPath()}/$shareId")
    }

    /**
     * Build a request that can upload a new file to the server, this will
     * create a new file at version 1.
     *
     * @param theFile The path of the local file to upload to the server.
     * @param name The name of this file.
     * @param title The title of this file.
     * @param description A description of the file.
     * @param mimeType The mime-type of the file, if known.
     * @return A RestRequest that can perform this upload.
     */
    @JvmStatic
    fun uploadFile(theFile: File, name: String, title: String?, description: String?, mimeType: String?): RestRequest {
        val mediaType = mimeType?.toMediaType()
        val builder = MultipartBody.Builder()
        if (title != null) builder.addFormDataPart("title", title)
        if (description != null) builder.addFormDataPart("desc", description)
        builder.addFormDataPart("fileData", name, theFile.asRequestBody(mediaType))
        return RestRequest(RestMethod.POST, base("connect/files/users").appendPath("me").toString(), builder.build(), ApiRequests.HTTP_HEADERS)
    }

    private fun makeFileShare(fileId: String, entityId: String, shareType: String): RequestBody {
        val share: Map<String, String> = linkedMapOf(
            "ContentDocumentId" to fileId,
            "LinkedEntityId" to entityId,
            "ShareType" to shareType
        )
        return JSONObject(share as Map<*, *>).toString().toRequestBody(RestRequest.MEDIA_TYPE_JSON)
    }
}
