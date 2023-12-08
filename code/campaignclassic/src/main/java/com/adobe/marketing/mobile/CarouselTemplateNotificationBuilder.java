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

    @NonNull static NotificationCompat.Builder construct(
            final CarouselPushTemplate pushTemplate, final Context context) {
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

        return builder;
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
        int downloadedImageCount = 0;
        String downloadImageUri = null;
        for (final CarouselPushTemplate.CarouselItem item : items) {
            final String imageUri = item.getImageUri();
            if (!StringUtils.isNullOrEmpty(imageUri)) {
                if (UrlUtils.isValidUrl(imageUri)) { // we need to download the images first
                    final RemoteViews carouselItem =
                            new RemoteViews(packageName, R.layout.push_template_carousel_item);
                    final Bitmap image = CampaignPushUtils.download(imageUri);
                    if (image != null) {
                        // scale down the bitmap to 300dp x 200dp as we don't want to use a full
                        // size image due to memory constraints
                        final Bitmap scaledBitmap =
                                Bitmap.createScaledBitmap(
                                        image,
                                        CampaignPushConstants.DefaultValues
                                                .CAROUSEL_MAX_BITMAP_WIDTH,
                                        CampaignPushConstants.DefaultValues
                                                .CAROUSEL_MAX_BITMAP_HEIGHT,
                                        false);
                        carouselItem.setImageViewBitmap(
                                R.id.carousel_item_image_view, scaledBitmap);
                        carouselItem.setTextViewText(
                                R.id.carousel_item_caption, item.getCaptionText());
                        expandedLayout.addView(R.id.auto_carousel_view_flipper, carouselItem);
                        downloadedImageCount++;
                        downloadImageUri = imageUri;
                    }
                }
            }
        }

        // fallback to a basic push template notification builder if only 1 (or less) image was able
        // to be downloaded
        if (downloadedImageCount
                <= CampaignPushConstants.DefaultValues.CAROUSEL_MINIMUM_IMAGE_COUNT) {
            if (!StringUtils.isNullOrEmpty(downloadImageUri)) {
                pushTemplate.modifyData(
                        CampaignPushConstants.PushPayloadKeys.IMAGE_URL, downloadImageUri);
            }
            final BasicPushTemplate basicPushTemplate =
                    new BasicPushTemplate(pushTemplate.getData());
            return BasicTemplateNotificationBuilder.construct(basicPushTemplate, context);
        }

        smallLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        smallLayout.setTextViewText(R.id.notification_body, pushTemplate.getBody());
        expandedLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        expandedLayout.setTextViewText(
                R.id.notification_body_expanded, pushTemplate.getExpandedBodyText());

        // get custom color from hex string and set it the notification background
        final String backgroundColorHex = pushTemplate.getNotificationBackgroundColor();
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                R.id.basic_small_layout,
                "#" + backgroundColorHex,
                "setBackgroundColor",
                "notification background");
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                R.id.carousel_container_layout,
                "#" + backgroundColorHex,
                "setBackgroundColor",
                "notification background");

        // get custom color from hex string and set it the notification title
        final String titleColorHex = pushTemplate.getTitleTextColor();
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                R.id.notification_title,
                "#" + titleColorHex,
                "setTextColor",
                "notification title");
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                R.id.notification_title,
                "#" + titleColorHex,
                "setTextColor",
                "notification title");

        // get custom color from hex string and set it the notification body text
        final String bodyColorHex = pushTemplate.getExpandedBodyTextColor();
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                R.id.notification_body,
                "#" + bodyColorHex,
                "setTextColor",
                "notification body text");
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                R.id.notification_body_expanded,
                "#" + bodyColorHex,
                "setTextColor",
                "notification body text");

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
