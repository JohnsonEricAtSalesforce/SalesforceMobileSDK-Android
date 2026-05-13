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
package com.salesforce.androidsdk.analytics

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import androidx.annotation.NonNull
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager.SalesforceAnalyticsPublishingType.PublishOnAppBackground
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager.SalesforceAnalyticsPublishingType.PublishPeriodically
import com.salesforce.androidsdk.analytics.manager.AnalyticsManager
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent
import com.salesforce.androidsdk.analytics.store.EventStoreManager
import com.salesforce.androidsdk.analytics.transform.AILTNTransform
import com.salesforce.androidsdk.analytics.transform.Transform
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.AdminSettingsManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * This class contains APIs that can be used to interact with
 * the SalesforceAnalytics library.
 *
 * @author bhariharan
 */
class SalesforceAnalyticsManager private constructor(private val account: UserAccount?) {

    val analyticsManager: AnalyticsManager
    val eventStoreManager: EventStoreManager
    private var enabled = false
    private val remotes: MutableMap<Class<out Transform>, Class<out AnalyticsPublisher>>

    init {
        val sdkManager = SalesforceSDKManager.getInstance()
        val deviceAppAttributes = getDeviceAppAttributes()
        val filenameSuffix = if (account != null) account.getCommunityLevelFilenameSuffix() else UNAUTH_INSTANCE_KEY
        analyticsManager = AnalyticsManager(
            filenameSuffix, sdkManager.appContext,
            SalesforceSDKManager.encryptionKey, deviceAppAttributes
        )
        eventStoreManager = analyticsManager.eventStoreManager
        remotes = HashMap()
        remotes[AILTNTransform::class.java] = AILTNPublisher::class.java

        // Reads the existing analytics policy and sets it upon initialization.
        readAnalyticsPolicy()
        enableLogging(enabled)
    }

