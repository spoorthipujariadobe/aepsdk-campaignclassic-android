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
import android.graphics.BitmapFactory;
import android.os.Build;
import android.widget.RemoteViews;
import androidx.core.app.NotificationCompat;
import com.adobe.marketing.mobile.campaignclassic.R;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.UrlUtils;
import com.google.firebase.components.MissingDependencyException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class AutoCarouselTemplateNotificationBuilder {
    private static final String SELF_TAG = "AutoCarouselTemplateNotificationBuilder";

    static NotificationCompat.Builder construct(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName)
            throws MissingDependencyException {
        final RemoteViews smallLayout =
                new RemoteViews(context.getPackageName(), R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(context.getPackageName(), R.layout.push_template_auto_carousel);
        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();
        final String cacheLocation = CampaignPushUtils.getAssetCacheLocation();

        if (cacheService == null) {
            throw new MissingDependencyException(
                    "Cache service is null, notification will not be constructed.");
        }

        // load images into the carousel
        final long imageProcessingStartTime = System.currentTimeMillis();
        final ArrayList<CarouselPushTemplate.CarouselItem> items = pushTemplate.getCarouselItems();
        final ArrayList<String> downloadedImageUris = new ArrayList<>();

        for (final CarouselPushTemplate.CarouselItem item : items) {
            Bitmap pushImage = null;
            final RemoteViews carouselItem =
                    new RemoteViews(packageName, R.layout.push_template_carousel_item);
            final String imageUri = item.getImageUri();

            if (!StringUtils.isNullOrEmpty(imageUri)) {
                final CacheResult cacheResult = cacheService.get(cacheLocation, imageUri);
                if (cacheResult == null) {
                    if (UrlUtils.isValidUrl(imageUri)) { // we need to download the images first
                        final Bitmap image = CampaignPushUtils.download(imageUri);
                        if (image != null) {
                            // scale down the bitmap to 300dp x 200dp as we don't want to use a full
                            // size image due to memory constraints
                            pushImage =
                                    Bitmap.createScaledBitmap(
                                            image,
                                            CampaignPushConstants.DefaultValues
                                                    .CAROUSEL_MAX_BITMAP_WIDTH,
                                            CampaignPushConstants.DefaultValues
                                                    .CAROUSEL_MAX_BITMAP_HEIGHT,
                                            false);
                            downloadedImageUris.add(imageUri);

                            // write bitmap to cache
                            try (final InputStream bitmapInputStream =
                                    CampaignPushUtils.bitmapToInputStream(pushImage)) {
                                CampaignPushUtils.cacheBitmapInputStream(
                                        cacheService, bitmapInputStream, imageUri);
                            } catch (final IOException exception) {
                                Log.trace(
                                        CampaignPushConstants.LOG_TAG,
                                        SELF_TAG,
                                        "Exception occurred creating an input stream from a"
                                                + " bitmap: %s.",
                                        exception.getLocalizedMessage());
                                break;
                            }
                        }
                    }
                } else { // we have previously downloaded the image
                    Log.trace(
                            CampaignPushConstants.LOG_TAG,
                            SELF_TAG,
                            "Found cached image for %s.",
                            imageUri);
                    downloadedImageUris.add(imageUri);
                    pushImage = BitmapFactory.decodeStream(cacheResult.getData());
                }

                carouselItem.setImageViewBitmap(R.id.carousel_item_image_view, pushImage);
                carouselItem.setTextViewText(R.id.carousel_item_caption, item.getCaptionText());
                expandedLayout.addView(R.id.auto_carousel_view_flipper, carouselItem);
            }
        }

        // log time needed to process the carousel images
        final long imageProcessingElapsedTime =
                System.currentTimeMillis() - imageProcessingStartTime;
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Processed %d auto carousel image(s) in %d milliseconds.",
                downloadedImageUris.size(),
                imageProcessingElapsedTime);

        // fallback to a basic push template notification builder if only 1 (or less) image was able
        // to be downloaded
        if (downloadedImageUris.size()
                <= CampaignPushConstants.DefaultValues.AUTO_CAROUSEL_MINIMUM_IMAGE_COUNT) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Only %d image(s) for the auto carousel notification were downloaded. Building"
                            + " a basic push notification instead.",
                    downloadedImageUris.size());

            // use the downloaded image if available
            if (!StringUtils.isNullOrEmpty(downloadedImageUris.get(0))) {
                pushTemplate.modifyData(
                        CampaignPushConstants.PushPayloadKeys.IMAGE_URL,
                        downloadedImageUris.get(0));
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
                CampaignPushConstants.MethodNames.SET_BACKGROUND_COLOR,
                CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BACKGROUND);
        AEPPushNotificationBuilder.setElementColor(
                expandedLayout,
                R.id.carousel_container_layout,
                "#" + backgroundColorHex,
                CampaignPushConstants.MethodNames.SET_BACKGROUND_COLOR,
                CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BACKGROUND);

        // get custom color from hex string and set it the notification title
        final String titleColorHex = pushTemplate.getTitleTextColor();
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
        final String bodyColorHex = pushTemplate.getExpandedBodyTextColor();
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
