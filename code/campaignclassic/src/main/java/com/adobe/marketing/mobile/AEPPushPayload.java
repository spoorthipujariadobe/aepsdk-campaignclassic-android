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
import androidx.core.app.NotificationCompat;

import com.adobe.marketing.mobile.services.ui.notification.NotificationPriority;
import com.adobe.marketing.mobile.services.ui.notification.NotificationVisibility;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.google.firebase.messaging.RemoteMessage;
import java.util.HashMap;
import java.util.Map;

/**
 * This class validates then stores the {@link Map<String, String>} message data contained in the
 * provided {@link RemoteMessage}.
 */
class AEPPushPayload {
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
    AEPPushPayload(final RemoteMessage message) throws IllegalArgumentException {
        if (message == null) {
            throw new IllegalArgumentException(
                    "Failed to create AEPPushPayload, remote message is null.");
        }
        validateMessageData(message.getData());

        if (!StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.ACC_PAYLOAD_BODY))) {
            messageData.put(CampaignPushConstants.PushPayloadKeys.BODY,
                    String.valueOf(messageData.get(CampaignPushConstants.PushPayloadKeys.ACC_PAYLOAD_BODY)));
        }

        // migrate any ACC push notification object payload keys if needed
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
    AEPPushPayload(final Map<String, String> messageData) throws IllegalArgumentException {
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

        // get the tag from the payload. if no tag was present in the payload use the message id
        // instead as its guaranteed to always be present.
        this.tag =
                !StringUtils.isNullOrEmpty(
                        messageData.get(CampaignPushConstants.PushPayloadKeys.TAG))
                        ? messageData.get(CampaignPushConstants.PushPayloadKeys.TAG)
                        : messageId;
    }

    private void convertNotificationPayloadData(final RemoteMessage.Notification notification) {
        // Migrate the 13 ACC KVP to "adb" prefixed keys.
        // Note, the key value pairs present in the data payload are preferred over the notification
        // key value pairs.
        // The notification key value pairs will only be added to the message data if the
        // corresponding key
        // does not have a value.
        // message.android.notification.icon to adb_small_icon
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
        // _msg to adb_body
        // message.notification.title to adb_title
        // message.notification.image to adb_image

        if (StringUtils.isNullOrEmpty(messageData.get(CampaignPushConstants.PushPayloadKeys.TAG))) {
            this.tag = notification.getTag();
            messageData.put(CampaignPushConstants.PushPayloadKeys.TAG, tag);
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.SMALL_ICON))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.SMALL_ICON, notification.getIcon());
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.SOUND))) {
            messageData.put(CampaignPushConstants.PushPayloadKeys.SOUND, notification.getSound());
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.ACTION_URI))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.ACTION_URI,
                    notification.getClickAction());
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.CHANNEL_ID))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.CHANNEL_ID, notification.getChannelId());
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.TICKER))) {
            messageData.put(CampaignPushConstants.PushPayloadKeys.TICKER, notification.getTicker());
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.STICKY))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.STICKY,
                    String.valueOf(notification.getSticky()));
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.NOTIFICATION_VISIBILITY))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.NOTIFICATION_VISIBILITY,
                    NotificationVisibility.getNotificationVisibility(notification.getVisibility()));
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.NOTIFICATION_PRIORITY))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.NOTIFICATION_PRIORITY,
                    NotificationPriority.getNotificationPriority(notification.getNotificationPriority()));
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.BADGE_NUMBER))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.BADGE_NUMBER,
                    String.valueOf(notification.getNotificationCount()));
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.BODY))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.BODY,
                    String.valueOf(notification.getBody()));
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.TITLE))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.TITLE,
                    String.valueOf(notification.getTitle()));
        }

        if (StringUtils.isNullOrEmpty(
                messageData.get(CampaignPushConstants.PushPayloadKeys.IMAGE_URL))) {
            messageData.put(
                    CampaignPushConstants.PushPayloadKeys.IMAGE_URL,
                    String.valueOf(notification.getImageUrl()));
        }
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