    /**
     * Disables or enables logging of events. If logging is disabled, no events
     * will be stored. However, publishing of events is still possible.
     *
     * @param enabled True - if logging should be enabled, False - otherwise.
     */
    fun enableLogging(enabled: Boolean) {
        if (enabled) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_AILTN_ENABLED)
        } else {
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(Features.FEATURE_AILTN_ENABLED)
        }
        storeAnalyticsPolicy(enabled)
        eventStoreManager.enableLogging(enabled)
    }

    /**
     * Updates the preferences of this library.
     */
    fun updateLoggingPrefs() {
        val settingsManager = AdminSettingsManager()
        val enabled = settingsManager.getPref(ANALYTICS_ON_OFF_KEY, account)
        if (!TextUtils.isEmpty(enabled)) {
            if (!enabled.toBoolean()) {
                enableLogging(false)
            } else {
                enableLogging(true)
            }
        }
    }

    /**
     * Returns whether logging is enabled or disabled.
     *
     * @return True - if logging is enabled, False - otherwise.
     */
    fun isLoggingEnabled(): Boolean {
        return enabled
    }

    /**
     * Publishes all stored events to all registered network endpoints after
     * applying the required event format transforms. Stored events will be
     * deleted if publishing was successful for all registered endpoints.
     * This method should NOT be called from the main thread.
     */
    @Synchronized
    fun publishAllEvents() {
        val events: Iterable<InstrumentationEvent> = eventStoreManager.iterateAllEvents().filterNotNull()
        publishEvents(events)
    }

    /**
     * Publishes a list of events to all registered network endpoints after
     * applying the required event format transforms. Stored events will be
     * deleted if publishing was successful for all registered endpoints.
     * This method should NOT be called from the main thread.
     *
     * @param events Iterable of events.
     */
    @Synchronized
    fun publishEvents(events: Iterable<InstrumentationEvent>?) {
        if (events == null) {
            return
        }

        val eventsIds = HashSet<String>()
        var success = true
        val remoteKeySet = remotes.entries
        for (remoteEntry in remoteKeySet) {
            val transformer: Transform
            try {
                transformer = remoteEntry.key.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while instantiating class", e)
                continue
            }

            val networkPublisher: AnalyticsPublisher
            try {
                networkPublisher = remoteEntry.value.getDeclaredConstructor().newInstance()
            } catch (e: Exception) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while instantiating class", e)
                continue
            }

            var eventCount = 0
            var eventsJSONArray = JSONArray()
            for (event in events) {
                if (event == null) {
                    continue
                }
                eventsIds.add(event.eventId ?: "")
                val eventJSON = transformer.transform(event)
                if (eventJSON == null) {
                    continue
                }
                eventsJSONArray.put(eventJSON)

                // Publish a batch if we've reached the batch size
                eventCount++
                if (eventCount >= sEventPublishBatchSize) {
                    eventCount = 0

                    val batchSuccess = networkPublisher.publish(eventsJSONArray)
                    eventsJSONArray = JSONArray()
                    success = success and batchSuccess
                    if (!batchSuccess) {
                        // Don't bother trying this publisher after the first failure
                        break
                    }
                }
            }

            // Publish events that didn't get batched
            if (eventCount > 0) {
                success = success and networkPublisher.publish(eventsJSONArray)
            }
        }

        /*
         * Deletes events from the event store if the network publishing was successful.
         */
        if (success) {
            eventStoreManager.deleteEvents(eventsIds)
        }
    }

    /**
     * Publishes an event to all registered network endpoints after
     * applying the required event format transforms. Stored event will be
     * deleted if publishing was successful for all registered endpoints.
     * This method should NOT be called from the main thread.
     *
     * @param event Event.
     */
    @Synchronized
    fun publishEvent(event: InstrumentationEvent?) {
        if (event == null) {
            return
        }
        val events = mutableListOf<InstrumentationEvent>()
        events.add(event)
        publishEvents(events)
    }

    /**
     * Adds a remote publisher to publish events to.
     *
     * @param transformer Transformer class.
     * @param publisher   Publisher class.
     */
    fun addRemotePublisher(
        transformer: Class<out Transform>?,
        publisher: Class<out AnalyticsPublisher>?
    ) {
        if (transformer == null || publisher == null) {
            SalesforceSDKLogger.w(TAG, "Invalid transformer and/or publisher")
            return
        }
        remotes[transformer] = publisher
    }

    /**
     * Removes a remote publisher to publish events to.
     *
     * @param transformer Transformer class.
     */
    internal fun removeRemotePublisher(transformer: Class<out Transform>?) {
        if (transformer == null) {
            SalesforceSDKLogger.w(TAG, "Invalid transformer")
            return
        }
        remotes.remove(transformer)
    }

    @Synchronized
    private fun storeAnalyticsPolicy(enabled: Boolean) {
        val context = SalesforceSDKManager.getInstance().appContext
        val filenameSuffix = if (account != null) account.getUserLevelFilenameSuffix() else UNAUTH_INSTANCE_KEY
        val filename = AILTN_POLICY_PREF + filenameSuffix
        val sp = context.getSharedPreferences(filename, Context.MODE_PRIVATE)
        val e = sp.edit()
        e.putBoolean(ANALYTICS_ON_OFF_KEY, enabled)
        e.commit()
        this.enabled = enabled
    }

    private fun readAnalyticsPolicy() {
        val context = SalesforceSDKManager.getInstance().appContext
        val filenameSuffix = if (account != null) account.getUserLevelFilenameSuffix() else UNAUTH_INSTANCE_KEY
        val filename = AILTN_POLICY_PREF + filenameSuffix
        val sp = context.getSharedPreferences(filename, Context.MODE_PRIVATE)
        if (!sp.contains(ANALYTICS_ON_OFF_KEY)) {
            storeAnalyticsPolicy(true)
        }
        enabled = sp.getBoolean(ANALYTICS_ON_OFF_KEY, true)
    }

    private fun resetAnalyticsPolicy() {
        val context = SalesforceSDKManager.getInstance().appContext
        val filenameSuffix = if (account != null) account.getUserLevelFilenameSuffix() else UNAUTH_INSTANCE_KEY
        val filename = AILTN_POLICY_PREF + filenameSuffix
        val sp = context.getSharedPreferences(filename, Context.MODE_PRIVATE)
        val e = sp.edit()
        e.clear()
        e.commit()
    }

    /**
     * The available Salesforce analytics publishing types.
     */
    enum class SalesforceAnalyticsPublishingType {
        /**
         * Specifies analytics should not be published
         */
        PublishDisabled,

        /**
         * Specifies analytics publishing should occur one time when the app is sent to the
         * background
         */
        PublishOnAppBackground,

        /**
         * Specifies analytics publishing should occur periodically as a Android Background Task
         * according to the frequency
         *
         * @see setPublishPeriodicallyFrequencyHours
         */
        PublishPeriodically
    }

    companion object {
        private const val ANALYTICS_ON_OFF_KEY = "ailtn_enabled"
        private const val AILTN_POLICY_PREF = "ailtn_policy"
        private const val DEFAULT_PUBLISH_FREQUENCY_IN_HOURS = 8
        private const val DEFAULT_BATCH_SIZE = 100
        private const val TAG = "AnalyticsManager"
        private const val UNAUTH_INSTANCE_KEY = "_no_user"

        private var INSTANCES: MutableMap<String, SalesforceAnalyticsManager>? = null
        private var isPublishWorkRequestEnqueued = false

        /** The enabled Salesforce analytics publishing type */
        @NonNull
        private var analyticsPublishingType: SalesforceAnalyticsPublishingType = PublishOnAppBackground

        private var publishPeriodicallyFrequencyHours = DEFAULT_PUBLISH_FREQUENCY_IN_HOURS
        private var sEventPublishBatchSize = DEFAULT_BATCH_SIZE

        /**
         * Returns the instance of this class associated with an unauthenticated user context.
         *
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getUnauthenticatedInstance(): SalesforceAnalyticsManager {
            return getInstance(null)
        }

        /**
         * Returns the instance of this class associated with this user account.
         *
         * @param account User account.
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(account: UserAccount?): SalesforceAnalyticsManager {
            return getInstance(account, null)
        }

        /**
         * Returns the instance of this class associated with this user and community.
         *
         * @param account     User account.
         * @param communityId Community ID.
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(account: UserAccount?, communityId: String?): SalesforceAnalyticsManager {
            var communityId = communityId
            var uniqueId = UNAUTH_INSTANCE_KEY
            if (account != null) {
                uniqueId = account.userId ?: UNAUTH_INSTANCE_KEY
                if (UserAccount.INTERNAL_COMMUNITY_ID == communityId) {
                    communityId = null
                }
                if (!TextUtils.isEmpty(communityId)) {
                    uniqueId = uniqueId + communityId
                }
            }
            val instance: SalesforceAnalyticsManager
            if (INSTANCES == null) {
                INSTANCES = HashMap()
                instance = SalesforceAnalyticsManager(account)
                INSTANCES!![uniqueId] = instance
            } else {
                var inst = INSTANCES!![uniqueId]
                if (inst == null) {
                    inst = SalesforceAnalyticsManager(account)
                    INSTANCES!![uniqueId] = inst
                }
                instance = inst
            }

            // Adds a handler for publishing if not already active.
            if (!isPublishWorkRequestEnqueued) {
                recreateAnalyticsPeriodicBackgroundPublishingWorkRequest()
                isPublishWorkRequestEnqueued = true
            }
            return instance
        }

        /**
         * Resets the instance of this class associated with an unauthenticated user context.
         */
        @JvmStatic
        @Synchronized
        fun resetUnauthenticatedInstance() {
            reset(null)
        }

        /**
         * Resets the instance of this class associated with this user account.
         *
         * @param account User account.
         */
        @JvmStatic
        @Synchronized
        fun reset(account: UserAccount?) {
            reset(account, null)
        }

        /**
         * Resets the instance of this class associated with this user and community.
         *
         * @param account     User account.
         * @param communityId Community ID.
         */
        @JvmStatic
        @Synchronized
        fun reset(account: UserAccount?, communityId: String?) {
            var communityId = communityId
            var uniqueId = UNAUTH_INSTANCE_KEY
            if (account != null) {
                uniqueId = account.userId ?: UNAUTH_INSTANCE_KEY
                if (UserAccount.INTERNAL_COMMUNITY_ID == communityId) {
                    communityId = null
                }
                if (!TextUtils.isEmpty(communityId)) {
                    uniqueId = uniqueId + communityId
                }
            }
            if (INSTANCES != null) {
                val manager = INSTANCES!![uniqueId]
                if (manager != null) {
                    manager.analyticsManager.reset()
                    manager.resetAnalyticsPolicy()
                }
                INSTANCES!!.remove(uniqueId)
            }
        }

        /**
         * Sets the interval for periodic background publishing in hours.
         *
         * @param periodicBackgroundPublishingHoursInterval The interval for
         *                                                  periodic background
         *                                                  publishing in hours. It
         *                                                  is recommended to keep
         *                                                  this value under seven
         *                                                  days
         * @see setAnalyticsPublishingType
         */
        @JvmStatic
        @Synchronized
        fun setPublishPeriodicallyFrequencyHours(
            periodicBackgroundPublishingHoursInterval: Int
        ) {
            publishPeriodicallyFrequencyHours = periodicBackgroundPublishingHoursInterval
            setAnalyticsPublishingType(PublishPeriodically)
        }

        /**
         * The enabled Salesforce analytics publishing type.
         *
         * @return The enabled Salesforce analytics publishing type
         */
        @JvmStatic
        @NonNull
        fun analyticsPublishingType(): SalesforceAnalyticsPublishingType {
            return analyticsPublishingType
        }

        /**
         * Sets the enabled Salesforce analytics publishing type.
         *
         * @param value The Salesforce analytics publishing type
         */
        @JvmStatic
        fun setAnalyticsPublishingType(@NonNull value: SalesforceAnalyticsPublishingType) {
            analyticsPublishingType = value
        }

        /**
         * Set the batch size for publishing instrumentation events. Will limit the
         * number of events sent in a single network request to the specified batch
         * size. Will silently return if batch size is less than or equal to zero.
         *
         * @param batchSize Event batch size
         */
        @JvmStatic
        @Synchronized
        fun setEventPublishBatchSize(batchSize: Int) {
            if (batchSize <= 0) {
                return
            }
            sEventPublishBatchSize = batchSize
        }

        /**
         * Returns the publish frequency currently set, in hours.
         *
         * @return Publish frequency, in hours.
         * @noinspection unused
         */
        @JvmStatic
        fun getPublishPeriodicallyFrequencyHours(): Int {
            return publishPeriodicallyFrequencyHours
        }

        /**
         * Returns the device app attributes associated with this device.
         *
         * @return Device app attributes.
         */
        @JvmStatic
        fun getDeviceAppAttributes(): DeviceAppAttributes {
            val sdkManager = SalesforceSDKManager.getInstance()
            val context = sdkManager.appContext
            val osVersion = Build.VERSION.RELEASE
            val osName = "android"
            val appType = sdkManager.appType
            val mobileSdkVersion = SalesforceSDKManager.SDK_VERSION
            val deviceModel = Build.MODEL
            val deviceId = sdkManager.deviceId
            val clientId = BootConfig.getBootConfig(context).getRemoteAccessConsumerKey() ?: ""
            val ailtnAppName = SalesforceSDKManager.ailtnAppName ?: ""
            return DeviceAppAttributes(
                sdkManager.appVersion,
                ailtnAppName, osVersion, osName, appType,
                mobileSdkVersion, deviceModel, deviceId, clientId
            )
        }

        private fun recreateAnalyticsPeriodicBackgroundPublishingWorkRequest() {
            AnalyticsPublishingWorker.enqueueAnalyticsPublishWorkRequest(
                SalesforceSDKManager.getInstance().appContext,
                publishPeriodicallyFrequencyHours.toLong()
            )
        }
    }
}
