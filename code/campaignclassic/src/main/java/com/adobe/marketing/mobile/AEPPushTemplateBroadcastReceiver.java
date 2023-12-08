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
import android.os.Bundle;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.campaignclassic.R;

import java.util.HashMap;
import java.util.Map;

public class AEPPushTemplateBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals(CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED) || intent.getAction().equals(CampaignPushConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED)) {
            handleFilmstripNotificationIntent(context, intent);
        }
    }

    private void handleFilmstripNotificationIntent(final Context context, final Intent intent) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        final String packageName = ServiceProvider.getInstance().getAppContextService().getApplication().getPackageName();

        // get filmstrip notification values from the intent extras
        final Bundle intentExtras = intent.getExtras();
        final String backgroundColorHex = intentExtras.getString(CampaignPushConstants.IntentKeys.BACKGROUND_COLOR);
        final String titleColorHex = intentExtras.getString(CampaignPushConstants.IntentKeys.TITLE_COLOR);
        final String bodyColorHex = intentExtras.getString(CampaignPushConstants.IntentKeys.BODY_COLOR);
        final String titleText = intentExtras.getString(CampaignPushConstants.IntentKeys.SMALL_TITLE_TEXT);
        final String smallBodyText = intentExtras.getString(CampaignPushConstants.IntentKeys.SMALL_BODY_TEXT);
        final String expandedBodyText = intentExtras.getString(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT);
        final Bitmap leftImage = (Bitmap) intentExtras.get(CampaignPushConstants.IntentKeys.LEFT_IMAGE);
        final Bitmap centerImage = (Bitmap) intentExtras.get(CampaignPushConstants.IntentKeys.CENTER_IMAGE);
        final Bitmap rightImage = (Bitmap) intentExtras.get(CampaignPushConstants.IntentKeys.RIGHT_IMAGE);
        final String leftCaptionText = intentExtras.getString(CampaignPushConstants.IntentKeys.LEFT_CAPTION);
        final String centerCaptionText = intentExtras.getString(CampaignPushConstants.IntentKeys.CENTER_CAPTION);
        final String rightCaptionText = intentExtras.getString(CampaignPushConstants.IntentKeys.RIGHT_CAPTION);
        final String channelId = intentExtras.getString(CampaignPushConstants.IntentKeys.CHANNEL_ID);

        final Map<Bitmap, String> captionMap = new HashMap<Bitmap, String>() {{
            put(leftImage, leftCaptionText);
            put(centerImage, centerCaptionText);
            put(rightImage, rightCaptionText);
        }};

        final RemoteViews smallLayout = new RemoteViews(packageName, R.layout.push_template_collapsed);
        final RemoteViews expandedLayout = new RemoteViews(packageName, R.layout.push_template_filmstrip_carousel);
        smallLayout.setTextViewText(R.id.notification_title, titleText);
        smallLayout.setTextViewText(R.id.notification_body, smallBodyText);
        expandedLayout.setTextViewText(R.id.notification_title, titleText);
        expandedLayout.setTextViewText(R.id.notification_body_expanded, expandedBodyText);

        String newCaption;
        final String action = intent.getAction();
        Bitmap newLeftImage;
        Bitmap newCenterImage;
        Bitmap newRightImage;

        if (action.equals(CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED)) {
            newCenterImage = leftImage;
            newRightImage = centerImage;
            newLeftImage = rightImage;
            newCaption = captionMap.get(leftImage);
        } else {
            newCenterImage = rightImage;
            newRightImage = leftImage;
            newLeftImage = centerImage;
            newCaption = captionMap.get(rightImage);
        }

        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_center, newCenterImage);
        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_left, newLeftImage);
        expandedLayout.setImageViewBitmap(R.id.manual_carousel_filmstrip_right, newRightImage);
        expandedLayout.setTextViewText(R.id.manual_carousel_filmstrip_caption, newCaption);

        // handle left and right navigation buttons
        final Intent leftButtonIntent = new Intent(CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED, null, context, AEPPushTemplateBroadcastReceiver.class);
        leftButtonIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        leftButtonIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.BACKGROUND_COLOR, backgroundColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_COLOR, titleColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_COLOR, bodyColorHex);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_IMAGE, newLeftImage);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_IMAGE, newCenterImage);
        leftButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_IMAGE, newRightImage);
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
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_IMAGE, newLeftImage);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_IMAGE, newCenterImage);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_IMAGE, newRightImage);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.LEFT_CAPTION, leftCaptionText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CENTER_CAPTION, centerCaptionText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.RIGHT_CAPTION, rightCaptionText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_TITLE_TEXT, titleText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_BODY_TEXT, smallBodyText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        rightButtonIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);

        final PendingIntent pendingIntentLeftButton = PendingIntent.getBroadcast(context, 0, leftButtonIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        final PendingIntent pendingIntentRightButton = PendingIntent.getBroadcast(context, 0, rightButtonIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        expandedLayout.setOnClickPendingIntent(R.id.leftImageButton, pendingIntentLeftButton);
        expandedLayout.setOnClickPendingIntent(R.id.rightImageButton, pendingIntentRightButton);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.blank) // Small Icon must be present, otherwise the notification will not be
                // displayed.
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(smallLayout)
                .setCustomBigContentView(expandedLayout);

        final Notification notification = builder.build();

        notificationManager.notify("FilmstripNotificationUpdate".hashCode(), notification);
    }
}
