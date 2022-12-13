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

package com.adobe.marketing.mobile;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.HashMap;

import com.adobe.marketing.mobile.campaignclassic.CampaignClassicConstants;
import com.adobe.marketing.mobile.campaignclassic.CampaignClassicExtension;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.StringUtils;

public class CampaignClassic {
	public final static Class<? extends Extension> EXTENSION = CampaignClassicExtension.class;

	private static final String SELF_TAG = "CampaignClassic";
	private static final String NULL_TOKEN_MESSAGE = "The provided token is null or empty";
	private static final String NULL_TRACK_INFO_MESSAGE = "The provided trackInfo map is null or empty";

	private CampaignClassic() {

	}

	/**
	 * Registers the extension with the MobileCore SDK.
	 * <p>
	 * This method should be called only once in your application class.
	 *
	 */
	@Deprecated
	public static void registerExtension() {
		MobileCore.registerExtension(CampaignClassicExtension.class, extensionError -> {
			if (extensionError == null) {
				return;
			}
			Log.error(CampaignClassicConstants.LOG_TAG, SELF_TAG, "There was an error when registering the CampaignClassic extension: %s",
					extensionError.getErrorName());
		});
	}

	/**
	 * Returns the current version of the {@code CampaignClassic} extension.
	 *
	 * @return {@code String} containing the current version
	 */
	public static String extensionVersion() {
		return CampaignClassicConstants.EXTENSION_VERSION;
	}

	/**
	 *  Registers a device with the configured Adobe Campaign Classic server instance.
	 *
	 *  @param token {@code String} containing unique registration token
	 *  @param userKey {@code String} containing the user identifier
	 *  @param additionalParams {@code Map} containing custom key-value pairs to be sent in the registration request
	 */
	public static void registerDevice(@NonNull final String token, final String userKey, final Map<String, Object> additionalParams) {
		if (StringUtils.isNullOrEmpty(token)) {
			Log.error(CampaignClassicConstants.LOG_TAG, SELF_TAG, "Failed to register device for CampaignClassic (%s)", NULL_TOKEN_MESSAGE);
			return;
		}
		final Map<String, Object> eventData = new HashMap<>();
		eventData.put(CampaignClassicConstants.EventDataKeys.CampaignClassic.REGISTER_DEVICE, true);
		eventData.put(CampaignClassicConstants.EventDataKeys.CampaignClassic.DEVICE_TOKEN, token);
		eventData.put(CampaignClassicConstants.EventDataKeys.CampaignClassic.USER_KEY, userKey);
		eventData.put(CampaignClassicConstants.EventDataKeys.CampaignClassic.ADDITIONAL_PARAMETERS, additionalParams);
		final Event event = new Event.Builder("CampaignClassic Register Device",
				EventType.CAMPAIGN,
				EventSource.REQUEST_CONTENT)
				.setEventData(eventData)
				.build();

		MobileCore.dispatchEvent(event);
	}

	/**
	 *  Sends notification tracking information to the configured Adobe Campaign Classic server.
	 *  <p>
	 *  This API may be used to send tracking information when a notification is received on the device. If {@code trackInfo} is null or
	 *  does not contain the necessary tracking identifiers, messageId {@code _mId} and deliveryId {@code _dId}, no track
	 *  request is sent.
	 *
	 *  @param trackInfo {@code Map<String, String>} containing {@code _dId} and {@code _mId} received in the message payload
	 */
	public static void trackNotificationReceive(@NonNull final Map<String, String> trackInfo) {
		if (trackInfo == null || trackInfo.isEmpty()) {
			Log.error(CampaignClassicConstants.LOG_TAG, SELF_TAG, "Failed to track notification receive for CampaignClassic (%s)", NULL_TRACK_INFO_MESSAGE);
			return;
		}

		final Map<String, Object> eventData = new HashMap<>();
		eventData.put(CampaignClassicConstants.EventDataKeys.CampaignClassic.TRACK_RECEIVE, true);
		eventData.put(CampaignClassicConstants.EventDataKeys.CampaignClassic.TRACK_INFO, trackInfo);

		final Event event = new Event.Builder("CampaignClassic Track Notification Receive",
				EventType.CAMPAIGN,
				EventSource.REQUEST_CONTENT)
				.setEventData(eventData)
				.build();
		MobileCore.dispatchEvent(event);
	}

	/**
	 *  Sends notification tracking information to the configured Adobe Campaign Classic server.
	 *  <p>
	 *  This API may be used to send tracking information when user clicks on a notification. If {@code trackInfo} is null or
	 *  does not contain the necessary tracking identifiers, messageId {@code _mId} and deliveryId {@code _dId}, no track
	 *  request is sent.
	 *
	 *  @param trackInfo {@code Map<String, String>} containing {@code _dId} and {@code _mId} received in the message payload
	 */
	public static void trackNotificationClick(@NonNull final Map<String, String> trackInfo) {
		if (trackInfo == null || trackInfo.isEmpty()) {
			Log.error(CampaignClassicConstants.LOG_TAG, SELF_TAG, "Failed to track notification click for CampaignClassic (%s)", NULL_TRACK_INFO_MESSAGE);
			return;
		}

		final Map<String, Object> eventData = new HashMap<>();
		eventData.put(CampaignClassicConstants.EventDataKeys.CampaignClassic.TRACK_CLICK, true);
		eventData.put(CampaignClassicConstants.EventDataKeys.CampaignClassic.TRACK_INFO, trackInfo);

		final Event event = new Event.Builder("CampaignClassic Track Notification Click",
				EventType.CAMPAIGN,
				EventSource.REQUEST_CONTENT)
				.setEventData(eventData).build();
		MobileCore.dispatchEvent(event);
	}


}
