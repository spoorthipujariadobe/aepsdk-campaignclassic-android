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

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.MobilePrivacyStatus
import com.adobe.marketing.mobile.SharedStateResult
import com.adobe.marketing.mobile.SharedStateStatus
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class CampaignClassicExtensionTests {

    private lateinit var extensionApi: ExtensionApi
    private lateinit var registrationManager: RegistrationManager
    private lateinit var trackRequestManager: TrackRequestManager
    private lateinit var campaignClassicExtension: CampaignClassicExtension

    @Before
    fun setup() {
        extensionApi = Mockito.mock(ExtensionApi::class.java)
        registrationManager = Mockito.mock(RegistrationManager::class.java)
        trackRequestManager = Mockito.mock(TrackRequestManager::class.java)

        campaignClassicExtension = CampaignClassicExtension(extensionApi, registrationManager, trackRequestManager)
    }

    // =================================================================================================================
    // fun readyForEvent(event: Event)
    // =================================================================================================================

    @Test
    fun readyForEvent_ConfigurationSet() {
        // setup
        setConfigurationSharedState()

        // verify
        Assert.assertTrue(campaignClassicExtension.readyForEvent(getCampaignRequestEvent()))
    }

    @Test
    fun readyForEvent_ConfigurationNotSet() {
        // verify
        Assert.assertFalse(campaignClassicExtension.readyForEvent(getCampaignRequestEvent()))
    }

    @Test
    fun readyForEvent_ConfigurationPending() {
        // setup
        Mockito.`when`(
            extensionApi.getSharedState(
                ArgumentMatchers.eq(CampaignClassicTestConstants.EventDataKeys.Configuration.EXTENSION_NAME),
                any(),
                ArgumentMatchers.anyBoolean(),
                any()
            )
        ).thenReturn(
            SharedStateResult(
                SharedStateStatus.PENDING,
                emptyMap()
            )
        )

        // verify
        Assert.assertFalse(campaignClassicExtension.readyForEvent(getCampaignRequestEvent()))
    }

    // =================================================================================================================
    // fun handleConfigurationRequestEvent(event: Event)
    // =================================================================================================================

    @Test
    fun handleConfigurationResponseEvent_PrivacyOptOut() {
        // setup
        setConfigurationSharedState(privacyStatus = MobilePrivacyStatus.OPT_OUT)

        // test
        campaignClassicExtension.handleConfigurationResponseEvent(getConfigurationRequestEvent())

        // verify
        Mockito.verify(registrationManager, Mockito.times(1)).clearRegistrationData()
    }

    @Test
    fun handleConfigurationResponseEvent_PrivacyOptIn() {
        // setup
        setConfigurationSharedState(privacyStatus = MobilePrivacyStatus.OPT_IN)

        // test
        campaignClassicExtension.handleConfigurationResponseEvent(getConfigurationRequestEvent())

        // verify
        Mockito.verifyNoInteractions(registrationManager)
    }

    // =================================================================================================================
    // fun handleCampaignRequestEvent(event: Event)
    // =================================================================================================================
    @Test
    fun handleCampaignRequestEvent_RegisterDeviceEvent() {
        // setup
        setConfigurationSharedState()

        // test
        campaignClassicExtension.handleCampaignRequestEvent(getCampaignRequestEvent(registerDevice = true))

        // verify
        Mockito.verify(registrationManager, Mockito.times(1)).registerDevice(any())
    }

    @Captor
    private lateinit var tagIdCaptor: ArgumentCaptor<String>

    @Test
    fun handleCampaignRequestEvent_trackReceiveEvent() {
        // setup
        setConfigurationSharedState()
        val trackReceiveEvent = getCampaignRequestEvent(trackReceive = true)

        // test
        campaignClassicExtension.handleCampaignRequestEvent(trackReceiveEvent)

        // verify
        Mockito.verify(trackRequestManager, Mockito.times(1)).handleTrackRequest(any(), capture(tagIdCaptor))
        Assert.assertEquals("1", tagIdCaptor.value)
    }

    @Test
    fun handleCampaignRequestEvent_trackClickEvent() {
        // setup
        setConfigurationSharedState()
        val trackReceiveEvent = getCampaignRequestEvent(trackClick = true)

        // test
        campaignClassicExtension.handleCampaignRequestEvent(trackReceiveEvent)

        // verify
        Mockito.verify(trackRequestManager, Mockito.times(1)).handleTrackRequest(any(), capture(tagIdCaptor))
        Assert.assertEquals("2", tagIdCaptor.value)
    }

    @Test
    fun handleCampaignRequestEvent_NullEventData() {
        // setup
        setConfigurationSharedState()
        val trackReceiveEvent = Event.Builder("Campaign Request", EventType.CAMPAIGN, EventSource.REQUEST_CONTENT)
            .build()

        // test
        campaignClassicExtension.handleCampaignRequestEvent(trackReceiveEvent)

        // verify
        Mockito.verifyNoInteractions(registrationManager)
        Mockito.verifyNoInteractions(trackRequestManager)
    }

    @Test
    fun handleCampaignRequestEvent_MissingType() {
        // setup
        setConfigurationSharedState()
        val trackReceiveEvent = Event.Builder("Campaign Request", EventType.CAMPAIGN, EventSource.REQUEST_CONTENT)
            .setEventData(mapOf("key" to "value"))
            .build()

        // test
        campaignClassicExtension.handleCampaignRequestEvent(trackReceiveEvent)

        // verify
        Mockito.verifyNoInteractions(registrationManager)
        Mockito.verifyNoInteractions(trackRequestManager)
    }

    // =================================================================================================================
    // private methods
    // =================================================================================================================

    private fun setConfigurationSharedState(
        privacyStatus: MobilePrivacyStatus = MobilePrivacyStatus.OPT_IN
    ) {
        Mockito.`when`(
            extensionApi.getSharedState(
                ArgumentMatchers.eq(CampaignClassicTestConstants.EventDataKeys.Configuration.EXTENSION_NAME),
                any(),
                ArgumentMatchers.anyBoolean(),
                any()
            )
        ).thenReturn(
            SharedStateResult(
                SharedStateStatus.SET,
                mapOf(
                    CampaignClassicTestConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY to privacyStatus.value
                )
            )
        )
    }

    private fun getCampaignRequestEvent(
        registerDevice: Boolean = false,
        trackReceive: Boolean = false,
        trackClick: Boolean = false
    ): Event {
        return Event.Builder("Campaign Request", EventType.CAMPAIGN, EventSource.REQUEST_CONTENT)
            .setEventData(
                mapOf(
                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic.REGISTER_DEVICE to registerDevice,
                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_RECEIVE to trackReceive,
                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_CLICK to trackClick
                )
            )
            .build()
    }

    private fun getConfigurationRequestEvent(): Event {
        return Event.Builder("Configuration Request", EventType.CONFIGURATION, EventSource.REQUEST_CONTENT)
            .build()
    }

    private fun <T> any(): T = ArgumentMatchers.any()
    private fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()
}
