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
import com.adobe.marketing.mobile.util.UrlUtils;
import java.util.ArrayList;

public class CarouselTemplateNotificationBuilder {
    private static final String SELF_TAG = "CarouselTemplateNotificationBuilder";

    @NonNull static Notification build(final CarouselPushTemplate pushTemplate, final Context context) {
        final String channelId =
                AEPPushNotificationBuilder.createChannelAndGetChannelID(pushTemplate, context);
        final String packageName =
                ServiceProvider.getInstance()
                        .getAppContextService()
                        .getApplication()
                        .getPackageName();

        final String carouselOperationMode = pushTemplate.getCarouselOperationMode();

        NotificationCompat.Builder builder;
        if (carouselOperationMode.equals(CampaignPushConstants.DefaultValues.AUTO_CAROUSEL_MODE)) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Building an auto carousel push notification.");
            builder = buildAutoCarouselNotification(pushTemplate, context, channelId, packageName);
        } else {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Building a manual carousel push notification.");
            builder =
                    buildManualCarouselNotification(pushTemplate, context, channelId, packageName);
        }

        return builder.build();
    }

    private static NotificationCompat.Builder buildAutoCarouselNotification(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName) {
        final RemoteViews smallLayout =
                new RemoteViews(context.getPackageName(), R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(context.getPackageName(), R.layout.push_template_auto_carousel);

        // load images into the carousel
        final ArrayList<CarouselPushTemplate.CarouselItem> items = pushTemplate.getCarouselItems();
        for (final CarouselPushTemplate.CarouselItem item : items) {
            final String imageUri = item.getImageUri();
            if (!StringUtils.isNullOrEmpty(imageUri)) {
                if (UrlUtils.isValidUrl(imageUri)) { // we need to download the images first
                    final Bitmap image = CampaignPushUtils.download(item.getImageUri());
                    RemoteViews carouselItem =
                            new RemoteViews(packageName, R.layout.push_template_carousel_item);
                    carouselItem.setImageViewBitmap(R.id.carousel_item_image_view, image);
                    carouselItem.setTextViewText(R.id.carousel_item_caption, item.getCaptionText());
                    expandedLayout.addView(R.id.auto_carousel_view_flipper, carouselItem);
                } else { // if we don't have a url check for bundled app assets
                    final int imageId =
                            context.getResources()
                                    .getIdentifier(item.getImageUri(), "drawable", packageName);
                    RemoteViews carouselItem =
                            new RemoteViews(packageName, R.layout.push_template_carousel_item);
                    carouselItem.setImageViewResource(R.id.carousel_item_image_view, imageId);
                    carouselItem.setTextViewText(R.id.carousel_item_caption, item.getCaptionText());
                    expandedLayout.addView(R.id.auto_carousel_view_flipper, carouselItem);
                }
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
                        R.id.carousel_container_layout,
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
        AEPPushNotificationBuilder.setSound(builder, pushTemplate, context);

        try {
            // sets the icon color
            final String smallIconColor = pushTemplate.getSmallIconColor();
            if (!StringUtils.isNullOrEmpty(smallIconColor)) {
                builder.setColorized(true).setColor(Color.parseColor("#" + smallIconColor));
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

        return builder;
    }

    private static NotificationCompat.Builder buildManualCarouselNotification(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName) {
        final String carouselLayoutType = pushTemplate.getCarouselLayoutType();
        if (carouselLayoutType.equals(
                CampaignPushConstants.DefaultValues.FILMSTRIP_CAROUSEL_MODE)) {
            return buildFilmstripCarouselNotification(
                    pushTemplate, context, channelId, packageName);
        }
        return buildDefaultManualCarouselNotification(
                pushTemplate, context, channelId, packageName);
    }

    private static NotificationCompat.Builder buildFilmstripCarouselNotification(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName) {
        // TODO
        return new NotificationCompat.Builder(context, channelId);
    }

    private static NotificationCompat.Builder buildDefaultManualCarouselNotification(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName) {
        // TODO
        return new NotificationCompat.Builder(context, channelId);
    }
}
