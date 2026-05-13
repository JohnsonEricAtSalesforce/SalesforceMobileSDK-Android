/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.util

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

import java.util.Date

/**
 * Test class for SyncState.
 */
@RunWith(AndroidJUnit4::class)
class SOQLMutatorTest {

    @Test
    fun testMutatorNoChange() {
        val soql = "select Id, Name from Account where Id in (select Id from Account) and Name like 'Mad Max' limit 1000"
        Assert.assertEquals(soql, SOQLMutator(soql).asBuilder().build())
    }

    @Test
    fun testSelectFieldPresenceWhenPresent() {
        val soql = "SELECT Id, Name FROM Account"
        Assert.assertTrue(SOQLMutator(soql).isSelectingField("Id"))
        Assert.assertTrue(SOQLMutator(soql).isSelectingField("Name"))
    }

    @Test
    fun testSelectFieldPresenceWhenAbsent() {
        val soql = "SELECT Id, Name FROM Account"
        Assert.assertFalse(SOQLMutator(soql).isSelectingField("Description"))
    }

    @Test
    fun testSelectFieldPresenceWhenPresentInWhereClause() {
        val soql = "SELECT Id FROM Account WHERE Name like 'James%'"
        Assert.assertFalse(SOQLMutator(soql).isSelectingField("Name"))
    }

    @Test
    fun testSelectFieldPresenceWhenPresentInSubquery() {
        Assert.assertFalse(SOQLMutator("SELECT Name, (SELECT LastName FROM Contacts) FROM Account").isSelectingField("LastName"))
    }

    @Test
    fun testSelectFieldPresenceWhenPresentAsSubstring() {
        Assert.assertFalse(SOQLMutator("SELECT LastName FROM Account").isSelectingField("Name"))
    }

    @Test
    fun testOrderByPresenceWhenPresent() {
        Assert.assertTrue(SOQLMutator("SELECT LastName FROM Account ORDER BY LastModifiedDate").isOrderingBy("LastModifiedDate"))
    }

    @Test
    fun testOrderByPresenceWhenPresentInSubquery() {
        Assert.assertFalse(SOQLMutator("SELECT LastName FROM Account WHERE Id IN (SELECT Id FROM Account ORDER BY LastModifiedDate)").isOrderingBy("LastModifiedDate"))
    }

    @Test
    fun testOrderByPresenceWhenAbsent() {
        Assert.assertFalse(SOQLMutator("SELECT LastName FROM Account").isOrderingBy("LastModifiedDate"))
    }

    @Test
    fun testOrderByPresenceWhenOrderingBySomethingElse() {
        Assert.assertFalse(SOQLMutator("SELECT LastName FROM Account ORDER BY FirstName").isOrderingBy("LastModifiedDate"))
    }

    @Test
    fun testAddSelectField() {
        val soql = "SELECT Description FROM Account"
        Assert.assertEquals("select Id,Name,Description from Account", SOQLMutator(soql).addSelectFields("Name").addSelectFields("Id").asBuilder().build())
    }

    @Test
    fun testReplaceSelectField() {
        val soql = "SELECT Description FROM Account"
        Assert.assertEquals("select Id from Account", SOQLMutator(soql).replaceSelectFields("Id").asBuilder().build())
    }

    @Test
    fun testAddWherePredicateWhenWhereClausePresent() {
        val soql = "SELECT Description FROM Account WHERE FirstName = 'James'"
        Assert.assertEquals("select Description from Account where LastModifiedDate > 123 and FirstName = 'James'", SOQLMutator(soql).addWherePredicates("LastModifiedDate > 123").asBuilder().build())
    }

    @Test
    fun testAddWherePredicateWhenWhereClauseAbsent() {
        val soql = "SELECT Description FROM Account"
        Assert.assertEquals("select Description from Account where LastModifiedDate > 123", SOQLMutator(soql).addWherePredicates("LastModifiedDate > 123").asBuilder().build())
    }

    @Test
    fun testReplaceOrderByWhenAbsent() {
        val soql = "SELECT Description FROM Account"
        Assert.assertEquals("select Description from Account order by LastModifiedDate", SOQLMutator(soql).replaceOrderBy("LastModifiedDate").asBuilder().build())
    }

    @Test
    fun testReplaceOrderByWhenPresent() {
        val soql = "SELECT Description FROM Account ORDER BY Name"
        Assert.assertEquals("select Description from Account order by LastModifiedDate", SOQLMutator(soql).replaceOrderBy("LastModifiedDate").asBuilder().build())
    }

    @Test
    fun testReplaceOrderByWhenLimit() {
        val soql = "SELECT Description FROM Account LIMIT 1000"
        Assert.assertEquals("select Description from Account order by LastModifiedDate limit 1000", SOQLMutator(soql).replaceOrderBy("LastModifiedDate").asBuilder().build())
    }


    @Test
    fun testDropOrderBy() {
        val soql = "SELECT Description FROM Account ORDER BY FirstName"
        Assert.assertEquals("select Description from Account", SOQLMutator(soql).replaceOrderBy("").asBuilder().build())
    }

    @Test
    fun testDropOrderByWhenLimit() {
        val soql = "SELECT Description FROM Account ORDER BY FirstName LIMIT 1000"
        Assert.assertEquals("select Description from Account limit 1000", SOQLMutator(soql).replaceOrderBy("").asBuilder().build())
    }

    @Test
    fun testHasOrderByWhenPresent() {
        Assert.assertTrue(SOQLMutator("SELECT Description FROM Account ORDER BY FirstName LIMIT 1000").hasOrderBy())
    }

