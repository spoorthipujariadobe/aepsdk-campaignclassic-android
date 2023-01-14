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
import com.adobe.marketing.mobile.services.DeviceInforming
import com.adobe.marketing.mobile.services.HttpConnecting
import com.adobe.marketing.mobile.services.NamedCollection
import com.adobe.marketing.mobile.services.NetworkCallback
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.Networking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.net.HttpURLConnection
import java.util.Locale

@RunWith(MockitoJUnitRunner.Silent::class)
class RegistrationManagerTests {

    private lateinit var extensionApi: ExtensionApi
    private lateinit var deviceInfoService: DeviceInforming
    private lateinit var dataStore: NamedCollection
    private lateinit var networkService: Networking
    private lateinit var registrationManager: RegistrationManager

    @Before
    fun setup() {
        extensionApi = Mockito.mock(ExtensionApi::class.java)
        deviceInfoService = Mockito.mock(DeviceInforming::class.java)
        mockDeviceInfo()
        dataStore = Mockito.mock(NamedCollection::class.java)
        networkService = Mockito.mock(Networking::class.java)

        registrationManager =
            RegistrationManager(extensionApi, deviceInfoService, dataStore, networkService)
    }

    private fun mockDeviceInfo() {
        Mockito.`when`(deviceInfoService.deviceUniqueId).thenReturn("12345")
        Mockito.`when`(deviceInfoService.device).thenReturn("mockDevice")
        Mockito.`when`(deviceInfoService.deviceBrand).thenReturn("mockDeviceBrand")
        Mockito.`when`(deviceInfoService.deviceManufacturer).thenReturn("mockDeviceManufacturer")
        Mockito.`when`(deviceInfoService.deviceName).thenReturn("mockDeviceName")
        Mockito.`when`(deviceInfoService.operatingSystemName).thenReturn("mockOSName")
        Mockito.`when`(deviceInfoService.operatingSystemVersion).thenReturn("mockOSVersion")
        Mockito.`when`(deviceInfoService.activeLocale).thenReturn(Locale("mockLocale"))
    }

    // =================================================================================================================
    // fun registerDevice(event: Event)
    // =================================================================================================================
    @Test
    fun registerDevice_Happy() {
        // setup
        setConfigurationSharedState()

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify network call
        val networkRequestCaptor = ArgumentCaptor.forClass(NetworkRequest::class.java)
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(networkRequestCaptor.capture(), ArgumentMatchers.any())

        // verify url
        val expectedUrl = "https://testMarketingServer/nms/mobile/1/registerAndroid.jssp"
        Assert.assertEquals(expectedUrl, networkRequestCaptor.value.url)

        // verify payload
        val payload = String(networkRequestCaptor.value.body, Charsets.UTF_8)
        Assert.assertTrue(payload.contains("registrationToken=testToken&"))
        Assert.assertTrue(payload.contains("mobileAppUuid=testIntegrationKey&"))
        Assert.assertTrue(payload.contains("userKey=testUser&"))
        Assert.assertTrue(payload.contains("deviceImei=12345&"))
        Assert.assertTrue(payload.contains("deviceName=mockDevice&"))
        Assert.assertTrue(payload.contains("deviceModel=mockDeviceName&"))
        Assert.assertTrue(payload.contains("deviceBrand=mockDeviceBrand&"))
        Assert.assertTrue(payload.contains("deviceManufacturer=mockDeviceManufacturer&"))
        Assert.assertTrue(payload.contains("osName=android&"))
        Assert.assertTrue(payload.contains("osVersion=mockOSName%20mockOSVersion&"))
        Assert.assertTrue(payload.contains("osLanguage=mocklocale&"))
        Assert.assertTrue(payload.contains("%3CadditionalParameters%3E%3Cparam%20name%3D%22key%22%20value%3D%22value%22%2F%3E%3C%2FadditionalParameters%3E"))

        // verify header
        Assert.assertEquals(2, networkRequestCaptor.value.headers.size)
        Assert.assertEquals(
            "application/x-www-form-urlencoded;charset=UTF-8",
            networkRequestCaptor.value.headers["Content-Type"]
        )
        Assert.assertEquals(
            payload.length.toString(),
            networkRequestCaptor.value.headers["Content-Length"]
        )
    }

