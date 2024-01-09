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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.adobe.marketing.mobile.campaignclassic.R;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.Calendar;

class BasicTemplateNotificationBuilder {
    private static final String SELF_TAG = "BasicTemplateNotificationBuilder";

    @NonNull static NotificationCompat.Builder construct(
            final BasicPushTemplate pushTemplate, final Context context)
            throws NotificationConstructionFailedException {

        if (pushTemplate == null) {
            throw new NotificationConstructionFailedException(
                    "Invalid push template received, filmstrip carousel notification will not be"
                            + " constructed.");
        }

        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Building a basic template push notification.");
        final String packageName =
                ServiceProvider.getInstance()
                        .getAppContextService()
                        .getApplication()
                        .getPackageName();
        final RemoteViews smallLayout =
                new RemoteViews(packageName, R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(packageName, R.layout.push_template_expanded);
        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();

        if (cacheService == null) {
            throw new NotificationConstructionFailedException(
                    "Cache service is null, basic push notification will not be constructed.");
        }

        // get push payload data
        final String imageUri = pushTemplate.getImageUrl();
        final Bitmap pushImage = CampaignPushUtils.downloadImage(cacheService, imageUri);
        if (pushImage != null) {
            expandedLayout.setImageViewBitmap(R.id.expanded_template_image, pushImage);
        }

        smallLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        smallLayout.setTextViewText(R.id.notification_body, pushTemplate.getBody());
        expandedLayout.setTextViewText(R.id.notification_title, pushTemplate.getTitle());
        expandedLayout.setTextViewText(
                R.id.notification_body_expanded, pushTemplate.getExpandedBodyText());

        return createNotificationBuilder(
                context,
                expandedLayout,
                smallLayout,
                pushTemplate.getRemindLaterTimestamp(),
                pushTemplate.getBadgeCount(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? pushTemplate.getNotificationVisibility()
                        : pushTemplate.getNotificationPriority(),
                pushTemplate.getNotificationImportance(),
                imageUri,
                pushTemplate.getActionUri(),
                pushTemplate.getChannelId(),
                pushTemplate.getSound(),
                pushTemplate.getTitle(),
                pushTemplate.getBody(),
                pushTemplate.getExpandedBodyText(),
                pushTemplate.getNotificationBackgroundColor(),
                pushTemplate.getTitleTextColor(),
                pushTemplate.getExpandedBodyTextColor(),
                pushTemplate.getMessageId(),
                pushTemplate.getDeliveryId(),
                pushTemplate.getIcon(),
                pushTemplate.getSmallIconColor(),
                pushTemplate.getActionButtonsString(),
                pushTemplate.getRemindLaterText());
    }

    private static NotificationCompat.Builder createNotificationBuilder(
            final Context context,
            final RemoteViews expandedLayout,
            final RemoteViews smallLayout,
            final long remindLaterTimestamp,
            final int badgeCount,
            final int visibility,
            final int importance,
            final String imageUri,
            final String actionUri,
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
            final String actionButtonsString,
            final String remindLaterText) {

        final String channelIdToUse =
                AEPPushNotificationBuilder.createChannelAndGetChannelID(
                        context, channelId, customSound, importance);

        // set any custom colors if needed
        AEPPushNotificationBuilder.setCustomNotificationColors(
                notificationBackgroundColor,
                titleTextColor,
                expandedBodyTextColor,
                smallLayout,
                expandedLayout,
                R.id.basic_expanded_layout);

        // Create the notification
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setNumber(badgeCount)
                        .setAutoCancel(true)
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setCustomContentView(smallLayout)
                        .setCustomBigContentView(expandedLayout);

        AEPPushNotificationBuilder.setSmallIcon(
                context,
                builder,
                smallIcon,
                smallIconColor); // Small Icon must be present, otherwise the
        // notification will not be displayed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AEPPushNotificationBuilder.setVisibility(builder, visibility);
        }

        AEPPushNotificationBuilder.addActionButtons(
                context,
                builder,
                actionButtonsString,
                messageId,
                deliveryId); // Add action buttons if any

        // add a remind later button if we have a label and a timestamp
        if (!StringUtils.isNullOrEmpty(remindLaterText) && remindLaterTimestamp > 0) {
            final PendingIntent remindPendingIntent =
                    createRemindPendingIntent(
                            context,
                            badgeCount,
                            visibility,
                            importance,
                            remindLaterTimestamp,
                            imageUri,
                            actionUri,
                            actionButtonsString,
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
                            remindLaterText);
            builder.addAction(0, remindLaterText, remindPendingIntent);
        }

        AEPPushNotificationBuilder.setSound(context, builder, customSound, false);
        AEPPushNotificationBuilder.setNotificationClickAction(
                context, builder, messageId, deliveryId, actionUri);
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

    static void handleScheduledIntent(final Context context, final Intent intent) {
        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);
        final String packageName =
                ServiceProvider.getInstance()
                        .getAppContextService()
                        .getApplication()
                        .getPackageName();

        // get basic notification values from the intent extras
        final Bundle intentExtras = intent.getExtras();
        if (intentExtras == null) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Intent extras are null, will not schedule a notification from the received"
                            + " intent with action %s",
                    intent.getAction());
            return;
        }

