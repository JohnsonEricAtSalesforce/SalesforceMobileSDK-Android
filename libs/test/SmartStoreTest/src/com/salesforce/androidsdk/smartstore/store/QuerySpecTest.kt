/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for QuerySpec
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class QuerySpecTest {

    @Test
    fun testAllQuerySmartSql() {
        val querySpec = QuerySpec.buildAllQuerySpec("employees", "lastName", QuerySpec.Order.descending, 1)
        Assert.assertEquals("Wrong smart sql for all query spec", "SELECT {employees:_soup} FROM {employees} ORDER BY {employees:lastName} DESC ", querySpec.smartSql)
    }

    @Test
    fun testAllQuerySmartSqlWithSelectPaths() {
        val querySpec = QuerySpec.buildAllQuerySpec("employees", arrayOf("firstName", "lastName"), "lastName", QuerySpec.Order.descending, 1)
        Assert.assertEquals("Wrong smart sql for all query spec", "SELECT {employees:firstName}, {employees:lastName} FROM {employees} ORDER BY {employees:lastName} DESC ", querySpec.smartSql)
    }

    @Test
    fun testAllQueryCountSmartSql() {
        val querySpec = QuerySpec.buildAllQuerySpec("employees", "lastName", QuerySpec.Order.descending, 1)
        Assert.assertEquals("Wrong count smart sql for all query spec", "SELECT count(*) FROM {employees} ", querySpec.countSmartSql)
    }

    @Test
    fun testAllQueryIdsSmartSql() {
        val querySpec = QuerySpec.buildAllQuerySpec("employees", "lastName", QuerySpec.Order.descending, 1)
        Assert.assertEquals("Wrong ids smart sql for all query spec", "SELECT id FROM {employees} ORDER BY {employees:lastName} DESC ", querySpec.idsSmartSql)
    }

    @Test
    fun testRangeQuerySmartSql() {
        val querySpec = QuerySpec.buildRangeQuerySpec("employees", "lastName", "Bond", "Smith", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong smart sql for range query spec", "SELECT {employees:_soup} FROM {employees} WHERE {employees:lastName} >= ? AND {employees:lastName} <= ? ORDER BY {employees:lastName} ASC ", querySpec.smartSql)
    }

    @Test
    fun testRangeQuerySmartSqlWithSelectPaths() {
        val querySpec = QuerySpec.buildRangeQuerySpec("employees", arrayOf("firstName"), "lastName", "Bond", "Smith", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong smart sql for range query spec", "SELECT {employees:firstName} FROM {employees} WHERE {employees:lastName} >= ? AND {employees:lastName} <= ? ORDER BY {employees:lastName} ASC ", querySpec.smartSql)
    }

    @Test
    fun testRangeQueryCountSmartSql() {
        val querySpec = QuerySpec.buildRangeQuerySpec("employees", "lastName", "Bond", "Smith", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong count smart sql for range query spec", "SELECT count(*) FROM {employees} WHERE {employees:lastName} >= ? AND {employees:lastName} <= ? ", querySpec.countSmartSql)
    }

    @Test
    fun testRangeQueryIdsSmartSql() {
        val querySpec = QuerySpec.buildRangeQuerySpec("employees", "lastName", "Bond", "Smith", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong ids smart sql for range query spec", "SELECT id FROM {employees} WHERE {employees:lastName} >= ? AND {employees:lastName} <= ? ORDER BY {employees:lastName} ASC ", querySpec.idsSmartSql)
    }

    @Test
    fun testExactQuerySmartSql() {
        val querySpec = QuerySpec.buildExactQuerySpec("employees", "lastName", "Bond", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong smart sql for exact query spec", "SELECT {employees:_soup} FROM {employees} WHERE {employees:lastName} = ? ORDER BY {employees:lastName} ASC ", querySpec.smartSql)
    }

    @Test
    fun testExactQuerySmartSqlWithSelectPaths() {
        val querySpec = QuerySpec.buildExactQuerySpec("employees", arrayOf("firstName", "lastName"), "lastName", "Bond", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong smart sql for exact query spec", "SELECT {employees:firstName}, {employees:lastName} FROM {employees} WHERE {employees:lastName} = ? ORDER BY {employees:lastName} ASC ", querySpec.smartSql)
    }

    @Test
    fun testExactQueryCountSmartSql() {
        val querySpec = QuerySpec.buildExactQuerySpec("employees", "lastName", "Bond", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong count smart sql for exact query spec", "SELECT count(*) FROM {employees} WHERE {employees:lastName} = ? ", querySpec.countSmartSql)
    }

    @Test
    fun testExactQueryIdsSmartSql() {
        val querySpec = QuerySpec.buildExactQuerySpec("employees", "lastName", "Bond", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong ids smart sql for exact query spec", "SELECT id FROM {employees} WHERE {employees:lastName} = ? ORDER BY {employees:lastName} ASC ", querySpec.idsSmartSql)
    }

    @Test
    fun testMatchQuerySmartSql() {
        val querySpec = QuerySpec.buildMatchQuerySpec("employees", "lastName", "Bond", "firstName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong smart sql for match query spec", "SELECT {employees:_soup} FROM {employees} WHERE {employees:_soupEntryId} IN (SELECT rowid FROM {employees}_fts WHERE {employees}_fts MATCH '{employees:lastName}:Bond') ORDER BY {employees:firstName} ASC ", querySpec.smartSql)
    }

    @Test
    fun testMatchQuerySmartSqlWithSelectPaths() {
        val querySpec = QuerySpec.buildMatchQuerySpec("employees", arrayOf("firstName", "lastName", "title"), "lastName", "Bond", "firstName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong smart sql for match query spec", "SELECT {employees:firstName}, {employees:lastName}, {employees:title} FROM {employees} WHERE {employees:_soupEntryId} IN (SELECT rowid FROM {employees}_fts WHERE {employees}_fts MATCH '{employees:lastName}:Bond') ORDER BY {employees:firstName} ASC ", querySpec.smartSql)
    }

    @Test
    fun testMatchQueryCountSmartSql() {
        val querySpec = QuerySpec.buildMatchQuerySpec("employees", "lastName", "Bond", "firstName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong count smart sql for match query spec", "SELECT count(*) FROM {employees} WHERE {employees:_soupEntryId} IN (SELECT rowid FROM {employees}_fts WHERE {employees}_fts MATCH '{employees:lastName}:Bond') ", querySpec.countSmartSql)
    }

    @Test
    fun testMatchQueryIdsSmartSql() {
        val querySpec = QuerySpec.buildMatchQuerySpec("employees", "lastName", "Bond", "firstName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong ids smart sql for match query spec", "SELECT id FROM {employees} WHERE {employees:_soupEntryId} IN (SELECT rowid FROM {employees}_fts WHERE {employees}_fts MATCH '{employees:lastName}:Bond') ORDER BY {employees:firstName} ASC ", querySpec.idsSmartSql)
    }

    @Test
    fun testLikeQuerySmartSql() {
        val querySpec = QuerySpec.buildLikeQuerySpec("employees", "lastName", "Bon%", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong smart sql for like query spec", "SELECT {employees:_soup} FROM {employees} WHERE {employees:lastName} LIKE ? ORDER BY {employees:lastName} ASC ", querySpec.smartSql)
    }

    @Test
    fun testLikeQueryCountSmartSql() {
        val querySpec = QuerySpec.buildLikeQuerySpec("employees", "lastName", "Bon%", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong count smart sql for like query spec", "SELECT count(*) FROM {employees} WHERE {employees:lastName} LIKE ? ", querySpec.countSmartSql)
    }

    @Test
    fun testLikeQueryIdsSmartSql() {
        val querySpec = QuerySpec.buildLikeQuerySpec("employees", "lastName", "Bon%", "lastName", QuerySpec.Order.ascending, 1)
        Assert.assertEquals("Wrong ids smart sql for like query spec", "SELECT id FROM {employees} WHERE {employees:lastName} LIKE ? ORDER BY {employees:lastName} ASC ", querySpec.idsSmartSql)
    }

    @Test
    fun testSmartQueryCountSmartSql() {
        val querySpec = QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1)
        Assert.assertEquals("Wrong count smart sql", "SELECT count(*) FROM (select {employees:salary} from {employees} where {employees:lastName} = 'Haas')", querySpec.countSmartSql)
    }

    @Test
    fun testSmartQueryIdsSmartSql() {
        val querySpec = QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1)
        Assert.assertEquals("Wrong ids smart sql", "SELECT id FROM (select {employees:salary} from {employees} where {employees:lastName} = 'Haas')", querySpec.idsSmartSql)
    }

    @Test
    fun testQualifyMatchKey() {
        Assert.assertEquals("Wrong qualified match query", "abc", QuerySpec.qualifyMatchKey(null, "abc"))
        Assert.assertEquals("Wrong qualified match query", "{soup:path}:abc", QuerySpec.qualifyMatchKey("{soup:path}", "abc"))
        Assert.assertEquals("Wrong qualified match query", "{soup:path2}:abc", QuerySpec.qualifyMatchKey("{soup:path1}", "{soup:path2}:abc"))
        Assert.assertEquals("Wrong qualified match query", "{soup:path}:abc AND {soup:path}:def", QuerySpec.qualifyMatchKey("{soup:path}", "abc AND def"))
        Assert.assertEquals("Wrong qualified match query", "{soup:path}:abc OR {soup:path}:def", QuerySpec.qualifyMatchKey("{soup:path}", "abc OR def"))
        Assert.assertEquals("Wrong qualified match query", "{soup:path1}:abc OR {soup:path2}:def", QuerySpec.qualifyMatchKey("{soup:path1}", "abc OR {soup:path2}:def"))
        Assert.assertEquals("Wrong qualified match query", "({soup:path}:abc AND {soup:path}:def) OR {soup:path}:ghi", QuerySpec.qualifyMatchKey("{soup:path}", "(abc AND def) OR ghi"))
        Assert.assertEquals("Wrong qualified match query", "({soup:path1}:abc AND {soup:path2}:def) OR {soup:path1}:ghi", QuerySpec.qualifyMatchKey("{soup:path1}", "(abc AND {soup:path2}:def) OR ghi"))
    }
}
