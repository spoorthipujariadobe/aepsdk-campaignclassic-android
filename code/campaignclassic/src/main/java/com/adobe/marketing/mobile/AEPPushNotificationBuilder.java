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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.campaignclassic.R.layout;
import com.adobe.marketing.mobile.campaignclassic.R.id;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Class for building push notifications.
 * <p>
 * The {@link #buildPushNotification(AEPPushPayload, Context)} method in this class takes the {@link AEPPushPayload} created from the
 * push notification and builds the notification.
 * This class is used internally by the {@link AEPMessagingService} to build the push notification.
 */
class AEPPushNotificationBuilder {

    private static final String SELF_TAG = "AEPPushNotificationBuilder";
    private static final String DEFAULT_CHANNEL_ID = "CampaignPushChannel";
    // When no channel name is received from the push notification, this default channel name is used.
    // This will appear in the notification settings for the app.
    private static final String DEFAULT_CHANNEL_NAME = "Campaign Classic General Notifications";

    /**
     * Builds a notification for the provided {@code AEPPushPayload}.
     *
     * @param payload {@link AEPPushPayload} created from the received push notification
     * @param context the application {@link Context}
     * @return the notification
     */
    @NonNull
    static Notification buildPushNotification(final AEPPushPayload payload,
                                              final Context context) throws IllegalArgumentException {
        Notification notification;
        final Map<String, String> messageData = payload.getMessageData();
        final PushTemplateType pushTemplateType = PushTemplateType.fromString(messageData.get(CampaignPushConstants.PushPayloadKeys.TEMPLATE_TYPE));
        switch (pushTemplateType) {
            case BASIC:
                final BasicPushTemplate basicPushTemplate = new BasicPushTemplate(messageData);
                notification = createBasicTemplatePushNotification(basicPushTemplate, context);
                break;
//            case CAROUSEL:
//                final CarouselPushTemplate carouselPushTemplate = new CarouselPushTemplate(messageData);
//                notification = createAutoCarouselTemplatePushNotification(payload, context);
//                break;
//            case INPUT_BOX:
//                final InputBoxPushTemplate inputBoxPushTemplate = new InputBoxPushTemplate(messageData);
//                notification = createInputBoxTemplatePushNotification(payload, context);
//                break;
            case UNKNOWN:
            default:
                final AEPPushTemplate aepPushTemplate = new AEPPushTemplate(messageData);
                notification = createLegacyPushNotification(aepPushTemplate, context);
                break;
        }

        return notification;
    }

    @NonNull
    static Notification createLegacyPushNotification(final AEPPushTemplate pushTemplate, final Context context) {
        final String channelId = createChannelAndGetChannelID(pushTemplate, context);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(pushTemplate.getTitle())
                .setContentText(pushTemplate.getBody())
                .setNumber(pushTemplate.getBadgeCount())
                .setPriority(pushTemplate.getNotificationPriority())
                .setAutoCancel(true);

        setLargeIcon(builder, pushTemplate);
        setSmallIcon(builder, pushTemplate, context); // Small Icon must be present, otherwise the notification will not be displayed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setVisibility(builder, pushTemplate);
        }
        addActionButtons(builder, pushTemplate, context); // Add action buttons if any
        setSound(builder, pushTemplate, context);
        setNotificationClickAction(builder, pushTemplate, context);
        setNotificationDeleteAction(builder, pushTemplate, context);

        return builder.build();
    }

    @NonNull
    static Notification createBasicTemplatePushNotification(final BasicPushTemplate pushTemplate, final Context context) {
        final String channelId = createChannelAndGetChannelID(pushTemplate, context);
        final String packageName = ServiceProvider.getInstance().getAppContextService().getApplication().getPackageName();
        final RemoteViews smallLayout = new RemoteViews(packageName, layout.push_template_collapsed);
        final RemoteViews expandedLayout = new RemoteViews(packageName, layout.push_template_expanded);

        // get push payload data
        final String imageUrl = pushTemplate.getImageUrl();
        if (!StringUtils.isNullOrEmpty(imageUrl)) {
            final Bitmap image = CampaignPushUtils.download(imageUrl);
            if (image != null) {
                smallLayout.setImageViewBitmap(id.template_image, image);
                expandedLayout.setImageViewBitmap(id.template_image, image);
            }
        }

        smallLayout.setTextViewText(id.notification_title, pushTemplate.getTitle());
        smallLayout.setTextViewText(id.notification_body, pushTemplate.getBody());
        expandedLayout.setTextViewText(id.notification_title, pushTemplate.getTitle());
        expandedLayout.setTextViewText(id.notification_body_ext, pushTemplate.getExpandedBodyText());

        try {
            // get custom color from hex string and set it the notification background
            final String backgroundColorHex = pushTemplate.getNotificationBackgroundColor();
            if (!StringUtils.isNullOrEmpty(backgroundColorHex)) {
                smallLayout.setInt(id.basic_small_layout, "setBackgroundColor", Color.parseColor("#" + backgroundColorHex));
                expandedLayout.setInt(id.basic_expanded_layout, "setBackgroundColor", Color.parseColor("#" + backgroundColorHex));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(CampaignPushConstants.LOG_TAG, SELF_TAG, "Unrecognized hex string passed to Color.parseColor(), custom color will not be applied to the notification background.");
        }

        try {
            // get custom color from hex string and set it the notification title
            final String titleColorHex = pushTemplate.getTitleTextColor();
            if (!StringUtils.isNullOrEmpty(titleColorHex)) {
                smallLayout.setInt(id.basic_small_layout, "setTextColor", Color.parseColor("#" + titleColorHex));
                expandedLayout.setInt(id.basic_expanded_layout, "setTextColor", Color.parseColor("#" + titleColorHex));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(CampaignPushConstants.LOG_TAG, SELF_TAG, "Unrecognized hex string passed to Color.parseColor(), custom color will not be applied to the notification title text.");
        }

        try {
            // get custom color from hex string and set it the notification body text
            final String bodyColorHex = pushTemplate.getExpandedBodyTextColor();
            if (!StringUtils.isNullOrEmpty(bodyColorHex)) {
                smallLayout.setInt(id.basic_small_layout, "setTextColor", Color.parseColor("#" + bodyColorHex));
                expandedLayout.setInt(id.basic_expanded_layout, "setTextColor", Color.parseColor("#" + bodyColorHex));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(CampaignPushConstants.LOG_TAG, SELF_TAG, "Unrecognized hex string passed to Color.parseColor(), custom color will not be applied to the notification body text.");
        }


        // Create the notification
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(pushTemplate.getTitle())
                .setContentText(pushTemplate.getBody())
                .setNumber(pushTemplate.getBadgeCount())
                .setAutoCancel(true)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(smallLayout)
                .setCustomBigContentView(expandedLayout);

        setSmallIcon(builder, pushTemplate, context); // Small Icon must be present, otherwise the notification will not be displayed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setVisibility(builder, pushTemplate);
        }
        addActionButtons(builder, pushTemplate, context); // Add action buttons if any
        setSound(builder, pushTemplate, context);
        setNotificationClickAction(builder, pushTemplate, context);
        setNotificationDeleteAction(builder, pushTemplate, context);

        try {
            // sets the icon color
            final String smallIconColor = pushTemplate.getSmallIconColor();
            if (!StringUtils.isNullOrEmpty(smallIconColor)) {
                builder.setColorized(true)
                        .setColor(Color.parseColor(smallIconColor));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(CampaignPushConstants.LOG_TAG, SELF_TAG, "Unrecognized hex string passed to Color.parseColor(), custom color will not be applied to the notification icon.");
        }

        // if API level is below 26 (prior to notification channels) then notification priority is set on the notification builder
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            builder.setPriority(Notification.PRIORITY_HIGH)
                    .setVibrate(new long[0]); // hack to enable heads up notifications as a HUD style notification requires a tone or vibration
        }

        return builder.build();
    }

    /**
     * Creates a channel if it does not exist and returns the channel ID.
     * If a channel ID is received from the payload and if channel exists for the channel ID, the same channel ID is returned.
     * If a channel ID is received from the payload and if channel does not exist for the channel ID, Campaign Classic extension's default channel is used.
     * If no channel ID is received from the payload, Campaign Classic extension's default channel is used.
     * For Android versions below O, no channel is created. Just return the obtained channel ID.
     *
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received push notification
     * @param context      the application {@link Context}
     * @return the channel ID
     */
    @NonNull
    private static String createChannelAndGetChannelID(final AEPPushTemplate pushTemplate,
                                                       final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // For Android versions below O, no channel is created. Just return the obtained channel ID.
            return pushTemplate.getChannelId() == null ? DEFAULT_CHANNEL_ID : pushTemplate.getChannelId();
        } else {
            // For Android versions O and above, create a channel if it does not exist and return the channel ID.
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            final String channelIdFromPayload = pushTemplate.getChannelId();

            // if a channel from the payload is not null and if a channel exists for the channel ID from the payload, use the same channel ID.
            if (channelIdFromPayload != null && notificationManager.getNotificationChannel(channelIdFromPayload) != null) {
                Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Channel exists for channel ID: " + channelIdFromPayload + ". Using the same for push notification.");
                return channelIdFromPayload;
            } else {
                Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Channel does not exist for channel ID obtained from payload ( " + channelIdFromPayload + "). Using the Campaign Classic Extension's default channel.");
            }

            // Use the default channel ID if the channel ID from the payload is null or if a channel does not exist for the channel ID from the payload.
            final String channelId = DEFAULT_CHANNEL_ID;
            if (notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID) != null) {
                Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Channel already exists for the default channel ID: " + channelId);
                return DEFAULT_CHANNEL_ID;
            } else {
                Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Creating a new channel for the default channel ID: " + channelId + ".");
                final NotificationChannel channel = new NotificationChannel(channelId, DEFAULT_CHANNEL_NAME, pushTemplate.getNotificationImportance());
                notificationManager.createNotificationChannel(channel);
            }
            return channelId;
        }
    }

    /**
     * Sets the small icon for the notification.
     * If a small icon is received from the payload, the same is used.
     * If a small icon is not received from the payload, we use the icon set using MobileCore.setSmallIcon().
     * If a small icon is not set using MobileCore.setSmallIcon(), we use the default small icon of the application.
     *
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received push notification
     * @param context      the application {@link Context}
     * @param builder      the notification builder
     */
    private static void setSmallIcon(final NotificationCompat.Builder builder,
                                     final AEPPushTemplate pushTemplate,
                                     final Context context) {
        final int iconFromPayload = CampaignPushUtils.getSmallIconWithResourceName(pushTemplate.getIcon(), context);
        final int iconFromMobileCore = MobileCore.getSmallIconResourceID();

        if (isValidIcon(iconFromPayload)) {
            builder.setSmallIcon(iconFromPayload);
        } else if (isValidIcon(iconFromMobileCore)) {
            builder.setSmallIcon(iconFromMobileCore);
        } else {
            final int iconFromApp = CampaignPushUtils.getDefaultAppIcon(context);
            if (isValidIcon(iconFromApp)) {
                builder.setSmallIcon(iconFromApp);
            } else {
                Log.warning(CampaignPushConstants.LOG_TAG, SELF_TAG, "No valid small icon found. Notification will not be displayed.");
            }
        }
    }

    /**
     * Sets the sound for the notification.
     * If a sound is received from the payload, the same is used.
     * If a sound is not received from the payload, the default sound is used
     * The sound name from the payload should also include the format of the sound file. eg: sound.mp3
     *
     * @param notificationBuilder the notification builder
     * @param pushTemplate        {@link AEPPushTemplate} containing the message data from the received push notification
     * @param context             the application {@link Context}
     */
    private static void setSound(final NotificationCompat.Builder notificationBuilder,
                                 final AEPPushTemplate pushTemplate,
                                 final Context context) {
        if (!StringUtils.isNullOrEmpty(pushTemplate.getSound())) {
            notificationBuilder.setSound(CampaignPushUtils.getSoundUriForResourceName(pushTemplate.getSound(), context));
            return;
        }
        notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
    }

    /**
     * Sets the large icon for the notification.
     * If a large icon url is received from the payload, the image is downloaded and the notification style is set to BigPictureStyle.
     * If large icon url is not received from the payload, default style is used for the notification.
     *
     * @param notificationBuilder the notification builder
     * @param pushTemplate        {@link AEPPushTemplate} containing the message data from the received push notification
     */
    private static void setLargeIcon(final NotificationCompat.Builder notificationBuilder,
                                     final AEPPushTemplate pushTemplate) {
        // Quick bail out if there is no image url
        if (StringUtils.isNullOrEmpty(pushTemplate.getImageUrl())) return;
        Bitmap bitmap = CampaignPushUtils.download(pushTemplate.getImageUrl());

        // Bail out if the download fails
        if (bitmap == null) return;
        notificationBuilder.setLargeIcon(bitmap);
        NotificationCompat.BigPictureStyle bigPictureStyle = new NotificationCompat.BigPictureStyle();
        bigPictureStyle.bigPicture(bitmap);
        bigPictureStyle.bigLargeIcon(null);
        bigPictureStyle.setBigContentTitle(pushTemplate.getTitle());
        bigPictureStyle.setSummaryText(pushTemplate.getBody());
        notificationBuilder.setStyle(bigPictureStyle);
    }

    /**
     * Sets the click action for the notification.
     * If an action type is received from the payload, the same is used.
     * If an action type is not received from the payload, the default action type is used.
     * If an action type is received from the payload, but the action type is not supported, the default action type is used.
     *
     * @param notificationBuilder the notification builder
     * @param pushTemplate        {@link AEPPushTemplate} containing the message data from the received push notification
     * @param context             the application {@link Context}
     */
    private static void setNotificationClickAction(final NotificationCompat.Builder notificationBuilder,
                                                   final AEPPushTemplate pushTemplate,
                                                   final Context context) {
        final PendingIntent pendingIntent;
        if (pushTemplate.getActionType() == AEPPushTemplate.ActionType.DEEPLINK || pushTemplate.getActionType() == AEPPushTemplate.ActionType.WEBURL) {
            pendingIntent = createPendingIntent(pushTemplate,
                    context,
                    CampaignPushConstants.NotificationAction.OPENED,
                    pushTemplate.getActionUri(),
                    null);
        } else {
            pendingIntent = createPendingIntent(pushTemplate,
                    context,
                    CampaignPushConstants.NotificationAction.OPENED,
                    null,
                    null);
        }
        notificationBuilder.setContentIntent(pendingIntent);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static void setVisibility(final NotificationCompat.Builder notificationBuilder,
                                      final AEPPushTemplate pushTemplate) {
        final int visibility = pushTemplate.getNotificationVisibility();
        switch (visibility) {
            case NotificationCompat.VISIBILITY_PUBLIC:
                notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                break;
            case NotificationCompat.VISIBILITY_PRIVATE:
                notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
                break;
            case NotificationCompat.VISIBILITY_SECRET:
                notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
                break;
            default:
                notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
                Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Invalid visibility value received from the payload. Using the default visibility value.");
                break;
        }
    }

    /**
     * Adds action buttons for the notification.
     *
     * @param builder      the notification builder
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received push notification
     * @param context      the application {@link Context}
     */
    private static void addActionButtons(final NotificationCompat.Builder builder,
                                         final AEPPushTemplate pushTemplate,
                                         final Context context) {
        final List<AEPPushTemplate.ActionButton> actionButtons = pushTemplate.getActionButtons();
        if (actionButtons == null || actionButtons.isEmpty()) {
            return;
        }

        for (final AEPPushTemplate.ActionButton eachButton : actionButtons) {

            final PendingIntent pendingIntent;
            if (eachButton.getType() == AEPPushTemplate.ActionType.DEEPLINK || eachButton.getType() == AEPPushTemplate.ActionType.WEBURL) {
                pendingIntent = createPendingIntent(pushTemplate, context,
                        CampaignPushConstants.NotificationAction.BUTTON_CLICKED,
                        eachButton.getLink(),
                        eachButton.getLabel());
            } else {
                pendingIntent = createPendingIntent(pushTemplate, context,
                        CampaignPushConstants.NotificationAction.BUTTON_CLICKED,
                        null,
                        eachButton.getLabel());
            }
            builder.addAction(0, eachButton.getLabel(), pendingIntent);
        }
    }

    /**
     * Creates a pending intent for the notification.
     *
     * @param pushTemplate       {@link AEPPushTemplate} containing the message data from the received push notification
     * @param context            the application {@link Context}
     * @param notificationAction the notification action
     * @param actionUri          the action uri
     * @param actionID           the action ID
     * @return the pending intent
     */
    private static PendingIntent createPendingIntent(final AEPPushTemplate pushTemplate,
                                                     final Context context,
                                                     final String notificationAction,
                                                     final String actionUri,
                                                     final String actionID) {
        final Intent intent = new Intent(notificationAction);
        intent.setClass(context.getApplicationContext(), CampaignPushTrackerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(CampaignPushConstants.Tracking.Keys.MESSAGE_ID, pushTemplate.getMessageId());
        intent.putExtra(CampaignPushConstants.Tracking.Keys.DELIVERY_ID, pushTemplate.getDeliveryId());
        addActionDetailsToIntent(intent, actionUri, actionID);

        // adding tracking details
        PendingIntent resultIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(new Random().nextInt(), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return resultIntent;
    }

    /**
     * Sets the delete action for the notification.
     *
     * @param builder      the notification builder
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received push notification
     * @param context      the application {@link Context}
     */
    private static void setNotificationDeleteAction(final NotificationCompat.Builder builder,
                                                    final AEPPushTemplate pushTemplate,
                                                    final Context context) {
        final Intent deleteIntent = new Intent(CampaignPushConstants.NotificationAction.DISMISSED);
        deleteIntent.setClass(context, CampaignPushTrackerActivity.class);
        deleteIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        deleteIntent.putExtra(CampaignPushConstants.Tracking.Keys.MESSAGE_ID, pushTemplate.getMessageId());
        deleteIntent.putExtra(CampaignPushConstants.Tracking.Keys.DELIVERY_ID, pushTemplate.getDeliveryId());

        final PendingIntent intent = PendingIntent.getActivity(context, new Random().nextInt(), deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setDeleteIntent(intent);
    }

    /**
     * Adds action details to the intent.
     *
     * @param intent    the intent
     * @param actionUri the action uri
     * @param actionId  the action ID
     */
    private static void addActionDetailsToIntent(final Intent intent, final String actionUri, final String actionId) {
        if (!StringUtils.isNullOrEmpty(actionUri)) {
            intent.putExtra(CampaignPushConstants.Tracking.Keys.ACTION_URI, actionUri);
        }

        if (!StringUtils.isNullOrEmpty(actionId)) {
            intent.putExtra(CampaignPushConstants.Tracking.Keys.ACTION_ID, actionId);
        }
    }

    /**
     * Checks if the icon is valid.
     *
     * @param icon the icon to be checked
     * @return true if the icon is valid, false otherwise
     */
    private static boolean isValidIcon(final int icon) {
        return icon > 0;
    }
}
