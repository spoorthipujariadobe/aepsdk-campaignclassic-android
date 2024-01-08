/*
  Copyright 2024 Adobe. All rights reserved.
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
import com.adobe.marketing.mobile.campaignclassic.R;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.util.StringUtils;
import com.google.android.gms.common.util.CollectionUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ManualCarouselTemplateNotificationBuilder {
    private static final String SELF_TAG = "ManualCarouselTemplateNotificationBuilder";
    private static final String IMAGE_URIS_KEY = "imageUris";
    private static final String IMAGE_CAPTIONS_KEY = "imageCaptions";
    private static final String IMAGE_ACTIONS_KEY = "imageActions";

    static NotificationCompat.Builder construct(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName)
            throws NotificationConstructionFailedException {
        final RemoteViews smallLayout =
                new RemoteViews(packageName, R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(packageName, R.layout.push_template_manual_carousel);
        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();

        if (cacheService == null) {
            throw new NotificationConstructionFailedException(
                    "Cache service is null, manual carousel notification will not be"
                            + " constructed.");
        }

        if (pushTemplate == null) {
            throw new NotificationConstructionFailedException(
                    "Invalid push template received, manual carousel notification will not be"
                            + " constructed.");
        }

        // load images into the carousel
        final ArrayList<CarouselPushTemplate.CarouselItem> items = pushTemplate.getCarouselItems();
        final Map<String, ArrayList<String>> extractedItemData =
                populateImages(
                        context,
                        cacheService,
                        expandedLayout,
                        items,
                        packageName,
                        pushTemplate.getMessageId(),
                        pushTemplate.getDeliveryId());

        final ArrayList<String> downloadedImageUris = extractedItemData.get(IMAGE_URIS_KEY);
        final ArrayList<String> imageCaptions = extractedItemData.get(IMAGE_CAPTIONS_KEY);
        final ArrayList<String> imageClickActions = extractedItemData.get(IMAGE_ACTIONS_KEY);

        // fallback to a basic push template notification builder if only 1 (or less) image was able
        // to be downloaded
        if (downloadedImageUris.size()
                < CampaignPushConstants.DefaultValues.MANUAL_CAROUSEL_MINIMUM_IMAGE_COUNT) {
            return CarouselTemplateNotificationBuilder.fallbackToBasicNotification(
                    context,
                    pushTemplate,
                    downloadedImageUris,
                    CampaignPushConstants.DefaultValues.MANUAL_CAROUSEL_MINIMUM_IMAGE_COUNT);
        }

        final String titleText = pushTemplate.getTitle();
        final String smallBodyText = pushTemplate.getBody();
        final String expandedBodyText = pushTemplate.getExpandedBodyText();
        smallLayout.setTextViewText(R.id.notification_title, titleText);
        smallLayout.setTextViewText(R.id.notification_body, smallBodyText);
        expandedLayout.setTextViewText(R.id.notification_title, titleText);
        expandedLayout.setTextViewText(R.id.notification_body_expanded, expandedBodyText);

        final int centerImageIndex =
                CampaignPushConstants.DefaultValues.CENTER_INDEX; // center index defaults to 1
        return createNotificationBuilder(
                context,
                expandedLayout,
                smallLayout,
                centerImageIndex,
                pushTemplate.getBadgeCount(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? pushTemplate.getNotificationVisibility()
                        : pushTemplate.getNotificationPriority(),
                pushTemplate.getNotificationImportance(),
                true,
                channelId,
                pushTemplate.getSound(),
                titleText,
                smallBodyText,
                expandedBodyText,
                pushTemplate.getNotificationBackgroundColor(),
                pushTemplate.getTitleTextColor(),
                pushTemplate.getExpandedBodyTextColor(),
                pushTemplate.getMessageId(),
                pushTemplate.getDeliveryId(),
                pushTemplate.getIcon(),
                pushTemplate.getSmallIconColor(),
                downloadedImageUris,
                imageCaptions,
                imageClickActions);
    }

    static void handleIntent(final Context context, final Intent intent) {
        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);
        final String packageName =
                ServiceProvider.getInstance()
                        .getAppContextService()
                        .getApplication()
                        .getPackageName();

        // get carousel notification values from the intent extras
        final Bundle intentExtras = intent.getExtras();
        if (intentExtras == null) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Intent extras are null, will not create a notification from the received"
                            + " intent with action %s",
                    intent.getAction());
            return;
        }

        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();
        final String assetCacheLocation = CampaignPushUtils.getAssetCacheLocation();
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
                new RemoteViews(packageName, R.layout.push_template_manual_carousel);
        smallLayout.setTextViewText(R.id.notification_title, titleText);
        smallLayout.setTextViewText(R.id.notification_body, bodyText);
        expandedLayout.setTextViewText(R.id.notification_title, titleText);
        expandedLayout.setTextViewText(R.id.notification_body_expanded, expandedBodyText);

        final String action = intent.getAction();
        int centerImageIndex =
                intentExtras.getInt(CampaignPushConstants.IntentKeys.CENTER_IMAGE_INDEX);

        final List<Integer> newIndices;
        if (CampaignPushConstants.IntentActions.MANUAL_CAROUSEL_LEFT_CLICKED.equals(action)) {
            newIndices =
                    CampaignPushUtils.calculateNewIndices(
                            centerImageIndex,
                            imageUrls.size(),
                            CampaignPushConstants.IntentActions.MANUAL_CAROUSEL_LEFT_CLICKED);
        } else {
            newIndices =
                    CampaignPushUtils.calculateNewIndices(
                            centerImageIndex,
                            imageUrls.size(),
                            CampaignPushConstants.IntentActions.MANUAL_CAROUSEL_RIGHT_CLICKED);
        }

        int newCenterIndex;
        if (newIndices == null) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unable to calculate new left, center, and right indices. Using default center"
                            + " image index of 1.");
            newCenterIndex = CampaignPushConstants.DefaultValues.CENTER_INDEX;
        } else {
            newCenterIndex = newIndices.get(1);
        }

        final ArrayList<CarouselPushTemplate.CarouselItem> items = new ArrayList<>();
        final CarouselPushTemplate.CarouselItem centerCarouselItem =
                new CarouselPushTemplate.CarouselItem(
                        imageUrls.get(newCenterIndex),
                        imageCaptions.get(newCenterIndex),
                        imageClickActions.get(newCenterIndex));
        items.add(centerCarouselItem);

        final String messageId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.MESSAGE_ID);
        final String deliveryId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.DELIVERY_ID);
        final int badgeCount = intentExtras.getInt(CampaignPushConstants.IntentKeys.BADGE_COUNT);
        final int visibility = intentExtras.getInt(CampaignPushConstants.IntentKeys.VISIBILITY);
        final int importance = intentExtras.getInt(CampaignPushConstants.IntentKeys.IMPORTANCE);
        final String channelId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.CHANNEL_ID);
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

        populateImages(
                context, cacheService, expandedLayout, items, packageName, messageId, deliveryId);

        final Notification notification =
                createNotificationBuilder(
                                context,
                                expandedLayout,
                                smallLayout,
                                newCenterIndex,
                                badgeCount,
                                visibility,
                                importance,
                                true,
                                channelId,
                                customSound,
                                titleText,
                                bodyText,
                                expandedBodyText,
                                notificationBackgroundColor,
                                titleTextColor,
                                expandedBodyTextColor,
                                messageId,
                                deliveryId,
                                smallIcon,
                                smallIconColor,
                                imageUrls,
                                imageCaptions,
                                imageClickActions)
                        .build();

        notificationManager.notify(messageId.hashCode(), notification);
    }

    private static NotificationCompat.Builder createNotificationBuilder(
            final Context context,
            final RemoteViews expandedLayout,
            final RemoteViews smallLayout,
            final int centerImageIndex,
            final int badgeCount,
            final int visibility,
            final int importance,
            final boolean handlingIntent,
            final String channelId,
            final String customSound,
            final String titleText,
            final String bodyText,
            final String expandedBodyText,
            final String notificationBackgroundColor,
            final String titleTextColor,
            final String expandedBodyTextColor,
            final String messageId,
            final String deliveryId,
            final String smallIcon,
            final String smallIconColor,
            final ArrayList<String> downloadedImageUris,
            final ArrayList<String> imageCaptions,
            final ArrayList<String> imageClickActions) {

        AEPPushNotificationBuilder.setRemoteViewClickAction(
                context,
                expandedLayout,
                R.id.carousel_item_image_view,
                messageId,
                deliveryId,
                imageClickActions.get(centerImageIndex));

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
                createClickIntent(
                        context,
                        channelId,
                        customSound,
                        titleText,
                        bodyText,
                        expandedBodyText,
                        notificationBackgroundColor,
                        titleTextColor,
                        expandedBodyTextColor,
                        messageId,
                        deliveryId,
                        smallIcon,
                        smallIconColor,
                        visibility,
                        importance,
                        centerImageIndex,
                        downloadedImageUris,
                        imageCaptions,
                        imageClickActions);

        final PendingIntent pendingIntentLeftButton =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        clickIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        clickIntent.setAction(CampaignPushConstants.IntentActions.MANUAL_CAROUSEL_RIGHT_CLICKED);
        final PendingIntent pendingIntentRightButton =
                PendingIntent.getBroadcast(
                        context,
                        0,
                        clickIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        expandedLayout.setOnClickPendingIntent(R.id.leftImageButton, pendingIntentLeftButton);
        expandedLayout.setOnClickPendingIntent(R.id.rightImageButton, pendingIntentRightButton);

        NotificationCompat.Builder builder;

        if (handlingIntent) {
            // we need to create a silent notification as this will be re-displaying a notification
            // rather than showing a new one.
            // the silent sound is set on the notification channel and notification builder.
            Log.trace(CampaignPushConstants.LOG_TAG, SELF_TAG, "Building a silent notification.");
            builder =
                    new NotificationCompat.Builder(
                            context,
                            CampaignPushConstants.DefaultValues.SILENT_NOTIFICATION_CHANNEL_ID);

            AEPPushNotificationBuilder.setSound(context, builder, customSound, true);
        } else {
            builder = new NotificationCompat.Builder(context, channelId);
            AEPPushNotificationBuilder.setSound(context, builder, customSound, false);
        }

        builder.setNumber(badgeCount)
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(smallLayout)
                .setCustomBigContentView(expandedLayout);

        AEPPushNotificationBuilder.setSmallIcon(
                context,
                builder,
                smallIcon,
                smallIconColor); // Small Icon must be present, otherwise the notification will not
        // be displayed.

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AEPPushNotificationBuilder.setVisibility(builder, visibility);
        }

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

    private static Intent createClickIntent(
            final Context context,
            final String channelId,
            final String customSound,
            final String title,
            final String bodyText,
            final String expandedBodyText,
            final String notificationBackgroundColor,
            final String titleTextColor,
            final String bodyTextColor,
            final String messageId,
            final String deliveryId,
            final String smallIcon,
            final String smallIconColor,
            final int visibility,
            final int importance,
            final int centerIndex,
            final ArrayList<String> imageUrls,
            final ArrayList<String> imageCaptions,
            final ArrayList<String> imageClickActions) {
        final Intent clickIntent =
                new Intent(
                        CampaignPushConstants.IntentActions.MANUAL_CAROUSEL_LEFT_CLICKED,
                        null,
                        context,
                        AEPPushTemplateBroadcastReceiver.class);
        clickIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        clickIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.CUSTOM_SOUND, customSound);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_IMAGE_INDEX, centerIndex);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.IMAGE_URLS, imageUrls);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.IMAGE_CAPTIONS, imageCaptions);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.IMAGE_CLICK_ACTIONS, imageClickActions);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_TEXT, title);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_TEXT, bodyText);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.NOTIFICATION_BACKGROUND_COLOR,
                notificationBackgroundColor);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_TEXT_COLOR, titleTextColor);
        clickIntent.putExtra(
                CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT_COLOR, bodyTextColor);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.MESSAGE_ID, messageId);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.DELIVERY_ID, deliveryId);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_ICON, smallIcon);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_ICON_COLOR, smallIconColor);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.VISIBILITY, visibility);
        clickIntent.putExtra(CampaignPushConstants.IntentKeys.IMPORTANCE, importance);

        return clickIntent;
    }

    private static Map<String, ArrayList<String>> populateImages(
            final Context context,
            final CacheService cacheService,
            final RemoteViews expandedLayout,
            final ArrayList<CarouselPushTemplate.CarouselItem> items,
            final String packageName,
            final String messageId,
            final String deliveryId) {
        final ArrayList<String> downloadedImageUris = new ArrayList<>();
        final ArrayList<String> imageCaptions = new ArrayList<>();
        final ArrayList<String> imageClickActions = new ArrayList<>();
        final Map<String, ArrayList<String>> itemData = new HashMap<>();
        final long imageProcessingStartTime = System.currentTimeMillis();

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

            final RemoteViews carouselItem =
                    new RemoteViews(packageName, R.layout.push_template_carousel_item);
            downloadedImageUris.add(imageUri);
            imageCaptions.add(item.getCaptionText());
            imageClickActions.add(item.getInteractionUri());
            carouselItem.setImageViewBitmap(R.id.carousel_item_image_view, pushImage);
            carouselItem.setTextViewText(R.id.carousel_item_caption, item.getCaptionText());

            // assign a click action pending intent for each carousel item
            AEPPushNotificationBuilder.setRemoteViewClickAction(
                    context,
                    carouselItem,
                    R.id.carousel_item_image_view,
                    messageId,
                    deliveryId,
                    item.getInteractionUri());

            // add the carousel item to the view flipper
            expandedLayout.addView(R.id.manual_carousel_view_flipper, carouselItem);
        }

        // log time needed to process the carousel images
        final long imageProcessingElapsedTime =
                System.currentTimeMillis() - imageProcessingStartTime;
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Processed %d manual carousel image(s) in %d milliseconds.",
                downloadedImageUris.size(),
                imageProcessingElapsedTime);

        itemData.put(IMAGE_URIS_KEY, downloadedImageUris);
        itemData.put(IMAGE_CAPTIONS_KEY, imageCaptions);
        itemData.put(IMAGE_ACTIONS_KEY, imageClickActions);

        return itemData;
    }
}
