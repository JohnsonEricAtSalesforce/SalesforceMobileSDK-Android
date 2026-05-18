package com.salesforce.androidsdk.mobilesync.manager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.salesforce.androidsdk.mobilesync.model.Layout
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class) @MediumTest
class LayoutSyncManagerTest : ManagerTestCase() {
    private lateinit var layoutSyncManager: LayoutSyncManager
    private lateinit var layoutSyncCallbackQueue: LayoutSyncCallbackQueue

    private class LayoutSyncCallbackQueue : LayoutSyncManager.LayoutSyncCallback {
        data class Result(val objectAPIName: String, val formFactor: String, val layoutType: String, val mode: String, val recordTypeId: String?, val layout: Layout)
        private val results = ArrayBlockingQueue<Result>(1)
        override fun onSyncComplete(objectAPIName: String?, formFactor: String?, layoutType: String?, mode: String?, recordTypeId: String?, layout: Layout?) { if (objectAPIName != null) { results.offer(Result(objectAPIName, formFactor!!, layoutType!!, mode!!, recordTypeId, layout!!)) } }
        fun clearQueue() { results.clear() }
        fun getResult(): Result { val result = results.poll(30, TimeUnit.SECONDS); if (result == null) throw RuntimeException("Timed out waiting for callback"); return result }
    }

    @Before override fun setUp() { super.setUp(); layoutSyncManager = LayoutSyncManager.getInstance(); layoutSyncCallbackQueue = LayoutSyncCallbackQueue() }
    @After override fun tearDown() { SyncManager.reset(); layoutSyncManager.smartStore.dropAllSoups(); LayoutSyncManager.reset(); layoutSyncCallbackQueue.clearQueue(); super.tearDown() }

    @Test fun testFetchLayoutInCacheOnlyMode() { layoutSyncManager.fetchLayout(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null, Constants.Mode.SERVER_FIRST, layoutSyncCallbackQueue); layoutSyncCallbackQueue.getResult(); layoutSyncCallbackQueue.clearQueue(); layoutSyncManager.fetchLayout(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null, Constants.Mode.CACHE_ONLY, layoutSyncCallbackQueue); validateResult(layoutSyncCallbackQueue.getResult()) }
    @Test fun testFetchLayoutInCacheFirstModeWithCacheData() { layoutSyncManager.fetchLayout(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null, Constants.Mode.SERVER_FIRST, layoutSyncCallbackQueue); layoutSyncCallbackQueue.getResult(); layoutSyncCallbackQueue.clearQueue(); layoutSyncManager.fetchLayout(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null, Constants.Mode.CACHE_FIRST, layoutSyncCallbackQueue); validateResult(layoutSyncCallbackQueue.getResult()) }
    @Test fun testFetchLayoutInCacheFirstModeWithoutCacheData() { layoutSyncManager.fetchLayout(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null, Constants.Mode.CACHE_FIRST, layoutSyncCallbackQueue); validateResult(layoutSyncCallbackQueue.getResult()) }
    @Test fun testFetchLayoutInServerFirstMode() { layoutSyncManager.fetchLayout(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null, Constants.Mode.SERVER_FIRST, layoutSyncCallbackQueue); validateResult(layoutSyncCallbackQueue.getResult()) }
    @Test fun testFetchLayoutMultipleTimes() { layoutSyncManager.fetchLayout(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null, Constants.Mode.SERVER_FIRST, layoutSyncCallbackQueue); validateResult(layoutSyncCallbackQueue.getResult()); layoutSyncManager.fetchLayout(Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null, Constants.Mode.SERVER_FIRST, layoutSyncCallbackQueue); validateResult(layoutSyncCallbackQueue.getResult()); val querySpec = QuerySpec.buildSmartQuerySpec(String.format(LayoutSyncManager.QUERY, Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM, Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null), 2); Assert.assertEquals("Number of rows should be 1", 1, layoutSyncManager.smartStore.countQuery(querySpec)) }
    private fun validateResult(result: LayoutSyncCallbackQueue.Result) { Assert.assertEquals("Object types should match", Constants.ACCOUNT, result.objectAPIName); Assert.assertNotNull("Layout data should not be null", result.layout); Assert.assertEquals("Form factors should match", Constants.FORM_FACTOR_MEDIUM, result.formFactor); Assert.assertEquals("Layout types should match", Constants.LAYOUT_TYPE_COMPACT, result.layout.layoutType); Assert.assertEquals("Modes should match", Constants.MODE_EDIT, result.mode); Assert.assertNotNull("Layout raw data should not be null", result.layout.rawData); Assert.assertNotNull("Layout sections should not be null", result.layout.sections); Assert.assertTrue("Number of layout sections should be 1 or more", result.layout.sections.size > 0) }
}
