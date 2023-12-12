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
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import com.adobe.marketing.mobile.services.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * This class is the entry point for all push notifications received from Firebase.
 *
 * <p>Once the AEPMessagingService is registered in the AndroidManifest.xml, this class will
 * automatically handle display and tracking of Campaign Classic push notifications.
 */
public class AEPMessagingService extends FirebaseMessagingService {
    static final String SELF_TAG = "AEPMessagingService";

    @Override
    public void onNewToken(@NonNull final String token) {
        super.onNewToken(token);
        MobileCore.setPushIdentifier(token);
    }

    @Override
    public void onMessageReceived(@NonNull final RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        handleRemoteMessage(this, remoteMessage);
    }

    public static boolean handleRemoteMessage(
            @NonNull final Context context, @NonNull final RemoteMessage remoteMessage) {
        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);
        AEPPushPayload payload;
        try {
            payload = new AEPPushPayload(remoteMessage);
        } catch (final IllegalArgumentException exception) {
            Log.error(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Failed to create push payload object, an exception occurred:" + " %s",
                    exception.getLocalizedMessage());
            return false;
        }

        try {
            final Notification notification =
                    AEPPushNotificationBuilder.buildPushNotification(payload, context);
            // display notification
            notificationManager.notify(payload.getMessageId().hashCode(), notification);
        } catch (final NotificationConstructionFailedException exception) {
            Log.error(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Failed to create a push notification, an exception occurred:" + " %s",
                    exception.getLocalizedMessage());
            return false;
        }

        return true;
    }
}
