/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.util.Pair
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.GridLayoutAnimationController
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.MultiAutoCompleteTextView
import android.widget.MultiAutoCompleteTextView.Tokenizer
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.smartstore.R
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.LinkedList
import java.util.Locale

class SmartStoreInspectorActivity : AppCompatActivity(), OnItemSelectedListener {

    // Store
    private lateinit var dbName: String
    private var isGlobal: Boolean = false
    private var smartStore: SmartStore? = null
    private lateinit var allStores: List<String>

    // View elements
    private lateinit var spinner: Spinner
    private lateinit var queryText: MultiAutoCompleteTextView
    private lateinit var pageSizeText: EditText
    private lateinit var pageIndexText: EditText
    private lateinit var resultGrid: GridView

    // Test support
    var lastAlertTitle: String? = null
        private set
    var lastAlertMessage: String? = null
        private set
    var lastResults: JSONArray? = null
        private set

    // Default queries
    private val soupsQuery = String.format(
        Locale.US,
        "select %s from %s",
        SmartStore.SOUP_NAME_COL,
        SmartStore.SOUP_ATTRS_TABLE
    )
    private val indicesQuery = String.format(
        Locale.US,
        "select %s, %s, %s from %s",
        SmartStore.SOUP_NAME_COL,
        SmartStore.PATH_COL,
        SmartStore.COLUMN_TYPE_COL,
        SmartStore.SOUP_INDEX_MAP_TABLE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readExtras()
        setContentView(R.layout.sf__inspector)
        supportActionBar?.setTitle(R.string.sf__inspector_title)
        spinner = findViewById(R.id.sf__inspector_stores_spinner)
        queryText = findViewById(R.id.sf__inspector_query_text)
        pageSizeText = findViewById(R.id.sf__inspector_pagesize_text)
        pageIndexText = findViewById(R.id.sf__inspector_pageindex_text)
        resultGrid = findViewById(R.id.sf__inspector_result_grid)
        setupSpinner()
    }

    override fun onResume() {
        super.onResume()
        setupStore(isGlobal, dbName)
    }

    private fun readExtras() {
        val bundle = intent.extras
        val hasUser = SmartStoreSDKManager.getInstance().userAccountManager.cachedCurrentUser != null
        // isGlobal is set to true
        //   if no bundle, or no value for isGlobalStore in bundle, or true specified for isGlobalStore in bundle, or there is no current user
        isGlobal = bundle == null || !bundle.containsKey(IS_GLOBAL_STORE) || bundle.getBoolean(IS_GLOBAL_STORE) || !hasUser
        // dbName is set to DBOpenHelper.DEFAULT_DB_NAME
        //   if no bundle, or no value for dbName in bundle
        dbName = if (bundle == null || !bundle.containsKey(DB_NAME)) {
            DBOpenHelper.DEFAULT_DB_NAME
        } else {
            bundle.getString(DB_NAME, DBOpenHelper.DEFAULT_DB_NAME)
        }
    }

