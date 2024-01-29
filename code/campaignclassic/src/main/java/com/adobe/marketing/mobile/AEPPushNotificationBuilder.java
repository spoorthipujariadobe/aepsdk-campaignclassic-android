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
import com.adobe.marketing.mobile.campaignclassic.R;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.StringUtils;
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
    private static final String SILENT_CHANNEL_NAME = "Campaign Classic Silent Notifications";

    /**
     * Builds a notification for the provided {@code AEPPushPayload}.
     *
     * @param payload {@link AEPPushPayload} created from the received push notification
     * @param context the application {@link Context}
     * @return the notification
     */
    @NonNull static Notification buildPushNotification(final AEPPushPayload payload, final Context context)
            throws IllegalArgumentException, NotificationConstructionFailedException {
        NotificationCompat.Builder builder;
        final Map<String, String> messageData = payload.getMessageData();
        final PushTemplateType pushTemplateType =
                messageData.get(CampaignPushConstants.PushPayloadKeys.TEMPLATE_TYPE) == null
                        ? PushTemplateType.UNKNOWN
                        : PushTemplateType.fromString(
                                messageData.get(
                                        CampaignPushConstants.PushPayloadKeys.TEMPLATE_TYPE));
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
     * @param context the application {@link Context}
     * @param channelId {@code String} containing the notification channel id
     * @param customSound {@code String} containing the custom sound to use
     * @param importance {@code int} containing the notification importance
     * @return the channel ID
     */
    @NonNull static String createChannelAndGetChannelID(
            final Context context,
            final String channelId,
            final String customSound,
            final int importance) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // For Android versions below O, no channel is created. Just return the obtained channel
            // ID.
            return channelId == null ? DEFAULT_CHANNEL_ID : channelId;
        } else {
            // For Android versions O and above, create a channel if it does not exist and return
            // the channel ID.
            final NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            final String channelIdFromPayload = channelId;

            // setup a silent channel for notification carousel item change
            setupSilentNotificationChannel(context, notificationManager, importance);

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
            } else if (channelIdFromPayload != null) {
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Channel does not exist for channel ID obtained from payload ( "
                                + channelIdFromPayload
                                + "). Creating a channel named %s.",
                        channelIdFromPayload);

                final NotificationChannel channel =
                        new NotificationChannel(
                                channelIdFromPayload, DEFAULT_CHANNEL_NAME, importance);

                // set a custom sound on the channel
                setSound(context, channel, customSound, false);

                // add the channel to the notification manager
                notificationManager.createNotificationChannel(channel);

                return channelIdFromPayload;
            } else {
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "No channel ID obtained from payload. Using the Campaign Classic"
                                + " Extension's default channel.");
            }

            // Use the default channel ID if the channel ID from the payload is null or if a channel
            // does not exist for the channel ID from the payload.
            if (notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID) != null) {
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Channel already exists for the default channel ID: " + DEFAULT_CHANNEL_ID);
            } else {
                Log.debug(
                        CampaignPushConstants.LOG_TAG,
                        SELF_TAG,
                        "Creating a new channel for the default channel ID: "
                                + DEFAULT_CHANNEL_ID
                                + ".");
                final NotificationChannel channel =
                        new NotificationChannel(
                                DEFAULT_CHANNEL_ID, DEFAULT_CHANNEL_NAME, importance);
                notificationManager.createNotificationChannel(channel);
            }
            return DEFAULT_CHANNEL_ID;
        }
    }

    private static void setupSilentNotificationChannel(
            final Context context,
            final NotificationManager notificationManager,
            final int importance) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            return;
        }

        if (notificationManager.getNotificationChannel(
                        CampaignPushConstants.DefaultValues.SILENT_NOTIFICATION_CHANNEL_ID)
                != null) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Using previously created silent channel.");
            return;
        }

        // create a channel containing no sound to be used when displaying an updated carousel
        // notification
        final NotificationChannel silentChannel =
                new NotificationChannel(
                        CampaignPushConstants.DefaultValues.SILENT_NOTIFICATION_CHANNEL_ID,
                        SILENT_CHANNEL_NAME,
                        importance);

        // set no sound on the silent channel
        setSound(context, silentChannel, null, true);

        // add the silent channel to the notification manager
        notificationManager.createNotificationChannel(silentChannel);
    }

    /**
     * Sets the small icon for the notification. If a small icon is received from the payload, the
     * same is used. If a small icon is not received from the payload, we use the icon set using
     * MobileCore.setSmallIcon(). If a small icon is not set using MobileCore.setSmallIcon(), we use
     * the default small icon of the application.
     *
     * @param context the application {@link Context}
     * @param smallIcon {@code String} containing the small icon to use
     * @param smallIconColor {@code String} containing the small icon color to use
     * @param builder the notification builder
     */
    static void setSmallIcon(
            final Context context,
            final NotificationCompat.Builder builder,
            final String smallIcon,
            final String smallIconColor) {
        final int iconFromPayload =
                CampaignPushUtils.getSmallIconWithResourceName(smallIcon, context);
        final int iconFromMobileCore = MobileCore.getSmallIconResourceID();
        int iconResourceId;

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
                return;
            }
        }

        final String iconColorHex = smallIconColor;
        setSmallIconColor(builder, iconColorHex);

        builder.setSmallIcon(iconResourceId);
    }
    /**
     * Sets a custom color to the notification's small icon.
     *
     * @param builder the notification builder
     * @param iconColorHex {@code String} containing a color code to be used in customizing the
     *     small icon color
     */
    private static void setSmallIconColor(
            final NotificationCompat.Builder builder, final String iconColorHex) {

        try {
            // sets the icon color if provided
            if (!StringUtils.isNullOrEmpty(iconColorHex)) {
                final String smallIconColor = "#" + iconColorHex;
                builder.setColorized(true).setColor(Color.parseColor(smallIconColor));
            }
        } catch (final IllegalArgumentException exception) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unrecognized hex string passed to Color.parseColor(), custom color will not"
                            + " be applied to the notification icon.");
        }
    }

    /**
     * Sets the sound for the notification. If a sound is received from the payload, the same is
     * used. If a sound is not received from the payload, the default sound is used.
     *
     * @param context the application {@link Context}
     * @param notificationBuilder the notification builder
     * @param customSound {@code String} containing the custom sound file name to load from the
     *     bundled assets
     */
    static void setSound(
            final Context context,
            final NotificationCompat.Builder notificationBuilder,
            final String customSound) {

        if (StringUtils.isNullOrEmpty(customSound)) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "No custom sound found in the push template, using the default notification"
                            + " sound.");
            notificationBuilder.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            return;
        }

        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Setting sound from bundle named %s.",
                customSound);
        notificationBuilder.setSound(
                CampaignPushUtils.getSoundUriForResourceName(customSound, context));
    }

    /**
     * Sets the sound for the provided {@code NotificationChannel}. If a sound is received from the
     * payload, the same is used. If a sound is not received from the payload, the default sound is
     * used.
     *
     * @param context the application {@link Context}
     * @param notificationChannel the {@link NotificationChannel} to assign the sound to
     * @param customSound {@code String} containing the custom sound file name to load from the
     *     bundled assets
     */
    static void setSound(
            final Context context,
            final NotificationChannel notificationChannel,
            final String customSound,
            final boolean isSilent) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            return;
        }

        if (isSilent) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Creating a silent notification channel.");
            notificationChannel.setSound(null, null);
            return;
        }

        if (StringUtils.isNullOrEmpty(customSound)) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "No custom sound found in the push template, using the default"
                            + " notification sound.");
            notificationChannel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
            return;
        }

        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Setting sound from bundle named %s.",
                customSound);
        notificationChannel.setSound(
                CampaignPushUtils.getSoundUriForResourceName(customSound, context), null);
    }

    /**
     * Sets the image url as the large icon for the notification. If a large icon url is received
     * from the payload, the image is downloaded and the notification style is set to
     * BigPictureStyle. If large icon url is not received from the payload, default style is used
     * for the notification.
     *
     * @param notificationBuilder the notification builder
     * @param imageUrl {@code String} containing the image url
     * @param title {@code String} containing the title
     * @param bodyText {@code String} containing the body text
     */
    static void setLargeIcon(
            final NotificationCompat.Builder notificationBuilder,
            final String imageUrl,
            final String title,
            final String bodyText) {
        // Quick bail out if there is no image url
        if (StringUtils.isNullOrEmpty(imageUrl)) return;
        Bitmap bitmap = CampaignPushUtils.download(imageUrl);

        // Bail out if the download fails
        if (bitmap == null) return;
        notificationBuilder.setLargeIcon(bitmap);
        NotificationCompat.BigPictureStyle bigPictureStyle =
                new NotificationCompat.BigPictureStyle();
        bigPictureStyle.bigPicture(bitmap);
        bigPictureStyle.bigLargeIcon(null);
        bigPictureStyle.setBigContentTitle(title);
        bigPictureStyle.setSummaryText(bodyText);
        notificationBuilder.setStyle(bigPictureStyle);
    }

    /**
     * Sets the click action for the notification.
     *
     * @param context the application {@link Context}
     * @param notificationBuilder the notification builder
     * @param messageId {@code String} containing the message id from the received push notification
     * @param deliveryId {@code String} containing the delivery id from the received push
     *     notification
     * @param actionUri the action uri
     * @param tag the tag used when scheduling the notification
     * @param stickyNotification {@code boolean} if false, remove the notification after the {@code
     *     RemoteViews} is pressed
     */
    static void setNotificationClickAction(
            final Context context,
            final NotificationCompat.Builder notificationBuilder,
            final String messageId,
            final String deliveryId,
            final String actionUri,
            final String tag,
            final boolean stickyNotification) {
        final PendingIntent pendingIntent =
                createPendingIntent(
                        context, messageId, deliveryId, actionUri, null, tag, stickyNotification);
        notificationBuilder.setContentIntent(pendingIntent);
    }

    /**
     * Sets the click action for the specified view in the custom push template {@code RemoteView}.
     *
     * @param context the application {@link Context}
     * @param pushTemplateRemoteView {@link RemoteViews} the parent view representing a push
     *     template
     * @param targetViewResourceId {@code int} containing the resource id of the view to attach the
     *     click action
     * @param messageId {@code String} containing the message id from the received push notification
     * @param deliveryId {@code String} containing the delivery id from the received push
     *     notification
     * @param actionUri {@code String} containing the action uri defined for the push template image
     * @param tag the tag used when scheduling the notification
     * @param stickyNotification {@code boolean} if false, remove the notification after the {@code
     *     RemoteViews} is pressed
     */
    static void setRemoteViewClickAction(
            final Context context,
            final RemoteViews pushTemplateRemoteView,
            final int targetViewResourceId,
            final String messageId,
            final String deliveryId,
            final String actionUri,
            final String tag,
            final boolean stickyNotification) {
        final PendingIntent pendingIntent =
                createPendingIntent(
                        context, messageId, deliveryId, actionUri, null, tag, stickyNotification);
        pushTemplateRemoteView.setOnClickPendingIntent(targetViewResourceId, pendingIntent);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static void setVisibility(
            final NotificationCompat.Builder notificationBuilder, final int visibility) {
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
     * @param context the application {@link Context}
     * @param builder the notification builder
     * @param actionButtonsString {@code String} a JSON string containing action buttons to attach
     *     to the notification
     * @param messageId {@code String} containing the message id from the received push notification
     * @param deliveryId {@code String} containing the delivery id from the received push
     *     notification
     * @param tag the tag used when scheduling the notification
     * @param stickyNotification {@code boolean} if false, remove the notification after the action
     *     button is pressed
     */
    static void addActionButtons(
            final Context context,
            final NotificationCompat.Builder builder,
            final String actionButtonsString,
            final String messageId,
            final String deliveryId,
            final String tag,
            final boolean stickyNotification) {
        final List<AEPPushTemplate.ActionButton> actionButtons =
                AEPPushTemplate.getActionButtonsFromString(actionButtonsString);
        if (actionButtons == null || actionButtons.isEmpty()) {
            return;
        }

        for (final AEPPushTemplate.ActionButton eachButton : actionButtons) {

            final PendingIntent pendingIntent;
            if (eachButton.getType() == AEPPushTemplate.ActionType.DEEPLINK
                    || eachButton.getType() == AEPPushTemplate.ActionType.WEBURL) {
                pendingIntent =
                        createPendingIntent(
                                context,
                                messageId,
                                deliveryId,
                                eachButton.getLink(),
                                eachButton.getLabel(),
                                tag,
                                stickyNotification);
            } else {
                pendingIntent =
                        createPendingIntent(
                                context,
                                messageId,
                                deliveryId,
                                null,
                                eachButton.getLabel(),
                                tag,
                                stickyNotification);
            }
            builder.addAction(0, eachButton.getLabel(), pendingIntent);
        }
    }

    /**
     * Creates a pending intent for the notification.
     *
     * @param context the application {@link Context}
     * @param messageId {@code String} containing the message id from the received push notification
     * @param deliveryId {@code String} containing the delivery id from the received push
     *     notification
     * @param actionUri the action uri
     * @param actionID the action ID
     * @param stickyNotification {@code boolean} if false, remove the notification after the {@code
     *     RemoteViews} is pressed
     * @return the pending intent
     */
    private static PendingIntent createPendingIntent(
            final Context context,
            final String messageId,
            final String deliveryId,
            final String actionUri,
            final String actionID,
            final String tag,
            final boolean stickyNotification) {
        final Intent intent = new Intent(CampaignPushConstants.NotificationAction.BUTTON_CLICKED);
        intent.setClass(context.getApplicationContext(), CampaignPushTrackerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(CampaignPushConstants.Tracking.Keys.MESSAGE_ID, messageId);
        intent.putExtra(CampaignPushConstants.Tracking.Keys.DELIVERY_ID, deliveryId);
        intent.putExtra(CampaignPushConstants.PushPayloadKeys.TAG, tag);
        intent.putExtra(CampaignPushConstants.PushPayloadKeys.STICKY, stickyNotification);
        addActionDetailsToIntent(intent, actionUri, actionID);

        // adding tracking details
        return TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(
                        new Random().nextInt(),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Sets the delete action for the notification.
     *
     * @param context the application {@link Context}
     * @param builder the notification builder
     * @param messageId {@code String} containing the message id from the received push notification
     * @param deliveryId {@code String} containing the delivery id from the received push
     *     notification
     */
    static void setNotificationDeleteAction(
            final Context context,
            final NotificationCompat.Builder builder,
            final String messageId,
            final String deliveryId) {
        final Intent deleteIntent = new Intent(CampaignPushConstants.NotificationAction.DISMISSED);
        deleteIntent.setClass(context, CampaignPushTrackerActivity.class);
        deleteIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        deleteIntent.putExtra(CampaignPushConstants.Tracking.Keys.MESSAGE_ID, messageId);
        deleteIntent.putExtra(CampaignPushConstants.Tracking.Keys.DELIVERY_ID, deliveryId);

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
     * Sets custom colors to UI elements present in the specified {@code RemoteViews} object.
     *
     * @param backgroundColor {@code String} containing the hex color code for the notification
     *     background
     * @param titleTextColor {@code String} containing the hex color code for the notification title
     *     text
     * @param expandedBodyTextColor {@code String} containing the hex color code for the expanded
     *     notification body text
     * @param smallLayout {@link RemoteViews} object for a collapsed custom notification
     * @param expandedLayout {@code RemoteViews} object for an expanded custom notification
     * @param containerViewId {@code int} containing the resource id of the layout container
     */
    static void setCustomNotificationColors(
            final String backgroundColor,
            final String titleTextColor,
            final String expandedBodyTextColor,
            final RemoteViews smallLayout,
            final RemoteViews expandedLayout,
            final int containerViewId) {
        // get custom color from hex string and set it the notification background
        if (!StringUtils.isNullOrEmpty(backgroundColor)) {
            setElementColor(
                    smallLayout,
                    R.id.basic_small_layout,
                    "#" + backgroundColor,
                    CampaignPushConstants.MethodNames.SET_BACKGROUND_COLOR,
                    CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BACKGROUND);
            setElementColor(
                    expandedLayout,
                    containerViewId,
                    "#" + backgroundColor,
                    CampaignPushConstants.MethodNames.SET_BACKGROUND_COLOR,
                    CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BACKGROUND);
        }

        // get custom color from hex string and set it the notification title
        if (!StringUtils.isNullOrEmpty(titleTextColor)) {
            setElementColor(
                    smallLayout,
                    R.id.notification_title,
                    "#" + titleTextColor,
                    CampaignPushConstants.MethodNames.SET_TEXT_COLOR,
                    CampaignPushConstants.FriendlyViewNames.NOTIFICATION_TITLE);
            setElementColor(
                    expandedLayout,
                    R.id.notification_title,
                    "#" + titleTextColor,
                    CampaignPushConstants.MethodNames.SET_TEXT_COLOR,
                    CampaignPushConstants.FriendlyViewNames.NOTIFICATION_TITLE);
        }

        // get custom color from hex string and set it the notification body text
        if (!StringUtils.isNullOrEmpty(expandedBodyTextColor)) {
            setElementColor(
                    smallLayout,
                    R.id.notification_body,
                    "#" + expandedBodyTextColor,
                    CampaignPushConstants.MethodNames.SET_TEXT_COLOR,
                    CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BODY_TEXT);
            setElementColor(
                    expandedLayout,
                    R.id.notification_body_expanded,
                    "#" + expandedBodyTextColor,
                    CampaignPushConstants.MethodNames.SET_TEXT_COLOR,
                    CampaignPushConstants.FriendlyViewNames.NOTIFICATION_BODY_TEXT);
        }
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
    private static void setElementColor(
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
