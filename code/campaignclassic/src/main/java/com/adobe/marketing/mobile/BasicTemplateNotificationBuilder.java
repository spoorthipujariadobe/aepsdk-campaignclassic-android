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

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.adobe.marketing.mobile.campaignclassic.R;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.StringUtils;

public class BasicTemplateNotificationBuilder {
    private static final String SELF_TAG = "BasicTemplateNotificationBuilder";

    @NonNull static Notification build(final BasicPushTemplate pushTemplate, final Context context) {
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

        // get push payload data
        final String imageUrl = pushTemplate.getImageUrl();
        if (!StringUtils.isNullOrEmpty(imageUrl)) {
            final Bitmap image = CampaignPushUtils.download(imageUrl);
            if (image != null) {
                smallLayout.setImageViewBitmap(R.id.template_image, image);
                expandedLayout.setImageViewBitmap(R.id.template_image, image);
            }
        }

        smallLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        smallLayout.setTextViewText(R.id.notification_body, pushTemplate.getBody());
        expandedLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        expandedLayout.setTextViewText(
                R.id.notification_body_ext, pushTemplate.getExpandedBodyText());

        try {
            // get custom color from hex string and set it the notification background
            final String backgroundColorHex = pushTemplate.getNotificationBackgroundColor();
            if (!StringUtils.isNullOrEmpty(backgroundColorHex)) {
                smallLayout.setInt(
                        R.id.basic_small_layout,
                        "setBackgroundColor",
                        Color.parseColor("#" + backgroundColorHex));
                expandedLayout.setInt(
                        R.id.basic_expanded_layout,
                        "setBackgroundColor",
                        Color.parseColor("#" + backgroundColorHex));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unrecognized hex string passed to Color.parseColor(), custom color will not"
                            + " be applied to the notification background.");
        }

        try {
            // get custom color from hex string and set it the notification title
            final String titleColorHex = pushTemplate.getTitleTextColor();
            if (!StringUtils.isNullOrEmpty(titleColorHex)) {
                smallLayout.setInt(
                        R.id.notification_title,
                        "setTextColor",
                        Color.parseColor("#" + titleColorHex));
                expandedLayout.setInt(
                        R.id.notification_title,
                        "setTextColor",
                        Color.parseColor("#" + titleColorHex));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unrecognized hex string passed to Color.parseColor(), custom color will not"
                            + " be applied to the notification title text.");
        }

        try {
            // get custom color from hex string and set it the notification body text
            final String bodyColorHex = pushTemplate.getExpandedBodyTextColor();
            if (!StringUtils.isNullOrEmpty(bodyColorHex)) {
                smallLayout.setInt(
                        R.id.notification_body,
                        "setTextColor",
                        Color.parseColor("#" + bodyColorHex));
                expandedLayout.setInt(
                        R.id.notification_body_ext,
                        "setTextColor",
                        Color.parseColor("#" + bodyColorHex));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unrecognized hex string passed to Color.parseColor(), custom color will not"
                            + " be applied to the notification body text.");
        }

        // Create the notification
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setContentTitle(pushTemplate.getTitle())
                        .setContentText(pushTemplate.getBody())
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

        try {
            // sets the icon color
            final String smallIconColor = pushTemplate.getSmallIconColor();
            if (!StringUtils.isNullOrEmpty(smallIconColor)) {
                builder.setColorized(true).setColor(Color.parseColor(smallIconColor));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unrecognized hex string passed to Color.parseColor(), custom color will not"
                            + " be applied to the notification icon.");
        }

        // if API level is below 26 (prior to notification channels) then notification priority is
        // set on the notification builder
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH)
                    .setVibrate(
                            new long[0]); // hack to enable heads up notifications as a HUD style
            // notification requires a tone or vibration
        }

        return builder.build();
    }
}
