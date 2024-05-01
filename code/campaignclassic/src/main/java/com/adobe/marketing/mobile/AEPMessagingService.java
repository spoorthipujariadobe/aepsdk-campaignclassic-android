package com.adobe.marketing.mobile;

import android.app.Notification;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ui.notification.AEPNotificationUtil;
import com.adobe.marketing.mobile.services.ui.notification.NotificationConstructionFailedException;
import com.google.firebase.messaging.RemoteMessage;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is the entry point for all Adobe Campaign Classic out-of-the-box push template
 * notifications received from Firebase.
 */
public class AEPMessagingService {
    static final String SELF_TAG = "AEPMessagingService";

    /**
     * Builds an {@link AEPPushPayload} then constructs a {@link Notification} using the {@code
     * RemoteMessage} payload. The built notification is then passed to the {@link
     * NotificationManagerCompat} to be displayed. If any exceptions are thrown when building the
     * {@code AEPPushPayload} or {@code Notification}, this method will return false signaling that
     * the remote message was not handled by the {@code AEPMessagingService}.
     *
     * @param context the application {@link Context}
     * @param remoteMessage the {@link RemoteMessage} containing a push notification payload
     * @return {@code boolean} signaling if the {@link AEPMessagingService} handled the remote
     *     message
     */
    public static boolean handleRemoteMessage(
            @NonNull final Context context, @NonNull final RemoteMessage remoteMessage) {
        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);
        AEPPushPayload payload;
        try {
            payload = new AEPPushPayload(remoteMessage);
            final String tag = payload.getTag();
            final NotificationCompat.Builder notificationBuilder =
                    AEPNotificationUtil.constructNotificationBuilder(
                            payload.getMessageData(),
                            CampaignPushTrackerActivity.class,
                            AEPPushTemplateBroadcastReceiver.class
                    );
            notificationManager.notify(tag.hashCode(), notificationBuilder.build());
        } catch (final IllegalArgumentException exception) {
            Log.error(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Failed to create a push notification, an illegal argument exception occurred:"
                            + " %s",
                    exception.getLocalizedMessage());
            return false;
        } catch (final NotificationConstructionFailedException exception) {
            Log.error(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Failed to create a push notification, a notification construction failed"
                            + " exception occurred: %s",
                    exception.getLocalizedMessage());
            return false;
        }
        return true;
    }
}