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

import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/**
 * This class validates then stores the {@link Map<String, String>} message data contained in the
 * provided {@link RemoteMessage}.
 */
public class AEPPushPayload {
    private final Map<String, String> messageData;
    private final String messageId;
    private final String deliveryId;

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

        final Map<String, String> messageData = message.getData();
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

    public AEPPushPayload(final Map<String, String> messageData) {
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

    Map<String, String> getMessageData() {
        return messageData;
    }

    String getMessageId() {
        return messageId;
    }

    String getDeliveryId() {
        return deliveryId;
    }
}
