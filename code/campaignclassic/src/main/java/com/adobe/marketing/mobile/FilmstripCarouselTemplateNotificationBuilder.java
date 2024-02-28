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
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.util.StringUtils;
import com.google.android.gms.common.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;

class FilmstripCarouselTemplateNotificationBuilder {
    private static final String SELF_TAG = "FilmstripCarouselTemplateNotificationBuilder";

    static NotificationCompat.Builder construct(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName)
            throws NotificationConstructionFailedException {
        if (pushTemplate == null) {
            throw new NotificationConstructionFailedException(
                    "Invalid push template received, filmstrip carousel notification will not be"
                            + " constructed.");
        }

        return createNotificationBuilder(context, pushTemplate, packageName, channelId);
    }

    static void handleIntent(final Context context, final Intent intent) {
        final Bundle intentExtras = intent.getExtras();
        if (intentExtras == null) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Intent extras are null, will not handle the manual filmstrip intent with"
                            + " action %s",
                    intent.getAction());
            return;
        }

        try {
            final NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(context);
            final Notification notification = createNotificationBuilder(context, intent).build();

            // get the tag from the intent extras. if no tag was present in the payload use the
            // message id instead as its guaranteed to always be present.
            final String tag =
                    !StringUtils.isNullOrEmpty(
                                    intentExtras.getString(CampaignPushConstants.IntentKeys.TAG))
                            ? intentExtras.getString(CampaignPushConstants.IntentKeys.TAG)
                            : intentExtras.getString(CampaignPushConstants.IntentKeys.MESSAGE_ID);
            notificationManager.notify(tag.hashCode(), notification);
        } catch (final NotificationConstructionFailedException exception) {
            Log.error(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Failed to create a push notification, a notification construction failed"
                            + " exception occurred: %s",
                    exception.getLocalizedMessage());
        }
    }

    // TODO: migrate logic of building the notification to a common class to be used by
    // FilmstripCarouselTemplateNotificationBuilder and ManualCarouselTemplateNotificationBuilder
    private static NotificationCompat.Builder createNotificationBuilder(
            final Context context,
            final CarouselPushTemplate pushTemplate,
            final String packageName,
            final String channelId)
            throws NotificationConstructionFailedException {

        final RemoteViews smallLayout =
                new RemoteViews(packageName, R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(packageName, R.layout.push_template_filmstrip_carousel);
        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();

        if (cacheService == null) {
            throw new NotificationConstructionFailedException(
                    "Cache service is null, filmstrip carousel notification will not be"
                            + " constructed.");
        }

        // download the carousel images and populate the image uri, image caption, and image click
        // action arrays
        final int centerImageIndex =
                CampaignPushConstants.DefaultValues.CENTER_INDEX; // center index defaults to 1
        final long imageProcessingStartTime = System.currentTimeMillis();
        final List<CarouselPushTemplate.CarouselItem> items = pushTemplate.getCarouselItems();
        final ArrayList<Bitmap> downloadedImages = new ArrayList<>();
        final ArrayList<String> downloadedImageUris = new ArrayList<>();
        final ArrayList<String> imageCaptions = new ArrayList<>();
        final ArrayList<String> imageClickActions = new ArrayList<>();

        for (final CarouselPushTemplate.CarouselItem item : items) {
            final String imageUri = item.getImageUri();
            final Bitmap pushImage = CampaignPushUtils.downloadImage(cacheService, imageUri);
            if (pushImage == null) {
                Log.trace(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Failed to retrieve an image from %s, will not create a new carousel item.",
                        imageUri);
                break;
            }
            downloadedImages.add(pushImage);
            downloadedImageUris.add(imageUri);
            imageCaptions.add(item.getCaptionText());
            imageClickActions.add(item.getInteractionUri());
        }

        // log time needed to process the carousel images
        final long imageProcessingElapsedTime =
                System.currentTimeMillis() - imageProcessingStartTime;
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Processed %d manual filmstrip carousel image(s) in %d milliseconds.",
                downloadedImageUris.size(),
                imageProcessingElapsedTime);

        // fallback to a basic push template notification builder if less than 3 images were able
        // to be downloaded
        if (downloadedImageUris.size()
                < CampaignPushConstants.DefaultValues.CAROUSEL_MINIMUM_IMAGE_COUNT) {
            return CarouselTemplateNotificationBuilder.fallbackToBasicNotification(
                    context, pushTemplate, downloadedImageUris);
        }

        final String titleText = pushTemplate.getTitle();
        final String smallBodyText = pushTemplate.getBody();
        final String expandedBodyText = pushTemplate.getExpandedBodyText();
        smallLayout.setTextViewText(R.id.notification_title, titleText);
        smallLayout.setTextViewText(R.id.notification_body, smallBodyText);
        expandedLayout.setTextViewText(R.id.notification_title, titleText);
        expandedLayout.setTextViewText(R.id.notification_body_expanded, expandedBodyText);

        // get all captions present then set center caption text
        final String centerCaptionText = imageCaptions.get(centerImageIndex);
        expandedLayout.setTextViewText(R.id.manual_carousel_filmstrip_caption, centerCaptionText);

        // set the downloaded bitmaps in the filmstrip image views
        expandedLayout.setImageViewBitmap(
                R.id.manual_carousel_filmstrip_left, downloadedImages.get(0));
        expandedLayout.setImageViewBitmap(
                R.id.manual_carousel_filmstrip_center, downloadedImages.get(1));
        expandedLayout.setImageViewBitmap(
                R.id.manual_carousel_filmstrip_right, downloadedImages.get(2));

        // assign a click action pending intent to the center image view
        AEPPushNotificationBuilder.setRemoteViewClickAction(
                context,
                expandedLayout,
                R.id.manual_carousel_filmstrip_center,
                pushTemplate.getMessageId(),
                pushTemplate.getDeliveryId(),
                imageClickActions.get(centerImageIndex),
                pushTemplate.getNotificationTag(),
                pushTemplate.isNotificationSticky());

        // set any custom colors if needed
        AEPPushNotificationBuilder.setCustomNotificationColors(
                pushTemplate.getNotificationBackgroundColor(),
                pushTemplate.getTitleTextColor(),
                pushTemplate.getExpandedBodyTextColor(),
                smallLayout,
                expandedLayout,
                R.id.carousel_container_layout);

        // handle left and right navigation buttons
        final Intent clickIntent =
                new Intent(
                        CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED,
                        null,
                        context,
                        AEPPushTemplateBroadcastReceiver.class);
        clickIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        clickIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.CUSTOM_SOUND, pushTemplate.getSound());
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_IMAGE_INDEX, centerImageIndex);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.IMAGE_URLS, downloadedImageUris);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.IMAGE_CAPTIONS, imageCaptions);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.IMAGE_CLICK_ACTIONS, imageClickActions);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_TEXT, titleText);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_TEXT, smallBodyText);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.NOTIFICATION_BACKGROUND_COLOR,
                pushTemplate.getNotificationBackgroundColor());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.TITLE_TEXT_COLOR,
                pushTemplate.getTitleTextColor());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT_COLOR,
                pushTemplate.getExpandedBodyTextColor());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.MESSAGE_ID, pushTemplate.getMessageId());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.DELIVERY_ID, pushTemplate.getDeliveryId());
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_ICON, pushTemplate.getIcon());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.SMALL_ICON_COLOR,
                pushTemplate.getSmallIconColor());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.VISIBILITY,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? pushTemplate.getNotificationVisibility()
                        : pushTemplate.getNotificationPriority());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.IMPORTANCE,
                pushTemplate.getNotificationImportance());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.TICKER, pushTemplate.getNotificationTicker());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.TAG, pushTemplate.getNotificationTag());
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.STICKY, pushTemplate.isNotificationSticky());

        final PendingIntent pendingIntentLeftButton =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        clickIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        clickIntent.setAction(CampaignPushConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED);
        final PendingIntent pendingIntentRightButton =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        clickIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // set onclick intents for the skip left and skip right buttons
        expandedLayout.setOnClickPendingIntent(R.id.leftImageButton, pendingIntentLeftButton);
        expandedLayout.setOnClickPendingIntent(R.id.rightImageButton, pendingIntentRightButton);

        // Create the notification
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setTicker(pushTemplate.getNotificationTicker())
                        .setNumber(pushTemplate.getBadgeCount())
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(smallLayout)
                        .setCustomBigContentView(expandedLayout);

        // set custom sound, note this applies to API 25 and lower only as API 26 and up set the
        // sound on the notification channel
        AEPPushNotificationBuilder.setSound(context, builder, pushTemplate.getSound());

        // small Icon must be present, otherwise the notification will not be displayed.
        AEPPushNotificationBuilder.setSmallIcon(
                context, builder, pushTemplate.getIcon(), pushTemplate.getSmallIconColor());

        // set notification visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AEPPushNotificationBuilder.setVisibility(
                    builder, pushTemplate.getNotificationVisibility());
        }

        // set notification delete action
        AEPPushNotificationBuilder.setNotificationDeleteAction(
                context, builder, pushTemplate.getMessageId(), pushTemplate.getDeliveryId());

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

    private static NotificationCompat.Builder createNotificationBuilder(
            final Context context, final Intent intent)
            throws NotificationConstructionFailedException {
        final Bundle intentExtras = intent.getExtras();
        if (intentExtras == null) {
            throw new NotificationConstructionFailedException(
                    "Intent extras are null, will not create a notification from the received"
                            + " intent with action "
                            + intent.getAction());
        }

        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();
        if (cacheService == null) {
            throw new NotificationConstructionFailedException(
                    "Cache service is null, filmstrip carousel notification will not be"
                            + " constructed.");
        }

        final String assetCacheLocation = CampaignPushUtils.getAssetCacheLocation();
        final String packageName =
                ServiceProvider.getInstance()
                        .getAppContextService()
                        .getApplication()
                        .getPackageName();

        // get filmstrip notification values from the intent extras
        final String messageId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.MESSAGE_ID);
        final String deliveryId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.DELIVERY_ID);
        final String channelId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.CHANNEL_ID);
        final int badgeCount = intentExtras.getInt(CampaignPushConstants.IntentKeys.BADGE_COUNT);
        final int visibility = intentExtras.getInt(CampaignPushConstants.IntentKeys.VISIBILITY);
        final int importance = intentExtras.getInt(CampaignPushConstants.IntentKeys.IMPORTANCE);
        final ArrayList<Bitmap> cachedImages = new ArrayList<>();
        final ArrayList<String> imageUrls =
                (ArrayList<String>) intentExtras.get(CampaignPushConstants.IntentKeys.IMAGE_URLS);
        final ArrayList<String> imageCaptions =
                (ArrayList<String>)
                        intentExtras.get(CampaignPushConstants.IntentKeys.IMAGE_CAPTIONS);
        final ArrayList<String> imageClickActions =
                (ArrayList<String>)
                        intentExtras.get(CampaignPushConstants.IntentKeys.IMAGE_CLICK_ACTIONS);
        final String titleText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.TITLE_TEXT);
        final String bodyText = intentExtras.getString(CampaignPushConstants.IntentKeys.BODY_TEXT);
        final String expandedBodyText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT);
        final String notificationBackgroundColor =
                intentExtras.getString(
                        CampaignPushConstants.IntentKeys.NOTIFICATION_BACKGROUND_COLOR);
        final String titleTextColor =
                intentExtras.getString(CampaignPushConstants.IntentKeys.TITLE_TEXT_COLOR);
        final String expandedBodyTextColor =
                intentExtras.getString(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT_COLOR);
        final String smallIcon =
                intentExtras.getString(CampaignPushConstants.IntentKeys.SMALL_ICON);
        final String smallIconColor =
                intentExtras.getString(CampaignPushConstants.IntentKeys.SMALL_ICON_COLOR);
        final String customSound =
                intentExtras.getString(CampaignPushConstants.IntentKeys.CUSTOM_SOUND);
        final String ticker = intentExtras.getString(CampaignPushConstants.IntentKeys.TICKER);
        final String tag = intentExtras.getString(CampaignPushConstants.IntentKeys.TAG);
        final boolean sticky = intentExtras.getBoolean(CampaignPushConstants.IntentKeys.STICKY);

        // as we are handling an intent, the image URLS should already be cached
        if (cacheService != null && !CollectionUtils.isEmpty(imageUrls)) {
            for (final String imageUri : imageUrls) {
                if (!StringUtils.isNullOrEmpty(imageUri)) {
                    final CacheResult cacheResult = cacheService.get(assetCacheLocation, imageUri);
                    if (cacheResult != null) {
                        cachedImages.add(BitmapFactory.decodeStream(cacheResult.getData()));
                    }
                }
            }
        }

        final RemoteViews smallLayout =
                new RemoteViews(packageName, R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(packageName, R.layout.push_template_filmstrip_carousel);
        smallLayout.setTextViewText(R.id.notification_title, titleText);
        smallLayout.setTextViewText(R.id.notification_body, bodyText);
        expandedLayout.setTextViewText(R.id.notification_title, titleText);
        expandedLayout.setTextViewText(R.id.notification_body_expanded, expandedBodyText);

        final String action = intent.getAction();
        int centerImageIndex =
                intentExtras.getInt(CampaignPushConstants.IntentKeys.CENTER_IMAGE_INDEX);
        final List<Integer> newIndices =
                CampaignPushUtils.calculateNewIndices(centerImageIndex, imageUrls.size(), action);

        String newCenterCaption;
        Bitmap newLeftImage;
        Bitmap newCenterImage;
        Bitmap newRightImage;
        int newCenterIndex;
        int newLeftIndex;
        int newRightIndex;
        if (newIndices == null) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unable to calculate new left, center, and right indices. Using default center"
                            + " image index of 1.");
            newCenterIndex = CampaignPushConstants.DefaultValues.CENTER_INDEX;
            newLeftImage = cachedImages.get(CampaignPushConstants.DefaultValues.CENTER_INDEX - 1);
            newCenterImage = cachedImages.get(CampaignPushConstants.DefaultValues.CENTER_INDEX);
            newRightImage = cachedImages.get(CampaignPushConstants.DefaultValues.CENTER_INDEX + 1);
            newCenterCaption = imageCaptions.get(CampaignPushConstants.DefaultValues.CENTER_INDEX);
        } else {
            newLeftIndex = newIndices.get(0);
            newCenterIndex = newIndices.get(1);
            newRightIndex = newIndices.get(2);
            newCenterImage = cachedImages.get(newCenterIndex);
            newLeftImage = cachedImages.get(newLeftIndex);
            newRightImage = cachedImages.get(newRightIndex);
            newCenterCaption = imageCaptions.get(newCenterIndex);
        }

        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_center, newCenterImage);
        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_left, newLeftImage);
        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_right, newRightImage);
        expandedLayout.setTextViewText(R.id.manual_carousel_filmstrip_caption, newCenterCaption);

        // assign a click action pending intent to the center image view
        AEPPushNotificationBuilder.setRemoteViewClickAction(
                context,
                expandedLayout,
                R.id.manual_carousel_filmstrip_center,
                messageId,
                deliveryId,
                imageClickActions.get(newCenterIndex),
                tag,
                sticky);

        // set any custom colors if needed
        AEPPushNotificationBuilder.setCustomNotificationColors(
                notificationBackgroundColor,
                titleTextColor,
                expandedBodyTextColor,
                smallLayout,
                expandedLayout,
                R.id.carousel_container_layout);

        // handle left and right navigation buttons
        final Intent clickIntent =
                new Intent(
                        CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED,
                        null,
                        context,
                        AEPPushTemplateBroadcastReceiver.class);
        clickIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        clickIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.CUSTOM_SOUND, customSound);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_IMAGE_INDEX, newCenterIndex);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.IMAGE_URLS, imageUrls);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.IMAGE_CAPTIONS, imageCaptions);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.IMAGE_CLICK_ACTIONS, imageClickActions);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_TEXT, titleText);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_TEXT, bodyText);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.NOTIFICATION_BACKGROUND_COLOR,
                notificationBackgroundColor);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_TEXT_COLOR, titleTextColor);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT_COLOR, expandedBodyTextColor);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.MESSAGE_ID, messageId);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.DELIVERY_ID, deliveryId);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_ICON, smallIcon);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_ICON_COLOR, smallIconColor);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.VISIBILITY, visibility);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.IMPORTANCE, importance);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.TICKER, ticker);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.TAG, tag);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.STICKY, sticky);

        final PendingIntent pendingIntentLeftButton =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        clickIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        clickIntent.setAction(CampaignPushConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED);
        final PendingIntent pendingIntentRightButton =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        clickIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // set onclick intents for the skip left and skip right buttons
        expandedLayout.setOnClickPendingIntent(R.id.leftImageButton, pendingIntentLeftButton);
        expandedLayout.setOnClickPendingIntent(R.id.rightImageButton, pendingIntentRightButton);

        // we need to create a silent notification as this will be re-displaying a notification
        // rather than showing a new one.
        // the silent sound is set on the notification channel and notification builder.
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Displaying a silent notification after handling an intent.");

        // Create the notification
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                                context,
                                CampaignPushConstants.DefaultValues.SILENT_NOTIFICATION_CHANNEL_ID)
                        .setSound(null)
                        .setTicker(ticker)
                        .setNumber(badgeCount)
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(smallLayout)
                        .setCustomBigContentView(expandedLayout);

        // small Icon must be present, otherwise the notification will not be displayed.
        AEPPushNotificationBuilder.setSmallIcon(context, builder, smallIcon, smallIconColor);

        // set notification visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AEPPushNotificationBuilder.setVisibility(builder, visibility);
        }

        // set notification delete action
        AEPPushNotificationBuilder.setNotificationDeleteAction(
                context, builder, messageId, deliveryId);

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
