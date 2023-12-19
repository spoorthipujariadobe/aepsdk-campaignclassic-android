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
import androidx.core.app.NotificationCompat;
import com.adobe.marketing.mobile.campaignclassic.R;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheService;
import java.util.ArrayList;
import java.util.List;

public class AutoCarouselTemplateNotificationBuilder {
    private static final String SELF_TAG = "AutoCarouselTemplateNotificationBuilder";

    static NotificationCompat.Builder construct(
            final CarouselPushTemplate pushTemplate,
            final Context context,
            final String channelId,
            final String packageName)
            throws NotificationConstructionFailedException {
        final RemoteViews smallLayout =
                new RemoteViews(context.getPackageName(), R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(context.getPackageName(), R.layout.push_template_auto_carousel);
        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();

        if (cacheService == null) {
            throw new NotificationConstructionFailedException(
                    "Cache service is null, auto carousel push notification will not be"
                            + " constructed.");
        }

        if (pushTemplate == null) {
            throw new NotificationConstructionFailedException(
                    "Invalid push template received, auto carousel notification will not be"
                            + " constructed.");
        }

        // load images into the carousel
        final ArrayList<CarouselPushTemplate.CarouselItem> items = pushTemplate.getCarouselItems();
        final List<String> downloadedImageUris =
                populateImages(
                        context, cacheService, expandedLayout, pushTemplate, items, packageName);

        // fallback to a basic push template notification builder if only 1 (or less) image was able
        // to be downloaded
        if (downloadedImageUris.size()
                <= CampaignPushConstants.DefaultValues.AUTO_CAROUSEL_MINIMUM_IMAGE_COUNT) {
            return CarouselTemplateNotificationBuilder.fallbackToBasicNotification(
                    context,
                    pushTemplate,
                    downloadedImageUris,
                    CampaignPushConstants.DefaultValues.AUTO_CAROUSEL_MINIMUM_IMAGE_COUNT);
        }

        smallLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        smallLayout.setTextViewText(R.id.notification_body, pushTemplate.getBody());
        expandedLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        expandedLayout.setTextViewText(
                R.id.notification_body_expanded, pushTemplate.getExpandedBodyText());

        // set any custom colors if needed
        AEPPushNotificationBuilder.setCustomNotificationColors(
                pushTemplate, smallLayout, expandedLayout, R.id.carousel_container_layout);

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
        AEPPushNotificationBuilder.setSound(builder, pushTemplate, context, false);

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

    private static List<String> populateImages(
            final Context context,
            final CacheService cacheService,
            final RemoteViews expandedLayout,
            final CarouselPushTemplate pushTemplate,
            final ArrayList<CarouselPushTemplate.CarouselItem> items,
            final String packageName) {
        final long imageProcessingStartTime = System.currentTimeMillis();
        final ArrayList<String> downloadedImageUris = new ArrayList<>();

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
            carouselItem.setImageViewBitmap(R.id.carousel_item_image_view, pushImage);
            carouselItem.setTextViewText(R.id.carousel_item_caption, item.getCaptionText());

            // assign a click action pending intent for each carousel item
            AEPPushNotificationBuilder.setRemoteViewClickAction(
                    context,
                    carouselItem,
                    R.id.carousel_item_image_view,
                    pushTemplate,
                    item.getInteractionUri());

            // add the carousel item to the view flipper
            expandedLayout.addView(R.id.auto_carousel_view_flipper, carouselItem);
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

        return downloadedImageUris;
    }
}