    @Test
    fun testHasOrderByWhenPresentInSubquery() {
        Assert.assertFalse(SOQLMutator("SELECT Description FROM Account WHERE Id IN (SELECT Id FROM Account ORDER BY FirstName) LIMIT 1000").hasOrderBy())
    }

    @Test
    fun testHasOrderByWhenPresentInValue() {
        Assert.assertFalse(SOQLMutator("SELECT Description FROM Account WHERE Name = ' order by \\' order by \\''").hasOrderBy())
    }

    @Test
    fun testHasOrderByWhenAbsent() {
        Assert.assertFalse(SOQLMutator("SELECT Description FROM Account LIMIT 1000").hasOrderBy())
    }

    @Test
    fun testModifyQueryWithInClause() {
        val soql = "select Name from Account where Id IN ('001P000001NQPjJIAX','001P000001NQPkdIAH') order by Name"
        val expectedSoql = "select Id,LastModifiedDate,Name from Account where Id IN ('001P000001NQPjJIAX','001P000001NQPkdIAH') order by LastModifiedDate"
        Assert.assertEquals(expectedSoql, SOQLMutator(soql).addSelectFields("LastModifiedDate").addSelectFields("Id").replaceOrderBy("LastModifiedDate").asBuilder().build())
    }

    @Test
    fun testModifyQueryWithComplexExpressions() {
        val soql = "select Name from Account where ((Name = 'James Bond') or (Name = 'Batman')) and (Description like '%savior%') order by Name"
        val expectedSoql = "select Id,LastModifiedDate,Name from Account where ((Name = 'James Bond') or (Name = 'Batman')) and (Description like '%savior%') order by LastModifiedDate"
        Assert.assertEquals(expectedSoql, SOQLMutator(soql).addSelectFields("LastModifiedDate").addSelectFields("Id").replaceOrderBy("LastModifiedDate").asBuilder().build())
    }

    @Test
    fun testModifyOrderByTwiceInComplexQuery() {
        val soql = "select LastModifiedDate,Id, OwnerId, WhatId, Status, Subject, Priority, Description, ActivityDate, WhoId from Task where (OwnerId = '<<<UserIDHERE>>>' OR (What.Type = 'Account' AND (Account.OwnerId = '<<<UserIDHERE>>>' OR Account.Owner.ManagerId = '<<<UserIDHERE>>>'))) AND (LastModifiedDate > 2019-05-15T07:52:27.000Z ) order by Description"
        val expectedSoql = "select LastModifiedDate,Id, OwnerId, WhatId, Status, Subject, Priority, Description, ActivityDate, WhoId from Task where (OwnerId = '<<<UserIDHERE>>>' OR (What.Type = 'Account' AND (Account.OwnerId = '<<<UserIDHERE>>>' OR Account.Owner.ManagerId = '<<<UserIDHERE>>>'))) AND (LastModifiedDate > 2019-05-15T07:52:27.000Z ) order by LastModifiedDate"
        Assert.assertEquals(expectedSoql, SOQLMutator(soql).replaceOrderBy("LastModifiedDate").replaceOrderBy("LastModifiedDate").asBuilder().build())
    }

    @Test
    fun testAddWherePredicateToQueryWithOrClause() {
        val soql = "select Id from Account where Id != null or Name != null"
        val additionalPredicate = "LastModifiedDate > " + Constants.TIMESTAMP_FORMAT.format(Date())
        val expectedSoql = "select Id from Account where $additionalPredicate and Id != null or Name != null"

        Assert.assertEquals(expectedSoql, SOQLMutator(soql).addWherePredicates(additionalPredicate).asBuilder().build())
    }

    @Test
    fun testTokenizeBasic() {
        tryTokenize("hello world", "hello# #world")
        tryTokenize("hello world: my name is   James    Bond", "hello# #world:# #my# #name# #is#   #James#    #Bond")
    }

    @Test
    fun testTokenizeWithOrderGroupBy() {
        tryTokenize("hello order by world", "hello# #order by# #world")
        tryTokenize("hello group by world", "hello# #group by# #world")
        tryTokenize("hello something by world", "hello# #something# #by# #world")
        tryTokenize("hello something  by world order  by abc group    by def order", "hello# #something#  #by# #world# #order by# #abc# #group by# #def# #order")
    }

    @Test
    fun testTokenizeWithQuotes() {
        tryTokenize("hello 'my world'", "hello# #'my world'")
        tryTokenize("hello 'my world\\''", "hello# #'my world\\''")
    }

    @Test
    fun testTokenizeWithParentheses() {
        tryTokenize("hello (this is a group)", "hello# #(this is a group)")
        tryTokenize("hello (a or (b and c) or d),(e or f)", "hello# #(a or (b and c) or d)#,#(e or f)")
    }

    @Test
    fun testTokenizeWithQuotesInParentheses() {
        tryTokenize("hello (this is a 'group')", "hello# #(this is a 'group')")
        tryTokenize("hello (a or (b and 'the name of c') or d)", "hello# #(a or (b and 'the name of c') or d)")
    }

    @Test
    fun testTokenizeWithParenthesesInQuotes() {
        tryTokenize("hello 'oh oh ( ) ( )))'", "hello# #'oh oh ( ) ( )))'")
    }


    private fun tryTokenize(soql: String, expectedTokensJoined: String) {
        val tokens = SOQLMutator.SOQLTokenizer(soql).tokenize()
        val actualTokensJoined = tokens.joinToString("#")
        Assert.assertEquals(expectedTokensJoined, actualTokensJoined)
    }
}
