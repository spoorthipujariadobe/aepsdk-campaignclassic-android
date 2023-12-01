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

import com.adobe.marketing.mobile.services.Log;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Objects;

/**
 * This class is used to create push notification payload objects from a remote message.
 * It provides with functions for getting attributes of push payload (title, body, actions etc ...)
 */
public class AEPPushPayload {
    static final String SELF_TAG = "AEPPushPayload";

    // POJO for remote message data
    private AEPPushTemplate pushTemplate;
    // Push template type enum
    private PushTemplateType pushTemplateType;

    /**
     * Constructor
     * <p>
     * Provides the AEPPushPayload object
     *
     * @param message {@link RemoteMessage} object received from {@link com.google.firebase.messaging.FirebaseMessagingService}
     */
    public AEPPushPayload(final RemoteMessage message) {
        if (message == null) {
            Log.error(CampaignPushConstants.LOG_TAG, SELF_TAG, "Failed to create AEPPushPayload, remote message is null");
            return;
        }

        final Map<String, String> messageData = message.getData();
        if (MapUtils.isNullOrEmpty(messageData)) {
            Log.error(CampaignPushConstants.LOG_TAG, SELF_TAG, "Failed to create AEPPushPayload, remote message data payload is null or empty");
            return;
        }

        final String messageId = messageData.get(CampaignPushConstants.Tracking.Keys.MESSAGE_ID);
        if (StringUtils.isNullOrEmpty(messageId)) {
            Log.error(CampaignPushConstants.LOG_TAG, SELF_TAG, "Failed to create AEPPushPayload, message id is null or empty");
            return;
        }

        final String deliveryId = messageData.get(CampaignPushConstants.Tracking.Keys.DELIVERY_ID);
        if (StringUtils.isNullOrEmpty(deliveryId)) {
            Log.error(CampaignPushConstants.LOG_TAG, SELF_TAG, "Failed to create AEPPushPayload, delivery id is null or empty");
            return;
        }

        pushTemplate = init(messageData);
    }

    @NonNull
    private AEPPushTemplate init(final Map<String, String> data) {
        this.pushTemplateType = PushTemplateType.fromInt(Integer.parseInt(Objects.requireNonNull(data.get(CampaignPushConstants.PushPayloadKeys.TEMPLATE_TYPE))));
        AEPPushTemplate aepPushTemplate = null;
        if (!pushTemplateType.equals(PushTemplateType.UNKNOWN)) {
            switch (pushTemplateType) {
                case BASIC:
                    aepPushTemplate = new BasicPushTemplate(data);
                    break;
                case AUTO_CAROUSEL:
                case MANUAL_CAROUSEL:
                    aepPushTemplate = new CarouselPushTemplate(data);
                    break;
                case INPUT_BOX:
                    aepPushTemplate = new InputBoxPushTemplate(data);
                    break;
                case LEGACY:
                    aepPushTemplate = new LegacyPushTemplate(data);
                    break;
            }
        } else {
            aepPushTemplate = new LegacyPushTemplate(data);
        }
        return aepPushTemplate;
    }

    public AEPPushTemplate getPushTemplate() {
        return pushTemplate;
    }

    public PushTemplateType getPushTemplateType() {
        return pushTemplateType;
    }
}
