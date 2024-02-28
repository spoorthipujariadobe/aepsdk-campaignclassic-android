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
package com.adobe.marketing.mobile.campaignclassic.internal;

import com.adobe.marketing.mobile.CampaignClassic;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.services.Log;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class CampaignClassicTests {

    @Test
    public void test_extensionVersion() {
        // test
        final String extensionVersion = CampaignClassic.extensionVersion();
        Assert.assertEquals(
                "extensionVersion API should return the correct version string.",
                CampaignClassicTestConstants.EXTENSION_VERSION,
                extensionVersion);
    }


    @Test
    public void test_registerDevice() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                Mockito.mockStatic(MobileCore.class)) {
            // setup
            String token = "pushToken";
            String userKey = "userKey";
            Map<String, Object> additionalParams = new HashMap<>();
            additionalParams.put("string", "abc");
            additionalParams.put("number", 4);
            additionalParams.put("boolean", true);

            // test
            CampaignClassic.registerDevice(token, userKey, additionalParams);

            // verify
            final ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            mobileCoreMockedStatic.verify(() -> MobileCore.dispatchEvent(eventCaptor.capture()));
            final Event event = eventCaptor.getValue();

            Assert.assertNotNull(event);
            Assert.assertEquals("com.adobe.eventType.campaign", event.getType());
            Assert.assertEquals("com.adobe.eventSource.requestContent", event.getSource());

            final Map<String, Object> eventData = event.getEventData();
            Assert.assertTrue(
                    (Boolean)
                            eventData.get(
                                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic
                                            .REGISTER_DEVICE));
            Assert.assertEquals(
                    token,
                    eventData.get(
                            CampaignClassicTestConstants.EventDataKeys.CampaignClassic
                                    .DEVICE_TOKEN));
            Assert.assertEquals(
                    userKey,
                    eventData.get(
                            CampaignClassicTestConstants.EventDataKeys.CampaignClassic.USER_KEY));
            Assert.assertEquals(
                    additionalParams,
                    eventData.get(
                            CampaignClassicTestConstants.EventDataKeys.CampaignClassic
                                    .ADDITIONAL_PARAMETERS));
        }
    }

    @Test
    public void test_registerDevice_NullUserKeyAndAdditionalData() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                Mockito.mockStatic(MobileCore.class)) {
            // setup
            String token = "pushToken";

            // test
            CampaignClassic.registerDevice(token, null, null);

            // verify
            final ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            mobileCoreMockedStatic.verify(() -> MobileCore.dispatchEvent(eventCaptor.capture()));
            final Event event = eventCaptor.getValue();

            Assert.assertNotNull(event);
            Assert.assertEquals("com.adobe.eventType.campaign", event.getType());
            Assert.assertEquals("com.adobe.eventSource.requestContent", event.getSource());

            final Map<String, Object> eventData = event.getEventData();
            Assert.assertTrue(
                    (Boolean)
                            eventData.get(
                                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic
                                            .REGISTER_DEVICE));
            Assert.assertEquals(
                    token,
                    eventData.get(
                            CampaignClassicTestConstants.EventDataKeys.CampaignClassic
                                    .DEVICE_TOKEN));
            Assert.assertNull(
                    eventData.get(
                            CampaignClassicTestConstants.EventDataKeys.CampaignClassic.USER_KEY));
            Assert.assertNull(
                    eventData.get(
                            CampaignClassicTestConstants.EventDataKeys.CampaignClassic
                                    .ADDITIONAL_PARAMETERS));
        }
    }

    @Test
    public void test_registerDevice_NullToken() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                        Mockito.mockStatic(MobileCore.class);
                MockedStatic<Log> logMockedStatic = Mockito.mockStatic(Log.class)) {
            // setup
            String userKey = "userKey";
            Map<String, Object> additionalParams = new HashMap<>();
            additionalParams.put("string", "abc");
            additionalParams.put("number", 4);
            additionalParams.put("boolean", true);
            // test
            CampaignClassic.registerDevice(null, userKey, additionalParams);

            // verify
            mobileCoreMockedStatic.verify(
                    () -> MobileCore.dispatchEvent(ArgumentMatchers.any()), Mockito.times(0));
            logMockedStatic.verify(
                    () ->
                            Log.error(
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.any()));
        }
    }

    @Test
    public void test_trackNotificationReceive() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                Mockito.mockStatic(MobileCore.class)) {
            // setup
            Map<String, String> trackInfo = new HashMap<>();
            trackInfo.put("key", "value");

            // test
            CampaignClassic.trackNotificationReceive(trackInfo);

            // verify
            final ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            mobileCoreMockedStatic.verify(() -> MobileCore.dispatchEvent(eventCaptor.capture()));
            final Event event = eventCaptor.getValue();

            Assert.assertNotNull(event);
            Assert.assertEquals("com.adobe.eventType.campaign", event.getType());
            Assert.assertEquals("com.adobe.eventSource.requestContent", event.getSource());

            final Map<String, Object> eventData = event.getEventData();
            Assert.assertTrue(
                    (Boolean)
                            eventData.get(
                                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic
                                            .TRACK_RECEIVE));
            Assert.assertEquals(
                    trackInfo,
                    eventData.get(
                            CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO));
        }
    }

    @Test
    public void test_trackNotificationReceive_NullTrackInfo() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                        Mockito.mockStatic(MobileCore.class);
                MockedStatic<Log> logMockedStatic = Mockito.mockStatic(Log.class)) {
            // test
            CampaignClassic.trackNotificationReceive(null);

            // verify
            mobileCoreMockedStatic.verify(
                    () -> MobileCore.dispatchEvent(ArgumentMatchers.any()), Mockito.times(0));
            logMockedStatic.verify(
                    () ->
                            Log.error(
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.any()));
        }
    }

    @Test
    public void test_trackNotificationReceive_EmptyTrackInfo() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                        Mockito.mockStatic(MobileCore.class);
                MockedStatic<Log> logMockedStatic = Mockito.mockStatic(Log.class)) {
            // test
            CampaignClassic.trackNotificationReceive(new HashMap<>());

            // verify
            mobileCoreMockedStatic.verify(
                    () -> MobileCore.dispatchEvent(ArgumentMatchers.any()), Mockito.times(0));
            logMockedStatic.verify(
                    () ->
                            Log.error(
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.any()));
        }
    }

    @Test
    public void test_trackNotificationClick() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                Mockito.mockStatic(MobileCore.class)) {
            // setup
            Map<String, String> trackInfo = new HashMap<>();
            trackInfo.put("key", "value");

            // test
            CampaignClassic.trackNotificationClick(trackInfo);

            // verify
            final ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            mobileCoreMockedStatic.verify(() -> MobileCore.dispatchEvent(eventCaptor.capture()));
            final Event event = eventCaptor.getValue();

            Assert.assertNotNull(event);
            Assert.assertEquals("com.adobe.eventType.campaign", event.getType());
            Assert.assertEquals("com.adobe.eventSource.requestContent", event.getSource());

            final Map<String, Object> eventData = event.getEventData();
            Assert.assertTrue(
                    (Boolean)
                            eventData.get(
                                    CampaignClassicTestConstants.EventDataKeys.CampaignClassic
                                            .TRACK_CLICK));
            Assert.assertEquals(
                    trackInfo,
                    eventData.get(
                            CampaignClassicTestConstants.EventDataKeys.CampaignClassic.TRACK_INFO));
        }
    }

    @Test
    public void test_trackNotificationClick_NullTrackInfo() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                        Mockito.mockStatic(MobileCore.class);
                MockedStatic<Log> logMockedStatic = Mockito.mockStatic(Log.class)) {
            // test
            CampaignClassic.trackNotificationClick(null);

            // verify
            mobileCoreMockedStatic.verify(
                    () -> MobileCore.dispatchEvent(ArgumentMatchers.any()), Mockito.times(0));
            logMockedStatic.verify(
                    () ->
                            Log.error(
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.any()));
        }
    }

    @Test
    public void test_trackNotificationClick_EmptyTrackInfo() {
        try (MockedStatic<MobileCore> mobileCoreMockedStatic =
                        Mockito.mockStatic(MobileCore.class);
                MockedStatic<Log> logMockedStatic = Mockito.mockStatic(Log.class)) {
            // test
            CampaignClassic.trackNotificationClick(new HashMap<>());

            // verify
            mobileCoreMockedStatic.verify(
                    () -> MobileCore.dispatchEvent(ArgumentMatchers.any()), Mockito.times(0));
            logMockedStatic.verify(
                    () ->
                            Log.error(
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.anyString(),
                                    ArgumentMatchers.any()));
        }
    }
}
