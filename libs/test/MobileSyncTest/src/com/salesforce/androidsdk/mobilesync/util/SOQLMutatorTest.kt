package com.salesforce.androidsdk.mobilesync.util
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class SOQLMutatorTest {
    @Test fun testMutatorNoChange() { val soql = "select Id, Name from Account where Id in (select Id from Account) and Name like 'Mad Max' limit 1000"; Assert.assertEquals(soql, SOQLMutator(soql).asBuilder().build()) }
    @Test fun testSelectFieldPresenceWhenPresent() { val soql = "SELECT Id, Name FROM Account"; Assert.assertTrue(SOQLMutator(soql).isSelectingField("Id")); Assert.assertTrue(SOQLMutator(soql).isSelectingField("Name")) }
    @Test fun testSelectFieldPresenceWhenAbsent() { Assert.assertFalse(SOQLMutator("SELECT Id, Name FROM Account").isSelectingField("Description")) }
    @Test fun testSelectFieldPresenceWhenPresentInWhereClause() { Assert.assertFalse(SOQLMutator("SELECT Id FROM Account WHERE Name like 'James%'").isSelectingField("Name")) }
    @Test fun testSelectFieldPresenceWhenPresentInSubquery() { Assert.assertFalse(SOQLMutator("SELECT Name, (SELECT LastName FROM Contacts) FROM Account").isSelectingField("LastName")) }
    @Test fun testSelectFieldPresenceWhenPresentAsSubstring() { Assert.assertFalse(SOQLMutator("SELECT LastName FROM Account").isSelectingField("Name")) }
    @Test fun testOrderByPresenceWhenPresent() { Assert.assertTrue(SOQLMutator("SELECT LastName FROM Account ORDER BY LastModifiedDate").isOrderingBy("LastModifiedDate")) }
    @Test fun testOrderByPresenceWhenPresentInSubquery() { Assert.assertFalse(SOQLMutator("SELECT LastName FROM Account WHERE Id IN (SELECT Id FROM Account ORDER BY LastModifiedDate)").isOrderingBy("LastModifiedDate")) }
    @Test fun testOrderByPresenceWhenAbsent() { Assert.assertFalse(SOQLMutator("SELECT LastName FROM Account").isOrderingBy("LastModifiedDate")) }
    @Test fun testOrderByPresenceWhenOrderingBySomethingElse() { Assert.assertFalse(SOQLMutator("SELECT LastName FROM Account ORDER BY FirstName").isOrderingBy("LastModifiedDate")) }
    @Test fun testAddSelectField() { Assert.assertEquals("select Id,Name,Description from Account", SOQLMutator("SELECT Description FROM Account").addSelectFields("Name").addSelectFields("Id").asBuilder().build()) }
    @Test fun testReplaceSelectField() { Assert.assertEquals("select Id from Account", SOQLMutator("SELECT Description FROM Account").replaceSelectFields("Id").asBuilder().build()) }
    @Test fun testAddWherePredicateWhenWhereClausePresent() { Assert.assertEquals("select Description from Account where LastModifiedDate > 123 and FirstName = 'James'", SOQLMutator("SELECT Description FROM Account WHERE FirstName = 'James'").addWherePredicates("LastModifiedDate > 123").asBuilder().build()) }
    @Test fun testAddWherePredicateWhenWhereClauseAbsent() { Assert.assertEquals("select Description from Account where LastModifiedDate > 123", SOQLMutator("SELECT Description FROM Account").addWherePredicates("LastModifiedDate > 123").asBuilder().build()) }
    @Test fun testReplaceOrderByWhenAbsent() { Assert.assertEquals("select Description from Account order by LastModifiedDate", SOQLMutator("SELECT Description FROM Account").replaceOrderBy("LastModifiedDate").asBuilder().build()) }
    @Test fun testReplaceOrderByWhenPresent() { Assert.assertEquals("select Description from Account order by LastModifiedDate", SOQLMutator("SELECT Description FROM Account ORDER BY Name").replaceOrderBy("LastModifiedDate").asBuilder().build()) }
    @Test fun testReplaceOrderByWhenLimit() { Assert.assertEquals("select Description from Account order by LastModifiedDate limit 1000", SOQLMutator("SELECT Description FROM Account LIMIT 1000").replaceOrderBy("LastModifiedDate").asBuilder().build()) }
    @Test fun testDropOrderBy() { Assert.assertEquals("select Description from Account", SOQLMutator("SELECT Description FROM Account ORDER BY FirstName").replaceOrderBy("").asBuilder().build()) }
    @Test fun testDropOrderByWhenLimit() { Assert.assertEquals("select Description from Account limit 1000", SOQLMutator("SELECT Description FROM Account ORDER BY FirstName LIMIT 1000").replaceOrderBy("").asBuilder().build()) }
    @Test fun testHasOrderByWhenPresent() { Assert.assertTrue(SOQLMutator("SELECT Description FROM Account ORDER BY FirstName LIMIT 1000").hasOrderBy()) }
    @Test fun testHasOrderByWhenPresentInSubquery() { Assert.assertFalse(SOQLMutator("SELECT Description FROM Account WHERE Id IN (SELECT Id FROM Account ORDER BY FirstName) LIMIT 1000").hasOrderBy()) }
    @Test fun testHasOrderByWhenPresentInValue() { Assert.assertFalse(SOQLMutator("SELECT Description FROM Account WHERE Name = ' order by \\' order by \\''").hasOrderBy()) }
    @Test fun testHasOrderByWhenAbsent() { Assert.assertFalse(SOQLMutator("SELECT Description FROM Account LIMIT 1000").hasOrderBy()) }
    @Test fun testModifyQueryWithInClause() { Assert.assertEquals("select Id,LastModifiedDate,Name from Account where Id IN ('001P000001NQPjJIAX','001P000001NQPkdIAH') order by LastModifiedDate", SOQLMutator("select Name from Account where Id IN ('001P000001NQPjJIAX','001P000001NQPkdIAH') order by Name").addSelectFields("LastModifiedDate").addSelectFields("Id").replaceOrderBy("LastModifiedDate").asBuilder().build()) }
    @Test fun testModifyQueryWithComplexExpressions() { Assert.assertEquals("select Id,LastModifiedDate,Name from Account where ((Name = 'James Bond') or (Name = 'Batman')) and (Description like '%savior%') order by LastModifiedDate", SOQLMutator("select Name from Account where ((Name = 'James Bond') or (Name = 'Batman')) and (Description like '%savior%') order by Name").addSelectFields("LastModifiedDate").addSelectFields("Id").replaceOrderBy("LastModifiedDate").asBuilder().build()) }
    @Test fun testModifyOrderByTwiceInComplexQuery() { val soql = "select LastModifiedDate,Id, OwnerId, WhatId, Status, Subject, Priority, Description, ActivityDate, WhoId from Task where (OwnerId = '<<<UserIDHERE>>>' OR (What.Type = 'Account' AND (Account.OwnerId = '<<<UserIDHERE>>>' OR Account.Owner.ManagerId = '<<<UserIDHERE>>>'))) AND (LastModifiedDate > 2019-05-15T07:52:27.000Z ) order by Description"; val expectedSoql = "select LastModifiedDate,Id, OwnerId, WhatId, Status, Subject, Priority, Description, ActivityDate, WhoId from Task where (OwnerId = '<<<UserIDHERE>>>' OR (What.Type = 'Account' AND (Account.OwnerId = '<<<UserIDHERE>>>' OR Account.Owner.ManagerId = '<<<UserIDHERE>>>'))) AND (LastModifiedDate > 2019-05-15T07:52:27.000Z ) order by LastModifiedDate"; Assert.assertEquals(expectedSoql, SOQLMutator(soql).replaceOrderBy("LastModifiedDate").replaceOrderBy("LastModifiedDate").asBuilder().build()) }
    @Test fun testAddWherePredicateToQueryWithOrClause() { val soql = "select Id from Account where Id != null or Name != null"; val additionalPredicate = "LastModifiedDate > " + Constants.TIMESTAMP_FORMAT.format(Date()); val expectedSoql = "select Id from Account where $additionalPredicate and Id != null or Name != null"; Assert.assertEquals(expectedSoql, SOQLMutator(soql).addWherePredicates(additionalPredicate).asBuilder().build()) }
    @Test fun testTokenizeBasic() { tryTokenize("hello world", "hello# #world"); tryTokenize("hello world: my name is   James    Bond", "hello# #world:# #my# #name# #is#   #James#    #Bond") }
    @Test fun testTokenizeWithOrderGroupBy() { tryTokenize("hello order by world", "hello# #order by# #world"); tryTokenize("hello group by world", "hello# #group by# #world"); tryTokenize("hello something by world", "hello# #something# #by# #world"); tryTokenize("hello something  by world order  by abc group    by def order", "hello# #something#  #by# #world# #order by# #abc# #group by# #def# #order") }
    @Test fun testTokenizeWithQuotes() { tryTokenize("hello 'my world'", "hello# #'my world'"); tryTokenize("hello 'my world\\''", "hello# #'my world\\''") }
    @Test fun testTokenizeWithParentheses() { tryTokenize("hello (this is a group)", "hello# #(this is a group)"); tryTokenize("hello (a or (b and c) or d),(e or f)", "hello# #(a or (b and c) or d)#,#(e or f)") }
    @Test fun testTokenizeWithQuotesInParentheses() { tryTokenize("hello (this is a 'group')", "hello# #(this is a 'group')"); tryTokenize("hello (a or (b and 'the name of c') or d)", "hello# #(a or (b and 'the name of c') or d)") }
    @Test fun testTokenizeWithParenthesesInQuotes() { tryTokenize("hello 'oh oh ( ) ( )))'", "hello# #'oh oh ( ) ( )))'") }
    private fun tryTokenize(soql: String, expectedTokensJoined: String) { val tokens = SOQLMutator.SOQLTokenizer(soql).tokenize(); Assert.assertEquals(expectedTokensJoined, tokens.joinToString("#")) }
}
