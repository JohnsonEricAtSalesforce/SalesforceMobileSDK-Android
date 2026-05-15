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
package com.salesforce.androidsdk.rest

import android.accounts.AccountManager
import android.app.Instrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.TestForceApp
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.accounts.UserAccountTest
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager.AccountInfoNotFoundException
import com.salesforce.androidsdk.rest.ClientManager.RestClientCallback
import com.salesforce.androidsdk.util.EventsObservable.EventType
import com.salesforce.androidsdk.util.test.EventsListenerQueue
import com.salesforce.androidsdk.util.test.TestCredentials
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@MediumTest
class ClientManagerTest {

    private lateinit var clientManager: ClientManager
    private lateinit var accountManager: AccountManager
    private lateinit var userAccountManager: UserAccountManager
    private lateinit var accountType: String
    private var eq: EventsListenerQueue? = null
    private var testOauthKeys: List<String>? = null
    private var testOauthValues: Map<String, String>? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val app = Instrumentation.newApplication(TestForceApp::class.java, targetContext)
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app)
        TestCredentials.init(InstrumentationRegistry.getInstrumentation().context)
        eq = EventsListenerQueue()
        if (!SalesforceSDKManager.hasInstance()) {
            eq!!.waitForEvent(EventType.AppCreateComplete, 5000)
        }
        accountType = SalesforceSDKManager.getInstance().accountType
        clientManager = ClientManager(targetContext, accountType, true)
        accountManager = clientManager.accountManager
        userAccountManager = SalesforceSDKManager.getInstance().userAccountManager
        testOauthKeys = listOf(UserAccountTest.TEST_CUSTOM_KEY)
        testOauthValues = mapOf(UserAccountTest.TEST_CUSTOM_KEY to UserAccountTest.TEST_CUSTOM_VALUE)
        SalesforceSDKManager.getInstance().additionalOauthKeys = testOauthKeys
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        eq?.let {
            it.tearDown()
            eq = null
        }
        cleanupAccounts()
        assertNoAccounts()
        testOauthKeys = null
        testOauthValues = null
        SalesforceSDKManager.getInstance().additionalOauthKeys = testOauthKeys
    }

    /**
     * Test getAccountType - removed as accountType is now private
     * The accountType is passed in the constructor and used internally
     */
    // Test removed - accountType is now a private property

    /**
     * Test createNewAccount
     */
    @Test
    fun testCreateAccount() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Call createNewAccount
        createTestAccountInAccountManager()

        // Check that the account did get created
        val accounts = clientManager.getAccounts()
        Assert.assertEquals("One account should have been returned", 1, accounts.size)
        Assert.assertEquals("Wrong account name", UserAccountTest.TEST_ACCOUNT_NAME, accounts[0].name)
        Assert.assertEquals("Wrong account type", accountType, accounts[0].type)
    }

    /**
     * Test getAccount
     */
    @Test
    fun testGetAccount() {

        val userAccount = UserAccountTest.createTestAccount()

        // Save to account manager (encrypt fields)
        clientManager.createNewAccount(userAccount)

        // Get account from account manager
        val account = clientManager.getAccount()

        // Build user account from account (decrypts fields)
        val restoredUserAccount = userAccountManager.buildUserAccount(account)

        // Make sure all the fields made it through and back
        UserAccountTest.checkSameUserAccount(userAccount, restoredUserAccount!!)
    }

    /**
     * Test getAccounts - when there is only one
     */
    @Test
    fun testGetAccountsWithSingleAccount() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Call createNewAccount
        createTestAccountInAccountManager()

        // Call getAccounts
        val accounts = clientManager.getAccounts()
        Assert.assertEquals("One account should have been returned", 1, accounts.size)
        Assert.assertEquals("Wrong account name", UserAccountTest.TEST_ACCOUNT_NAME, accounts[0].name)
        Assert.assertEquals("Wrong account type", accountType, accounts[0].type)
    }

    /**
     * Test getAccounts - when there are several accounts
     */
    @Test
    fun testGetAccountsWithSeveralAccounts() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Call two accounts
        createTestAccountInAccountManager()
        createOtherTestAccountInAccountManager()

        // Call getAccounts
        val accounts = clientManager.getAccounts()

        Assert.assertEquals("Two accounts should have been returned", 2, accounts.size)

        // Sorting
        val sortedAccounts = accounts.sortedBy { it.name }

        Assert.assertEquals("Wrong account name", UserAccountTest.TEST_ACCOUNT_NAME, sortedAccounts[0].name)
        Assert.assertEquals("Wrong account name", UserAccountTest.TEST_ACCOUNT_NAME_2, sortedAccounts[1].name)
    }

    /**
     * Test getAccountByName
     */
    @Test
    fun testGetAccountByName() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Create two accounts
        createTestAccountInAccountManager()
        createOtherTestAccountInAccountManager()

        // Check that the accounts did get created
        val accounts = clientManager.getAccounts()
        Assert.assertEquals("Two accounts should have been returned", 2, accounts.size)

        // Get the first one by name
        var account = clientManager.getAccountByName(UserAccountTest.TEST_ACCOUNT_NAME)
        Assert.assertNotNull("An account should have been returned", account)
        Assert.assertEquals("Wrong account name", UserAccountTest.TEST_ACCOUNT_NAME, account!!.name)
        Assert.assertEquals("Wrong account type", accountType, account.type)

        // Get the second one by name
        account = clientManager.getAccountByName(UserAccountTest.TEST_ACCOUNT_NAME_2)
        Assert.assertNotNull("An account should have been returned", account)
        Assert.assertEquals("Wrong account name", UserAccountTest.TEST_ACCOUNT_NAME_2, account!!.name)
        Assert.assertEquals("Wrong account type", accountType, account.type)
    }


    /**
     * Test removeAccounts when there is only one
     */
    @Test
    fun testRemoveOnlyAccount() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Create an account
        createTestAccountInAccountManager()

        // Check that the account did get created
        val accounts = clientManager.getAccounts()
        Assert.assertEquals("One account should have been returned", 1, accounts.size)
        Assert.assertEquals("Wrong account name", UserAccountTest.TEST_ACCOUNT_NAME, accounts[0].name)

        // Remove the account
        clientManager.removeAccounts(accounts)

        // Make sure there are no accounts left
        assertNoAccounts()
    }

    /**
     * Test removeAccounts - removing one account where there are several
     */
    @Test
    fun testRemoveOneOfSeveralAccounts() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Create two accounts
        createTestAccountInAccountManager()
        createOtherTestAccountInAccountManager()

        // Check that the accounts did get created
        val accounts = clientManager.getAccounts()
        Assert.assertEquals("Two accounts should have been returned", 2, accounts.size)

        // Remove one of them
        clientManager.removeAccounts(arrayOf(accounts[0]))

        // Make sure the other account is still there
        val accountsLeft = clientManager.getAccounts()
        Assert.assertEquals("One account should have been returned", 1, accountsLeft.size)
        Assert.assertEquals("Wrong account name", accounts[1].name, accountsLeft[0].name)
    }

    /**
     * Test removeAccounts - removing two accounts
     */
    @Test
    fun testRemoveSeveralAccounts() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Create two accounts
        createTestAccountInAccountManager()
        createOtherTestAccountInAccountManager()

        // Check that the accounts did get created
        val accounts = clientManager.getAccounts()
        Assert.assertEquals("Two accounts should have been returned", 2, accounts.size)

        // Remove one of them
        clientManager.removeAccounts(accounts)

        // Make sure there are no accounts left
        assertNoAccounts()
    }

    /**
     * Test peekRestClient - when there is no account
     */
    @Test
    fun testPeekRestClientWhenNoAccounts() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Call peekRestClient - expect exception
        try {
            clientManager.peekRestClient()
            Assert.fail("Expected AccountInfoNotFoundException")
        } catch (e: AccountInfoNotFoundException) {
            // as expected
        }
    }

    /**
     * Test peekRestClient - when there is an account
     */
    @Test
    @Throws(Exception::class)
    fun testPeekRestClientWithAccountSetup() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Create account
        createTestAccountInAccountManager()

        // Call peekRestClient - expect restClient
        try {
            val restClient = clientManager.peekRestClient()
            Assert.assertNotNull("RestClient expected", restClient)
            Assert.assertEquals("Wrong authToken", UserAccountTest.TEST_AUTH_TOKEN, restClient.getAuthToken())
            Assert.assertEquals("Wrong instance Url", URI(UserAccountTest.TEST_INSTANCE_URL), restClient.clientInfo.instanceUrl)
        } catch (e: AccountInfoNotFoundException) {
            Assert.fail("Did not expect AccountInfoNotFoundException")
        }
    }

    /**
     * Test getRestClient - when there is an account
     */
    @Ignore("Test requires an Activity instance but InstrumentationRegistry.context is a ContextImpl, not an Activity. Needs ActivityScenario to fix properly.")
    @Test
    @Throws(Exception::class)
    fun testGetRestClientWithAccountSetup() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Create account
        createTestAccountInAccountManager()

        // Call getRestClient - expect restClient
        val q: BlockingQueue<RestClient> = ArrayBlockingQueue(1)
        val activity = InstrumentationRegistry.getInstrumentation().context as android.app.Activity
        clientManager.getRestClient(activity, RestClientCallback { client ->
            client?.let { q.add(it) }
        })

        // Wait for getRestClient to complete
        try {
            val restClient = q.poll(10L, TimeUnit.SECONDS)
            Assert.assertNotNull("RestClient expected", restClient)
            Assert.assertEquals("Wrong authToken", UserAccountTest.TEST_AUTH_TOKEN, restClient.getAuthToken())
            Assert.assertEquals("Wrong instance Url", URI(UserAccountTest.TEST_INSTANCE_URL), restClient.clientInfo.instanceUrl)
        } catch (e: InterruptedException) {
            Assert.fail("getRestClient did not return after 5s")
        }
    }

    /**
     * Test removeAccount
     */
    @Test
    @Throws(Exception::class)
    fun testRemoveAccount() {

        // Make sure we have no accounts initially
        assertNoAccounts()

        // Create account
        createTestAccountInAccountManager()

        // Check that the accounts did get created
        val accounts = clientManager.getAccounts()
        Assert.assertEquals("One account should have been returned", 1, accounts.size)

        // Call removeAccount
        clientManager.removeAccount(clientManager.getAccount())

        // Make sure there are no accounts left
        assertNoAccounts()
    }

    /**
     * Checks there are no test accounts
     */
    private fun assertNoAccounts() {
        Assert.assertEquals("There should be no accounts", 0, accountManager.getAccountsByType(accountType).size)
    }

    /**
     * Remove any existing accounts
     */
    @Throws(Exception::class)
    private fun cleanupAccounts() {
        clientManager.removeAccounts(accountManager.getAccountsByType(accountType))
    }

    /**
     * Create test account
     */
    private fun createTestAccountInAccountManager(): UserAccount {
        val userAccount = UserAccountTest.createTestAccount()
        clientManager.createNewAccount(userAccount)
        return userAccount
    }

    /**
     * Create other test account
     */
    private fun createOtherTestAccountInAccountManager(): UserAccount {
        val userAccount = UserAccountTest.createOtherTestAccount()
        clientManager.createNewAccount(userAccount)
        return userAccount
    }
}