        final RemoteViews smallLayout =
                new RemoteViews(packageName, R.layout.push_template_collapsed);
        final RemoteViews expandedLayout =
                new RemoteViews(packageName, R.layout.push_template_expanded);
        final CacheService cacheService = ServiceProvider.getInstance().getCacheService();

        // get push payload data
        final String titleText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.TITLE_TEXT);
        final String bodyText = intentExtras.getString(CampaignPushConstants.IntentKeys.BODY_TEXT);
        final String expandedBodyText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT);
        final String imageUri = intentExtras.getString(CampaignPushConstants.IntentKeys.IMAGE_URI);
        final Bitmap pushImage = CampaignPushUtils.downloadImage(cacheService, imageUri);

        if (pushImage != null) {
            expandedLayout.setImageViewBitmap(R.id.expanded_template_image, pushImage);
        }

        smallLayout.setTextViewText(R.id.notification_title, titleText);
        smallLayout.setTextViewText(R.id.notification_body, bodyText);
        expandedLayout.setTextViewText(R.id.notification_title, titleText);
        expandedLayout.setTextViewText(R.id.notification_body_expanded, expandedBodyText);

        final String actionUri =
                intentExtras.getString(CampaignPushConstants.IntentKeys.ACTION_URI);
        final String messageId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.MESSAGE_ID);
        final String deliveryId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.DELIVERY_ID);
        final long remindLaterTimestamp =
                intentExtras.getLong(CampaignPushConstants.IntentKeys.REMIND_TS);
        final String remindLaterText =
                intentExtras.getString(CampaignPushConstants.IntentKeys.REMIND_LABEL);
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
        final String actionButtonsString =
                intentExtras.getString(CampaignPushConstants.IntentKeys.ACTION_BUTTONS_STRING);

        final Notification notification =
                createNotificationBuilder(
                                context,
                                expandedLayout,
                                smallLayout,
                                remindLaterTimestamp,
                                badgeCount,
                                visibility,
                                importance,
                                imageUri,
                                actionUri,
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
                                actionButtonsString,
                                remindLaterText)
                        .build();

        notificationManager.notify(messageId.hashCode(), notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    static void handleRemindIntent(final Context context, final Intent intent) {
        // get basic notification values from the intent extras
        final Bundle intentExtras = intent.getExtras();
        if (intentExtras == null) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Intent extras are null, will not schedule a notification from the received"
                            + " intent with action %s",
                    intent.getAction());
            return;
        }

        // set the calender time to the remind timestamp to allow the notification to be displayed
        // at the later time
        final int remindLaterTimestamp =
                (int) (intentExtras.getLong(CampaignPushConstants.IntentKeys.REMIND_TS));
        final Calendar calendar = Calendar.getInstance();
        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);
        final String messageId =
                intentExtras.getString(CampaignPushConstants.IntentKeys.MESSAGE_ID);

        if (remindLaterTimestamp > 0) {
            // calculate difference in fire date. if fire date is greater than 0 then we want to
            // schedule a reminder notification.
            final int secondsUntilFireDate =
                    remindLaterTimestamp - (int) (calendar.getTimeInMillis() / 1000);
            if (secondsUntilFireDate <= 0) {
                Log.trace(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Remind later date is before the current date. Will not reschedule the"
                                + " notification.",
                        secondsUntilFireDate);
                // cancel the displayed notification
                notificationManager.cancel(messageId.hashCode());
                return;
            }

            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Remind later pressed, will reschedule the notification to be displayed %d"
                            + " seconds from now",
                    secondsUntilFireDate);
            calendar.add(Calendar.SECOND, secondsUntilFireDate);
            // schedule a pending intent to be broadcast at the specified timestamp
            final PendingIntent pendingIntent =
                    createPendingIntentForScheduledNotification(context, intent);
            final AlarmManager alarmManager =
                    (AlarmManager) context.getSystemService(android.content.Context.ALARM_SERVICE);

            if (alarmManager != null) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);

                // cancel the displayed notification
                notificationManager.cancel(messageId.hashCode());
            }
        }
    }

    private static PendingIntent createRemindPendingIntent(
            final Context context,
            final int badgeCount,
            final int visibility,
            final int importance,
            final long remindTimestamp,
            final String imageUri,
            final String actionUri,
            final String actionButtonsString,
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
            final String remindText) {
        final Intent remindIntent =
                new Intent(
                        CampaignPushConstants.IntentActions.REMIND_LATER_CLICKED,
                        null,
                        context,
                        AEPPushTemplateBroadcastReceiver.class);
        remindIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        remindIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.IMAGE_URI, imageUri);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.ACTION_URI, actionUri);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.CHANNEL_ID, channelId);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.CUSTOM_SOUND, customSound);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_TEXT, titleText);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.BODY_TEXT, bodyText);
        remindIntent.putExtra(
                CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT, expandedBodyText);
        remindIntent.putExtra(
                CampaignPushConstants.IntentKeys.NOTIFICATION_BACKGROUND_COLOR,
                notificationBackgroundColor);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.TITLE_TEXT_COLOR, titleTextColor);
        remindIntent.putExtra(
                CampaignPushConstants.IntentKeys.EXPANDED_BODY_TEXT_COLOR, expandedBodyTextColor);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.MESSAGE_ID, messageId);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.DELIVERY_ID, deliveryId);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_ICON, smallIcon);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.SMALL_ICON_COLOR, smallIconColor);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.VISIBILITY, visibility);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.IMPORTANCE, importance);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.BADGE_COUNT, badgeCount);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.REMIND_TS, remindTimestamp);
        remindIntent.putExtra(CampaignPushConstants.IntentKeys.REMIND_LABEL, remindText);
        remindIntent.putExtra(
                CampaignPushConstants.IntentKeys.ACTION_BUTTONS_STRING, actionButtonsString);

        return PendingIntent.getBroadcast(
                context,
                0,
                remindIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent createPendingIntentForScheduledNotification(
            final Context context, final Intent intent) {
        final Intent remindIntent =
                new Intent(
                        CampaignPushConstants.IntentActions.SCHEDULED_NOTIFICATION_BROADCAST,
                        null,
                        context,
                        AEPPushTemplateBroadcastReceiver.class);
        remindIntent.setClass(context, AEPPushTemplateBroadcastReceiver.class);
        remindIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        remindIntent.putExtras(intent.getExtras());

        return PendingIntent.getBroadcast(
                context,
                0,
                remindIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
