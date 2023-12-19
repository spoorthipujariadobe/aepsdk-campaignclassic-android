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

public class LegacyNotificationBuilder {
    private static final String SELF_TAG = "LegacyNotificationBuilder";

    @NonNull static NotificationCompat.Builder construct(
            final AEPPushTemplate pushTemplate, final Context context) {
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Building a legacy style push notification.");
        final String channelId =
                AEPPushNotificationBuilder.createChannelAndGetChannelID(pushTemplate, context);
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setContentTitle(pushTemplate.getTitle())
                        .setContentText(pushTemplate.getBody())
                        .setNumber(pushTemplate.getBadgeCount())
                        .setPriority(pushTemplate.getNotificationPriority())
                        .setAutoCancel(true);

        AEPPushNotificationBuilder.setLargeIcon(builder, pushTemplate);
        AEPPushNotificationBuilder.setSmallIcon(
                builder,
                pushTemplate,
                context); // Small Icon must be present, otherwise the notification will not be
        // displayed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AEPPushNotificationBuilder.setVisibility(builder, pushTemplate);
        }
        AEPPushNotificationBuilder.addActionButtons(
                builder, pushTemplate, context); // Add action buttons if any
        AEPPushNotificationBuilder.setSound(builder, pushTemplate, context, false);
        AEPPushNotificationBuilder.setNotificationClickAction(builder, pushTemplate, context);
        AEPPushNotificationBuilder.setNotificationDeleteAction(builder, pushTemplate, context);

        return builder;
    }
}