    @Test
    fun registerDevice_WhenNetworkSuccess_ThenStoreToken() {
        // setup
        setConfigurationSharedState()
        val connection = Mockito.mock(HttpConnecting::class.java)
        Mockito.`when`(connection.responseCode).thenReturn(HttpURLConnection.HTTP_OK)
        Mockito.`when`(networkService.connectAsync(ArgumentMatchers.any(), ArgumentMatchers.any())).thenAnswer { invocation ->
            (invocation.arguments[1] as NetworkCallback).call(connection)
            null
        }

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify network call
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(ArgumentMatchers.any(), ArgumentMatchers.any())

        // verify hashed token is stored
        val dataStoreCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(dataStore, Mockito.times(1)).setString(ArgumentMatchers.anyString(), dataStoreCaptor.capture())
        Assert.assertNotNull(dataStoreCaptor.value)
    }

    @Test
    fun registerDevice_WhenNetworkError_ThenDoesNotStoreToken() {
        // setup
        setConfigurationSharedState()

        val connection = Mockito.mock(HttpConnecting::class.java)
        Mockito.`when`(connection.responseCode).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST)
        Mockito.`when`(networkService.connectAsync(ArgumentMatchers.any(), ArgumentMatchers.any())).thenAnswer { invocation ->
            (invocation.arguments[1] as NetworkCallback).call(connection)
            null
        }

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify network call
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(ArgumentMatchers.any(), ArgumentMatchers.any())

