/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.campaignclassic.internal

import androidx.annotation.VisibleForTesting
import com.adobe.marketing.mobile.CampaignClassic
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.Extension
import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.MobilePrivacyStatus
import com.adobe.marketing.mobile.SharedStateResolution
import com.adobe.marketing.mobile.SharedStateStatus
import com.adobe.marketing.mobile.services.DataStoring
import com.adobe.marketing.mobile.services.DeviceInforming
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.Networking
import com.adobe.marketing.mobile.services.ServiceProvider

/**
 * The Campaign Classic Extension class is responsible for registering users with Adobe Campaign Classic as well as
 * providing tracking methods for push message receive and click through events.
 *
 * The Campaign Classic extension listens for the following [Event]
 * - [EventType.CAMPAIGN], [EventSource.REQUEST_CONTENT]
 * - [EventType.CONFIGURATION], [EventSource.RESPONSE_CONTENT]
 *
 * The Campaign Classic extension dispatches the following [Event]:
 * - [EventType.CAMPAIGN], [EventSource.RESPONSE_CONTENT]
 *
 * The Campaign Classic extension has dependencies on the following services from [ServiceProvider]:
 * - [DataStoring]
 * - [Networking]
 * - [DeviceInforming]
 */
class CampaignClassicExtension : Extension {
    private val extensionApi: ExtensionApi
    private val registrationManager: RegistrationManager
    private val trackRequestManager: TrackRequestManager

    constructor(extensionApi: ExtensionApi) : super(extensionApi) {
        this.extensionApi = extensionApi
        registrationManager = RegistrationManager(api)
        trackRequestManager = TrackRequestManager(api)
    }

    @VisibleForTesting
    internal constructor(
        extensionApi: ExtensionApi,
        registrationManager: RegistrationManager,
        trackRequestManager: TrackRequestManager
    ) : super(extensionApi) {
        this.extensionApi = extensionApi
        this.registrationManager = registrationManager
        this.trackRequestManager = trackRequestManager
    }

    companion object {
        private const val SELF_TAG = "CampaignClassicExtension"
    }

    override fun getName(): String {
        return CampaignClassicConstants.EXTENSION_NAME
    }

    override fun getVersion(): String {
        return CampaignClassic.extensionVersion()
    }

    override fun getFriendlyName(): String {
        return CampaignClassicConstants.FRIENDLY_NAME
    }

    override fun onRegistered() {
        api.registerEventListener(EventType.CAMPAIGN, EventSource.REQUEST_CONTENT) {
            handleCampaignRequestEvent(it)
        }
        api.registerEventListener(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT) {
            handleConfigurationResponseEvent(it)
        }
    }

    override fun readyForEvent(event: Event): Boolean {
        return api.getSharedState(
            CampaignClassicConstants.EventDataKeys.Configuration.EXTENSION_NAME,
            event,
            false,
            SharedStateResolution.ANY
        )?.status == SharedStateStatus.SET
    }

    /**
     * Processes event with type [EventType.CONFIGURATION] and source [EventSource.RESPONSE_CONTENT].
     * <p>
     * If the configuration event data contains {@code global.privacy} key, and the new privacy status
     * is [MobilePrivacyStatus.OPT_OUT], then the persisted identifiers for this extension are cleared.
     *
     * @param event incoming configuration response [Event]
     */
    internal fun handleConfigurationResponseEvent(event: Event) {
        val configData = CampaignClassicConfiguration(event, extensionApi)
        if (configData.privacyStatus == MobilePrivacyStatus.OPT_OUT) {
            // Reset registration info in data store
            Log.debug(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG,
                "handleConfigurationResponseEvent - Privacy status is opt out, clearing persisted registration info."
            )
            registrationManager.clearRegistrationData()
        }
    }

    /**
     * Processes event with type [EventType.CAMPAIGN] and source [EventSource.REQUEST_CONTENT]
     * based on key and value set in current {@code event} event data.
     *
     * @param event incoming [Event]
     */
    internal fun handleCampaignRequestEvent(event: Event) {
        if (event.eventData == null || event.eventData.isEmpty()) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG,
                "handleCampaignRequestEvent - Failed to process CAMPAIGN_CLASSIC REQUEST_CONTENT event" +
                    "(event.eventData was null or empty)"
            )
            return
        }
        if (event.isRegisterEvent) {
            handleRegistrationRequest(event)
        } else if (event.isTrackReceiveEvent) {
            handleTrackRequest(event, CampaignClassicConstants.MESSAGE_RECEIVED_TAGID)
        } else if (event.isTrackClickEvent) {
            handleTrackRequest(event, CampaignClassicConstants.MESSAGE_CLICKED_TAGID)
        }
    }

    /**
     *  Sends device registration request to configured Campaign Classic server.
     *
     *  @param event [Event] initiating the Campaign Classic registration request
     */
    private fun handleRegistrationRequest(event: Event) {
        registrationManager.registerDevice(event)
    }

    /**
     * Sends track request to the configured Campaign Classic tracking server.
     *
     * @param event [Event] initiating the Campaign Classic track request
     */
    private fun handleTrackRequest(event: Event, tagId: String) {
        trackRequestManager.handleTrackRequest(event, tagId)
    }
}
