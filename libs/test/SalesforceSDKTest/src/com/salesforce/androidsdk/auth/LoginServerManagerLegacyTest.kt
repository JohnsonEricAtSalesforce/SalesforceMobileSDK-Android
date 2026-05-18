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
package com.salesforce.androidsdk.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.salesforce.androidsdk.config.LoginServerManager
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer
import com.salesforce.androidsdk.config.RuntimeConfig.Companion.getRuntimeConfig
import com.salesforce.androidsdk.tests.R
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for LoginServerManager.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class LoginServerManagerLegacyTest {

    companion object {
        private const val PRODUCTION_URL = "https://login.salesforce.com"
        private const val SANDBOX_URL = "https://test.salesforce.com"
        private const val OTHER_URL = "https://other.salesforce.com"
        private const val CUSTOM_NAME = "New"
        private const val CUSTOM_URL = "https://new.com"
        private const val CUSTOM_NAME_2 = "New2"
        private const val CUSTOM_URL_2 = "https://new2.com"

        fun assertProduction(server: LoginServer) {
            assertEquals("Expected production's name", "Production", server.name)
            assertEquals("Expected production's url", PRODUCTION_URL, server.url)
            assertFalse("Expected production to be marked as not custom", server.isCustom)
        }

        fun assertSandbox(server: LoginServer) {
            assertEquals("Expected sandbox's name", "Sandbox", server.name)
            assertEquals("Expected sandbox's url", SANDBOX_URL, server.url)
            assertFalse("Expected sandbox to be marked as not custom", server.isCustom)
        }

        fun assertOther(server: LoginServer) {
            assertEquals("Expected other's name", "Other", server.name)
            assertEquals("Expected other's url", OTHER_URL, server.url)
            assertFalse("Expected other to be marked as not custom", server.isCustom)
        }

        fun assertCustom(server: LoginServer) {
            assertEquals("Expected custom's name", CUSTOM_NAME, server.name)
            assertEquals("Expected custom's url", CUSTOM_URL, server.url)
            Assert.assertTrue("Expected custom to be marked as not custom", server.isCustom)
        }

        fun assertCustom2(server: LoginServer) {
            assertEquals("Expected custom2's name", CUSTOM_NAME_2, server.name)
            assertEquals("Expected custom2's url", CUSTOM_URL_2, server.url)
            Assert.assertTrue("Expected custom2 to be marked as not custom", server.isCustom)
        }
    }

    private lateinit var loginServerManager: LoginServerManager

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        loginServerManager = LoginServerManager(getInstrumentation().targetContext)
        loginServerManager.reset()
    }

    @After
    fun tearDown() {
        loginServerManager.reset()
    }

    /**
     * Test for getLoginServerFromURL.
     */
    @Test
    fun testGetLoginServerFromURL() {
        assertProduction(loginServerManager.getLoginServerFromURL(PRODUCTION_URL)!!)
        assertSandbox(loginServerManager.getLoginServerFromURL(SANDBOX_URL)!!)
        assertOther(loginServerManager.getLoginServerFromURL(OTHER_URL)!!)
        Assert.assertNull("Expected null", loginServerManager.getLoginServerFromURL("https://wrong.salesforce.com"))
    }

    /**
     * Test for testGetLegacyDefaultLoginServers.
     */
    @Test
    fun testGetLegacyDefaultLoginServers() {
        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_empty
        )

        val servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 2, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for testGetLegacyDefaultLoginServersWhenResourcesAreMissing.
     */
    @Test
    fun testGetLegacyDefaultLoginServersWhenResourcesAreMissing() {
        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            0
        )

        val servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 2, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for getDefaultLoginServer.
     */
    @Test
    fun testGetDefaultLoginServers() {
        val servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 3, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])
        assertOther(servers[2])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for testAddDefaultLoginServers.
     */
    @Test
    fun testAddDefaultLoginServers() {
        var servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 3, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])
        assertOther(servers[2])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_addition
        )

        servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 4, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])
        assertEquals("Added", servers[2].name)
        assertEquals("https://added.salesforce.com", servers[2].url)
        assertFalse(servers[2].isCustom)
        assertOther(servers[3])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for testUpdateDefaultLoginServers.
     */
    @Test
    fun testUpdateDefaultLoginServers() {
        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_addition
        )
        var servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 4, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])
        assertEquals("Added", servers[2].name)
        assertEquals("https://added.salesforce.com", servers[2].url)
        assertFalse(servers[2].isCustom)
        assertOther(servers[3])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_update
        )

        servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 4, servers.size)
        assertProduction(servers[0])
        assertEquals("Updated", servers[1].name)
        assertEquals("https://updated.salesforce.com", servers[1].url)
        assertFalse(servers[1].isCustom)
        assertSandbox(servers[2])
        assertOther(servers[3])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for testRemoveDefaultLoginServers.
     */
    @Test
    fun testRemoveDefaultLoginServers() {
        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_update
        )
        var servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 4, servers.size)
        assertProduction(servers[0])
        assertEquals("Updated", servers[1].name)
        assertEquals("https://updated.salesforce.com", servers[1].url)
        assertFalse(servers[1].isCustom)
        assertSandbox(servers[2])
        assertOther(servers[3])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_remove
        )

        servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 2, servers.size)
        assertProduction(servers[0])
        assertOther(servers[1])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for testAddDefaultLoginServersWithCustomServers.
     */
    @Test
    fun testAddDefaultLoginServersWithCustomServers() {
        var servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 3, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])
        assertOther(servers[2])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        var addedServers = loginServerManager.getLoginServers()
        assertCustom(addedServers[addedServers.size - 1])
        assertCustom(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_addition
        )

        servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 5, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])
        assertEquals("Added", servers[2].name)
        assertEquals("https://added.salesforce.com", servers[2].url)
        assertFalse(servers[2].isCustom)
        assertOther(servers[3])

        val finalAddServers = loginServerManager.getLoginServers()
        assertCustom(finalAddServers[finalAddServers.size - 1])
        assertCustom(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for testUpdateDefaultLoginServersWithCustomServers.
     */
    @Test
    fun testUpdateDefaultLoginServersWithCustomServers() {
        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_addition
        )
        var servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 4, servers.size)
        assertProduction(servers[0])
        assertSandbox(servers[1])
        assertEquals("Added", servers[2].name)
        assertEquals("https://added.salesforce.com", servers[2].url)
        assertFalse(servers[2].isCustom)
        assertOther(servers[3])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        var addedServers = loginServerManager.getLoginServers()
        assertCustom(addedServers[addedServers.size - 1])
        assertCustom(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_update
        )

        servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 5, servers.size)
        assertProduction(servers[0])
        assertEquals("Updated", servers[1].name)
        assertEquals("https://updated.salesforce.com", servers[1].url)
        assertFalse(servers[1].isCustom)
        assertSandbox(servers[2])
        assertOther(servers[3])

        val finalUpdateServers = loginServerManager.getLoginServers()
        assertCustom(finalUpdateServers[finalUpdateServers.size - 1])
        assertCustom(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for testRemoveDefaultLoginServersWithCustomServers.
     */
    @Test
    fun testRemoveDefaultLoginServersWithCustomServers() {
        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_update
        )
        var servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 4, servers.size)
        assertProduction(servers[0])
        assertEquals("Updated", servers[1].name)
        assertEquals("https://updated.salesforce.com", servers[1].url)
        assertFalse(servers[1].isCustom)
        assertSandbox(servers[2])
        assertOther(servers[3])

        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        var addedServers = loginServerManager.getLoginServers()
        assertCustom(addedServers[addedServers.size - 1])
        assertCustom(loginServerManager.getSelectedLoginServer()!!)

        loginServerManager = LoginServerManager(
            getInstrumentation().targetContext,
            getRuntimeConfig(getInstrumentation().targetContext),
            R.xml.servers_remove
        )

        servers = loginServerManager.getLoginServers()
        assertEquals("Wrong number of servers", 3, servers.size)
        assertProduction(servers[0])
        assertOther(servers[1])

        val finalRemoveServers = loginServerManager.getLoginServers()
        assertCustom(finalRemoveServers[finalRemoveServers.size - 1])
        assertCustom(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for getSelectedLoginServer/setSelectedLoginServer when there is no custom login server.
     */
    @Test
    fun testGetSetLoginServerWithoutCustomServer() {
        // Starting point, production selected by default.
        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        // Selecting production.
        loginServerManager.setSelectedLoginServer(LoginServer("Production", PRODUCTION_URL, false))
        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        // Selecting sandbox.
        loginServerManager.setSelectedLoginServer(LoginServer("Sandbox", SANDBOX_URL, false))
        assertSandbox(loginServerManager.getSelectedLoginServer()!!)

        // Selecting other.
        loginServerManager.setSelectedLoginServer(LoginServer("Other", OTHER_URL, false))
        assertOther(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for getSelectedLoginServer/setSelectedLoginServer when there is a custom login server.
     */
    @Test
    fun testGetSetLoginServerWithCustomServer() {
        // Starting point, production selected by default.
        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        // Adding custom server, custom should be selected.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        assertCustom(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for adding more than one custom server.
     */
    @Test
    fun testAddMultipleCustomServers() {
        // Starting point, only 3 servers.
        var servers = loginServerManager.getLoginServers()
        assertEquals("Expected no custom login servers", 3, servers.size)

        // Adding first custom server.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        servers = loginServerManager.getLoginServers()
        assertEquals("Expected one custom login server", 4, servers.size)

        // Adding second custom server.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME_2, CUSTOM_URL_2)
        servers = loginServerManager.getLoginServers()
        assertEquals("Expected one custom login server", 5, servers.size)
    }

    /**
     * Test for getCustomLoginServer/setCustomLoginServer.
     */
    @Test
    fun testGetSetCustomLoginServer() {
        // Starting point, custom is null.
        Assert.assertNull("Expected no custom login server", loginServerManager.getLoginServerFromURL(CUSTOM_URL))

        // Adding custom server.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        assertCustom(loginServerManager.getSelectedLoginServer()!!)

        // Adding a second custom server.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME_2, CUSTOM_URL_2)
        assertCustom2(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for useSandbox.
     */
    @Test
    fun testUseSandbox() {
        // Starting point, production selected by default.
        assertProduction(loginServerManager.getSelectedLoginServer()!!)

        // Calling useSandbox.
        loginServerManager.useSandbox()
        assertSandbox(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test for reset.
     */
    @Test
    fun testReset() {
        // Starting point, only 3 servers.
        var servers = loginServerManager.getLoginServers()
        assertEquals("Expected no custom login servers", 3, servers.size)

        // Adding custom server.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        servers = loginServerManager.getLoginServers()
        assertEquals("Expected one custom login server", 4, servers.size)

        // Selecting sandbox.
        loginServerManager.useSandbox()
        assertSandbox(loginServerManager.getSelectedLoginServer()!!)

        /*
         * Calling reset - selection should go back to production
         * and custom server should be removed from shared prefs.
         */
        loginServerManager.reset()
        servers = loginServerManager.getLoginServers()
        assertEquals("Expected no custom login servers", 3, servers.size)
        assertProduction(loginServerManager.getSelectedLoginServer()!!)
    }

    /**
     * Test selectedServer LiveData.
     */
    @Test
    fun testLiveData() {
        // Assert the method returns the same result as the backing LiveData.
        assertLiveData()

        loginServerManager.addCustomLoginServer("live data", PRODUCTION_URL)
        assertLiveData()

        loginServerManager.selectedServer.postValue(LoginServer("Live Data 2", PRODUCTION_URL, false))
        assertLiveData()
    }

    /**
     * Test removing the last server.
     */
    @Test
    fun testRemoveServer() {
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        val originalServerSize = 4 // 3 default servers + 1 custom
        var servers = loginServerManager.getLoginServers()
        assertEquals("Expected one custom login server", originalServerSize, servers.size)
        val lastServer = servers[3]

        // Remove
        loginServerManager.removeServer(lastServer)
        servers = loginServerManager.getLoginServers()
        assertEquals("", originalServerSize - 1, servers.size)
        assertFalse("List should not contain removed server.", servers.contains(lastServer))
    }

    /**
     * Test removing a server in the middle reorders the rest.
     */
    @Test
    fun testRemoveReordersServers() {
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        loginServerManager.addCustomLoginServer(CUSTOM_NAME_2, CUSTOM_URL_2)
        val originalServerSize = 5 // 3 default servers + 2 custom
        var servers = loginServerManager.getLoginServers()
        assertEquals("Expected one custom login server", originalServerSize, servers.size)
        val serverToDelete = servers[3]

        // Remove
        loginServerManager.removeServer(serverToDelete)
        servers = loginServerManager.getLoginServers()
        assertEquals("No servers removed.", originalServerSize - 1, servers.size)
        assertFalse("List should not contain removed server.", servers.contains(serverToDelete))

        // Assert Reorder
        assertProduction(servers[0])
        assertSandbox(servers[1])
        assertOther(servers[2])
        assertCustom2(servers[3])
    }

    /**
     * Test attempting to remove a non-custom server.
     */
    @Test
    fun testRemoveNonCustomServer() {
        val originalServerSize = 3 // 3 default servers
        var servers = loginServerManager.getLoginServers()
        assertEquals("Expected one custom login server", originalServerSize, servers.size)
        val serverToDelete = servers[0]

        // Remove
        loginServerManager.removeServer(serverToDelete)
        servers = loginServerManager.getLoginServers()
        assertEquals("Servers should not be removed.", originalServerSize, servers.size)
    }

    /**
     * Test attempting to add a duplicate server default or custom server.
     */
    @Test
    fun testAddingDuplicateServers() {
        val originalServerSize = 3 // 3 default servers
        var servers = loginServerManager.getLoginServers()
        assertEquals("Expected one custom login server", originalServerSize, servers.size)
        val prodServer = loginServerManager.getLoginServerFromURL(PRODUCTION_URL)!!

        // Attempt to add a default server as a custom server.
        loginServerManager.addCustomLoginServer(prodServer.name, prodServer.url)
        assertEquals(
            "Duplicate server should not be added.", originalServerSize,
            loginServerManager.getLoginServers().size
        )

        // Attempt to add a duplicate custom server.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        assertEquals(
            "Custom server should be added.", originalServerSize + 1,
            loginServerManager.getLoginServers().size
        )
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL)
        assertEquals(
            "Duplicate custom server should not be added.", originalServerSize + 1,
            loginServerManager.getLoginServers().size
        )

        // Ensure servers with duplicate names but unique URLs are allowed.
        loginServerManager.addCustomLoginServer(CUSTOM_NAME, CUSTOM_URL_2)
        assertEquals(
            "Custom server should be added.", originalServerSize + 2,
            loginServerManager.getLoginServers().size
        )
        loginServerManager.addCustomLoginServer(prodServer.name, "https://custom3.com")
        assertEquals(
            "Custom server should be added..", originalServerSize + 3,
            loginServerManager.getLoginServers().size
        )
    }

    /**
     * Test both replace and re-order custom login server.
     */
    @Test
    fun testReplaceAndReOrderCustomLoginServer() {
        // Test data.
        val originalName = "ORIGINAL_CUSTOM_LOGIN_SERVER_FOR_REPLACEMENT_TEST"
        val originalUrl = "https://original.example.com"
        val originalCustomLoginServer = LoginServer(originalName, originalUrl, true)
        val otherName = "OTHER_CUSTOM_LOGIN_SERVER_FOR_REPLACEMENT_TEST"
        val otherUrl = "https://other.example.com"
        val otherCustomLoginServer = LoginServer(otherName, otherUrl, true)
        val updatedName = "UPDATED_CUSTOM_LOGIN_SERVER_FOR_REPLACEMENT_TEST"
        val updatedUrl = "https://updated.example.com"
        val updatedCustomLoginServer = LoginServer(updatedName, updatedUrl, true)
        val nonCustomName = "NON_CUSTOM_LOGIN_SERVER_FOR_REPLACEMENT_TEST"
        val nonCustomUrl = "https://non.custom.example.com"
        val nonCustomLoginServer = LoginServer(nonCustomName, nonCustomUrl, false)

        // Verify the original and other custom login servers are not present.
        assertFalse(loginServerManager.getLoginServers().contains(originalCustomLoginServer))
        assertFalse(loginServerManager.getLoginServers().contains(otherCustomLoginServer))

        // Add the original and other custom login server.
        loginServerManager.addCustomLoginServer(originalName, originalUrl)
        loginServerManager.addCustomLoginServer(otherName, otherUrl)

        // Verify the original and other custom login servers were added.
        assertEquals(originalCustomLoginServer, loginServerManager.getLoginServers()[loginServerManager.getLoginServers().size - 2])
        assertEquals(otherCustomLoginServer, loginServerManager.getLoginServers()[loginServerManager.getLoginServers().size - 1])

        // Prepare for negative tests.
        val production = LoginServer("Production", "https://login.salesforce.com", false)
        val productionMismatch = LoginServer("Production?", "https://login.salesforce.com", true)
        val productionReplacement = LoginServer("Production Replaced", "https://login.salesforce.com", false)
        val productionReplacementMismatch = LoginServer("Production Replaced?", "https://login.salesforce.com", true)

        // Attempt the prohibited replacement of a non-custom login server where the original matches.
        loginServerManager.replaceCustomLoginServer(production, productionReplacement)
        Assert.assertTrue(loginServerManager.getLoginServers().contains(production))
        assertFalse(loginServerManager.getLoginServers().contains(productionReplacement))

        // Attempt the prohibited replacement of a non-custom login server where the original doesn't exit.
        loginServerManager.replaceCustomLoginServer(productionMismatch, productionReplacementMismatch)
        Assert.assertTrue(loginServerManager.getLoginServers().contains(production))
        assertFalse(loginServerManager.getLoginServers().contains(productionReplacement))

        // Attempt the prohibited reordering of a non-custom login server.
        loginServerManager.reorderCustomLoginServer(0, 1)
        assertEquals(loginServerManager.getLoginServers()[0], production)

        // Replace the original custom login server with a non-custom server.
        loginServerManager.replaceCustomLoginServer(originalCustomLoginServer, nonCustomLoginServer)

        // Verify the original and other custom login servers weren't changed.
        assertFalse(loginServerManager.getLoginServers().contains(nonCustomLoginServer))
        assertEquals(originalCustomLoginServer, loginServerManager.getLoginServers()[loginServerManager.getLoginServers().size - 2])
        assertEquals(otherCustomLoginServer, loginServerManager.getLoginServers()[loginServerManager.getLoginServers().size - 1])

        // Replace the original custom login server.
        loginServerManager.replaceCustomLoginServer(originalCustomLoginServer, updatedCustomLoginServer)

        // Verify the original custom login server is not present.
        assertFalse(loginServerManager.getLoginServers().contains(originalCustomLoginServer))

        // Verify the updated and other custom login servers are present.
        assertEquals(updatedCustomLoginServer, loginServerManager.getLoginServers()[loginServerManager.getLoginServers().size - 2])
        assertEquals(otherCustomLoginServer, loginServerManager.getLoginServers()[loginServerManager.getLoginServers().size - 1])

        // Attempt to move the updated custom login server above the non-custom login servers.
        loginServerManager.reorderCustomLoginServer(loginServerManager.getLoginServers().indexOf(updatedCustomLoginServer), 0)

        // Verify the updated custom login server is actually immediately following the last non-custom login server.
        val loginServers = loginServerManager.getLoginServers()
        var lastNonCustomIndex = -1
        for (i in loginServers.indices) {
            val loginServer = loginServers[i]
            if (!loginServer.isCustom) {
                lastNonCustomIndex = i
            }
        }
        assertEquals(loginServers[lastNonCustomIndex + 1], updatedCustomLoginServer)

        // Attempt to move the updated custom login server one greater than the upper bounds of the login servers list.
        loginServerManager.reorderCustomLoginServer(loginServerManager.getLoginServers().indexOf(updatedCustomLoginServer), loginServerManager.getLoginServers().size)

        // Attempt to move the updated custom login server more than one greater than the upper bounds of the login servers list.
        loginServerManager.reorderCustomLoginServer(loginServerManager.getLoginServers().indexOf(updatedCustomLoginServer), loginServerManager.getLoginServers().size + 1)

        // Attempt to move the updated custom login server more than one less than the upper bounds of the login servers list.
        loginServerManager.reorderCustomLoginServer(loginServerManager.getLoginServers().indexOf(updatedCustomLoginServer), loginServerManager.getLoginServers().size - 1)

        // Verify the updated custom login server is now the last login server in the list.
        val reorderedServers = loginServerManager.getLoginServers()
        assertEquals(reorderedServers[reorderedServers.size - 1], updatedCustomLoginServer)
    }

    private fun assertLiveData() {
        assertEquals(loginServerManager.getSelectedLoginServer(), loginServerManager.selectedServer.value)
    }
}