    private fun setupSpinner() {
        val mgr = SmartStoreSDKManager.getInstance()
        allStores = buildList {
            for (dbName in mgr.getUserStoresPrefixList()) {
                add(getDisplayNameForStore(false, dbName))
            }
            for (dbName in mgr.getGlobalStoresPrefixList()) {
                add(getDisplayNameForStore(true, dbName))
            }
        }
        val selectedStoreIndex = allStores.indexOf(getDisplayNameForStore(this.isGlobal, this.dbName))
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, allStores)
        spinner.setSelection(selectedStoreIndex)
        spinner.onItemSelectedListener = this
    }

    private fun getDisplayNameForStore(isGlobal: Boolean, dbName: String): String {
        return (if (DBOpenHelper.DEFAULT_DB_NAME == dbName) DEFAULT_STORE else dbName) +
                (if (isGlobal) GLOBAL_STORE else USER_STORE)
    }

    private fun getStoreFromDisplayName(storeDisplayName: String): Pair<Boolean, String> {
        val isGlobal: Boolean
        var dbName: String
        if (storeDisplayName.endsWith(GLOBAL_STORE)) {
            isGlobal = true
            dbName = storeDisplayName.substring(0, storeDisplayName.length - GLOBAL_STORE.length)
        } else {
            isGlobal = false
            dbName = storeDisplayName.substring(0, storeDisplayName.length - USER_STORE.length)
        }
        dbName = if (dbName == DEFAULT_STORE) DBOpenHelper.DEFAULT_DB_NAME else dbName
        return Pair(isGlobal, dbName)
    }

    private fun setupStore(isGlobal: Boolean, dbName: String) {
        val mgr = SmartStoreSDKManager.getInstance()
        val currentUser = mgr.userAccountManager.cachedCurrentUser
        if (this.isGlobal != isGlobal || this.dbName != dbName || smartStore == null) {
            this.isGlobal = isGlobal
            this.dbName = dbName
            smartStore = if (isGlobal) {
                mgr.getGlobalSmartStore(dbName)
            } else {
                mgr.getSmartStore(dbName, currentUser, null)
            }
            setupAutocomplete(queryText)
        }
    }

    /**
     * Called when item selected in stores drop down
     */
    override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
        val selectedStore = getStoreFromDisplayName(allStores[i])
        setupStore(selectedStore.first, selectedStore.second)
    }

    /**
     * Called when no item is selected in stores drop down
     */
    override fun onNothingSelected(adapterView: AdapterView<*>?) {
    }

    /**
     * Called when "Clear" button is clicked
     */
    fun onClearClick(v: View?) {
        reset()
    }

    /**
     * Reset activity to its original state
     */
    fun reset() {
        queryText.setText("")
        pageSizeText.setText("")
        pageIndexText.setText("")
        resultGrid.adapter = null
        lastAlertTitle = null
        lastAlertMessage = null
        lastResults = null
    }

    /**
     * Called when "Run" button is clicked
     */
    fun onRunClick(v: View?) {
        runQuery()
    }

    /**
     * Called when "Soups" button is clicked
     */
    fun onSoupsClick(v: View?) {
        val names = smartStore!!.getAllSoupNames()

        if (names.isEmpty()) {
            showAlert(null, getString(R.string.sf__inspector_no_soups_found))
            return
        }

        if (names.size > 100) {
            queryText.setText(soupsQuery)
        } else {
            val sb = StringBuilder()
            var first = true
            for (name in names) {
                if (!first) {
                    sb.append(" union ")
                }
                sb.append("select '")
                sb.append(name)
                sb.append("', count(*) from {")
                sb.append(name)
                sb.append("}")
                first = false
            }
            queryText.setText(sb.toString())
        }
        runQuery()
    }

    /**
     * Called when "Indices" button is clicked
     */
    fun onIndicesClick(v: View?) {
        queryText.setText(indicesQuery)
        runQuery()
    }

    /**
     * Helper method that builds query spec from typed query, runs it and
     * updates result grid
     */
    private fun runQuery() {
        try {
            val query = queryText.text.toString()
            if (query.isEmpty()) {
                showAlert(null, getString(R.string.sf__inspector_no_query_specified))
                return
            }
            val pageSize = getInt(pageSizeText, DEFAULT_PAGE_SIZE)
            val pageIndex = getInt(pageIndexText, DEFAULT_PAGE_INDEX)
            val querySpec = QuerySpec.buildSmartQuerySpec(query, pageSize)
            showResult(smartStore!!.query(querySpec, pageIndex))
        } catch (e: Exception) {
            showAlert(e.javaClass.simpleName, e.message)
        }
    }

    /**
     * Helper function to get integer typed in a text field Returns defaultValue
     * if no integer were typed
     */
    private fun getInt(textField: EditText, defaultValue: Int): Int {
        val s = textField.text.toString()
        return if (s.isEmpty()) {
            defaultValue
        } else {
            s.toInt()
        }
    }

    private fun showAlert(title: String?, message: String?) {
        lastAlertTitle = title
        lastAlertMessage = message
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .show()
    }

    /**
     * Helper method to populate result grid with query result set (expected to
     * be a JSONArray of JSONArray's)
     */
    @Throws(JSONException::class)
    private fun showResult(result: JSONArray) {
        lastResults = result
        val adapter = ArrayAdapter<String>(this, R.layout.sf__inspector_result_cell)

        if (result.length() == 0) {
            showAlert(null, getString(R.string.sf__inspector_no_rows_returned))
        }

        for (j in 0 until result.length()) {
            val row = result.getJSONArray(j)
            for (i in 0 until row.length()) {
                val value = JSONObjectHelper.opt(row, i)
                adapter.add(
                    when (value) {
                        is JSONObject -> value.toString(2)
                        null -> "null"
                        else -> value.toString()
                    }
                )
            }
        }

        val numColumns = if (result.length() > 0) result.getJSONArray(0).length() else 0
        resultGrid.numColumns = numColumns
        resultGrid.adapter = adapter
        animateGridView(resultGrid)
    }

    /**
     * Helper method to attach animation to grid view
     */
    private fun animateGridView(gridView: GridView) {
        val animation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        val animationController = GridLayoutAnimationController(animation, 0f, 0.1f)
        gridView.layoutAnimation = animationController
        animationController.start()
    }

    /**
     * Helper method to setup auto-complete for query input field
     */
    private fun setupAutocomplete(textView: MultiAutoCompleteTextView) {
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)

        // Adding {soupName} and {soupName:specialField}
        val names = LinkedList<String>()
        names.addAll(smartStore!!.getAllSoupNames())
        for (name in names) {
            adapter.add("{$name}")
            adapter.add("{$name:${SmartSqlHelper.SOUP}}")
            adapter.add("{$name:${SmartStore.SOUP_ENTRY_ID}}")
            adapter.add("{$name:${SmartStore.SOUP_LAST_MODIFIED_DATE}}")
        }

        // Adding {soupName:indexedPath}
        try {
            val result = smartStore!!.query(
                QuerySpec.buildSmartQuerySpec("SELECT soupName, path FROM soup_index_map", 1000),
                0
            )
            for (j in 0 until result.length()) {
                val row = result.getJSONArray(j)
                adapter.add("{${row.getString(0)}:${row.getString(1)}}")
            }
        } catch (e: JSONException) {
            SmartStoreLogger.e(TAG, "Error occurred while parsing JSON", e)
        }

        // Adding some SQL keywords
        adapter.add("select")
        adapter.add("from")
        adapter.add("where")
        adapter.add("order by")
        adapter.add("asc")
        adapter.add("desc")
        adapter.add("group by")

        textView.setAdapter(adapter)
        textView.setTokenizer(QueryTokenizer())
    }

    companion object {
        // Keys for extras bundle
        private const val IS_GLOBAL_STORE = "isGlobalStore"
        private const val DB_NAME = "dbName"
        private const val TAG = "SmartStoreInspectorActivity"

        // Default page size / index
        private const val DEFAULT_PAGE_SIZE = 100
        private const val DEFAULT_PAGE_INDEX = 0
        const val USER_STORE = " (user store)"
        const val GLOBAL_STORE = " (global store)"
        const val DEFAULT_STORE = "default"

        /**
         * Create intent to bring up inspector
         * @param parentActivity
         * @param isGlobal pass true to get an inspector for the default global smartstore
         *                 pass false to get an inspector for the default user smartstore
         * @param dbName
         * @return
         */
        @JvmStatic
        fun getIntent(parentActivity: Activity, isGlobal: Boolean, dbName: String): Intent {
            val bundle = Bundle()
            bundle.putBoolean(IS_GLOBAL_STORE, isGlobal)
            bundle.putString(DB_NAME, dbName)

            val intent = Intent(parentActivity, SmartStoreInspectorActivity::class.java)
            intent.putExtras(bundle)
            return intent
        }
    }
}

/**
 * Tokenized used by query auto-complete field
 */
class QueryTokenizer : Tokenizer {

    override fun findTokenStart(text: CharSequence, cursor: Int): Int {
        var i = cursor
        while (i > 0 && text[i - 1] != ' ') {
            i--
        }
        return i
    }

    override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
        var i = cursor
        val len = text.length
        while (i < len) {
            if (text[i] == ' ') {
                return i
            } else {
                i++
            }
        }
        return len
    }

    override fun terminateToken(text: CharSequence): CharSequence {
        var i = text.length
        while (i > 0 && text[i - 1] == ' ') {
            i--
        }

        return if (i > 0 && text[i - 1] == ' ') {
            text
        } else {
            if (text is Spanned) {
                val sp = SpannableString("$text ")
                TextUtils.copySpansFrom(text, 0, text.length, Any::class.java, sp, 0)
                sp
            } else {
                text.toString() + " "
            }
        }
    }
}
