/*
 * Copyright (c) 2020-present, salesforce.com, inc.
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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.salesforce.androidsdk.smartstore.R
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore

class KeyValueStoreInspectorActivity : Activity() {

    // Store
    private var currentStore: KeyValueEncryptedFileStore? = null
    private var allStores: List<String> = ArrayList()

    // View elements
    private lateinit var storesDropdown: AutoCompleteTextView
    private lateinit var keyInput: EditText
    private lateinit var getValueButton: Button
    private lateinit var resultsListView: ListView
    private val keyValueList = ArrayList<KeyValuePair>()
    private lateinit var listAdapter: ArrayAdapter<KeyValuePair>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(com.salesforce.androidsdk.R.style.SalesforceSDK_Inspector)

        setContentView(R.layout.sf__key_value_inspector)
        storesDropdown = findViewById(R.id.sf__inspector_stores_dropdown)
        keyInput = findViewById(R.id.sf__inspector_key_text)
        getValueButton = findViewById(R.id.sf__inspector_get_value_button)
        resultsListView = findViewById(R.id.sf__inspector_key_value_list)

        listAdapter = object : ArrayAdapter<KeyValuePair>(
            this,
            R.layout.sf__inspector_key_value_results_cell,
            R.id.sf__inspector_value,
            keyValueList
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.sf__inspector_key_value_results_cell, null)

                val pair = keyValueList[position]
                view.findViewById<TextView>(R.id.sf__inspector_value).text = pair.value
                view.findViewById<TextView>(R.id.sf__inspector_key).text = pair.key
                return view
            }
        }
        resultsListView.adapter = listAdapter
        resultsListView.onItemLongClickListener = AdapterView.OnItemLongClickListener { parent, _, position, _ ->
            val label = "Value for key: ${keyValueList[position].key}"
            val value = keyValueList[position].value
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

            Toast.makeText(parent.context, "Value copied to clipboard.", Toast.LENGTH_SHORT).show()
            true
        }
        setupStoresDropdown()
        keyInput.requestFocus()
    }

    private fun setupStoresDropdown() {
        val mgr = SmartStoreSDKManager.getInstance()
        allStores = buildList {
            addAll(mgr.getKeyValueStoresPrefixList())
            addAll(mgr.getGlobalKeyValueStoresPrefixList().map { it + GLOBAL_STORE })
        }.sorted()

        if (allStores.isEmpty()) {
            allStores = listOf(NO_STORE)
            getValueButton.isEnabled = false
            getValueButton.alpha = .5f
        } else {
            setCurrentStore(allStores[0])
        }

        val adapter = ArrayAdapter(this, R.layout.sf__inspector_menu_popup_item, allStores)
        storesDropdown.setAdapter(adapter)
        storesDropdown.setText(allStores[0], false)
    }

    private fun setCurrentStore(storeName: String) {
        var name = storeName
        currentStore = if (storeName.endsWith(GLOBAL_STORE)) {
            name = storeName.substring(0, storeName.length - GLOBAL_STORE.length)
            SmartStoreSDKManager.getInstance().getGlobalKeyValueStore(name)
        } else {
            SmartStoreSDKManager.getInstance().getKeyValueStore(name)
        }
    }

    fun onGetValueClick(v: View?) {
        val typedKey = keyInput.text.toString()
        setCurrentStore(storesDropdown.text.toString())
        keyValueList.clear()

        if (typedKey.isEmpty()) {
            return
        }

        // Return all key/value pairs were key matches typedKey
        // if v2 kv store AND typedKey contains a *
        if (currentStore!!.getStoreVersion() == 2 && typedKey.contains("*")) {
            val allKeys = currentStore!!.keySet().toTypedArray()
            allKeys.sort()

            for (key in allKeys) {
                if (matches(typedKey, key)) {
                    keyValueList.add(KeyValuePair(key, currentStore!!.getValue(key)!!))
                }
            }
        }
        // Otherwise simply lookup typeKey
        else {
            val value = currentStore!!.getValue(typedKey)

            if (value != null) {
                keyValueList.add(KeyValuePair(typedKey, value))
            }
        }

        // Show alert if nothing matched
        if (keyValueList.isEmpty()) {
            AlertDialog.Builder(this).setTitle(ERROR_DIALOG_TITLE)
                .setMessage(ERROR_DIALOG_MESSAGE).show()
        } else {
            listAdapter.notifyDataSetChanged()
        }
    }

    private fun matches(typedKey: String, key: String): Boolean {
        return if (typedKey.contains("*")) {
            key.matches(Regex(typedKey.replace("*", ".*")))
        } else {
            key == typedKey
        }
    }

    private data class KeyValuePair(val key: String, val value: String)

    companion object {
        // Keys for extras bundle
        private const val TAG = "KeyValueStoreInspectorActivity"
        const val NO_STORE = "No KeyValueEncryptedFileStore found."
        const val GLOBAL_STORE = " (global store)"
        const val ERROR_DIALOG_TITLE = "Error"
        const val ERROR_DIALOG_MESSAGE = "Key not found in the current store."

        /**
         * Create intent to bring up inspector
         * @param parentActivity
         * @return KeyValueStoreInspectorActivity intent
         */
        @JvmStatic
        fun getIntent(parentActivity: Activity): Intent {
            return Intent(parentActivity, KeyValueStoreInspectorActivity::class.java)
        }
    }
}
