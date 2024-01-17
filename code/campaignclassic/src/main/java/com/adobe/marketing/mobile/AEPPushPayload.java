/*
  Copyright 2023 Adobe. All rights reserved.
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
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/**
 * This class validates then stores the {@link Map<String, String>} message data contained in the
 * provided {@link RemoteMessage}.
 */
public class AEPPushPayload {
    private Map<String, String> messageData;
    private String messageId;
    private String deliveryId;
    private String tag;

    /**
     * Constructor
     *
     * <p>Provides the AEPPushPayload object
     *
     * @param message {@link RemoteMessage} object received from {@link
     *     com.google.firebase.messaging.FirebaseMessagingService}
     * @throws IllegalArgumentException if the message, message data, message id, or delivery id is
     *     null
     */
    public AEPPushPayload(final RemoteMessage message) throws IllegalArgumentException {
        if (message == null) {
            throw new IllegalArgumentException(
                    "Failed to create AEPPushPayload, remote message is null.");
        }
        validateMessageData(message.getData());

        // migrate any ACC push payload keys if needed
        final RemoteMessage.Notification notification = message.getNotification();
        if (notification != null) {
            convertNotificationPayloadData(notification);
        }
    }

    /**
     * Constructor
     *
     * <p>Provides the AEPPushPayload object
     *
     * @param messageData {@link Map<String, String>} containing the message data present in a
     *     notification received from {@link com.google.firebase.messaging.FirebaseMessagingService}
     * @throws IllegalArgumentException if the message data, message id, or delivery id is null
     */
    public AEPPushPayload(final Map<String, String> messageData) throws IllegalArgumentException {
        validateMessageData(messageData);
    }

    private void validateMessageData(final Map<String, String> messageData) {
        if (MapUtils.isNullOrEmpty(messageData)) {
            throw new IllegalArgumentException(
                    "Failed to create AEPPushPayload, remote message data payload is null or"
                            + " empty.");
        }

        messageId = messageData.get(CampaignPushConstants.Tracking.Keys.MESSAGE_ID);
        if (StringUtils.isNullOrEmpty(messageId)) {
            throw new IllegalArgumentException(
                    "Failed to create AEPPushPayload, message id is null or empty.");
        }

        deliveryId = messageData.get(CampaignPushConstants.Tracking.Keys.DELIVERY_ID);
        if (StringUtils.isNullOrEmpty(deliveryId)) {
            throw new IllegalArgumentException(
                    "Failed to create AEPPushPayload, delivery id is null or empty.");
        }

        this.messageData = messageData;
    }

    private void convertNotificationPayloadData(final RemoteMessage.Notification notification) {
        // migrate the 13 ACC KVP to "adb" prefixed keys
        // message.android.notification.icon to adb_icon
        // message.android.notification.sound to adb_sound
        // message.android.notification.tag	to adb_tag
        // message.android.notification.click_action to adb_uri
        // message.android.notification.channel_id to adb_channel_id
        // message.android.notification.ticker to adb_ticker (NEW)
        // message.android.notification.sticky to adb_sticky (NEW)
        // message.android.notification.visibility to adb_n_visibility
        // message.android.notification.notification_priority to adb_n_priority
        // message.android.notification.notification_count to adb_n_count
        // message.notification.body to adb_body
        // message.notification.title to adb_title
        // message.notification.image to adb_image

        tag = notification.getTag();
        messageData.put(CampaignPushConstants.PushPayloadKeys.TAG, tag);
        messageData.put(CampaignPushConstants.PushPayloadKeys.ICON, notification.getIcon());
        messageData.put(CampaignPushConstants.PushPayloadKeys.SOUND, notification.getSound());
        messageData.put(
                CampaignPushConstants.PushPayloadKeys.ACTION_URI, notification.getClickAction());
        messageData.put(
                CampaignPushConstants.PushPayloadKeys.CHANNEL_ID, notification.getChannelId());
        messageData.put(CampaignPushConstants.PushPayloadKeys.TICKER, notification.getTicker());
        messageData.put(
                CampaignPushConstants.PushPayloadKeys.STICKY,
                String.valueOf(notification.getSticky()));
        messageData.put(
                CampaignPushConstants.PushPayloadKeys.NOTIFICATION_VISIBILITY,
                String.valueOf(notification.getVisibility()));
        messageData.put(
                CampaignPushConstants.PushPayloadKeys.NOTIFICATION_PRIORITY,
                String.valueOf(notification.getNotificationPriority()));
        messageData.put(
                CampaignPushConstants.PushPayloadKeys.BADGE_NUMBER,
                String.valueOf(notification.getNotificationCount()));
        messageData.put(CampaignPushConstants.PushPayloadKeys.BODY, notification.getBody());
        messageData.put(CampaignPushConstants.PushPayloadKeys.TITLE, notification.getTitle());
        messageData.put(
                CampaignPushConstants.PushPayloadKeys.IMAGE_URL,
                String.valueOf(notification.getImageUrl()));
    }

    @NonNull Map<String, String> getMessageData() {
        return messageData;
    }

    @NonNull String getMessageId() {
        return messageId;
    }

    @NonNull String getDeliveryId() {
        return deliveryId;
    }

    @NonNull String getTag() {
        return tag;
    }
}
