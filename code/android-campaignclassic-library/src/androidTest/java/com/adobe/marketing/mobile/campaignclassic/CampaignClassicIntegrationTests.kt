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
package com.adobe.marketing.mobile.campaignclassic

import android.app.Application
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adobe.marketing.mobile.CampaignClassic
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.MobilePrivacyStatus
import com.adobe.marketing.mobile.services.HttpConnecting
import com.adobe.marketing.mobile.services.NamedCollection
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.Networking
import com.adobe.marketing.mobile.services.ServiceProvider
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

typealias NetworkMonitor = (request: NetworkRequest) -> Unit

@RunWith(AndroidJUnit4::class)
class CampaignClassicIntegrationTests {

    companion object {
        private var networkMonitor: NetworkMonitor? = null
        private var dataStore: NamedCollection? = null

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            val appContext =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

            val countDownLatch = CountDownLatch(1)

            // Setup services
            ServiceProvider.getInstance().networkService = Networking { request, callback ->
                var connection: HttpConnecting? = null
                with(request.url) {
                    when {
                        startsWith("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp") ||
                            startsWith("https://testTrackingServer/r/") -> {
                            connection = MockedHttpConnecting()
                        }
                        startsWith("https://testMarketingServerFailed/nms/mobile/1/registerAndroid.jssp") ||
                            startsWith("https://testTrackingServerFailed/r/") -> {
                            connection = MockedHttpConnectingFailed()
                        }
                    }
                }
                if (callback != null && connection != null) {
                    callback.call(connection)
                } else {
                    // If no callback is passed by the client, close the connection.
                    connection?.close()
                }
                networkMonitor?.let { it(request) }
            }

            // initialize Campaign Classic extension
            MobileCore.setApplication(appContext)
            MobileCore.setLogLevel(LoggingMode.VERBOSE)
            MobileCore.registerExtensions(listOf(CampaignClassicExtension::class.java)) {
                countDownLatch.countDown()
            }
            countDownLatch.await(100, TimeUnit.MILLISECONDS)
        }
    }

    @Before
    fun setup() {
        dataStore = ServiceProvider.getInstance().dataStoreService?.getNamedCollection(CampaignClassicTestConstants.DATASTORE_KEY)
        dataStore?.removeAll()
    }

    @Test
    fun testExtensionVersion() {
        Assert.assertEquals(CampaignClassicTestConstants.EXTENSION_VERSION, CampaignClassic.extensionVersion())
    }

    // =================================================================================================================
    // void registerDevice(final String token, final String userKey, final Map<String, Object> additionalParams)
    // =================================================================================================================

    @Test
    fun test_registerDevice_VerifySuccessfulDeviceRegistrationWhenAllParametersArePresent() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp", request.url)

            // verify header
            Assert.assertEquals("application/x-www-form-urlencoded;charset=UTF-8", request.headers["Content-Type"])

            // verify payload
            val payload = String(request.body, Charsets.UTF_8)
            Assert.assertTrue(payload.contains("registrationToken=testToken&"))
            Assert.assertTrue(payload.contains("mobileAppUuid=testIntegrationKey&"))
            Assert.assertTrue(payload.contains("userKey=user%40email.com&"))
            Assert.assertTrue(payload.contains("deviceImei"))
            Assert.assertTrue(payload.contains("deviceName=${Build.DEVICE}&"))
            Assert.assertTrue(payload.contains("deviceModel=${Build.MODEL}&"))
            Assert.assertTrue(payload.contains("deviceBrand=${Build.BRAND}&"))
            Assert.assertTrue(payload.contains("deviceManufacturer=${Build.MANUFACTURER}&"))
            Assert.assertTrue(payload.contains("osName=android&"))
            Assert.assertTrue(payload.contains("osVersion=Android%20${Build.VERSION.RELEASE}&"))
            Assert.assertTrue(payload.contains("osLanguage=en-US&"))

            // verify additional params
            Assert.assertTrue(payload.contains("subscribed%22%20value%3D%22true"))
            Assert.assertTrue(payload.contains("zipcode%22%20value%3D%2294403"))
            Assert.assertTrue(payload.contains("name%22%20value%3D%22testUser"))
            Assert.assertTrue(payload.contains("age%22%20value%3D%2235.9"))

            // verify registration hash stored in datastore
            Assert.assertNotNull(dataStore?.getString(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH, null))

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_registerDevice_VerifyNoDeviceRegistrationRequestSentWhenDeviceTokenIsEmpty() {
        // setup
        var registerDeviceRequestCaught = false
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp")) {
                registerDeviceRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(registerDeviceRequestCaught)
    }

    @Test
    fun test_registerDevice_VerifySuccessfulDeviceRegistrationWhenUserKeyIsEmpty() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp", request.url)

            // verify payload
            val payload = String(request.body, Charsets.UTF_8)
            Assert.assertTrue(payload.contains("registrationToken=testToken&"))
            Assert.assertTrue(payload.contains("userKey=&"))

            // verify additional params
            Assert.assertTrue(payload.contains("subscribed%22%20value%3D%22true"))
            Assert.assertTrue(payload.contains("zipcode%22%20value%3D%2294403"))
            Assert.assertTrue(payload.contains("name%22%20value%3D%22testUser"))
            Assert.assertTrue(payload.contains("age%22%20value%3D%2235.9"))

            // verify registration hash stored in datastore
            Assert.assertNotNull(dataStore?.getString(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH, null))

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_registerDevice_VerifySuccessfulDeviceRegistrationWhenUserKeyIsNull() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", null,
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp", request.url)

            // verify payload
            val payload = String(request.body, Charsets.UTF_8)
            Assert.assertTrue(payload.contains("registrationToken=testToken&"))
            Assert.assertTrue(payload.contains("userKey=&"))

            // verify additional params
            Assert.assertTrue(payload.contains("subscribed%22%20value%3D%22true"))
            Assert.assertTrue(payload.contains("zipcode%22%20value%3D%2294403"))
            Assert.assertTrue(payload.contains("name%22%20value%3D%22testUser"))
            Assert.assertTrue(payload.contains("age%22%20value%3D%2235.9"))

            // verify registration hash stored in datastore
            Assert.assertNotNull(dataStore?.getString(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH, null))

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_registerDevice_VerifySuccessfulDeviceRegistrationWhenAdditionalParameterIsNull() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice("testToken", "user@email.com", null)

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp", request.url)

            // verify payload
            val payload = String(request.body, Charsets.UTF_8)
            Assert.assertTrue(payload.contains("registrationToken=testToken&"))
            Assert.assertTrue(payload.contains("userKey=user%40email.com&"))

            // verify additional params
            Assert.assertTrue(payload.contains("additionalParameters%3E%3C%2FadditionalParameters%3E"))

            // verify registration hash stored in datastore
            Assert.assertNotNull(dataStore?.getString(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH, null))

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_registerDevice_VerifyNoDeviceRegistrationRequestSentWhenIntegrationKeyIsNotPresent() {
        // setup
        var registerDeviceRequestCaught = false
        val countDownLatch = CountDownLatch(1)
        setupConfiguration(integrationKey = null)
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp")) {
                registerDeviceRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(registerDeviceRequestCaught)
    }

    @Test
    fun test_registerDevice_VerifyNoDeviceRegistrationRequestSentWhenMarketingServerIsNotPresent() {
        // setup
        var registerDeviceRequestCaught = false
        val countDownLatch = CountDownLatch(1)
        setupConfiguration(marketingServer = null)
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp")) {
                registerDeviceRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(registerDeviceRequestCaught)
    }

    @Test
    fun test_registerDevice_VerifyNoDeviceRegistrationRequestSentWhenPrivacyStatusUnknown() {
        // setup
        var registerDeviceRequestCaught = false
        val countDownLatch = CountDownLatch(1)
        setupConfiguration(privacyStatus = MobilePrivacyStatus.UNKNOWN.value)
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp")) {
                registerDeviceRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(registerDeviceRequestCaught)
    }

    @Test
    fun test_registerDevice_VerifyNoDeviceRegistrationRequestSentWhenPrivacyStatusOptOut() {
        // setup
        var registerDeviceRequestCaught = false
        val countDownLatch = CountDownLatch(1)
        setupConfiguration(privacyStatus = MobilePrivacyStatus.OPT_OUT.value)
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp")) {
                registerDeviceRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(registerDeviceRequestCaught)
    }

    @Test
    fun test_registerDevice_VerifyNoDeviceRegistrationRequestSentWhenNoConfiguration() {
        // setup
        MobileCore.clearUpdatedConfiguration()
        Thread.sleep(20)
        var registerDeviceRequestCaught = false
        val countDownLatch = CountDownLatch(1)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp")) {
                registerDeviceRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(registerDeviceRequestCaught)
    }

    @Test
    fun test_registerDevice_VerifySecondDeviceRegistrationRequestIsNotSentWhenRegistrationParametersAreUnchanged() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp", request.url)

            // verify registration hash stored in datastore
            Assert.assertNotNull(dataStore?.getString(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH, null))

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        networkMonitor = null
        var registerDeviceRequestCaught = false
        val countDownLatch2 = CountDownLatch(1)
        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp")) {
                registerDeviceRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch2.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(registerDeviceRequestCaught)
    }

    @Test
    fun test_registerDevice_VerifySecondDeviceRegistrationRequestIsSentWhenRegistrationParametersAreChanged() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp", request.url)

            // verify registration hash stored in datastore
            Assert.assertNotNull(dataStore?.getString(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH, null))

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        networkMonitor = null
        val countDownLatch2 = CountDownLatch(1)
        // test
        CampaignClassic.registerDevice(
            "newTestToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals("https://testMarketingServer/nms/mobile/1/registerAndroid.jssp", request.url)

            // verify registration hash stored in datastore
            Assert.assertNotNull(dataStore?.getString(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH, null))

            countDownLatch2.countDown()
        }
        Assert.assertTrue(countDownLatch2.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_registerDevice_VerifyFailedDeviceRegistrationWhenMarketingServerReturns404Error() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration(marketingServer = "testMarketingServerFailed")
        Thread.sleep(20)

        // test
        CampaignClassic.registerDevice(
            "testToken", "user@email.com",
            mapOf(
                "zipcode" to 94403,
                "subscribed" to true,
                "name" to "testUser",
                "age" to 35.9
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testMarketingServerFailed/nms/mobile/1/registerAndroid.jssp",
                request.url
            )

            // verify registration hash stored in datastore
            Assert.assertNull(dataStore?.getString(CampaignClassicTestConstants.DataStoreKeys.TOKEN_HASH, null))

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    // =================================================================================================================
    // void trackNotificationReceive(final Map<String, String> trackInfo)
    // =================================================================================================================

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestSentWhenAllParametersArePresentV7() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testTrackingServer/r/?id=h${java.lang.String.format("%x",12345)},testDeliveryId,1",
                request.url
            )

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestSentWhenAllParametersArePresentV8() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "6b6499a8-9d43-4bc5-acf0-b6aeb96846f6"
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testTrackingServer/r/?id=h6b6499a8-9d43-4bc5-acf0-b6aeb96846f6,testDeliveryId,1",
                request.url
            )

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenBroadLogIdIsNotPresent() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(mapOf(CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId"))

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenBroadLogIdIsEmpty() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to ""
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenBroadLogIdIsNull() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to null
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenBroadLogIdIsInvalid() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "00112233-4455-6677-8899-aabbccddee"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenDeliveryIdIsNotPresent() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(mapOf(CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"))

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenDeliveryIdIsEmpty() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenDeliveryIdIsNull() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenTrackingServerIsNull() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration(trackingServer = null)
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenNoConfiguration() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        MobileCore.clearUpdatedConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenPrivacyStatusIsOptOut() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration(privacyStatus = MobilePrivacyStatus.OPT_OUT.value)
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestNotSentWhenPrivacyStatusIsUnknown() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration(privacyStatus = MobilePrivacyStatus.UNKNOWN.value)
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveMultipleRequestsOnMultipleCalls() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testTrackingServer/r/?id=h${java.lang.String.format("%x",12345)},testDeliveryId,1",
                request.url
            )

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        val countDownLatch2 = CountDownLatch(1)
        networkMonitor = null

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testTrackingServer/r/?id=h${java.lang.String.format("%x",12345)},testDeliveryId,1",
                request.url
            )

            countDownLatch2.countDown()
        }
        Assert.assertTrue(countDownLatch2.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_trackNotificationReceive_VerifyTrackNotificationReceiveRequestWhenTrackingServerReturns404Error() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration(trackingServer = "testTrackingServerFailed")
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationReceive(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            Assert.assertEquals(
                "https://testTrackingServerFailed/r/?id=h${java.lang.String.format("%x",12345)},testDeliveryId,1",
                request.url
            )
            countDownLatch.countDown()
        }

        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    // =================================================================================================================
    // void trackNotificationClick(final Map<String, String> trackInfo)
    // =================================================================================================================

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestSentWhenAllParametersArePresentV7() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testTrackingServer/r/?id=h${java.lang.String.format("%x",12345)},testDeliveryId,2",
                request.url
            )

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestSentWhenAllParametersArePresentV8() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "6b6499a8-9d43-4bc5-acf0-b6aeb96846f6"
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testTrackingServer/r/?id=h6b6499a8-9d43-4bc5-acf0-b6aeb96846f6,testDeliveryId,2",
                request.url
            )

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenBroadLogIdIsNotPresent() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(mapOf(CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId"))

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenBroadLogIdIsEmpty() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to ""
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenBroadLogIdIsNull() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to null
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenBroadLogIdIsInvalid() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "00112233-4455-6677-8899-aabbccddee"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenDeliveryIdIsNotPresent() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(mapOf(CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"))

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenDeliveryIdIsEmpty() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenDeliveryIdIsNull() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenTrackingServerIsNull() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration(trackingServer = null)
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenNoConfiguration() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        MobileCore.clearUpdatedConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenPrivacyStatusIsOptOut() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration(privacyStatus = MobilePrivacyStatus.OPT_OUT.value)
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestNotSentWhenPrivacyStatusIsUnknown() {
        // setup
        val countDownLatch = CountDownLatch(1)
        var trackNotificationRequestCaught = false
        setupConfiguration(privacyStatus = MobilePrivacyStatus.UNKNOWN.value)
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to null,
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            if (request.url.startsWith("https://testTrackingServer/r/?id=h")) {
                trackNotificationRequestCaught = true
            }
        }

        Assert.assertFalse(countDownLatch.await(1, TimeUnit.SECONDS))
        Assert.assertFalse(trackNotificationRequestCaught)
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickMultipleRequestsOnMultipleCalls() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration()
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testTrackingServer/r/?id=h${java.lang.String.format("%x",12345)},testDeliveryId,2",
                request.url
            )

            countDownLatch.countDown()
        }
        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))

        val countDownLatch2 = CountDownLatch(1)
        networkMonitor = null

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            // verify network request url
            Assert.assertEquals(
                "https://testTrackingServer/r/?id=h${java.lang.String.format("%x",12345)},testDeliveryId,2",
                request.url
            )

            countDownLatch2.countDown()
        }
        Assert.assertTrue(countDownLatch2.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun test_trackNotificationClick_VerifyTrackNotificationClickRequestWhenTrackingServerReturns404Error() {
        // setup
        val countDownLatch = CountDownLatch(1)
        setupConfiguration(trackingServer = "testTrackingServerFailed")
        Thread.sleep(20)

        // test
        CampaignClassic.trackNotificationClick(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID to "testDeliveryId",
                CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID to "12345"
            )
        )

        // verify
        networkMonitor = { request ->
            Assert.assertEquals(
                "https://testTrackingServerFailed/r/?id=h${java.lang.String.format("%x",12345)},testDeliveryId,2",
                request.url
            )
            countDownLatch.countDown()
        }

        Assert.assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    private fun setupConfiguration(
        marketingServer: String? = "testMarketingServer",
        integrationKey: String? = "testIntegrationKey",
        trackingServer: String? = "testTrackingServer",
        privacyStatus: String? = MobilePrivacyStatus.OPT_IN.value,
        networkTimeout: Int = CampaignClassicTestConstants.DEFAULT_TIMEOUT
    ) {
        MobileCore.updateConfiguration(
            mapOf(
                CampaignClassicTestConstants.EventDataKeys.Configuration.CAMPAIGNCLASSIC_MARKETING_SERVER to marketingServer,
                CampaignClassicTestConstants.EventDataKeys.Configuration.CAMPAIGNCLASSIC_APP_INTEGRATION_KEY to integrationKey,
                CampaignClassicTestConstants.EventDataKeys.Configuration.CAMPAIGNCLASSIC_TRACKING_SERVER to trackingServer,
                CampaignClassicTestConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY to privacyStatus,
                CampaignClassicTestConstants.EventDataKeys.Configuration.CAMPAIGNCLASSIC_TIMEOUT to networkTimeout
            )
        )
    }
}

private class MockedHttpConnecting : HttpConnecting {

    override fun getInputStream(): InputStream? {
        return null
    }

    override fun getErrorStream(): InputStream? {
        return null
    }

    override fun getResponseCode(): Int {
        return 200
    }

    override fun getResponseMessage(): String {
        return ""
    }

    override fun getResponsePropertyValue(responsePropertyKey: String?): String {
        return ""
    }

    override fun close() {}
}

private class MockedHttpConnectingFailed : HttpConnecting {

    override fun getInputStream(): InputStream? {
        return null
    }

    override fun getErrorStream(): InputStream? {
        return null
    }

    override fun getResponseCode(): Int {
        return 404
    }

    override fun getResponseMessage(): String {
        return "Server Error: Not Found"
    }

    override fun getResponsePropertyValue(responsePropertyKey: String?): String {
        return ""
    }

    override fun close() {}
}
