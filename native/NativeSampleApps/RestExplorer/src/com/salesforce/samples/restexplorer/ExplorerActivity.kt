/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.samples.restexplorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TabHost
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestRequest.RestMethod
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.rest.files.FileRequests
import com.salesforce.androidsdk.ui.SalesforceActivity
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType
import okhttp3.FormBody
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.util.TreeSet
import java.util.regex.Pattern

/**
 * Main activity for REST explorer.
 */
class ExplorerActivity : SalesforceActivity() {

    private val apiVersion by lazy { ApiVersionStrings.getVersionNumber(this) }
    private var client: RestClient? = null
    private lateinit var resultText: TextView
    private lateinit var tabHost: TabHost

    // Use for objectId fields auto-complete.
    private val knownIds = TreeSet<String>()

    private val idPattern = Pattern.compile("0[0-9a-zA-Z]{17}")

    internal fun getClient(): RestClient? = client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.explorer)
        tabHost = findViewById(android.R.id.tabhost)
        tabHost.setup()
        addTab("versions", R.string.versions_tab, R.id.versions_tab)
        addTab("resources", R.string.resources_tab, R.id.resources_tab)
        addTab("describe_global", R.string.describe_global_tab, R.id.describe_global_tab)
        addTab("metadata", R.string.metadata_tab, R.id.metadata_tab)
        addTab("describe", R.string.describe_tab, R.id.describe_tab)
        addTab("create", R.string.create_tab, R.id.create_tab)
        addTab("retrieve", R.string.retrieve_tab, R.id.retrieve_tab)
        addTab("update", R.string.update_tab, R.id.update_tab)
        addTab("upsert", R.string.upsert_tab, R.id.upsert_tab)
        addTab("delete", R.string.delete_tab, R.id.delete_tab)
        addTab("query", R.string.query_tab, R.id.query_tab)
        addTab("search", R.string.search_tab, R.id.search_tab)
        addTab("manual", R.string.manual_request_tab, R.id.manual_request_tab)
        addTab("search_scope_and_order", R.string.search_scope_and_order_tab, R.id.search_scope_and_order_tab)
        addTab("search_result_layout", R.string.search_result_layout_tab, R.id.search_result_layout_tab)
        addTab("owned_files_list", R.string.owned_files_list_tab, R.id.owned_files_list_tab)
        addTab("files_in_users_groups", R.string.files_in_users_groups_tab, R.id.files_in_users_groups_tab)
        addTab("files_shared_with_user", R.string.files_shared_with_user_tab, R.id.files_shared_with_user_tab)
        addTab("file_details", R.string.file_details_tab, R.id.file_details_tab)
        addTab("batch_file_details", R.string.batch_file_details_tab, R.id.batch_file_details_tab)
        addTab("files_shares", R.string.file_shares_tab, R.id.file_shares_tab)
        addTab("add_file_share", R.string.add_file_share_tab, R.id.add_file_share_tab)
        addTab("delete_file_share", R.string.delete_file_share_tab, R.id.delete_file_share_tab)
        addTab("priming_records", R.string.priming_records_tab, R.id.priming_records_tab)

        // Makes the result area scrollable.
        resultText = findViewById(R.id.result_text)
        resultText.movementMethod = ScrollingMovementMethod()
        (SalesforceSDKManager.getInstance() as RestExplorerApp.RestExplorerSDKManager)
            .addDevAction(this, "Export Credentials to Clipboard",
                object : SalesforceSDKManager.DevActionHandler {
                    override fun onSelected() {
                        exportCredentials()
                    }
                }
            )

        // Fix UI being drawn behind status and navigation bars on Android 15+
        if (SDK_INT > UPSIDE_DOWN_CAKE) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
                val mInsets = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            and WindowInsetsCompat.Type.displayCutout()
                            or WindowInsetsCompat.Type.displayCutout()
                )
                v.setPadding(mInsets.left, mInsets.top, mInsets.right, mInsets.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    override fun onResume(client: RestClient?) {
        this.client = client
    }

    private fun addTab(tag: String, titleId: Int, tabId: Int) {
        tabHost.addTab(
            tabHost.newTabSpec(tag).setIndicator(getString(titleId)).setContent(tabId)
        )
    }

    /**************************************************************************************************
     *
     * Buttons click handlers
     *
     **************************************************************************************************/

    /**
     * Called when the "print info" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onPrintInfoClick(@Suppress("UNUSED_PARAMETER") v: View) {
        printInfo()
    }

    /**
     * Called when the "clear" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onClearClick(@Suppress("UNUSED_PARAMETER") v: View) {
        resultText.text = ""
    }

    /**
     * Called when the "get versions" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onGetVersionsClick(@Suppress("UNUSED_PARAMETER") v: View) {
        sendRequest(RestRequest.getRequestForVersions())
    }

    /**
     * Called when the "get resources" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onGetResourcesClick(@Suppress("UNUSED_PARAMETER") v: View) {
        sendRequest(RestRequest.getRequestForResources(apiVersion))
    }

    /**
     * Called when the "describe global" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onDescribeGlobalClick(@Suppress("UNUSED_PARAMETER") v: View) {
        sendRequest(RestRequest.getRequestForDescribeGlobal(apiVersion))
    }

    /**
     * Called when the "metadata" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onGetMetadataClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val objectType = findViewById<EditText>(R.id.metadata_object_type_text)
            .text.toString()
        sendRequest(RestRequest.getRequestForMetadata(apiVersion, objectType))
    }

    /**
     * Called when the "describe" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onDescribeClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val objectType = findViewById<EditText>(R.id.describe_object_type_text)
            .text.toString()
        sendRequest(RestRequest.getRequestForDescribe(apiVersion, objectType))
    }

    /**
     * Called when the "create" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onCreateClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val objectType = findViewById<EditText>(R.id.create_object_type_text)
            .text.toString()
        val fields = parseFieldMap(R.id.create_fields_text)
        val request = try {
            RestRequest.getRequestForCreate(apiVersion, objectType, fields)
        } catch (e: Exception) {
            printHeader("Could not build create request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "retrieve" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onRetrieveClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val objectType = findViewById<EditText>(R.id.retrieve_object_type_text)
            .text.toString()
        val objectId = findViewById<EditText>(R.id.retrieve_object_id_text)
            .text.toString()
        val fieldList = parseCommaSeparatedList(R.id.retrieve_field_list_text)
        val request = try {
            RestRequest.getRequestForRetrieve(apiVersion, objectType, objectId, fieldList)
        } catch (e: Exception) {
            printHeader("Could not build retrieve request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "update" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onUpdateClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val objectType = findViewById<EditText>(R.id.update_object_type_text)
            .text.toString()
        val objectId = findViewById<EditText>(R.id.update_object_id_text)
            .text.toString()
        val fields = parseFieldMap(R.id.update_fields_text)
        val request = try {
            RestRequest.getRequestForUpdate(apiVersion, objectType, objectId, fields)
        } catch (e: Exception) {
            printHeader("Could not build update request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "upsert" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onUpsertClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val objectType = findViewById<EditText>(R.id.upsert_object_type_text)
            .text.toString()
        val externalIdField = findViewById<EditText>(R.id.upsert_external_id_field_text)
            .text.toString()
        val externalId = findViewById<EditText>(R.id.upsert_external_id_text)
            .text.toString()
        val fields = parseFieldMap(R.id.upsert_fields_text)
        val request = try {
            RestRequest.getRequestForUpsert(apiVersion, objectType, externalIdField, externalId, fields)
        } catch (e: Exception) {
            printHeader("Could not build upsert request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "delete" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onDeleteClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val objectType = findViewById<EditText>(R.id.delete_object_type_text)
            .text.toString()
        val objectId = findViewById<EditText>(R.id.delete_object_id_text)
            .text.toString()
        sendRequest(RestRequest.getRequestForDelete(apiVersion, objectType, objectId))
    }

    /**
     * Called when the "query" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onQueryClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val soql = findViewById<EditText>(R.id.query_soql_text).text.toString()
        val request = try {
            RestRequest.getRequestForQuery(apiVersion, soql)
        } catch (e: UnsupportedEncodingException) {
            printHeader("Could not build query request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "search" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onSearchClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val sosl = findViewById<EditText>(R.id.search_sosl_text).text.toString()
        val request = try {
            RestRequest.getRequestForSearch(apiVersion, sosl)
        } catch (e: UnsupportedEncodingException) {
            printHeader("Could not build search request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "manual" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onManualRequestClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val request = try {
            val editText = findViewById<EditText>(R.id.manual_request_path_text)
            val hintText = String.format(
                resources.getString(R.string.path_hint),
                ApiVersionStrings.getVersionNumber(this)
            )
            editText.hint = hintText
            val path = editText.text.toString()
            val paramsEntity = getParamsEntity(R.id.manual_request_params_text)
            val method = getMethod(R.id.manual_request_method_radiogroup)
            RestRequest(method, path, paramsEntity)
        } catch (e: UnsupportedEncodingException) {
            printHeader("Could not build manual request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "search scope and order" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onSearchScopeAndOrderClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val request = RestRequest.getRequestForSearchScopeAndOrder(ApiVersionStrings.getVersionNumber(this))
        sendRequest(request)
    }

    /**
     * Called when the "search result layout" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onSearchResultLayoutClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val objectList = parseCommaSeparatedList(R.id.search_result_layout_object_list_text)
        val request = try {
            RestRequest.getRequestForSearchResultLayout(ApiVersionStrings.getVersionNumber(this), objectList)
        } catch (e: UnsupportedEncodingException) {
            printHeader("Could not build search result layout request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "owned files list" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onOwnedFilesListClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val userId = findViewById<EditText>(R.id.owned_files_list_user_id_text)
            .text.toString()
        val pageStr = findViewById<EditText>(R.id.owned_files_list_page_text).text.toString()
        val request = try {
            val page = pageStr.toInt()
            FileRequests.ownedFilesList(userId, page)
        } catch (e: NumberFormatException) {
            printHeader("Could not build owned files list request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "files in users groups" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onFilesInUsersGroupsClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val userId = findViewById<EditText>(R.id.files_in_users_groups_user_or_group_id_text)
            .text.toString()
        val pageStr = findViewById<EditText>(R.id.files_in_users_groups_page_text).text.toString()
        val request = try {
            val page = pageStr.toInt()
            FileRequests.filesInUsersGroups(userId, page)
        } catch (e: NumberFormatException) {
            printHeader("Could not build files in users groups request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "files shared with user" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onFilesSharedWithUserClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val userId = findViewById<EditText>(R.id.files_shared_with_user_user_id_text)
            .text.toString()
        val pageStr = findViewById<EditText>(R.id.files_shared_with_user_page_text).text.toString()
        val request = try {
            val page = pageStr.toInt()
            FileRequests.filesSharedWithUser(userId, page)
        } catch (e: NumberFormatException) {
            printHeader("Could not build files shared with user request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "file details" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onFileDetailsClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val documentId = findViewById<EditText>(R.id.file_details_document_id_text)
            .text.toString()
        val version = findViewById<EditText>(R.id.file_details_version_text).text.toString()
        val request = FileRequests.fileDetails(documentId, version)
        sendRequest(request)
    }

    /**
     * Called when the "batch file details" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onBatchFileDetailsClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val documentIdList = parseCommaSeparatedList(R.id.batch_file_details_document_id_list_text)
        val request = FileRequests.batchFileDetails(documentIdList)
        sendRequest(request)
    }

    /**
     * Called when the "files shares" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onFileSharesClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val documentId = findViewById<EditText>(R.id.file_shares_document_id_text)
            .text.toString()
        val pageStr = findViewById<EditText>(R.id.file_shares_page_text).text.toString()
        val request = try {
            val page = pageStr.toInt()
            FileRequests.fileShares(documentId, page)
        } catch (e: NumberFormatException) {
            printHeader("Could not build files shares request")
            printException(e)
            return
        }
        sendRequest(request)
    }

    /**
     * Called when the "add file share" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onAddFileShareClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val documentId = findViewById<EditText>(R.id.add_file_share_document_id_text)
            .text.toString()
        val entityId = findViewById<EditText>(R.id.add_file_share_entity_id_text)
            .text.toString()
        val shareType = findViewById<EditText>(R.id.add_file_share_share_type_text)
            .text.toString()
        val request = FileRequests.addFileShare(documentId, entityId, shareType)
        sendRequest(request)
    }

    /**
     * Called when the "delete file share" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onDeleteFileShareClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val shareId = findViewById<EditText>(R.id.delete_file_share_share_id_text)
            .text.toString()
        val request = FileRequests.deleteFileShare(shareId)
        sendRequest(request)
    }

    /**
     * Called when the "priming records go" button is clicked.
     *
     * @param v View that was clicked.
     */
    @Throws(UnsupportedEncodingException::class)
    fun onGetPrimingRecordsClick(@Suppress("UNUSED_PARAMETER") v: View) {
        val request = RestRequest.getRequestForPrimingRecords(apiVersion, null, null)
        sendRequest(request)
    }

    private fun getParamsEntity(manualRequestParamsText: Int): RequestBody {
        var params = parseFieldMap(manualRequestParamsText)
        if (params == null) {
            params = HashMap()
        }
        val builder = FormBody.Builder()
        for ((key, value) in params) {
            builder.add(key, value as String)
        }
        return builder.build()
    }

    private fun getMethod(methodRadioGroup: Int): RestMethod {
        val radioGroup = findViewById<RadioGroup>(methodRadioGroup)
        val radioButton = findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
        return RestMethod.valueOf(radioButton.tag as String)
    }

    /**
     * Called when "Dev Menu" button is clicked.
     *
     * @param v View that was clicked.
     */
    fun onDevMenuClick(@Suppress("UNUSED_PARAMETER") v: View) {
        SalesforceSDKManager.getInstance().showDevSupportDialog(this)
    }

    /**
     * Helper to read a JSON string representing field name-value map.
     *
     * @param jsonTextField Text field.
     * @return Map of JSON.
     */
    private fun parseFieldMap(jsonTextField: Int): Map<String, Object>? {
        val fieldsString = findViewById<EditText>(jsonTextField).text.toString()
        if (fieldsString.isEmpty()) {
            return null
        }
        return try {
            val fieldsJson = JSONObject(fieldsString)
            val fields = HashMap<String, Object>()
            val names = fieldsJson.names()
            if (names != null) {
                for (i in 0 until names.length()) {
                    val name = names.get(i) as String
                    fields[name] = fieldsJson.get(name) as Object
                }
            }
            fields
        } catch (e: Exception) {
            printHeader("Could not parse: $fieldsString")
            printException(e)
            null
        }
    }

    private fun parseCommaSeparatedList(textField: Int): List<String> {
        val fieldsCsv = findViewById<EditText>(textField).text.toString()
        return fieldsCsv.split(",")
    }

    /**
     * Helper that sends a request to the server and prints the result in a text field.
     *
     * @param request REST request.
     */
    private fun sendRequest(request: RestRequest) {
        hideKeyboard()
        println("")
        printHeader(request)
        try {
            sendFromUIThread(request)
        } catch (e: Exception) {
            printException(e)
        }
    }

    /**
     * Sends a REST request using RestClient's sendAsync method.
     * Note: Synchronous calls are not allowed from code running on the UI thread.
     *
     * @param restRequest REST request.
     */
    private fun sendFromUIThread(restRequest: RestRequest) {
        client!!.sendAsync(restRequest, object : AsyncRequestCallback {
            private val start = System.nanoTime()

            override fun onSuccess(request: RestRequest, result: RestResponse) {
                result.consumeQuietly() // consume before going back to main thread
                runOnUiThread {
                    try {
                        val duration = System.nanoTime() - start
                        println(result)
                        val size = result.asString()!!.length
                        val statusCode = result.statusCode
                        printRequestInfo(duration, size, statusCode)
                        extractIdsFromResponse(result.asString()!!)
                    } catch (e: Exception) {
                        printException(e)
                    }
                    EventsObservable.get().notifyEvent(EventType.RenditionComplete)
                }
            }

            override fun onError(exception: Exception) {
                runOnUiThread {
                    printException(exception)
                    EventsObservable.get().notifyEvent(EventType.RenditionComplete)
                }
            }
        })
    }

    /**
     * Helper method to hide the soft keyboard.
     */
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(resultText.windowToken, 0)
    }

    /**************************************************************************************************
     *
     * Pretty printing helpers
     *
     **************************************************************************************************/

    private fun printRequestInfo(nanoDuration: Long, characterLength: Int, statusCode: Int) {
        println(SINGLE_LINE)
        println("Time (ms): " + nanoDuration / 1000000)
        println("Size (chars): $characterLength")
        println("Status code: $statusCode")
    }

    private fun printException(e: Exception) {
        println("Error: " + e.javaClass.simpleName)
        println(e.message)
    }

    private fun printHeader(obj: Any?) {
        println(DOUBLE_LINE)
        println(obj)
        println(SINGLE_LINE)
    }

    /**
     * Helper method to pretty print objects in the result_text field.
     *
     * @param object Object to print.
     */
    private fun println(obj: Any?) {
        if (!::resultText.isInitialized) {
            return
        }
        val sb = StringBuffer(resultText.text)
        val text = obj?.toString() ?: "null"
        sb.append(text).append("\n")
        resultText.text = sb

        // Auto scrolls to the bottom if needed.
        if (resultText.layout != null) {
            val scroll = resultText.layout.getLineTop(resultText.lineCount) - resultText.height
            resultText.scrollTo(0, if (scroll > 0) scroll else 0)
        }
    }

    /**
     * Dumps info about the app and rest client.
     */
    private fun printInfo() {
        printHeader("Info")
        println(SalesforceSDKManager.getInstance())
        println(client)
    }

    /**
     * Copies credentials to clipboard.
     */
    private fun exportCredentials() {
        val config = BootConfig.getBootConfig(this)
        val user = UserAccountManager.getInstance().currentUser!!
        val credsMap = HashMap<String, String?>()
        credsMap["test_client_id"] = config.remoteAccessConsumerKey
        credsMap["test_login_domain"] = user.loginServer
        credsMap["test_redirect_uri"] = config.oauthRedirectURI
        credsMap["refresh_token"] = user.refreshToken
        credsMap["instance_url"] = user.instanceServer
        credsMap["identity_url"] = user.idUrl
        credsMap["access_token"] = "__NOT_REQUIRED__"
        credsMap["organization_id"] = user.orgId
        credsMap["username"] = user.username
        credsMap["user_id"] = user.userId
        credsMap["display_name"] = user.displayName
        credsMap["photo_url"] = user.photoUrl

        if (user.communityUrl != null) {
            credsMap["community_url"] = user.communityUrl
        }

        if (user.apiInstanceServer != null) {
            credsMap["api_instance_url"] = user.apiInstanceServer
        }

        val credentials = JSONObject(credsMap as Map<*, *>).toString().replace("\\\\", "")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val data = ClipData.newPlainText("credentials", credentials)
        clipboard.setPrimaryClip(data)
    }

    /**
     * Helper to show/hide several views
     */
    fun showHide(show: Boolean, vararg resIds: Int) {
        for (resId in resIds) {
            val v = findViewById<View>(resId)
            v?.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    /**************************************************************************************************
     *
     * Extracting ids from response for auto-complete
     *
     **************************************************************************************************/

    private fun extractIdsFromResponse(responseString: String) {
        val matcher = idPattern.matcher(responseString)
        val ids = ArrayList<String>()
        while (matcher.find()) {
            ids.add(matcher.group())
        }
        knownIds.addAll(ids)
        fixAutoCompleteFields(
            R.id.retrieve_object_id_text,
            R.id.update_object_id_text,
            R.id.delete_object_id_text
        )
    }

    private fun fixAutoCompleteFields(vararg fieldIds: Int) {
        for (fieldId in fieldIds) {
            val tv = findViewById<AutoCompleteTextView>(fieldId)
            tv.setAdapter(
                ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    knownIds.toTypedArray()
                )
            )
        }
    }

    companion object {
        private const val DOUBLE_LINE = "=============================================================================="
        private const val SINGLE_LINE = "------------------------------------------------------------------------------"
    }
}
