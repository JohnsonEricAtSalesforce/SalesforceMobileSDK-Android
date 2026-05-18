/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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
import com.salesforce.androidsdk.smartstore.store.QuerySpec.Order
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper.SmartSqlException
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import com.salesforce.androidsdk.util.JSONTestHelper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for "smart" sql
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class SmartSqlTest : SmartStoreTestCase() {

    companion object {
        private const val BUDGET = "budget"
        private const val NAME = "name"
        private const val SALARY = "salary"
        private const val MANAGER_ID = "managerId"
        private const val EMPLOYEE_ID = "employeeId"
        private const val LAST_NAME = "lastName"
        private const val FIRST_NAME = "firstName"
        private const val DEPT_CODE = "deptCode"
        private const val EMPLOYEES_SOUP = "employees"
        private const val DEPARTMENTS_SOUP = "departments"
        private const val EDUCATION = "education"
        private const val IS_MANAGER = "isManager"
        private const val BUILDING = "building"
    }

    override fun getEncryptionKey(): String = ""

    @Before
    override fun setUp() {
        super.setUp()
        store.registerSoup(EMPLOYEES_SOUP, arrayOf(
            IndexSpec(FIRST_NAME, Type.string),
            IndexSpec(LAST_NAME, Type.string),
            IndexSpec(DEPT_CODE, Type.string),
            IndexSpec(EMPLOYEE_ID, Type.string),
            IndexSpec(MANAGER_ID, Type.string),
            IndexSpec(SALARY, Type.integer),
            IndexSpec(EDUCATION, Type.json1),
            IndexSpec(IS_MANAGER, Type.json1)
        ))
        store.registerSoup(DEPARTMENTS_SOUP, arrayOf(
            IndexSpec(DEPT_CODE, Type.string),
            IndexSpec(NAME, Type.string),
            IndexSpec(BUDGET, Type.integer),
            IndexSpec(BUILDING, Type.json1)
        ))
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    fun testSimpleConvertSmartSql() {
        Assert.assertEquals("select TABLE_1_0, TABLE_1_1 from TABLE_1 order by TABLE_1_1",
            store.convertSmartSql("select {employees:firstName}, {employees:lastName} from {employees} order by {employees:lastName}"))
        Assert.assertEquals("select TABLE_2_1 from TABLE_2 order by TABLE_2_0",
            store.convertSmartSql("select {departments:name} from {departments} order by {departments:deptCode}"))
    }

    @Test
    fun testConvertSmartSqlWithJoin() {
        Assert.assertEquals("select TABLE_2_1, TABLE_1_0 || ' ' || TABLE_1_1 " +
                "from TABLE_1, TABLE_2 " +
                "where TABLE_2_0 = TABLE_1_2 " +
                "order by TABLE_2_1, TABLE_1_1",
            store.convertSmartSql("select {departments:name}, {employees:firstName} || ' ' || {employees:lastName} " +
                "from {employees}, {departments} " +
                "where {departments:deptCode} = {employees:deptCode} " +
                "order by {departments:name}, {employees:lastName}"))
    }

    @Test
    fun testConvertSmartSqlWithSelfJoin() {
        Assert.assertEquals("select mgr.TABLE_1_1, e.TABLE_1_1 " +
                "from TABLE_1 as mgr, TABLE_1 as e " +
                "where mgr.TABLE_1_3 = e.TABLE_1_4",
            store.convertSmartSql("select mgr.{employees:lastName}, e.{employees:lastName} " +
                "from {employees} as mgr, {employees} as e " +
                "where mgr.{employees:employeeId} = e.{employees:managerId}"))
    }

    @Test
    fun testConvertSmartSqlWithSelfJoinAndJsonExtractedField() {
        Assert.assertEquals("select json_extract(mgr.soup, '\$.education'), json_extract(e.soup, '\$.education') " +
                "from TABLE_1 as mgr, TABLE_1 as e " +
                "where json_extract(mgr.soup, '\$.education') = json_extract(e.soup, '\$.education')",
            store.convertSmartSql("select mgr.{employees:education}, e.{employees:education} " +
                "from {employees} as mgr, {employees} as e " +
                "where mgr.{employees:education} = e.{employees:education}"))
    }

    @Test
    fun testConvertSmartSqlWithSelfJoinAndJsonExtractedFieldNoLeadingSpace() {
        Assert.assertEquals("select json_extract(mgr.soup, '\$.education'),json_extract(e.soup, '\$.education') " +
                "from TABLE_1 as mgr, TABLE_1 as e " +
                "where not (json_extract(mgr.soup, '\$.education')=json_extract(e.soup, '\$.education'))",
            store.convertSmartSql("select mgr.{employees:education},e.{employees:education} " +
                "from {employees} as mgr, {employees} as e " +
                "where not (mgr.{employees:education}=e.{employees:education})"))
    }

    @Test
    fun testConvertSmartSqlWithSpecialColumns() {
        Assert.assertEquals("select TABLE_1.id, TABLE_1.created, TABLE_1.lastModified, TABLE_1.soup from TABLE_1",
            store.convertSmartSql("select {employees:_soupEntryId}, {employees:_soupCreatedDate}, {employees:_soupLastModifiedDate}, {employees:_soup} from {employees}"))
    }

    @Test
    fun testConvertSmartSqlWithSpecialColumnsAndJoin() {
        Assert.assertEquals("select TABLE_1.id, TABLE_2.id from TABLE_1, TABLE_2",
            store.convertSmartSql("select {employees:_soupEntryId}, {departments:_soupEntryId} from {employees}, {departments}"))
    }

    @Test
    fun testConvertSmartSqlWithSpecialColumnsAndSelfJoin() {
        Assert.assertEquals("select mgr.id, e.id from TABLE_1 as mgr, TABLE_1 as e",
            store.convertSmartSql("select mgr.{employees:_soupEntryId}, e.{employees:_soupEntryId} from {employees} as mgr, {employees} as e"))
    }

    @Test
    fun testConvertSmartSqlWithInsertUpdateDelete() {
        for (smartSql in arrayOf("insert into {employees}", "update {employees}", "delete from {employees}")) {
            try {
                store.convertSmartSql(smartSql)
                Assert.fail("Should have thrown exception for $smartSql")
            } catch (e: SmartSqlException) {
                // Expected
            }
        }
    }

    @Test
    fun testConvertSmartSqlWithJSON1() {
        Assert.assertEquals("select TABLE_1_1, json_extract(soup, '\$.education') from TABLE_1 where json_extract(soup, '\$.education') = 'MIT'",
            store.convertSmartSql("select {employees:lastName}, {employees:education} from {employees} where {employees:education} = 'MIT'"))
    }

    @Test
    fun testConvertSmartSqlWithJSON1AndTableQualifiedColumn() {
        Assert.assertEquals("select json_extract(TABLE_1.soup, '\$.education') from TABLE_1 order by json_extract(TABLE_1.soup, '\$.education')",
            store.convertSmartSql("select {employees}.{employees:education} from {employees} order by {employees}.{employees:education}"))
    }

    @Test
    fun testConvertSmartSqlWithJSON1AndTableAliases() {
        Assert.assertEquals("select json_extract(e.soup, '\$.education'), json_extract(soup, '\$.building') from TABLE_1 as e, TABLE_2",
            store.convertSmartSql("select e.{employees:education}, {departments:building} from {employees} as e, {departments}"))
    }

    @Test
    fun testConvertSmartSqlForNonIndexedColumns() {
        Assert.assertEquals("select json_extract(soup, '\$.education'), json_extract(soup, '\$.address.zipcode') from TABLE_1 where json_extract(soup, '\$.address.city') = 'San Francisco'",
            store.convertSmartSql("select {employees:education}, {employees:address.zipcode} from {employees} where {employees:address.city} = 'San Francisco'"))
    }

    @Test
    fun testConvertSmartSqlWithQuotedCurlyBraces() {
        Assert.assertEquals("select json_extract(soup, '\$.education') from TABLE_1 where json_extract(soup, '\$.education') like 'Account(where: {Name: {eq: \"Jason\"}})'",
            store.convertSmartSql("select {employees:education} from {employees} where {employees:education} like 'Account(where: {Name: {eq: \"Jason\"}})'"))
    }

    @Test
    fun testConvertOtherComplexSmartSql() {
        Assert.assertEquals("SELECT json_set('{}', '\$.data.uiapi.query.Account.edges', ( SELECT json_group_array(json_set('{}', '\$.node.Id', (json_extract('Account.JSON', '\$.data.fields.Id.value')) )) FROM (SELECT 'Account'.TABLE_1_1 as 'Account.JSON' FROM TABLE_1 as 'Account' WHERE ( json_extract('Account.JSON', '\$.data.apiName') = 'Account' ) ) ) ) as json",
            store.convertSmartSql("SELECT json_set('{}', '\$.data.uiapi.query.Account.edges', ( SELECT json_group_array(json_set('{}', '\$.node.Id', (json_extract('Account.JSON', '\$.data.fields.Id.value')) )) FROM (SELECT 'Account'.TABLE_1_1 as 'Account.JSON' FROM TABLE_1 as 'Account' WHERE ( json_extract('Account.JSON', '\$.data.apiName') = 'Account' ) ) ) ) as json"))
    }

    @Test
    fun testConvertSmartSqlWithMultipleQuotedCurlyBraces() {
        Assert.assertEquals("select json_extract(soup, '\$.education'), '{a:b}', TABLE_1_0 from TABLE_1 where json_extract(soup, '\$.address') = '{\"city\": \"San Francisco\"}' or TABLE_1_1 like 'B%'",
            store.convertSmartSql("select {employees:education}, '{a:b}', {employees:firstName} from {employees} where {employees:address} = '{\"city\": \"San Francisco\"}' or {employees:lastName} like 'B%'"))
    }

    @Test
    fun testConvertSmartSqlWithQuotedUnbalancedCurlyBraces() {
        Assert.assertEquals("select json_extract(soup, '\$.education') from TABLE_1 where json_extract(soup, '\$.education') like ' { { { } } '",
            store.convertSmartSql("select {employees:education} from {employees} where {employees:education} like ' { { { } } '"))
    }

    @Test
    fun testSmartQueryDoingCount() {
        loadData()
        val result = store.query(QuerySpec.buildSmartQuerySpec("select count(*) from {employees}", 1), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[7]]"), result)
    }

    @Test
    fun testSmartQueryDoingSum() {
        loadData()
        val result = store.query(QuerySpec.buildSmartQuerySpec("select sum({departments:budget}) from {departments}", 1), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[3000000]]"), result)
    }

    @Test
    fun testSmartQueryReturningOneRowWithOneInteger() {
        loadData()
        val result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[200000]]"), result)
    }

    @Test
    fun testSmartQueryReturningOneRowWithTwoIntegers() {
        loadData()
        val result = store.query(QuerySpec.buildSmartQuerySpec("select mgr.{employees:salary}, e.{employees:salary} from {employees} as mgr, {employees} as e where e.{employees:lastName} = 'Thompson' and mgr.{employees:employeeId} = e.{employees:managerId}", 1), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[200000,120000]]"), result)
    }

    @Test
    fun testSmartQueryReturningTwoRowsWithOneIntegerEach() {
        loadData()
        val result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:salary} from {employees} where {employees:managerId} = '00010' order by {employees:firstName}", 2), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[120000],[100000]]"), result)
    }

    @Test
    fun testSmartQueryReturningSoupStringAndInteger() {
        loadData()
        val christineJson = store.query(QuerySpec.buildExactQuerySpec(EMPLOYEES_SOUP, "employeeId", "00010", null, null, 1), 0).getJSONObject(0)
        Assert.assertEquals("Wrong elt", "Christine", christineJson.getString(FIRST_NAME))
        val result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:_soup}, {employees:firstName}, {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1), 0)
        Assert.assertEquals("Expected one row", 1, result.length())
        JSONTestHelper.assertSameJSON("Wrong soup", christineJson, result.getJSONArray(0).getJSONObject(0))
        Assert.assertEquals("Wrong first name", "Christine", result.getJSONArray(0).getString(1))
        Assert.assertEquals("Wrong salary", 200000, result.getJSONArray(0).getInt(2))
    }

    @Test
    fun testSmartQueryWithPaging() {
        loadData()
        val query = QuerySpec.buildSmartQuerySpec("select {employees:firstName} from {employees} order by {employees:firstName}", 1)
        Assert.assertEquals("Expected 7 employees", 7, store.countQuery(query))
        val expectedResults = arrayOf("Christine", "Eileen", "Eva", "Irving", "John", "Michael", "Sally")
        for (i in 0 until 7) {
            val result = store.query(query, i)
            JSONTestHelper.assertSameJSONArray("Wrong result at page $i", JSONArray("[[${expectedResults[i]}]]"), result)
        }
    }

    @Test
    fun testSmartQueryWithSpecialFields() {
        loadData()
        val christineJson = store.query(QuerySpec.buildExactQuerySpec(EMPLOYEES_SOUP, "employeeId", "00010", null, null, 1), 0).getJSONObject(0)
        Assert.assertEquals("Wrong elt", "Christine", christineJson.getString(FIRST_NAME))
        val result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:_soup}, {employees:_soupEntryId}, {employees:_soupLastModifiedDate}, {employees:salary} from {employees} where {employees:lastName} = 'Haas'", 1), 0)
        Assert.assertEquals("Expected one row", 1, result.length())
        JSONTestHelper.assertSameJSON("Wrong soup", christineJson, result.getJSONArray(0).getJSONObject(0))
        JSONTestHelper.assertSameJSON("Wrong soupEntryId", christineJson.getString(SmartStore.SOUP_ENTRY_ID), result.getJSONArray(0).getInt(1))
        JSONTestHelper.assertSameJSON("Wrong soupLastModifiedDate", christineJson.getString(SmartStore.SOUP_LAST_MODIFIED_DATE), result.getJSONArray(0).getLong(2))
    }

    @Test
    fun testSmartQueryMatchingNullField() {
        var createdEmployee: JSONObject

        // Employee with dept code
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"001\",\"deptCode\":\"xyz\"}")
        Assert.assertEquals("xyz", createdEmployee.get(DEPT_CODE))

        // Employee with JSONObject.NULL dept code
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"002\",\"deptCode\":null}")
        Assert.assertTrue(createdEmployee.isNull(DEPT_CODE))

        // Employee with "" dept code
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"003\",\"deptCode\":\"\"}")
        Assert.assertEquals("", createdEmployee.get(DEPT_CODE))

        // Employee with no dept code
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"004\"}")
        Assert.assertFalse(createdEmployee.has(DEPT_CODE))

        // Smart sql with is not null
        var result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:deptCode} is not null order by {employees:employeeId}", 4), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"001\"],[\"003\"]]"), result)

        // Smart sql with is null
        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:deptCode} is null order by {employees:employeeId}", 4), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"002\"],[\"004\"]]"), result)

        // Smart sql looking for empty string
        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:deptCode} = \"\" order by {employees:employeeId}", 4), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"003\"]]"), result)
    }

    @Test
    fun testSmartQueryMachingBooleanInJSON1Field() {
        var createdEmployee: JSONObject

        loadData()

        // Creating another employee from a json string with isManager true
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"101\",\"isManager\":true}")
        Assert.assertEquals(true, createdEmployee.get(IS_MANAGER))

        // Creating another employee from a json string with isManager false
        createdEmployee = createEmployeeWithJsonString("{\"employeeId\":\"102\",\"isManager\":false}")
        Assert.assertEquals(false, createdEmployee.get(IS_MANAGER))

        // Smart sql looking for isManager true
        var result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:isManager} = 1 order by {employees:employeeId}", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"00010\"],[\"00040\"],[\"00050\"],[\"101\"]]"), result)
        // Smart sql looking for isManager false
        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:isManager} = 0 order by {employees:employeeId}", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"00020\"],[\"00060\"],[\"00070\"],[\"00310\"],[\"102\"]]"), result)
    }

    @Test
    fun testSmartQueryMachingNonAsciiInStringField() {
        createEmployeeWithJsonString("{\"employeeId\":\"101\",\"firstName\":\"Göktuğ\"}")
        createEmployeeWithJsonString("{\"employeeId\":\"102\",\"firstName\":\"보배\"}")

        var result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:firstName} like '%ğ%'", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"101\"]]"), result)

        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:firstName} like '%배%'", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"102\"]]"), result)
    }

    @Test
    fun testSmartQueryMachingNonAsciiInJSON1Field() {
        createEmployeeWithJsonString("{\"employeeId\":\"101\",\"education\":\"latince uzmanı\"}")
        createEmployeeWithJsonString("{\"employeeId\":\"102\",\"education\":\"라틴어 전문가\"}")

        var result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:education} like '%ı%'", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"101\"]]"), result)

        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:education} like '%문%'", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"102\"]]"), result)
    }

    @Test
    fun testSmartQueryMachingNonAsciiInNonIndexedField() {
        createEmployeeWithJsonString("{\"employeeId\":\"101\",\"country\":\"Türkçe\"}")
        createEmployeeWithJsonString("{\"employeeId\":\"102\",\"country\":\"한국\"}")

        var result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:country} like '%ç%'", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"101\"]]"), result)

        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:country} like '%국%'", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"102\"]]"), result)
    }

    @Test
    fun testSmartQueryFilteringByNonIndexedField() {
        createEmployeeWithJsonString("{\"employeeId\":\"101\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94105}}")
        createEmployeeWithJsonString("{\"employeeId\":\"102\",\"address\":{\"city\":\"New York City\", \"zipcode\":10004}}")
        createEmployeeWithJsonString("{\"employeeId\":\"103\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94106}}")
        createEmployeeWithJsonString("{\"employeeId\":\"104\",\"address\":{\"city\":\"New York City\", \"zipcode\":10006}}")

        var result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:address.city} = 'San Francisco' order by {employees:employeeId}", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"101\"],[\"103\"]]"), result)

        result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId} from {employees} where {employees:address.zipcode} = 10006", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"104\"]]"), result)
    }

    @Test
    fun testSmartQueryReturningNonIndexedField() {
        createEmployeeWithJsonString("{\"employeeId\":\"101\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94105}}")
        createEmployeeWithJsonString("{\"employeeId\":\"102\",\"address\":{\"city\":\"New York City\", \"zipcode\":10004}}")
        createEmployeeWithJsonString("{\"employeeId\":\"103\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94106}}")
        createEmployeeWithJsonString("{\"employeeId\":\"104\",\"address\":{\"city\":\"New York City\", \"zipcode\":10006}}")

        val result = store.query(QuerySpec.buildSmartQuerySpec("select {employees:employeeId}, {employees:address.zipcode} from {employees} where {employees:address.city} = 'San Francisco' order by {employees:employeeId}", 10), 0)
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"101\", 94105],[\"103\", 94106]]"), result)
    }

    @Test
    fun testSmartQueryUsingWhereArgs() {
        createEmployeeWithJsonString("{\"employeeId\":\"101\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94105}}")
        createEmployeeWithJsonString("{\"employeeId\":\"102\",\"address\":{\"city\":\"New York City\", \"zipcode\":10004}}")
        createEmployeeWithJsonString("{\"employeeId\":\"103\",\"address\":{\"city\":\"San Francisco\", \"zipcode\":94106}}")
        createEmployeeWithJsonString("{\"employeeId\":\"104\",\"address\":{\"city\":\"New York City\", \"zipcode\":10006}}")

        val querySpec = QuerySpec.buildSmartQuerySpec("select {employees:employeeId}, {employees:address.zipcode} from {employees} where {employees:address.city} = ? order by {employees:employeeId}", 10)
        var result = store.queryWithArgs(querySpec, 0, "San Francisco")
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"101\", 94105],[\"103\", 94106]]"), result)
        result = store.queryWithArgs(querySpec, 0, "New York City")
        JSONTestHelper.assertSameJSONArray("Wrong result", JSONArray("[[\"102\", 10004],[\"104\", 10006]]"), result)
    }

    @Test
    fun testNonSmartQueryUsingWhereArgs() {
        val querySpec = QuerySpec.buildAllQuerySpec(EMPLOYEES_SOUP, EMPLOYEE_ID, Order.ascending, 10)
        try {
            store.queryWithArgs(querySpec, 0, "San Francisco")
            Assert.fail("SmartStoreException should have been thrown")
        } catch (e: SmartStoreException) {
            Assert.assertEquals("whereArgs can only be provided for smart queries", e.message)
        }
    }

    @Test
    fun testCleanupRegexpFaster() {
        val oldRegexp = "([^ ]+)\\.json_extract\\(soup"

        // At least 500 times faster than the old regexp
        Assert.assertTrue(
            timeRegexpInMs(SmartSqlHelper.TABLE_DOT_JSON_EXTRACT_REGEXP) * 500 < timeRegexpInMs(oldRegexp))
        // No more than 25ms
        Assert.assertTrue(timeRegexpInMs(SmartSqlHelper.TABLE_DOT_JSON_EXTRACT_REGEXP) < 25)
    }

    private fun timeRegexpInMs(regexp: String): Double {
        val q = "SELECT {DEFAULT:LdsSoupKey}, {DEFAULT:LdsSoupValue}\nFROM {DEFAULT}\nWHERE {DEFAULT:LdsSoupKey}\nIN (\'UiApi::BatchRepresentation(childRelationships:undefined,fields:undefined,layoutTypes:undefined,modes:undefined,optionalFields:Account.AccountSource,Account.AnnualRevenue,Account.BillingAddress,Account.BillingCity,Account.BillingCountry,Account.BillingGeocodeAccuracy,Account.BillingLatitude,Account.BillingLongitude,Account.BillingPostalCode,Account.BillingState,Account.BillingStreet,Account.ChannelProgramLevelName,Account.ChannelProgramName,Account.CreatedById,Account.CreatedDate,Account.Description,Account.Fax,Account.Id,Account.Industry,Account.IsCustomerPortal,Account.IsDeleted,Account.IsLocked,Account.IsPartner,Account.Jigsaw,Account.JigsawCompanyId,Account.LastActivityDate,Account.LastModifiedById,Account.LastModifiedDate,Account.LastReferencedDate,Account.LastViewedDate,Account.MasterRecordId,Account.MayEdit,Account.Name,Account.NumberOfEmployees,Account.OperatingHoursId,Account.OwnerId,Account.ParentId,Account.Phone,Account.PhotoUrl\')"
        val start = System.nanoTime()
        q.replace(Regex(regexp), "json_extract(\$1.soup")
        return (System.nanoTime() - start) / 1000000.0
    }

    private fun loadData() {
        // Employees
        createEmployee("Christine", "Haas", "A00", "00010", null, 200000, true)
        createEmployee("Michael", "Thompson", "A00", "00020", "00010", 120000, false)
        createEmployee("Sally", "Kwan", "A00", "00310", "00010", 100000, false)
        createEmployee("John", "Geyer", "B00", "00040", null, 102000, true)
        createEmployee("Irving", "Stern", "B00", "00050", "00040", 100000, true)
        createEmployee("Eva", "Pulaski", "B00", "00060", "00050", 80000, false)
        createEmployee("Eileen", "Henderson", "B00", "00070", "00050", 70000, false)

        // Departments
        createDepartment("A00", "Sales", 1000000)
        createDepartment("B00", "R&D", 2000000)
    }

    private fun createEmployee(firstName: String, lastName: String, deptCode: String, employeeId: String, managerId: String?, salary: Int, isManager: Boolean) {
        val employee = JSONObject()
        employee.put(FIRST_NAME, firstName)
        employee.put(LAST_NAME, lastName)
        employee.put(DEPT_CODE, deptCode)
        employee.put(EMPLOYEE_ID, employeeId)
        employee.put(MANAGER_ID, managerId)
        employee.put(SALARY, salary)
        employee.put(IS_MANAGER, isManager)
        store.create(EMPLOYEES_SOUP, employee)
    }

    private fun createEmployeeWithJsonString(json: String): JSONObject {
        val employee = JSONObject(json)
        return store.create(EMPLOYEES_SOUP, employee)!!
    }

    private fun createDepartment(deptCode: String, name: String, budget: Int) {
        val department = JSONObject()
        department.put(DEPT_CODE, deptCode)
        department.put(NAME, name)
        department.put(BUDGET, budget)
        store.create(DEPARTMENTS_SOUP, department)
    }
}
