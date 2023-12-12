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
import com.adobe.marketing.mobile.util.StringUtils;
import com.google.firebase.components.MissingDependencyException;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Class for building push notifications.
 *
 * <p>The {@link #buildPushNotification(AEPPushPayload, Context)} method in this class takes the
 * {@link AEPPushPayload} created from the push notification and builds the notification. This class
 * is used internally by the {@link AEPMessagingService} to build the push notification.
 */
class AEPPushNotificationBuilder {

    private static final String SELF_TAG = "AEPPushNotificationBuilder";
    private static final String DEFAULT_CHANNEL_ID = "CampaignPushChannel";
    // When no channel name is received from the push notification, this default channel name is
    // used.
    // This will appear in the notification settings for the app.
    private static final String DEFAULT_CHANNEL_NAME = "Campaign Classic General Notifications";

    /**
     * Builds a notification for the provided {@code AEPPushPayload}.
     *
     * @param payload {@link AEPPushPayload} created from the received push notification
     * @param context the application {@link Context}
     * @return the notification
     */
    @NonNull static Notification buildPushNotification(final AEPPushPayload payload, final Context context)
            throws IllegalArgumentException, MissingDependencyException {
        NotificationCompat.Builder builder;
        final Map<String, String> messageData = payload.getMessageData();
        final PushTemplateType pushTemplateType =
                PushTemplateType.fromString(
                        messageData.get(CampaignPushConstants.PushPayloadKeys.TEMPLATE_TYPE));
        switch (pushTemplateType) {
            case BASIC:
                final BasicPushTemplate basicPushTemplate = new BasicPushTemplate(messageData);
                builder = BasicTemplateNotificationBuilder.construct(basicPushTemplate, context);
                break;
            case CAROUSEL:
                final CarouselPushTemplate carouselPushTemplate =
                        new CarouselPushTemplate(messageData);
                builder =
                        CarouselTemplateNotificationBuilder.construct(
                                carouselPushTemplate, context);
                break;
            case UNKNOWN:
            default:
                final AEPPushTemplate aepPushTemplate = new AEPPushTemplate(messageData);
                builder = LegacyNotificationBuilder.construct(aepPushTemplate, context);
                break;
        }

        return builder.build();
    }

    /**
     * Creates a channel if it does not exist and returns the channel ID. If a channel ID is
     * received from the payload and if channel exists for the channel ID, the same channel ID is
     * returned. If a channel ID is received from the payload and if channel does not exist for the
     * channel ID, Campaign Classic extension's default channel is used. If no channel ID is
     * received from the payload, Campaign Classic extension's default channel is used. For Android
     * versions below O, no channel is created. Just return the obtained channel ID.
     *
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received
     *     push notification
     * @param context the application {@link Context}
     * @return the channel ID
     */
    @NonNull static String createChannelAndGetChannelID(
            final AEPPushTemplate pushTemplate, final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // For Android versions below O, no channel is created. Just return the obtained channel
            // ID.
            return pushTemplate.getChannelId() == null
                    ? DEFAULT_CHANNEL_ID
                    : pushTemplate.getChannelId();
        } else {
            // For Android versions O and above, create a channel if it does not exist and return
            // the channel ID.
            final NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            final String channelIdFromPayload = pushTemplate.getChannelId();

            // if a channel from the payload is not null and if a channel exists for the channel ID
            // from the payload, use the same channel ID.
            if (channelIdFromPayload != null
                    && notificationManager.getNotificationChannel(channelIdFromPayload) != null) {
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Channel exists for channel ID: "
                                + channelIdFromPayload
                                + ". Using the same for push notification.");
                return channelIdFromPayload;
            } else {
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Channel does not exist for channel ID obtained from payload ( "
                                + channelIdFromPayload
                                + "). Using the Campaign Classic Extension's default channel.");
            }

            // Use the default channel ID if the channel ID from the payload is null or if a channel
            // does not exist for the channel ID from the payload.
            final String channelId = DEFAULT_CHANNEL_ID;
            if (notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID) != null) {
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Channel already exists for the default channel ID: " + channelId);
                return DEFAULT_CHANNEL_ID;
            } else {
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Creating a new channel for the default channel ID: " + channelId + ".");
                final NotificationChannel channel =
                        new NotificationChannel(
                                channelId,
                                DEFAULT_CHANNEL_NAME,
                                pushTemplate.getNotificationImportance());
                notificationManager.createNotificationChannel(channel);
            }
            return channelId;
        }
    }

    /**
     * Sets the small icon for the notification. If a small icon is received from the payload, the
     * same is used. If a small icon is not received from the payload, we use the icon set using
     * MobileCore.setSmallIcon(). If a small icon is not set using MobileCore.setSmallIcon(), we use
     * the default small icon of the application.
     *
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received
     *     push notification
     * @param context the application {@link Context}
     * @param builder the notification builder
     */
    static void setSmallIcon(
            final NotificationCompat.Builder builder,
            final AEPPushTemplate pushTemplate,
            final Context context) {
        final int iconFromPayload =
                CampaignPushUtils.getSmallIconWithResourceName(pushTemplate.getIcon(), context);
        final int iconFromMobileCore = MobileCore.getSmallIconResourceID();
        int iconResourceId = 0;

        if (isValidIcon(iconFromPayload)) {
            iconResourceId = iconFromPayload;
        } else if (isValidIcon(iconFromMobileCore)) {
            iconResourceId = iconFromMobileCore;
        } else {
            final int iconFromApp = CampaignPushUtils.getDefaultAppIcon(context);
            if (isValidIcon(iconFromApp)) {
                iconResourceId = iconFromApp;
            } else {
                Log.warning(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "No valid small icon found. Notification will not be displayed.");
            }
        }

        // add icon resource id to datastore
        CampaignPushUtils.setSmallIconIdInDatastore(iconResourceId);

        final String iconColorHex = pushTemplate.getSmallIconColor();
        setSmallIcon(builder, iconColorHex, iconResourceId);
    }

    static void setSmallIcon(
            final NotificationCompat.Builder builder,
            final String iconColorHex,
            final int smallIconResourceId) {

        try {
            // sets the icon color if provided
            final String smallIconColor = "#" + iconColorHex;
            if (!StringUtils.isNullOrEmpty(smallIconColor)) {
                builder.setColorized(true).setColor(Color.parseColor(smallIconColor));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unrecognized hex string passed to Color.parseColor(), custom color will not"
                            + " be applied to the notification icon.");
        }

        builder.setSmallIcon(smallIconResourceId);
    }

    /**
     * Sets the sound for the notification. If a sound is received from the payload, the same is
     * used. If a sound is not received from the payload, the default sound is used The sound name
     * from the payload should also include the format of the sound file. eg: sound.mp3
     *
     * @param notificationBuilder the notification builder
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received
     *     push notification
     * @param context the application {@link Context}
     */
    static void setSound(
            final NotificationCompat.Builder notificationBuilder,
            final AEPPushTemplate pushTemplate,
            final Context context) {
        if (!StringUtils.isNullOrEmpty(pushTemplate.getSound())) {
            notificationBuilder.setSound(
                    CampaignPushUtils.getSoundUriForResourceName(pushTemplate.getSound(), context));
            return;
        }
        notificationBuilder.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
    }

    /**
     * Sets the large icon for the notification. If a large icon url is received from the payload,
     * the image is downloaded and the notification style is set to BigPictureStyle. If large icon
     * url is not received from the payload, default style is used for the notification.
     *
     * @param notificationBuilder the notification builder
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received
     *     push notification
     */
    static void setLargeIcon(
            final NotificationCompat.Builder notificationBuilder,
            final AEPPushTemplate pushTemplate) {
        // Quick bail out if there is no image url
        if (StringUtils.isNullOrEmpty(pushTemplate.getImageUrl())) return;
        Bitmap bitmap = CampaignPushUtils.download(pushTemplate.getImageUrl());

        // Bail out if the download fails
        if (bitmap == null) return;
        notificationBuilder.setLargeIcon(bitmap);
        NotificationCompat.BigPictureStyle bigPictureStyle =
                new NotificationCompat.BigPictureStyle();
        bigPictureStyle.bigPicture(bitmap);
        bigPictureStyle.bigLargeIcon(null);
        bigPictureStyle.setBigContentTitle(pushTemplate.getTitle());
        bigPictureStyle.setSummaryText(pushTemplate.getBody());
        notificationBuilder.setStyle(bigPictureStyle);
    }

    /**
     * Sets the click action for the notification. If an action type is received from the payload,
     * the same is used. If an action type is not received from the payload, the default action type
     * is used. If an action type is received from the payload, but the action type is not
     * supported, the default action type is used.
     *
     * @param notificationBuilder the notification builder
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received
     *     push notification
     * @param context the application {@link Context}
     */
    static void setNotificationClickAction(
            final NotificationCompat.Builder notificationBuilder,
            final AEPPushTemplate pushTemplate,
            final Context context) {
        final PendingIntent pendingIntent;
        if (pushTemplate.getActionType() == AEPPushTemplate.ActionType.DEEPLINK
                || pushTemplate.getActionType() == AEPPushTemplate.ActionType.WEBURL) {
            pendingIntent =
                    createPendingIntent(
                            pushTemplate,
                            context,
                            CampaignPushConstants.NotificationAction.OPENED,
                            pushTemplate.getActionUri(),
                            null);
        } else {
            pendingIntent =
                    createPendingIntent(
                            pushTemplate,
                            context,
                            CampaignPushConstants.NotificationAction.OPENED,
                            null,
                            null);
        }
        notificationBuilder.setContentIntent(pendingIntent);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static void setVisibility(
            final NotificationCompat.Builder notificationBuilder,
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
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Invalid visibility value received from the payload. Using the default"
                                + " visibility value.");
                break;
        }
    }

    /**
     * Adds action buttons for the notification.
     *
     * @param builder the notification builder
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received
     *     push notification
     * @param context the application {@link Context}
     */
    static void addActionButtons(
            final NotificationCompat.Builder builder,
            final AEPPushTemplate pushTemplate,
            final Context context) {
        final List<AEPPushTemplate.ActionButton> actionButtons = pushTemplate.getActionButtons();
        if (actionButtons == null || actionButtons.isEmpty()) {
            return;
        }

        for (final AEPPushTemplate.ActionButton eachButton : actionButtons) {

            final PendingIntent pendingIntent;
            if (eachButton.getType() == AEPPushTemplate.ActionType.DEEPLINK
                    || eachButton.getType() == AEPPushTemplate.ActionType.WEBURL) {
                pendingIntent =
                        createPendingIntent(
                                pushTemplate,
                                context,
                                CampaignPushConstants.NotificationAction.BUTTON_CLICKED,
                                eachButton.getLink(),
                                eachButton.getLabel());
            } else {
                pendingIntent =
                        createPendingIntent(
                                pushTemplate,
                                context,
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
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received
     *     push notification
     * @param context the application {@link Context}
     * @param notificationAction the notification action
     * @param actionUri the action uri
     * @param actionID the action ID
     * @return the pending intent
     */
    private static PendingIntent createPendingIntent(
            final AEPPushTemplate pushTemplate,
            final Context context,
            final String notificationAction,
            final String actionUri,
            final String actionID) {
        final Intent intent = new Intent(notificationAction);
        intent.setClass(context.getApplicationContext(), CampaignPushTrackerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(
                CampaignPushConstants.Tracking.Keys.MESSAGE_ID, pushTemplate.getMessageId());
        intent.putExtra(
                CampaignPushConstants.Tracking.Keys.DELIVERY_ID, pushTemplate.getDeliveryId());
        addActionDetailsToIntent(intent, actionUri, actionID);

        // adding tracking details
        PendingIntent resultIntent =
                TaskStackBuilder.create(context)
                        .addNextIntentWithParentStack(intent)
                        .getPendingIntent(
                                new Random().nextInt(),
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return resultIntent;
    }

    /**
     * Sets the delete action for the notification.
     *
     * @param builder the notification builder
     * @param pushTemplate {@link AEPPushTemplate} containing the message data from the received
     *     push notification
     * @param context the application {@link Context}
     */
    static void setNotificationDeleteAction(
            final NotificationCompat.Builder builder,
            final AEPPushTemplate pushTemplate,
            final Context context) {
        final Intent deleteIntent = new Intent(CampaignPushConstants.NotificationAction.DISMISSED);
        deleteIntent.setClass(context, CampaignPushTrackerActivity.class);
        deleteIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        deleteIntent.putExtra(
                CampaignPushConstants.Tracking.Keys.MESSAGE_ID, pushTemplate.getMessageId());
        deleteIntent.putExtra(
                CampaignPushConstants.Tracking.Keys.DELIVERY_ID, pushTemplate.getDeliveryId());

        final PendingIntent intent =
                PendingIntent.getActivity(
                        context,
                        new Random().nextInt(),
                        deleteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setDeleteIntent(intent);
    }

    /**
     * Adds action details to the intent.
     *
     * @param intent the intent
     * @param actionUri the action uri
     * @param actionId the action ID
     */
    private static void addActionDetailsToIntent(
            final Intent intent, final String actionUri, final String actionId) {
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

    /**
     * Sets a provided color hex string to a UI element contained in a specified {@code RemoteViews}
     * view.
     *
     * @param remoteView {@link RemoteViews} object containing a UI element to be updated
     * @param elementId {@code int} containing the resource id of the UI element
     * @param colorHex {@code String} containing the color hex string
     * @param methodName {@code String} containing the method to be called on the UI element to
     *     update the color
     * @param viewFriendlyName {@code String} containing the friendly name of the view to be used
     *     for logging purposes
     */
    static void setElementColor(
            final RemoteViews remoteView,
            final int elementId,
            final String colorHex,
            final String methodName,
            final String viewFriendlyName) {
        if (StringUtils.isNullOrEmpty(methodName)) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Null or empty method name provided, custom color will not"
                            + " be applied to"
                            + viewFriendlyName);
            return;
        }

        try {
            if (!StringUtils.isNullOrEmpty(colorHex)) {
                remoteView.setInt(elementId, methodName, Color.parseColor(colorHex));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unrecognized hex string passed to Color.parseColor(), custom color will not"
                            + " be applied to"
                            + viewFriendlyName);
        }
    }
}
