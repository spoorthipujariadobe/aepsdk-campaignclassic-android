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

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.adobe.marketing.mobile.services.Log;

class LegacyNotificationBuilder {
    private static final String SELF_TAG = "LegacyNotificationBuilder";

    @NonNull static NotificationCompat.Builder construct(
            final AEPPushTemplate pushTemplate, final Context context) {
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Building a legacy style push notification.");
        final String channelId =
                AEPPushNotificationBuilder.createChannelAndGetChannelID(
                        context,
                        pushTemplate.getChannelId(),
                        pushTemplate.getSound(),
                        pushTemplate.getNotificationImportance());
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setAutoCancel(pushTemplate.getNotificationAutoCancel())
                        .setTicker(pushTemplate.getNotificationTicker())
                        .setContentTitle(pushTemplate.getTitle())
                        .setContentText(pushTemplate.getBody())
                        .setNumber(pushTemplate.getBadgeCount())
                        .setPriority(pushTemplate.getNotificationPriority())
                        .setAutoCancel(pushTemplate.getNotificationAutoCancel());

        AEPPushNotificationBuilder.setLargeIcon(
                builder,
                pushTemplate.getImageUrl(),
                pushTemplate.getTitle(),
                pushTemplate.getExpandedBodyText());
        AEPPushNotificationBuilder.setSmallIcon(
                context,
                builder,
                pushTemplate.getIcon(),
                pushTemplate.getSmallIconColor()); // Small Icon must be present, otherwise the
        // notification will not be displayed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AEPPushNotificationBuilder.setVisibility(
                    builder, pushTemplate.getNotificationVisibility());
        }

        AEPPushNotificationBuilder.addActionButtons(
                context,
                builder,
                pushTemplate.getActionButtonsString(),
                pushTemplate.getMessageId(),
                pushTemplate.getDeliveryId()); // Add action buttons if any
        AEPPushNotificationBuilder.setSound(context, builder, pushTemplate.getSound());
        AEPPushNotificationBuilder.setNotificationClickAction(
                context,
                builder,
                pushTemplate.getMessageId(),
                pushTemplate.getDeliveryId(),
                pushTemplate.getActionUri());
        AEPPushNotificationBuilder.setNotificationDeleteAction(
                context, builder, pushTemplate.getMessageId(), pushTemplate.getDeliveryId());

        return builder;
    }
}
