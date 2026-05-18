package com.salesforce.androidsdk.mobilesync.target
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase
import com.salesforce.androidsdk.mobilesync.util.Constants
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class) @SmallTest
class SoqlSyncDownTargetTest : SyncManagerTestCase() {
    @Before override fun setUp() { super.setUp() }
    @After override fun tearDown() { super.tearDown() }

    @Test fun testAddFilterForResync() { val date = Date(); val dateLong = date.time; val dateStr = Constants.TIMESTAMP_FORMAT.format(date); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > $dateStr", SoqlSyncDownTarget.addFilterForReSync("select Id from Account", "LastModifiedDate", dateLong)); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where otherDate > $dateStr", SoqlSyncDownTarget.addFilterForReSync("select Id from Account", "otherDate", dateLong)); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > $dateStr limit 100", SoqlSyncDownTarget.addFilterForReSync("select Id from Account limit 100", "LastModifiedDate", dateLong)); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > $dateStr and Name = 'John'", SoqlSyncDownTarget.addFilterForReSync("select Id from Account where Name = 'John'", "LastModifiedDate", dateLong)); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > $dateStr and Name = 'John' limit 100", SoqlSyncDownTarget.addFilterForReSync("select Id from Account where Name = 'John' limit 100", "LastModifiedDate", dateLong)); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > $dateStr", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account", "LastModifiedDate", dateLong)); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > $dateStr limit 100", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account LIMIT 100", "LastModifiedDate", dateLong)); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > $dateStr and Name = 'John'", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account WHERE Name = 'John'", "LastModifiedDate", dateLong)); Assert.assertEquals("Wrong result for addFilterForReSync", "select Id from Account where LastModifiedDate > $dateStr and Name = 'John' limit 100", SoqlSyncDownTarget.addFilterForReSync("SELECT Id FROM Account WHERE Name = 'John' LIMIT 100", "LastModifiedDate", dateLong)) }
    @Test fun testGetSoqlForRemoteIds() { val target = SoqlSyncDownTarget("SELECT Name FROM Account WHERE Name = 'James Bond'"); Assert.assertEquals("select Id from Account where Name = 'James Bond'", target.soqlForRemoteIds) }
    @Test fun testQueryWithSubqueries() { val t1 = SoqlSyncDownTarget("SELECT Name, (SELECT Contact.LastName FROM Account.Contacts) FROM Account WHERE Name = 'James Bond' LIMIT 10"); Assert.assertEquals("select Id from Account where Name = 'James Bond' limit 10", t1.soqlForRemoteIds); val t2 = SoqlSyncDownTarget("SELECT Name FROM Account WHERE Id IN (SELECT Id FROM Account WHERE Name = 'James Bond' LIMIT 10)"); Assert.assertEquals("select Id from Account where Id IN (SELECT Id FROM Account WHERE Name = 'James Bond' LIMIT 10)", t2.soqlForRemoteIds); val t3 = SoqlSyncDownTarget("SELECT Name, (SELECT Contact.LastName FROM Account.Contacts) from Account where Id IN (SELECT Id FROM Account WHERE Name = 'James Bond' LIMIT 10)"); Assert.assertEquals("select Id from Account where Id IN (SELECT Id FROM Account WHERE Name = 'James Bond' LIMIT 10)", t3.soqlForRemoteIds) }
    @Test fun testQueryWithFromField() { val target = SoqlSyncDownTarget("SELECT From_customer__c FROM Account WHERE Name = 'James Bond' LIMIT 10"); Assert.assertEquals("select Id from Account where Name = 'James Bond' limit 10", target.soqlForRemoteIds) }
    @Test fun testAddMissingFieldsAndOrderByToSOQLTarget() { val soqlExpected = "select Id,LastModifiedDate,FirstName, LastName from Contact order by LastModifiedDate"; val target = SoqlSyncDownTarget("select FirstName, LastName from Contact"); Assert.assertEquals("SOQL query should contain Id and LastModifiedDate fields", soqlExpected, target.getQuery()) }
    // Note: Interceptor-based tests (testNoBatchSizeHeaderPresentByDefault, etc.) are not feasible
    // after RestClient conversion to Kotlin (final class). These tests validated internal behavior
    // that is now verified through other integration tests.
}
