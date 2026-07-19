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
package com.salesforce.androidsdk.config

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources.NotFoundException
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHostLabels
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey.AppServiceHosts
import com.salesforce.androidsdk.config.RuntimeConfig.Companion.getRuntimeConfig
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import org.xmlpull.v1.XmlPullParser
import java.util.ArrayDeque
import java.util.Locale

/**
 * Class to manage login hosts (default and user entered).
 *
 * @author bhariharan
 */
class LoginServerManager @VisibleForTesting constructor(
    private val ctx: Context,
    private val runtimeConfig: RuntimeConfig,
    private val serversXmlResourceId: Int
) {

    /** LiveData representation of the users current selected server. */
    @JvmField
    val selectedServer: MutableLiveData<LoginServer> = MutableLiveData()

    /** Shared preferences when non-custom resources login servers are provided by servers.xml */
    private val settings: SharedPreferences =
        ctx.getSharedPreferences(SERVER_URL_FILE, Context.MODE_PRIVATE)

    /** Shared preferences when non-custom login servers are provided by runtime config MDM */
    private val runtimePrefs: SharedPreferences =
        ctx.getSharedPreferences(RUNTIME_PREFS_FILE, Context.MODE_PRIVATE)

    /**
     * Constructs a new login server manager.
     *
     * @param ctx The context
     */
    constructor(ctx: Context) : this(ctx, getRuntimeConfig(ctx), R.xml.servers)

    init {
        // (Re-)initialize non-custom login servers provided by the resources servers.xml.
        resetNonCustomLoginServers(settings)
        initSharedPrefFile()

        // Select a default login server.
        getSelectedLoginServer()
    }

    /**
     * Returns a LoginServer instance from URL.
     *
     * @param url Server URL.
     * @return Matching LoginServer instance if found, or null.
     */
    fun getLoginServerFromURL(url: String?): LoginServer? {
        if (url == null) {
            return null
        }
        val allServers = getLoginServers()
        for (server in allServers) {
            if (url == server.url) {
                return server
            }
        }
        return null
    }

    /**
     * Returns the selected login server. This will set a default login server if needed and ensure
     * the selected login server is available in the current list of login servers.
     *
     * @return The selected login server.
     */
    fun getSelectedLoginServer(): LoginServer? {
        // Fetch the selected login server.
        val selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE, Context.MODE_PRIVATE)
        val selectedServerName = selectedServerPrefs.getString(SERVER_NAME, null)
        val selectedServerUrl = selectedServerPrefs.getString(SERVER_URL, null)
        val selectedServerIsCustom = selectedServerPrefs.getBoolean(IS_CUSTOM, false)
        val selectedServerHasValidNameAndUrl = selectedServerName != null && selectedServerUrl != null

        // Refresh the list of mobile device management (MDM) servers from the runtime config.
        if (isRuntimeConfigAppServiceHostsSet()) {
            getLoginServersFromRuntimeConfig()
        }

        // Get the active list of login servers.
        val loginServers = getLoginServers()

        // Check if the selected login server is available in the active list of login servers.
        val selectedLoginServerIsAvailable = loginServers.any { server ->
            server.name == selectedServerName && server.url == selectedServerUrl
        }

        // If the selected login server is valid and is available in the active list of login servers.
        var selectedLoginServer: LoginServer? = null
        if (selectedServerHasValidNameAndUrl) {
            if (selectedLoginServerIsAvailable) {
                selectedLoginServer = LoginServer(selectedServerName!!, selectedServerUrl!!, selectedServerIsCustom)

                // Notify live data consumers if the value has changed.
                if (selectedLoginServer != selectedServer.value) {
                    selectedServer.postValue(selectedLoginServer)
                }
            }
        }

        // If the selected login server is invalid or not available in the active list of login servers.
        if (selectedLoginServer == null) {
            // Default to the first login server on the list.
            if (loginServers.isNotEmpty()) {
                selectedLoginServer = loginServers[0]
                selectedServer.postValue(selectedLoginServer)
            }

            // Store the selected login server.
            setSelectedLoginServer(selectedLoginServer)
        }

        return selectedLoginServer
    }

    /**
     * Sets the currently selected login server to display.
     *
     * @param server LoginServer instance.
     */
    fun setSelectedLoginServer(server: LoginServer?) {
        if (server == null) {
            return
        }
        val selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE, Context.MODE_PRIVATE)
        val edit = selectedServerPrefs.edit()
        edit.clear()
        edit.putString(SERVER_NAME, server.name)
        edit.putString(SERVER_URL, server.url)
        edit.putBoolean(IS_CUSTOM, server.isCustom)
        edit.apply()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            selectedServer.setValue(server)
        } else {
            selectedServer.postValue(server)
        }
    }

    /**
     * Selects Sandbox as login server (used in tests).
     */
    fun useSandbox() {
        val sandboxServer = getLoginServerFromURL(SANDBOX_LOGIN_URL)
        setSelectedLoginServer(sandboxServer)
    }

    /**
     * Adds a custom login server.
     *
     * @param name The login server name.
     * @param url The login server URL.
     */
    fun addCustomLoginServer(name: String, url: String) {
        // Prevent duplicate servers.
        for (existingServer in getLoginServers()) {
            if (url == existingServer.url) {
                setSelectedLoginServer(existingServer)
                return
            }
        }

        persistLoginServer(
            name,
            url,
            true, /* Custom */
            getSharedPreferences() /* Active Shared Preferences */
        )
        setSelectedLoginServer(LoginServer(name, url, true))
    }

    /**
     * Clears all saved custom servers.
     */
    fun reset() {
        var edit = settings.edit()
        edit.clear()
        edit.apply()
        edit = runtimePrefs.edit()
        edit.clear()
        edit.apply()
        val selectedServerPrefs = ctx.getSharedPreferences(SERVER_SELECTION_FILE, Context.MODE_PRIVATE)
        edit = selectedServerPrefs.edit()
        edit.clear()
        edit.apply()
        initSharedPrefFile()
    }

    /**
     * Removes a custom login server from the list.
     *
     * @param server The server to remove. If the server is not custom, this method does nothing.
     */
    fun removeServer(server: LoginServer) {
        removeServer(server, settings, false)
    }

    /**
     * Removes a login server from the list.
     *
     * @param server The server to remove.
     * @param sharedPreferences The shared preferences to remove the server from.
     * @param allowNonCustomRemoval Boolean true allows the removal of non-custom login servers.
     */
    @VisibleForTesting
    fun removeServer(
        server: LoginServer,
        sharedPreferences: SharedPreferences,
        allowNonCustomRemoval: Boolean
    ) {
        val servers = getLoginServersFromPreferences(sharedPreferences)
        val index = servers.indexOf(server)
        val removalAlwaysAllowed = server.isCustom && index != -1
        if (allowNonCustomRemoval || removalAlwaysAllowed) {
            var numServers = servers.size
            val stack = ArrayDeque(servers.subList(index + 1, numServers))

            val edit = sharedPreferences.edit()
            edit.remove(String.format(Locale.US, SERVER_NAME, index))
                .remove(String.format(Locale.US, SERVER_URL, index))
                .remove(String.format(Locale.US, IS_CUSTOM, index))

            // Re-index servers after the one removed from the list.
            for (i in (index + 1) until numServers) {
                val reIndexServer = stack.pop()
                edit.remove(String.format(Locale.US, SERVER_NAME, i))
                    .remove(String.format(Locale.US, SERVER_URL, i))
                    .remove(String.format(Locale.US, IS_CUSTOM, i))
                    .putString(String.format(Locale.US, SERVER_NAME, i - 1), reIndexServer.name)
                    .putString(String.format(Locale.US, SERVER_URL, i - 1), reIndexServer.url)
                    .putBoolean(String.format(Locale.US, IS_CUSTOM, i - 1), reIndexServer.isCustom)
            }

            numServers--
            edit.putInt(NUMBER_OF_ENTRIES, numServers).apply()
        }
    }

    /**
     * Returns the list of login servers.
     *
     * @return The list of login servers.
     */
    fun getLoginServers(): List<LoginServer> {
        return getLoginServersFromPreferences(getSharedPreferences())
    }

    /**
     * Returns the active shared preferences when using login servers from MDM or servers.xml.
     */
    private fun getSharedPreferences(): SharedPreferences {
        return if (isRuntimeConfigAppServiceHostsSet()) runtimePrefs else settings
    }

    /**
     * Determines if managed login servers are provided by MDM.
     */
    private fun isRuntimeConfigAppServiceHostsSet(): Boolean {
        return runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts) != null
    }

    /**
     * Resets the list of MDM login servers from the runtime configuration.
     *
     * @return The list of login servers from the runtime configuration.
     */
    @Suppress("UnusedReturnValue")
    fun getLoginServersFromRuntimeConfig(): List<LoginServer>? {
        val mdmLoginServers = runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHosts)
        val allServers = mutableListOf<LoginServer>()
        if (mdmLoginServers != null) {
            var mdmLoginServersLabels = runtimeConfig.getStringArrayStoredAsArrayOrCSV(AppServiceHostLabels)
            if (mdmLoginServersLabels == null || mdmLoginServersLabels.size != mdmLoginServers.size) {
                SalesforceSDKLogger.w(TAG, "No login servers labels provided or wrong number of login servers labels provided - using URLs for the labels")
                mdmLoginServersLabels = mdmLoginServers
            }

            // Reset non-custom servers from MDM.
            resetNonCustomLoginServers(runtimePrefs)

            // Null-cleanse MDM login server URLs and names.
            val mdmLoginServersList = mdmLoginServers.filterNotNull().toMutableList()
            val mdmLoginServersLabelsList = mdmLoginServersLabels.filterNotNull().toMutableList()

            for (i in mdmLoginServersList.indices) {
                val name = mdmLoginServersLabelsList[i]
                val url = mdmLoginServersList[i]

                val server = LoginServer(name, url, false)
                persistLoginServer(
                    name,
                    url,
                    false, /* Non-Custom */
                    runtimePrefs
                )
                allServers.add(server)
            }
        }
        return if (allServers.isNotEmpty()) allServers else null
    }

    /**
     * Returns the list of login servers from resources (servers.xml) and the user's custom servers.
     *
     * @return The list of login servers from resources (servers.xml) and the user's custom servers.
     */
    @Suppress("unused")
    fun getLoginServersFromPreferences(): List<LoginServer> {
        return getLoginServersFromPreferences(settings)
    }

    /**
     * Reorders a custom login server in the list of login servers.
     *
     * @param originalIndex The original index of the custom login server.
     * @param updatedIndex The new index of the custom login server.
     */
    @Suppress("unused")
    fun reorderCustomLoginServer(originalIndex: Int, updatedIndex: Int) {
        var adjustedUpdatedIndex = updatedIndex
        // Get the login server at the original index.
        val loginServers = getLoginServers().toMutableList()
        val originalLoginServer = loginServers[originalIndex]

        // Guard against reordering a non-custom login server.
        if (!originalLoginServer.isCustom) {
            return
        }

        // Adjust the re-ordered custom login server index to be within bounds.
        adjustedUpdatedIndex = getIndexAdjustedToCustomLoginServerBounds(adjustedUpdatedIndex, getSharedPreferences())

        // Update the login server list.
        loginServers.removeAt(originalIndex)
        loginServers.add(adjustedUpdatedIndex, originalLoginServer)

        // Edit each login server indexed after the updated index.
        val editor = getSharedPreferences().edit()
        for (i in adjustedUpdatedIndex until loginServers.size) {
            val loginServer = loginServers[i]
            editor.remove(String.format(Locale.US, SERVER_NAME, i))
                .remove(String.format(Locale.US, SERVER_URL, i))
                .remove(String.format(Locale.US, IS_CUSTOM, i))
                .putString(String.format(Locale.US, SERVER_NAME, i), loginServer.name)
                .putString(String.format(Locale.US, SERVER_URL, i), loginServer.url)
                .putBoolean(String.format(Locale.US, IS_CUSTOM, i), loginServer.isCustom)
        }
        editor.apply()
    }

    /**
     * Replaces one custom login server with another.
     *
     * @param originalCustomLoginServer The original custom login server.
     * @param updatedCustomLoginServer The updated custom login server.
     */
    @Suppress("unused")
    fun replaceCustomLoginServer(
        originalCustomLoginServer: LoginServer,
        updatedCustomLoginServer: LoginServer
    ) {
        // Guard against replacing a non-custom login server.
        if (!originalCustomLoginServer.isCustom || !updatedCustomLoginServer.isCustom) {
            return
        }

        val originalIndex = getLoginServers().indexOf(originalCustomLoginServer)

        // Guard against an original login server that doesn't exist.
        if (originalIndex == -1) {
            return
        }

        removeServer(originalCustomLoginServer)
        addCustomLoginServer(updatedCustomLoginServer.name, updatedCustomLoginServer.url)
        reorderCustomLoginServer(getLoginServers().size - 1, originalIndex)
    }

    /**
     * Adjusts a login server index to be within the bounds of the custom login servers.
     */
    private fun getIndexAdjustedToCustomLoginServerBounds(
        index: Int?,
        sharedPreferences: SharedPreferences
    ): Int {
        val firstCustomLoginServerIndex = getNextNonCustomLoginServerIndex(sharedPreferences)
        val servers = getLoginServers()
        return when {
            index == null -> servers.size
            index <= firstCustomLoginServerIndex -> firstCustomLoginServerIndex
            index >= servers.size -> servers.size - 1
            else -> index
        }
    }

    /**
     * Returns production and sandbox as the login servers
     * (only called when servers.xml is missing).
     */
    private fun getLegacyLoginServers(): List<LoginServer> {
        val loginServers = mutableListOf<LoginServer>()
        val productionServer = LoginServer(
            ctx.getString(R.string.sf__auth_login_production),
            PRODUCTION_LOGIN_URL, false
        )
        loginServers.add(productionServer)
        val sandboxServer = LoginServer(
            ctx.getString(R.string.sf__auth_login_sandbox),
            SANDBOX_LOGIN_URL, false
        )
        loginServers.add(sandboxServer)
        return loginServers
    }

    /**
     * Returns the list of login servers from XML.
     *
     * @return Login servers defined in 'res/xml/servers.xml', or empty list.
     */
    private fun getLoginServersFromXML(): List<LoginServer> {
        val loginServers = mutableListOf<LoginServer>()
        val xml = try {
            ctx.resources.getXml(serversXmlResourceId)
        } catch (e: NotFoundException) {
            return loginServers
        }

        var eventType = -1
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (xml.name == "server") {
                    val name = xml.getAttributeValue(null, "name")
                    val url = xml.getAttributeValue(null, "url")
                    if (name != null && url != null) {
                        val loginServer = LoginServer(name, url, false)
                        loginServers.add(loginServer)
                    }
                }
            }
            try {
                eventType = xml.next()
            } catch (e: Exception) {
                SalesforceSDKLogger.w(TAG, "Exception thrown while parsing XML", e)
                break
            }
        }
        return loginServers
    }

    /**
     * Returns the next available non-custom login server index.
     */
    private fun getNextNonCustomLoginServerIndex(sharedPreferences: SharedPreferences): Int {
        val servers = getLoginServersFromPreferences(sharedPreferences)
        var result = servers.size
        for (i in result - 1 downTo 0) {
            if (servers[i].isCustom) {
                result = i
            }
        }
        return result
    }

    /**
     * Resets the list of resources login servers from the server.xml.
     */
    private fun initSharedPrefFile() {
        if (isRuntimeConfigAppServiceHostsSet()) {
            return
        }
        var servers = getLoginServersFromXML()
        if (servers.isEmpty()) {
            servers = getLegacyLoginServers()
        }
        val numServers = servers.size
        val edit = settings.edit()
        for (i in 0 until numServers) {
            val curServer = servers[i]
            persistLoginServer(
                curServer.name,
                curServer.url,
                curServer.isCustom,
                settings
            )

            // Set the default login server to the first entry once.
            if (i == 0 && ctx.getSharedPreferences(SERVER_SELECTION_FILE, Context.MODE_PRIVATE).all.isEmpty()) {
                setSelectedLoginServer(curServer)
            }
        }
        edit.apply()
    }

    /**
     * Persists a login server to the specified shared preferences.
     */
    private fun persistLoginServer(
        name: String,
        url: String,
        isCustom: Boolean,
        sharedPreferences: SharedPreferences
    ) {
        // Fetch the current number of servers.
        val numberOfServers = sharedPreferences.getInt(NUMBER_OF_ENTRIES, 0)

        // Adjust the requested index to the bounds of the non-custom (managed) or custom servers.
        val adjustedIndex: Int = if (isCustom) {
            getIndexAdjustedToCustomLoginServerBounds(null, sharedPreferences)
        } else {
            getNextNonCustomLoginServerIndex(sharedPreferences)
        }

        val editor = sharedPreferences.edit()

        // Increment existing login servers as needed.
        for (i in numberOfServers - 1 downTo adjustedIndex) {
            val incrementedIndex = i + 1
            val loginServerNameKey = String.format(Locale.US, SERVER_NAME, i)
            val loginServerUrlKey = String.format(Locale.US, SERVER_URL, i)
            val loginServerIsCustomKey = String.format(Locale.US, IS_CUSTOM, i)

            val loginServerName = sharedPreferences.getString(loginServerNameKey, null)
            val loginServerUrl = sharedPreferences.getString(loginServerUrlKey, null)
            val loginServerIsCustom = sharedPreferences.getBoolean(loginServerIsCustomKey, false)

            editor
                .remove(loginServerNameKey)
                .remove(loginServerUrlKey)
                .remove(loginServerIsCustomKey)
                .putString(String.format(Locale.US, SERVER_NAME, incrementedIndex), loginServerName)
                .putString(String.format(Locale.US, SERVER_URL, incrementedIndex), loginServerUrl)
                .putBoolean(String.format(Locale.US, IS_CUSTOM, incrementedIndex), loginServerIsCustom)
        }

        // Insert the new login server.
        editor.putString(String.format(Locale.US, SERVER_NAME, adjustedIndex), name.trim())
        editor.putString(String.format(Locale.US, SERVER_URL, adjustedIndex), url.trim())
        editor.putBoolean(String.format(Locale.US, IS_CUSTOM, adjustedIndex), isCustom)

        editor.putInt(NUMBER_OF_ENTRIES, numberOfServers + 1)
        editor.apply()
    }

    /**
     * Returns the list of all saved servers, including custom servers.
     *
     * @param prefs SharedPreferences file.
     * @return List of all saved servers.
     */
    @VisibleForTesting
    fun getLoginServersFromPreferences(prefs: SharedPreferences): List<LoginServer> {
        val numServers = prefs.getInt(NUMBER_OF_ENTRIES, 0)
        if (numServers == 0) {
            return ArrayList()
        }
        val allServers = mutableListOf<LoginServer>()
        for (i in 0 until numServers) {
            val name = prefs.getString(String.format(Locale.US, SERVER_NAME, i), null)
            val url = prefs.getString(String.format(Locale.US, SERVER_URL, i), null)
            val isCustom = prefs.getBoolean(String.format(Locale.US, IS_CUSTOM, i), false)
            if (name != null && url != null) {
                val server = LoginServer(name, url.trim(), isCustom)
                allServers.add(server)
            }
        }
        return allServers
    }

    /**
     * Resets the list of non-custom login servers in the provided shared preferences.
     */
    private fun resetNonCustomLoginServers(sharedPreferences: SharedPreferences) {
        val loginServersFromPreferences = getLoginServersFromPreferences(sharedPreferences)
        for (loginServer in loginServersFromPreferences) {
            if (!loginServer.isCustom) {
                removeServer(loginServer, sharedPreferences, true)
            }
        }
    }

    /**
     * Class to encapsulate a login server name, URL, index and type (custom or not).
     */
    class LoginServer(
        @JvmField val name: String,
        @JvmField val url: String,
        @JvmField val isCustom: Boolean
    ) {
        override fun toString(): String {
            return "Name: $name, URL: $url, Custom URL: $isCustom"
        }

        override fun equals(other: Any?): Boolean {
            if (other == null || other.javaClass != javaClass) {
                return false
            }
            val server = other as LoginServer
            return (name.trim() == server.name.trim() &&
                    url.trim() == server.url.trim() && isCustom == server.isCustom)
        }

        override fun hashCode(): Int {
            var result = name.trim().hashCode()
            result = 31 * result + url.trim().hashCode()
            result = 31 * result + isCustom.hashCode()
            return result
        }
    }

    companion object {
        private const val TAG = "LoginServerManager"

        // Default login servers.
        const val PRODUCTION_LOGIN_URL = "https://login.salesforce.com"
        const val WELCOME_LOGIN_URL = "https://welcome.salesforce.com/discovery"
        const val SANDBOX_LOGIN_URL = "https://test.salesforce.com"

        /** Returns true when [serverUrl] is one of the three Salesforce pool (non-my-domain) servers. */
        @JvmStatic
        fun isPoolServer(serverUrl: String): Boolean =
            serverUrl == PRODUCTION_LOGIN_URL ||
                serverUrl == SANDBOX_LOGIN_URL ||
                serverUrl == WELCOME_LOGIN_URL

        /** Shared preferences when non-custom login servers are provided by resources servers.xml */
        @JvmField
        @VisibleForTesting
        val SERVER_URL_FILE = "server_url_file"

        /** Shared preferences when non-custom login servers are provided by runtime config MDM */
        @JvmField
        @VisibleForTesting
        val RUNTIME_PREFS_FILE = "runtime_prefs_file"

        @JvmField
        @VisibleForTesting
        val NUMBER_OF_ENTRIES = "number_of_entries"

        @JvmField
        @VisibleForTesting
        val SERVER_NAME = "server_name_%d"

        @JvmField
        @VisibleForTesting
        val SERVER_URL = "server_url_%d"

        @JvmField
        @VisibleForTesting
        val IS_CUSTOM = "is_custom_%d"

        /** Shared preferences for the selected login server */
        @JvmField
        @VisibleForTesting
        val SERVER_SELECTION_FILE = "server_selection_file"
    }
}
