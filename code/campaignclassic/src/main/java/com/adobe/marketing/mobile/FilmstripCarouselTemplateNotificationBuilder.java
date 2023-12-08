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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;

public class FilmstripCarouselTemplateNotificationBuilder {

    static NotificationCompat.Builder construct(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName) {

        final RemoteViews smallLayout = new RemoteViews(packageName, com.adobe.marketing.mobile.campaignclassic.R.layout.push_template_collapsed);
        final RemoteViews expandedLayout = new RemoteViews(packageName, com.adobe.marketing.mobile.campaignclassic.R.layout.push_template_filmstrip_carousel);

        // download the carousel images
        final List<CarouselPushTemplate.CarouselItem> items = pushTemplate.getCarouselItems();
        final List<Bitmap> downloadedImages = new ArrayList<>();
        String downloadImageUri = null;
        for (final CarouselPushTemplate.CarouselItem item : items) {
            final String imageUri = item.getImageUri();
            if (!StringUtils.isNullOrEmpty(imageUri)) {
                if (UrlUtils.isValidUrl(imageUri)) { // we need to download the images first
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
                        downloadedImages.add(scaledBitmap);
                        downloadImageUri = imageUri;
                    }
                }
            }
        }

        // fallback to a basic push template notification builder if less than 3 images were able
        // to be downloaded
        if (downloadedImages.size()
                <= CampaignPushConstants.DefaultValues.FILMSTRIP_CAROUSEL_MINIMUM_IMAGE_COUNT) {
            if (!StringUtils.isNullOrEmpty(downloadImageUri)) {
                pushTemplate.modifyData(
                        CampaignPushConstants.PushPayloadKeys.IMAGE_URL, downloadImageUri);
            }
            final BasicPushTemplate basicPushTemplate =
                    new BasicPushTemplate(pushTemplate.getData());
            return BasicTemplateNotificationBuilder.construct(basicPushTemplate, context);
        }

        final String titleText = pushTemplate.getTitle();
        final String smallBodyText = pushTemplate.getBody();
        final String expandedBodyText = pushTemplate.getExpandedBodyText();
        smallLayout.setTextViewText(com.adobe.marketing.mobile.campaignclassic.R.id.notification_title, titleText);
        smallLayout.setTextViewText(com.adobe.marketing.mobile.campaignclassic.R.id.notification_body, smallBodyText);
        expandedLayout.setTextViewText(com.adobe.marketing.mobile.campaignclassic.R.id.notification_title, titleText);
        expandedLayout.setTextViewText(com.adobe.marketing.mobile.campaignclassic.R.id.notification_body_expanded, expandedBodyText);

        // get all captions present then set center caption text
        final String leftCaptionText = items.get(0).getCaptionText();
        final String centerCaptionText = items.get(1).getCaptionText();
        final String rightCaptionText = items.get(2).getCaptionText();
        expandedLayout.setTextViewText(com.adobe.marketing.mobile.campaignclassic.R.id.manual_carousel_filmstrip_caption, centerCaptionText);

        // set the downloaded bitmaps in the filmstrip image views
        expandedLayout.setImageViewBitmap(com.adobe.marketing.mobile.campaignclassic.R.id.manual_carousel_filmstrip_left, downloadedImages.get(0));
        expandedLayout.setImageViewBitmap(com.adobe.marketing.mobile.campaignclassic.R.id.manual_carousel_filmstrip_center, downloadedImages.get(1));
        expandedLayout.setImageViewBitmap(com.adobe.marketing.mobile.campaignclassic.R.id.manual_carousel_filmstrip_right, downloadedImages.get(2));

        // get custom color from hex string and set it the notification background
        final String backgroundColorHex = pushTemplate.getNotificationBackgroundColor();
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                com.adobe.marketing.mobile.campaignclassic.R.id.basic_small_layout,
                "#" + backgroundColorHex,
                "setBackgroundColor",
                "notification background");
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                com.adobe.marketing.mobile.campaignclassic.R.id.carousel_container_layout,
                "#" + backgroundColorHex,
                "setBackgroundColor",
                "notification background");

        // get custom color from hex string and set it the notification title
        final String titleColorHex = pushTemplate.getTitleTextColor();
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                com.adobe.marketing.mobile.campaignclassic.R.id.notification_title,
                "#" + titleColorHex,
                "setTextColor",
                "notification title");
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                com.adobe.marketing.mobile.campaignclassic.R.id.notification_title,
                "#" + titleColorHex,
                "setTextColor",
                "notification title");

        // get custom color from hex string and set it the notification body text
        final String bodyColorHex = pushTemplate.getExpandedBodyTextColor();
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                com.adobe.marketing.mobile.campaignclassic.R.id.notification_body,
                "#" + bodyColorHex,
                "setTextColor",
                "notification body text");
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                com.adobe.marketing.mobile.campaignclassic.R.id.notification_body_expanded,
                "#" + bodyColorHex,
                "setTextColor",
                "notification body text");

        // handle left and right navigation buttons
        final Intent leftButtonIntent = new Intent(CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED, null, context, AEPPushTemplateBroadcastReceiver.class);
        leftButtonIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        leftButtonIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.BACKGROUND_COLOR, backgroundColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_COLOR, titleColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_COLOR, bodyColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_IMAGE, downloadedImages.get(0));
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_IMAGE, downloadedImages.get(1));
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_IMAGE, downloadedImages.get(2));
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_CAPTION, leftCaptionText);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_CAPTION, centerCaptionText);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_CAPTION, rightCaptionText);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_TITLE_TEXT, titleText);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_BODY_TEXT, smallBodyText);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);

        final Intent rightButtonIntent = new Intent(CampaignPushConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED, null, context, AEPPushTemplateBroadcastReceiver.class);
        rightButtonIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        rightButtonIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.BACKGROUND_COLOR, backgroundColorHex);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_COLOR, titleColorHex);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_COLOR, bodyColorHex);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_IMAGE, downloadedImages.get(0));
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_IMAGE, downloadedImages.get(1));
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_IMAGE, downloadedImages.get(2));
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_CAPTION, leftCaptionText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_CAPTION, centerCaptionText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_CAPTION, rightCaptionText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_TITLE_TEXT, titleText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_BODY_TEXT, smallBodyText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);

        final PendingIntent pendingIntentLeftButton = PendingIntent.getBroadcast(context, 0, leftButtonIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        final PendingIntent pendingIntentRightButton = PendingIntent.getBroadcast(context, 0, rightButtonIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        expandedLayout.setOnClickPendingIntent(com.adobe.marketing.mobile.campaignclassic.R.id.leftImageButton, pendingIntentLeftButton);
        expandedLayout.setOnClickPendingIntent(com.adobe.marketing.mobile.campaignclassic.R.id.rightImageButton, pendingIntentRightButton);

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
        AEPPushNotificationBuilder.setSound(builder, pushTemplate, context);

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
