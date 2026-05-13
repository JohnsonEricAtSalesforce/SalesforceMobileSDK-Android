/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.target

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.JSONTestHelper
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import java.util.Arrays
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for CollectionSyncUpTarget.
 * Running all the same tests as SyncUpTargetTest but using a CollectionSyncUpTarget
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class CollectionSyncUpTargetTest : SyncUpTargetTest() {

    @Throws(JSONException::class)
    override fun trySyncUp(
        numberChanges: Int, options: SyncOptions, createFieldlist: List<String>?,
        updateFieldlist: List<String>?, externalIdFieldName: String?
    ) {
        trySyncUp(
            CollectionSyncUpTarget(
                createFieldlist, updateFieldlist, null, null,
                externalIdFieldName
            ), numberChanges, options, false
        )
    }

    @Test
    fun testMaxBatchSizeExceedingLimit() {
        val target = CollectionSyncUpTarget(null, null, 201)

        Assert.assertTrue("Max batch size should be 200", 200 == target.maxBatchSize)
    }

    @Test
    @Throws(Exception::class)
    fun testMaxBatchSizeExceedingLimitInJSON() {
        val targetJson = JSONObject()
        targetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name)
        targetJson.put(BatchSyncUpTarget.MAX_BATCH_SIZE, 201)

        val target = CollectionSyncUpTarget(targetJson)

        Assert.assertTrue("Max batch size should be 200", 200 == target.maxBatchSize)
    }


    @Test
    fun testConstructors() {
        val createdFieldArr = arrayOf(Constants.NAME)
        val updatedFieldArr = arrayOf(Constants.NAME, Constants.DESCRIPTION)
        val maxBatchSize = 150

        var target = CollectionSyncUpTarget()
        Assert.assertNull("Wrong createFieldList", target.createFieldlist)
        Assert.assertNull("Wrong updateFieldList", target.updateFieldlist)
        Assert.assertEquals("Wrong maxBatchSize", 200, target.maxBatchSize)

        target = CollectionSyncUpTarget(
            Arrays.asList(*createdFieldArr),
            Arrays.asList(*updatedFieldArr)
        )
        Assert.assertArrayEquals(
            "Wrong createFieldList", createdFieldArr,
            target.createFieldlist!!.toTypedArray()
        )
        Assert.assertArrayEquals(
            "Wrong updateFieldList", updatedFieldArr,
            target.updateFieldlist!!.toTypedArray()
        )
        Assert.assertEquals("Wrong maxBatchSize", 200, target.maxBatchSize)

        target = CollectionSyncUpTarget(
            Arrays.asList(*createdFieldArr),
            Arrays.asList(*updatedFieldArr), maxBatchSize
        )
        Assert.assertArrayEquals(
            "Wrong createFieldList", createdFieldArr,
            target.createFieldlist!!.toTypedArray()
        )
        Assert.assertArrayEquals(
            "Wrong updateFieldList", updatedFieldArr,
            target.updateFieldlist!!.toTypedArray()
        )
        Assert.assertEquals("Wrong maxBatchSize", maxBatchSize, target.maxBatchSize)
    }


    @Test
    @Throws(Exception::class)
    fun testConstructorWithJSON() {
        val createdFieldArr = arrayOf(Constants.NAME)
        val updatedFieldArr = arrayOf(Constants.NAME, Constants.DESCRIPTION)
        val maxBatchSize = 12

        val targetJson = JSONObject()
        targetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name)
        targetJson.put(SyncUpTarget.CREATE_FIELDLIST, JSONArray(createdFieldArr))
        targetJson.put(SyncUpTarget.UPDATE_FIELDLIST, JSONArray(updatedFieldArr))
        targetJson.put(BatchSyncUpTarget.MAX_BATCH_SIZE, maxBatchSize)

        val target = CollectionSyncUpTarget(targetJson)

        Assert.assertArrayEquals(
            "Wrong createFieldList", createdFieldArr,
            target.createFieldlist!!.toTypedArray()
        )
        Assert.assertArrayEquals(
            "Wrong updateFieldList", updatedFieldArr,
            target.updateFieldlist!!.toTypedArray()
        )
        Assert.assertEquals("Wrong maxBatchSize", maxBatchSize, target.maxBatchSize)
    }


    @Test
    @Throws(Exception::class)
    fun testConstructorWithJSONWithoutOptionalFields() {
        val targetJson = JSONObject()
        targetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name)

        val target = CollectionSyncUpTarget(targetJson)

        Assert.assertNull("Wrong createFieldList", target.createFieldlist)
        Assert.assertNull("Wrong updateFieldList", target.updateFieldlist)
        Assert.assertEquals(
            "Wrong maxBatchSize",
            CollectionSyncUpTarget.MAX_RECORDS_SOBJECT_COLLECTION_API, target.maxBatchSize
        )
    }


    @Test
    @Throws(Exception::class)
    fun testFromJSON() {
        val maxBatchSize = 12

        val targetJson = JSONObject()
        targetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name)
        targetJson.put(BatchSyncUpTarget.MAX_BATCH_SIZE, maxBatchSize)

        val target = SyncUpTarget.fromJSON(targetJson)

        Assert.assertTrue(target is CollectionSyncUpTarget)
        Assert.assertEquals(
            "Wrong maxBatchSize", maxBatchSize,
            (target as CollectionSyncUpTarget).maxBatchSize
        )
    }

    @Test
    @Throws(Exception::class)
    fun testToJSON() {
        val createdFieldArr = arrayOf(Constants.NAME)
        val updatedFieldArr = arrayOf(Constants.NAME, Constants.DESCRIPTION)
        val maxBatchSize = 150

        val target = CollectionSyncUpTarget(
            Arrays.asList(*createdFieldArr),
            Arrays.asList(*updatedFieldArr), maxBatchSize
        )

        val expectedTargetJson = JSONObject()
        expectedTargetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name)
        expectedTargetJson.put(SyncTarget.ID_FIELD_NAME, Constants.ID)
        expectedTargetJson
            .put(SyncTarget.MODIFICATION_DATE_FIELD_NAME, Constants.LAST_MODIFIED_DATE)
        expectedTargetJson.put(SyncUpTarget.CREATE_FIELDLIST, JSONArray(createdFieldArr))
        expectedTargetJson.put(SyncUpTarget.UPDATE_FIELDLIST, JSONArray(updatedFieldArr))
        expectedTargetJson.put(BatchSyncUpTarget.MAX_BATCH_SIZE, maxBatchSize)

        JSONTestHelper.assertSameJSON("Wrong json", expectedTargetJson, target.asJSON())
    }

    @Test
    @Throws(Exception::class)
    fun testCollectionSyncUpTargetIsDefault() {
        val targetJson = JSONObject()

        val target = SyncUpTarget.fromJSON(targetJson)

        Assert.assertTrue(target is CollectionSyncUpTarget)
        Assert.assertNull("Wrong createFieldList", target.createFieldlist)
        Assert.assertNull("Wrong updateFieldList", target.updateFieldlist)
        Assert.assertEquals(
            "Wrong maxBatchSize",
            CollectionSyncUpTarget.MAX_RECORDS_SOBJECT_COLLECTION_API,
            (target as CollectionSyncUpTarget).maxBatchSize
        )
    }
}