        // verify hashed token is stored
        Mockito.verify(dataStore, Mockito.times(0)).setString(
            ArgumentMatchers.eq(
                CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH
            ),
            ArgumentMatchers.anyString()
        )
    }

    @Test
    fun registerDevice_ConfigurationNotSet() {
        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test
    fun registerDevice_NullDeviceToken() {
        // setup
        setConfigurationSharedState()

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent(deviceToken = null))

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test

    fun registerDevice_EmptyDeviceToken() {
        // setup
        setConfigurationSharedState()

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent(deviceToken = ""))

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test
    fun registerDevice_NullUserKey() {
        // setup
        setConfigurationSharedState()

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent(userToken = null))

        // verify network call is still made with correct payload
        val networkRequestCaptor = ArgumentCaptor.forClass(NetworkRequest::class.java)
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(networkRequestCaptor.capture(), ArgumentMatchers.any())
        Assert.assertTrue(networkRequestCaptor.value.payloadAsString().contains("userKey=&"))
    }

    @Test
    fun registerDevice_EmptyUserKey() {
        // setup
        setConfigurationSharedState()

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent(userToken = ""))

        // verify network call is still made with correct payload
        val networkRequestCaptor = ArgumentCaptor.forClass(NetworkRequest::class.java)
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(networkRequestCaptor.capture(), ArgumentMatchers.any())
        Assert.assertTrue(networkRequestCaptor.value.payloadAsString().contains("userKey=&"))
    }

    @Test
    fun registerDevice_NullAdditionalParams() {
        // setup
        setConfigurationSharedState()

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent(additionalParams = null))

        // verify network call is still made with correct payload
        val networkRequestCaptor = ArgumentCaptor.forClass(NetworkRequest::class.java)
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(networkRequestCaptor.capture(), ArgumentMatchers.any())

        // verify payload
        val payload = String(networkRequestCaptor.value.body, Charsets.UTF_8)
        Assert.assertTrue(payload.contains("&additionalParams=%3CadditionalParameters%3E%3C%2FadditionalParameters%3E"))
    }

    @Test
    fun registerDevice_EmptyAdditionalParams() {
        // setup
        setConfigurationSharedState()

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent(additionalParams = emptyMap()))

        // verify network call is still made with correct payload
        val networkRequestCaptor = ArgumentCaptor.forClass(NetworkRequest::class.java)
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(networkRequestCaptor.capture(), ArgumentMatchers.any())

        // verify payload
        val payload = String(networkRequestCaptor.value.body, Charsets.UTF_8)
        Assert.assertTrue(payload.contains("&additionalParams=%3CadditionalParameters%3E%3C%2FadditionalParameters%3E"))
    }

    @Test
    fun registerDevice_NullMarketingServer() {
        // setup
        setConfigurationSharedState(marketingServer = null)

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test
    fun registerDevice_EmptyMarketingServer() {
        // setup
        setConfigurationSharedState(marketingServer = "")

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test
    fun registerDevice_NullIntegrationKey() {
        // setup
        setConfigurationSharedState(integrationKey = null)

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test
    fun registerDevice_EmptyIntegrationKey() {
        // setup
        setConfigurationSharedState(integrationKey = "")

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test
    fun registerDevice_PrivacyStatusOptOut() {
        // setup
        setConfigurationSharedState(privacyStatus = MobilePrivacyStatus.OPT_OUT)

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test
    fun registerDevice_PrivacyStatusUnknown() {
        // setup
        setConfigurationSharedState(privacyStatus = MobilePrivacyStatus.UNKNOWN)

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verifyNoInteractions(networkService)
        Mockito.verifyNoInteractions(dataStore)
    }

    @Test
    fun registerDevice_WhenCalledWithSameDetails() {
        // setup
        setConfigurationSharedState()

        Mockito.`when`(dataStore.getString(ArgumentMatchers.eq(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH), ArgumentMatchers.any()))
            .thenReturn("b1315b4b0514a6092451017162124d59dea702370eed96077e3d524fe6ce899d")

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify no network call
        Mockito.verifyNoInteractions(networkService)
    }

    @Test
    fun registerDevice_NetworkServiceNotAvailable() {
        // setup
        setConfigurationSharedState()

        // test
        RegistrationManager(extensionApi, deviceInfoService, dataStore, null).registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verify(dataStore, Mockito.times(0)).setString(
            ArgumentMatchers.eq(
                CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH
            ),
            ArgumentMatchers.anyString()
        )
    }

    @Test
    fun registerDevice_DataStoreNotAvailable() {
        // setup
        setConfigurationSharedState()

        // test
        RegistrationManager(extensionApi, deviceInfoService, null, networkService).registerDevice(getRegisterDeviceEvent())

        // verify
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun registerDevice_CustomTimeoutSet() {
        // setup
        setConfigurationSharedState(timeout = 20)

        // test
        registrationManager.registerDevice(getRegisterDeviceEvent())

        // verify network call is still made with correct payload
        val networkRequestCaptor = ArgumentCaptor.forClass(NetworkRequest::class.java)
        Mockito.verify(networkService, Mockito.times(1)).connectAsync(networkRequestCaptor.capture(), ArgumentMatchers.any())
        Assert.assertEquals(20, networkRequestCaptor.value.connectTimeout)
        Assert.assertEquals(20, networkRequestCaptor.value.readTimeout)
    }

    // =================================================================================================================
    // fun clearRegistrationData(event: Event)
    // =================================================================================================================

    @Test
    fun clearRegistrationData() {
        // test
        registrationManager.clearRegistrationData()

        // verify
        Mockito.verify(dataStore, Mockito.times(1)).remove(
            ArgumentMatchers.eq(
                CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH
            )
        )
    }

    // =================================================================================================================
    // private methods
    // =================================================================================================================

    private fun setConfigurationSharedState(
        marketingServer: String? = "testMarketingServer",
        integrationKey: String? = "testIntegrationKey",
        timeout: Int = 5,
        privacyStatus: MobilePrivacyStatus = MobilePrivacyStatus.OPT_IN
    ) {
        Mockito.`when`(
            extensionApi.getSharedState(
                ArgumentMatchers.eq(CampaignClassicTestConstants.EventDataKeys.Configuration.EXTENSION_NAME),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyBoolean(),
                ArgumentMatchers.any()
            )
        ).thenReturn(
            SharedStateResult(
                SharedStateStatus.SET,
                mapOf(
                    CampaignClassicTestConstants.EventDataKeys.Configuration.CAMPAIGNCLASSIC_MARKETING_SERVER to marketingServer,
                    CampaignClassicTestConstants.EventDataKeys.Configuration.CAMPAIGNCLASSIC_TRACKING_SERVER to "",
                    CampaignClassicTestConstants.EventDataKeys.Configuration.CAMPAIGNCLASSIC_APP_INTEGRATION_KEY to integrationKey,
                    CampaignClassicTestConstants.EventDataKeys.Configuration.CAMPAIGNCLASSIC_TIMEOUT to timeout,
                    CampaignClassicTestConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY to privacyStatus.value
                )
            )
        )
    }

    private fun getRegisterDeviceEvent(
        deviceToken: String? = "testToken",
        userToken: String? = "testUser",
        additionalParams: Map<String, String>? = mapOf("key" to "value")
    ): Event {
        return Event.Builder("Register Device", EventType.CAMPAIGN, EventSource.REQUEST_CONTENT)
            .setEventData(
                mapOf(
                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic.DEVICE_TOKEN to deviceToken,
                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic.USER_KEY to userToken,
                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic.ADDITIONAL_PARAMETERS to additionalParams
                )
            )
            .build()
    }

    private fun NetworkRequest.payloadAsString(): String {
        return String(this.body, Charsets.UTF_8)
    }
}
