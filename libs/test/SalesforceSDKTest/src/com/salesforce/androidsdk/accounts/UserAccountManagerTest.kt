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
package com.salesforce.androidsdk.accounts

import android.accounts.AccountManager
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccountTest.Companion.TEST_USERNAME
import com.salesforce.androidsdk.accounts.UserAccountTest.Companion.checkSameUserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.security.SalesforceKeyGenerator
import com.salesforce.androidsdk.util.LogoutCompleteReceiver
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Semaphore

/**
 * Tests for UserAccountManager.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class UserAccountManagerTest {

    companion object {
        const val TEST_ACCOUNT_TYPE = "com.salesforce.androidsdk.salesforcesdktest.login" // must match authenticator.xml in SalesforceSDK project

        /**
         * Removes any existing accounts.
         */
        @JvmStatic
        fun cleanupAccounts(accountManager: AccountManager) {
            for (account in accountManager.getAccountsByType(TEST_ACCOUNT_TYPE)) {
                accountManager.removeAccountExplicitly(account)
            }
        }

        /**
         * Create a test account.
         *
         * @return UserAccount.
         */
        @JvmStatic
        fun createTestAccountInAccountManager(userAccountManager: UserAccountManager): UserAccount {
            val userAccount = UserAccountTest.createTestAccount()
            userAccountManager.createAccount(userAccount)
            return userAccount
        }
    }

    private lateinit var userAccMgr: UserAccountManager
    private lateinit var accMgr: AccountManager
    private lateinit var logoutCompleteReceiver: FakeLogoutCompleteReceiver

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        accMgr = AccountManager.get(targetContext)
        cleanupAccounts(accMgr)
        userAccMgr = UserAccountManager.getInstance()
        Assert.assertNull("There should be no authenticated users", userAccMgr.authenticatedUsers)
        logoutCompleteReceiver = FakeLogoutCompleteReceiver()
        ContextCompat.registerReceiver(
            targetContext, logoutCompleteReceiver,
            IntentFilter(SalesforceSDKManager.LOGOUT_COMPLETE_INTENT_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @After
    fun tearDown() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        if (::logoutCompleteReceiver.isInitialized) {
            targetContext.unregisterReceiver(logoutCompleteReceiver)
        }
        cleanupAccounts(accMgr)
    }

    /**
     * Test creating an account from a UserAccount and vice versa
     */
    @Test
    fun testUserAccountToAccountToUserAccount() {
        val userAccount = UserAccountTest.createTestAccount()
        // Save to account manager (encrypt fields)
        userAccMgr.createAccount(userAccount)
        // Get account from account manager
        val account = userAccMgr.currentAccount!!
        // Build user account from account (decrypts fields)
        val restoredUserAccount = userAccMgr.buildUserAccount(account)
        // Make sure all the fields made it through and back
        checkSameUserAccount(userAccount, restoredUserAccount!!)
    }

    /*
     * Server-side Refresh Token Rotation (RTR) regression test.
     *
     * The refresh token is persisted as the Account's password and is read back via accountManager.getPassword().
     * updateAccount must therefore persist a rotated refresh token via setPassword.
     */
    @Test
    fun testUpdateAccountPersistsRotatedRefreshToken() {
        val original = UserAccountTest.createTestAccount()
        userAccMgr.createAccount(original)
        val account = userAccMgr.currentAccount!!
        Assert.assertEquals(
            "Initial refresh token should round-trip through AccountManager",
            UserAccountTest.TEST_REFRESH_TOKEN,
            userAccMgr.buildUserAccount(account)!!.refreshToken
        )

        // Simulate a server-side refresh token rotation by building a
        // UserAccount with a new refresh token value and updating.
        val rotatedRefreshToken = "rotated_refresh_token"
        val rotated = UserAccountBuilder.getInstance()
            .populateFromUserAccount(original)
            .refreshToken(rotatedRefreshToken)
            .build()
        userAccMgr.updateAccount(account, rotated)

        // The persisted refresh token must reflect the rotated value.
        val reloaded = userAccMgr.buildUserAccount(account)!!
        Assert.assertEquals(
            "Rotated refresh token should be persisted by updateAccount",
            rotatedRefreshToken,
            reloaded.refreshToken
        )

        // Encryption (AES-GCM with a random IV) is non-deterministic, so
        // compare against the decrypted password rather than re-encrypting
        // the expected value.
        val encryptionKey = SalesforceSDKManager.encryptionKey
        Assert.assertEquals(
            "Rotated refresh token should be used as the account password.",
            rotatedRefreshToken,
            SalesforceSDKManager.decrypt(accMgr.getPassword(account), encryptionKey)
        )
    }

    /*
     * Server-side Refresh Token Rotation (RTR) regression test.
     *
     * A UserAccount snapshot built from the persisted Account must reflect a
     * subsequent rotation of the refresh token via updateAccount, even when
     * the snapshot itself was built before the rotation.  This guards the
     * live-lookup behavior of UserAccount.refreshToken.
     */
    @Test
    fun testGetRefreshTokenReflectsLatestPersistedValue() {
        val original = UserAccountTest.createTestAccount()
        userAccMgr.createAccount(original)
        val account = userAccMgr.currentAccount!!

        // Snapshot taken before rotation.
        val staleUser = userAccMgr.buildUserAccount(account)!!
        Assert.assertEquals(
            "Initial refresh token should be observed via the snapshot",
            UserAccountTest.TEST_REFRESH_TOKEN,
            staleUser.refreshToken
        )

        // Rotate via updateAccount.
        val rotatedRefreshToken = "rotated_refresh_token"
        val rotated = UserAccountBuilder.getInstance()
            .populateFromUserAccount(original)
            .refreshToken(rotatedRefreshToken)
            .build()
        userAccMgr.updateAccount(account, rotated)

        Assert.assertEquals(
            "Initial refresh token should be observed via the snapshot",
            UserAccountTest.TEST_REFRESH_TOKEN,
            staleUser.refreshTokenForPersistence
        )
        // The previously-built snapshot should now observe the rotated value
        // through its live lookup against AccountManager.
        Assert.assertEquals(
            "Snapshot built before rotation should reflect the latest persisted refresh token",
            rotatedRefreshToken,
            staleUser.refreshToken
        )
    }

    /**
     * Test to get all authenticated users.
     */
    @Test
    fun testGetAllUserAccounts() {
        val firstUser = createTestAccountInAccountManager(userAccMgr)
        var users = userAccMgr.authenticatedUsers
        Assert.assertEquals("There should be 1 authenticated user", 1, users!!.size)
        checkSameUserAccount(firstUser, users[0])
        val secondUser = createOtherTestAccountInAccountManager()
        users = userAccMgr.authenticatedUsers
        Assert.assertEquals("There should be 2 authenticated users", 2, users!!.size)
        checkSameUserAccount(secondUser, users[1])
    }

    /**
     * Test to get the current user account.
     */
    @Test
    fun testGetCurrentUserAccount() {
        val userAccount = createTestAccountInAccountManager(userAccMgr)
        checkSameUserAccount(userAccount, userAccMgr.currentUser!!)
    }

    /**
     * Test to switch to a user account.
     */
    @Test
    fun testSwitchToUserAccount() {
        val firstUser = createTestAccountInAccountManager(userAccMgr)
        checkSameUserAccount(firstUser, userAccMgr.currentUser!!)

        val secondUser = createOtherTestAccountInAccountManager()
        checkSameUserAccount(secondUser, userAccMgr.currentUser!!)

        userAccMgr.switchToUser(firstUser)
        checkSameUserAccount(firstUser, userAccMgr.currentUser!!)

        userAccMgr.switchToUser(secondUser)
        checkSameUserAccount(secondUser, userAccMgr.currentUser!!)
    }

    /**
     * Test to check if a user account exists.
     */
    @Test
    fun testDoesUserAccountExist() {
        // Creating UserAccount objects - but not in AccountManager
        val firstUser = UserAccountTest.createTestAccount()
        val secondUser = UserAccountTest.createOtherTestAccount()

        Assert.assertFalse("User should not exist yet", userAccMgr.doesUserAccountExist(firstUser))
        Assert.assertFalse("User should not exist yet", userAccMgr.doesUserAccountExist(secondUser))

        // Saving first to AccountManager
        userAccMgr.createAccount(firstUser)
        Assert.assertTrue("User should exist now", userAccMgr.doesUserAccountExist(firstUser))
        Assert.assertFalse("User should not exist yet", userAccMgr.doesUserAccountExist(secondUser))

        // Saving second to AccountManager
        userAccMgr.createAccount(secondUser)
        Assert.assertTrue("User should exist now", userAccMgr.doesUserAccountExist(firstUser))
        Assert.assertTrue("User should exist now", userAccMgr.doesUserAccountExist(secondUser))
    }

    /**
     * Test to signout of the current user.
     */
    @Test
    fun testSignoutCurrentUser() {
        createTestAccountInAccountManager(userAccMgr)
        Assert.assertEquals("There should be 1 authenticated user", 1, userAccMgr.authenticatedUsers!!.size)
        userAccMgr.signoutCurrentUser(null, true, OAuth2.LogoutReason.USER_LOGOUT)
        Assert.assertNull("There should be no authenticated users", userAccMgr.authenticatedUsers)
        Assert.assertEquals(OAuth2.LogoutReason.USER_LOGOUT, logoutCompleteReceiver.getLastReasonReceived())
        Assert.assertNotNull(logoutCompleteReceiver.getLastUserAccountReceived())
        Assert.assertEquals(TEST_USERNAME, logoutCompleteReceiver.getLastUserAccountReceived()!!.username)
    }

    /**
     * Test to signout of a background user.
     */
    @Test
    fun testSignoutBackgroundUser() {
        val firstUser = createTestAccountInAccountManager(userAccMgr)
        val secondUser = createOtherTestAccountInAccountManager()
        userAccMgr.signoutUser(firstUser, null, false, OAuth2.LogoutReason.USER_LOGOUT)
        Assert.assertEquals("There should be 1 authenticated user", 1, userAccMgr.authenticatedUsers!!.size)
        checkSameUserAccount(secondUser, userAccMgr.currentUser!!)
        Assert.assertEquals(OAuth2.LogoutReason.USER_LOGOUT, logoutCompleteReceiver.getLastReasonReceived())
        Assert.assertNotNull(logoutCompleteReceiver.getLastUserAccountReceived())
        Assert.assertEquals(TEST_USERNAME, logoutCompleteReceiver.getLastUserAccountReceived()!!.username)
    }

    /**
     * Test that shared preferences are cleared when the last user logs out.
     * This verifies the fix for W-17366971 - ensuring that identifier.xml
     * and current_user_info files are deleted on logout.
     */
    @Test
    fun testSharedPreferencesCleanupOnLastUserLogout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Create a user and force creation of identifier.xml by accessing the encryption key
        createTestAccountInAccountManager(userAccMgr)
        val encryptionKey = SalesforceKeyGenerator.getEncryptionKey("test_key")
        Assert.assertNotNull("Encryption key should be created", encryptionKey)

        // Verify that identifier.xml shared preferences exists with data
        var identifierPrefs: SharedPreferences = context.getSharedPreferences("identifier.xml", 0)
        Assert.assertFalse("identifier.xml should have data", identifierPrefs.all.isEmpty())

        // Verify that current_user_info shared preferences exists with data
        var currentUserPrefs: SharedPreferences = context.getSharedPreferences("current_user_info", 0)
        Assert.assertFalse("current_user_info should have data", currentUserPrefs.all.isEmpty())

        // Logout the last user
        userAccMgr.signoutCurrentUser(null, false, OAuth2.LogoutReason.USER_LOGOUT)

        // Verify that identifier.xml shared preferences is cleared
        identifierPrefs = context.getSharedPreferences("identifier.xml", 0)
        Assert.assertTrue("identifier.xml should be empty after logout", identifierPrefs.all.isEmpty())

        // Verify that current_user_info shared preferences is cleared
        currentUserPrefs = context.getSharedPreferences("current_user_info", 0)
        Assert.assertTrue("current_user_info should be empty after logout", currentUserPrefs.all.isEmpty())
    }

    /**
     * Create a test account.
     *
     * @return UserAccount.
     */
    private fun createOtherTestAccountInAccountManager(): UserAccount {
        val userAccount = UserAccountTest.createOtherTestAccount()
        userAccMgr.createAccount(userAccount)
        return userAccount
    }

    private class FakeLogoutCompleteReceiver : LogoutCompleteReceiver() {
        private var lastReasonReceived: OAuth2.LogoutReason? = null
        private var lastUserAccountReceived: UserAccount? = null
        // Use a semaphore here to ensure the test doesn't proceed until the logout complete
        // receiver has been called
        private val completionSemaphore = Semaphore(0)

        override fun onLogoutComplete(reason: OAuth2.LogoutReason, userAccount: UserAccount?) {
            lastReasonReceived = reason
            lastUserAccountReceived = userAccount
            completionSemaphore.release()
        }

        fun getLastReasonReceived(): OAuth2.LogoutReason? {
            try {
                completionSemaphore.acquire()
            } catch (e: InterruptedException) {
                Assert.fail("Interrupted while waiting for lastReasonReceived to be set")
            }
            completionSemaphore.release()
            return lastReasonReceived
        }

        fun getLastUserAccountReceived(): UserAccount? {
            try {
                completionSemaphore.acquire()
            } catch (e: InterruptedException) {
                Assert.fail("Interrupted while waiting for lastUserAccountReceived to be set")
            }
            completionSemaphore.release()
            return lastUserAccountReceived
        }
    }
}
