package com.salesforce.androidsdk.mobilesync.target

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.JSONTestHelper
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class CollectionSyncUpTargetTest : SyncUpTargetTest() {
    override fun trySyncUp(numberChanges: Int, options: SyncOptions, createFieldlist: List<String>?, updateFieldlist: List<String>?, externalIdFieldName: String?) {
        trySyncUp(CollectionSyncUpTarget(createFieldlist, updateFieldlist, null, null, externalIdFieldName), numberChanges, options, false)
    }

    @Test fun testMaxBatchSizeExceedingLimit() { val target = CollectionSyncUpTarget(null, null, 201); Assert.assertTrue("Max batch size should be 200", 200 == target.maxBatchSize) }
    @Test fun testMaxBatchSizeExceedingLimitInJSON() { val targetJson = JSONObject(); targetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name); targetJson.put(BatchSyncUpTarget.MAX_BATCH_SIZE, 201); val target = CollectionSyncUpTarget(targetJson); Assert.assertTrue("Max batch size should be 200", 200 == target.maxBatchSize) }
    @Test fun testConstructors() { val createdFieldArr = arrayOf(Constants.NAME); val updatedFieldArr = arrayOf(Constants.NAME, Constants.DESCRIPTION); var target = CollectionSyncUpTarget(); Assert.assertNull("Wrong createFieldList", target.createFieldlist); Assert.assertNull("Wrong updateFieldList", target.updateFieldlist); Assert.assertEquals("Wrong maxBatchSize", 200, target.maxBatchSize); target = CollectionSyncUpTarget(listOf(*createdFieldArr), listOf(*updatedFieldArr)); Assert.assertArrayEquals("Wrong createFieldList", createdFieldArr, target.createFieldlist!!.toTypedArray()); Assert.assertArrayEquals("Wrong updateFieldList", updatedFieldArr, target.updateFieldlist!!.toTypedArray()); Assert.assertEquals("Wrong maxBatchSize", 200, target.maxBatchSize); target = CollectionSyncUpTarget(listOf(*createdFieldArr), listOf(*updatedFieldArr), 150); Assert.assertArrayEquals("Wrong createFieldList", createdFieldArr, target.createFieldlist!!.toTypedArray()); Assert.assertArrayEquals("Wrong updateFieldList", updatedFieldArr, target.updateFieldlist!!.toTypedArray()); Assert.assertEquals("Wrong maxBatchSize", 150, target.maxBatchSize) }
    @Test fun testConstructorWithJSON() { val createdFieldArr = arrayOf(Constants.NAME); val updatedFieldArr = arrayOf(Constants.NAME, Constants.DESCRIPTION); val targetJson = JSONObject(); targetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name); targetJson.put(SyncUpTarget.CREATE_FIELDLIST, JSONArray(createdFieldArr)); targetJson.put(SyncUpTarget.UPDATE_FIELDLIST, JSONArray(updatedFieldArr)); targetJson.put(BatchSyncUpTarget.MAX_BATCH_SIZE, 12); val target = CollectionSyncUpTarget(targetJson); Assert.assertArrayEquals("Wrong createFieldList", createdFieldArr, target.createFieldlist!!.toTypedArray()); Assert.assertArrayEquals("Wrong updateFieldList", updatedFieldArr, target.updateFieldlist!!.toTypedArray()); Assert.assertEquals("Wrong maxBatchSize", 12, target.maxBatchSize) }
    @Test fun testConstructorWithJSONWithoutOptionalFields() { val targetJson = JSONObject(); targetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name); val target = CollectionSyncUpTarget(targetJson); Assert.assertNull("Wrong createFieldList", target.createFieldlist); Assert.assertNull("Wrong updateFieldList", target.updateFieldlist); Assert.assertEquals("Wrong maxBatchSize", CollectionSyncUpTarget.MAX_RECORDS_SOBJECT_COLLECTION_API, target.maxBatchSize) }
    @Test fun testFromJSON() { val targetJson = JSONObject(); targetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name); targetJson.put(BatchSyncUpTarget.MAX_BATCH_SIZE, 12); val target = SyncUpTarget.fromJSON(targetJson); Assert.assertTrue(target is CollectionSyncUpTarget); Assert.assertEquals("Wrong maxBatchSize", 12, (target as CollectionSyncUpTarget).maxBatchSize) }
    @Test fun testToJSON() { val createdFieldArr = arrayOf(Constants.NAME); val updatedFieldArr = arrayOf(Constants.NAME, Constants.DESCRIPTION); val target = CollectionSyncUpTarget(listOf(*createdFieldArr), listOf(*updatedFieldArr), 150); val expectedTargetJson = JSONObject(); expectedTargetJson.put(SyncTarget.ANDROID_IMPL, CollectionSyncUpTarget::class.java.name); expectedTargetJson.put(SyncTarget.ID_FIELD_NAME, Constants.ID); expectedTargetJson.put(SyncTarget.MODIFICATION_DATE_FIELD_NAME, Constants.LAST_MODIFIED_DATE); expectedTargetJson.put(SyncUpTarget.CREATE_FIELDLIST, JSONArray(createdFieldArr)); expectedTargetJson.put(SyncUpTarget.UPDATE_FIELDLIST, JSONArray(updatedFieldArr)); expectedTargetJson.put(BatchSyncUpTarget.MAX_BATCH_SIZE, 150); JSONTestHelper.assertSameJSON("Wrong json", expectedTargetJson, target.asJSON()) }
    @Test fun testCollectionSyncUpTargetIsDefault() { val targetJson = JSONObject(); val target = SyncUpTarget.fromJSON(targetJson); Assert.assertTrue(target is CollectionSyncUpTarget); Assert.assertNull("Wrong createFieldList", target.createFieldlist); Assert.assertNull("Wrong updateFieldList", target.updateFieldlist); Assert.assertEquals("Wrong maxBatchSize", CollectionSyncUpTarget.MAX_RECORDS_SOBJECT_COLLECTION_API, (target as CollectionSyncUpTarget).maxBatchSize) }
}
