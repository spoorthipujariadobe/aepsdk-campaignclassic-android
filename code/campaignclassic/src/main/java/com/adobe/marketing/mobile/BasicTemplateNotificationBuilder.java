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
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.adobe.marketing.mobile.campaignclassic.R;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheService;

public class BasicTemplateNotificationBuilder {
    private static final String SELF_TAG = "BasicTemplateNotificationBuilder";

    @NonNull static NotificationCompat.Builder construct(
            final BasicPushTemplate pushTemplate, final Context context)
            throws NotificationConstructionFailedException {

        if (pushTemplate == null) {
            throw new NotificationConstructionFailedException(
                    "Invalid push template received, filmstrip carousel notification will not be"
                            + " constructed.");
        }

        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Building a basic template push notification.");
        final String channelId =
                AEPPushNotificationBuilder.createChannelAndGetChannelID(pushTemplate, context);
        final String packageName =
                ServiceProvider.getInstance()
                        .getAppContextService()
                        .getApplication()
                        .getPackageName();
        final RemoteViews smallLayout =
                new RemoteViews(packageName, R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(packageName, R.layout.push_template_expanded);
        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();

        if (cacheService == null) {
            throw new NotificationConstructionFailedException(
                    "Cache service is null, basic push notification will not be constructed.");
        }

        // get push payload data
        final String imageUri = pushTemplate.getImageUrl();
        final Bitmap pushImage = CampaignPushUtils.downloadImage(cacheService, imageUri);
        if (pushImage != null) {
            expandedLayout.setImageViewBitmap(R.id.expanded_template_image, pushImage);
        }

        smallLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        smallLayout.setTextViewText(R.id.notification_body, pushTemplate.getBody());
        expandedLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        expandedLayout.setTextViewText(
                R.id.notification_body_expanded, pushTemplate.getExpandedBodyText());

        // set any custom colors if needed
        AEPPushNotificationBuilder.setCustomNotificationColors(
                pushTemplate, smallLayout, expandedLayout, R.id.basic_expanded_layout);

        // Create the notification
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setNumber(pushTemplate.getBadgeCount())
                        .setAutoCancel(true)
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(smallLayout)
                        .setCustomBigContentView(expandedLayout);

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
        AEPPushNotificationBuilder.setSound(builder, pushTemplate, context);
        AEPPushNotificationBuilder.setNotificationClickAction(builder, pushTemplate, context);
        AEPPushNotificationBuilder.setNotificationDeleteAction(builder, pushTemplate, context);

        // if API level is below 26 (prior to notification channels) then notification priority is
        // set on the notification builder
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVibrate(
                            new long[0]); // hack to enable heads up notifications as a HUD style
            // notification requires a tone or vibration
        }
        return builder;
    }
}
