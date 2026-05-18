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
package com.salesforce.androidsdk.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for JSONObjectHelper
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class JSONObjectHelperTest {

    @Test
    fun testOptForJSONObject() {
        val obj = JSONObject("{'a': null, 'b': 'B'}")
        Assert.assertSame("Should be JSONObject.NULL", JSONObject.NULL, obj.opt("a"))
        Assert.assertNull("Should be null", JSONObjectHelper.opt(obj, "a"))
        Assert.assertEquals("Should be \"B\"", "B", obj.opt("b"))
        Assert.assertEquals("Should be \"B\"", "B", JSONObjectHelper.opt(obj, "b"))
    }

    @Test
    fun testOptForJSONArray() {
        val arr = JSONArray("[null, 'B']")
        Assert.assertSame("Should be JSONObject.NULL", JSONObject.NULL, arr.opt(0))
        Assert.assertNull("Should be null", JSONObjectHelper.opt(arr, 0))
        Assert.assertEquals("Should be \"B\"", "B", arr.opt(1))
        Assert.assertEquals("Should be \"B\"", "B", JSONObjectHelper.opt(arr, 1))
    }

    @Test
    fun testOptString() {
        val obj = JSONObject("{'a': null, 'b': 'B'}")
        Assert.assertEquals("Should be \"null\"", "null", obj.optString("a"))
        Assert.assertNull("Should be null", JSONObjectHelper.optString(obj, "a"))
        Assert.assertEquals("Should be \"B\"", "B", obj.optString("b"))
        Assert.assertEquals("Should be \"B\"", "B", JSONObjectHelper.optString(obj, "b"))
    }

    @Test
    fun testOptStringArray() {
        val obj = JSONObject("{'a': null, 'b': ['B1', 'B2']}")
        Assert.assertNull("Should be null", JSONObjectHelper.optStringArray(obj, "a"))
        JSONTestHelper.assertSameJSONArray("Should be ['B1', 'B2']", JSONArray("['B1', 'B2']"), obj.optJSONArray("b")!!)
        val stringArray = JSONObjectHelper.optStringArray(obj, "b")
        Assert.assertNotNull(stringArray)
        Assert.assertEquals("Should be 2", 2, stringArray!!.size)
        Assert.assertEquals("Should 'B1'", "B1", stringArray[0])
        Assert.assertEquals("Should 'B2'", "B2", stringArray[1])
    }

    @Test
    fun testToList() {
        val arr = JSONArray("['a','b','c']")
        val list = JSONObjectHelper.toList<String>(arr)!!
        Assert.assertEquals("Should be 3", 3, list.size)
        Assert.assertEquals("Should be 'a'", "a", list[0])
        Assert.assertEquals("Should be 'b'", "b", list[1])
        Assert.assertEquals("Should be 'c'", "c", list[2])
    }

    @Test
    fun testPluck() {
        val arr = JSONArray("[{'a':1}, {'a':2}, {'a':3}, {'a':4}]")
        val plucked = JSONObjectHelper.pluck<Int>(arr, "a")
        Assert.assertEquals("Should be 4", 4, plucked.size)
        Assert.assertTrue("Should be 1", 1 == plucked[0])
        Assert.assertTrue("Should be 2", 2 == plucked[1])
        Assert.assertTrue("Should be 3", 3 == plucked[2])
        Assert.assertTrue("Should be 4", 4 == plucked[3])
    }
}
