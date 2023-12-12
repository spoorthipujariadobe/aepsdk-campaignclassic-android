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
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.adobe.marketing.mobile.campaignclassic.R;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AEPPushTemplateBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals(CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED)
                || intent.getAction()
                        .equals(CampaignPushConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED)) {
            handleFilmstripNotificationIntent(context, intent);
        }
    }

    private void handleFilmstripNotificationIntent(final Context context, final Intent intent) {
        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);
        final String packageName =
                ServiceProvider.getInstance()
                        .getAppContextService()
                        .getApplication()
                        .getPackageName();

        // get filmstrip notification values from the intent extras
        final Bundle intentExtras = intent.getExtras();
        final String backgroundColorHex =
                intentExtras.getString(CampaignPushConstants.IntentKeys.BACKGROUND_COLOR);
        final String titleColorHex =
                intentExtras.getString(CampaignPushConstants.IntentKeys.TITLE_COLOR);
        final String bodyColorHex =
                intentExtras.getString(CampaignPushConstants.IntentKeys.BODY_COLOR);
        final String iconColorHex =
                intentExtras.getString(CampaignPushConstants.IntentKeys.ICON_COLOR);
        final String titleText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.SMALL_TITLE_TEXT);
        final String smallBodyText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.SMALL_BODY_TEXT);
        final String expandedBodyText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT);
        final String leftImageUri =
                intentExtras.getString(CampaignPushConstants.IntentKeys.LEFT_IMAGE);
        final String centerImageUri =
                intentExtras.getString(CampaignPushConstants.IntentKeys.CENTER_IMAGE);
        final String rightImageUri =
                intentExtras.getString(CampaignPushConstants.IntentKeys.RIGHT_IMAGE);
        final String leftCaptionText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.LEFT_CAPTION);
        final String centerCaptionText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.CENTER_CAPTION);
        final String rightCaptionText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.RIGHT_CAPTION);
        final String channelId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.CHANNEL_ID);
        final String messageId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.MESSAGE_ID);

        final Map<String, String> captionMap =
                new HashMap<String, String>() {
                    {
                        put(leftImageUri, leftCaptionText);
                        put(centerImageUri, centerCaptionText);
                        put(rightImageUri, rightCaptionText);
                    }
                };

        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();
        final String assetCacheLocation = CampaignPushUtils.getAssetCacheLocation();
        final ArrayList<Bitmap> cachedImages = new ArrayList<>();
        if (cacheService != null) {
            if (!StringUtils.isNullOrEmpty(leftImageUri)) {
                final CacheResult cachedLeftImage =
                        cacheService.get(assetCacheLocation, leftImageUri);
                if (cachedLeftImage != null) {
                    cachedImages.add(BitmapFactory.decodeStream(cachedLeftImage.getData()));
                }
            }

            if (!StringUtils.isNullOrEmpty(centerImageUri)) {
                final CacheResult cachedCenterImage =
                        cacheService.get(assetCacheLocation, centerImageUri);
                if (cachedCenterImage != null) {
                    cachedImages.add(BitmapFactory.decodeStream(cachedCenterImage.getData()));
                }
            }

            if (!StringUtils.isNullOrEmpty(rightImageUri)) {
                final CacheResult cachedRightImage =
                        cacheService.get(assetCacheLocation, rightImageUri);
                if (cachedRightImage != null) {
                    cachedImages.add(BitmapFactory.decodeStream(cachedRightImage.getData()));
                }
            }
        }

        final RemoteViews smallLayout =
                new RemoteViews(packageName, R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(packageName, R.layout.push_template_filmstrip_carousel);
        smallLayout.setTextViewText(R.id.notification_title, titleText);
        smallLayout.setTextViewText(R.id.notification_body, smallBodyText);
        expandedLayout.setTextViewText(R.id.notification_title, titleText);
        expandedLayout.setTextViewText(R.id.notification_body_expanded, expandedBodyText);

        final String action = intent.getAction();
        String newLeftCaption;
        String newCenterCaption;
        String newRightCaption;
        Bitmap newLeftImage;
        Bitmap newCenterImage;
        Bitmap newRightImage;
        String newLeftImageUri;
        String newCenterImageUri;
        String newRightImageUri;

        if (action.equals(CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED)) {
            newCenterImage = cachedImages.get(0);
            newRightImage = cachedImages.get(1);
            newLeftImage = cachedImages.get(2);
            newCenterImageUri = leftImageUri;
            newRightImageUri = centerImageUri;
            newLeftImageUri = rightImageUri;
            newLeftCaption = captionMap.get(rightImageUri);
            newCenterCaption = captionMap.get(leftImageUri);
            newRightCaption = captionMap.get(centerImageUri);
        } else {
            newCenterImage = cachedImages.get(2);
            newRightImage = cachedImages.get(0);
            newLeftImage = cachedImages.get(1);
            newCenterImageUri = rightImageUri;
            newRightImageUri = leftImageUri;
            newLeftImageUri = centerImageUri;
            newLeftCaption = captionMap.get(centerImageUri);
            newCenterCaption = captionMap.get(rightImageUri);
            newRightCaption = captionMap.get(leftImageUri);
        }

        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_center, newCenterImage);
        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_left, newLeftImage);
        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_right, newRightImage);
        expandedLayout.setTextViewText(R.id.manual_carousel_filmstrip_caption, newCenterCaption);

        // get custom color from hex string and set it the notification background
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                R.id.basic_small_layout,
                "#" + backgroundColorHex,
                CampaignPushConstants.MethodNames.SET_BACKGROUND_COLOR,
                CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BACKGROUND);
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                R.id.carousel_container_layout,
                "#" + backgroundColorHex,
                CampaignPushConstants.MethodNames.SET_BACKGROUND_COLOR,
                CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BACKGROUND);

        // get custom color from hex string and set it the notification title
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                R.id.notification_title,
                "#" + titleColorHex,
                CampaignPushConstants.MethodNames.SET_TEXT_COLOR,
                CampaignPushConstants.FriendlyViewNames.NOTIFICATION_TITLE);
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                R.id.notification_title,
                "#" + titleColorHex,
                CampaignPushConstants.MethodNames.SET_TEXT_COLOR,
                CampaignPushConstants.FriendlyViewNames.NOTIFICATION_TITLE);

        // get custom color from hex string and set it the notification body text
        AEPPushNotificationBuilder.setElementColor(
                smallLayout,
                R.id.notification_body,
                "#" + bodyColorHex,
                CampaignPushConstants.MethodNames.SET_TEXT_COLOR,
                CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BODY_TEXT);
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                R.id.notification_body_expanded,
                "#" + bodyColorHex,
                CampaignPushConstants.MethodNames.SET_TEXT_COLOR,
                CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BODY_TEXT);

        // handle left and right navigation buttons
        final Intent leftButtonIntent =
                new Intent(
                        CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED,
                        null,
                        context,
                        AEPPushTemplateBroadcastReceiver.class);
        leftButtonIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        leftButtonIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        leftButtonIntent.putExtra(
                CampaignPushConstants.IntentKeys.BACKGROUND_COLOR, backgroundColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_COLOR, titleColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_COLOR, bodyColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.ICON_COLOR, iconColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_IMAGE, newLeftImageUri);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_IMAGE, newCenterImageUri);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_IMAGE, newRightImageUri);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_CAPTION, newLeftCaption);
        leftButtonIntent.putExtra(
                CampaignPushConstants.IntentKeys.CENTER_CAPTION, newCenterCaption);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_CAPTION, newRightCaption);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_TITLE_TEXT, titleText);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_BODY_TEXT, smallBodyText);
        leftButtonIntent.putExtra(
                CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.MESSAGE_ID, messageId);

        final Intent rightButtonIntent =
                new Intent(
                        CampaignPushConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED,
                        null,
                        context,
                        AEPPushTemplateBroadcastReceiver.class);
        rightButtonIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        rightButtonIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        rightButtonIntent.putExtra(
                CampaignPushConstants.IntentKeys.BACKGROUND_COLOR, backgroundColorHex);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_COLOR, titleColorHex);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_COLOR, bodyColorHex);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.ICON_COLOR, iconColorHex);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_IMAGE, newLeftImageUri);
        rightButtonIntent.putExtra(
                CampaignPushConstants.IntentKeys.CENTER_IMAGE, newCenterImageUri);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_IMAGE, newRightImageUri);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_CAPTION, newLeftCaption);
        rightButtonIntent.putExtra(
                CampaignPushConstants.IntentKeys.CENTER_CAPTION, newCenterCaption);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_CAPTION, newRightCaption);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_TITLE_TEXT, titleText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_BODY_TEXT, smallBodyText);
        rightButtonIntent.putExtra(
                CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.MESSAGE_ID, messageId);

        final PendingIntent pendingIntentLeftButton =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        leftButtonIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        final PendingIntent pendingIntentRightButton =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        rightButtonIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        expandedLayout.setOnClickPendingIntent(R.id.leftImageButton, pendingIntentLeftButton);
        expandedLayout.setOnClickPendingIntent(R.id.rightImageButton, pendingIntentRightButton);

        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(smallLayout)
                        .setCustomBigContentView(expandedLayout);

        final int smallIconResourceId = CampaignPushUtils.getSmallIconIdFromDatastore();
        AEPPushNotificationBuilder.setSmallIcon(
                builder,
                iconColorHex,
                smallIconResourceId); // Small Icon must be present, otherwise the notification will
        // not be
        // displayed.

        final Notification notification = builder.build();

        notificationManager.notify(messageId.hashCode(), notification);
    }
}
