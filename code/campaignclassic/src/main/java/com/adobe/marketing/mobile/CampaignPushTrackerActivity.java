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

import android.app.Activity;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.HashMap;
import java.util.Map;

public class CampaignPushTrackerActivity extends Activity {

    private static final String SELF_TAG = "CampaignPushTrackerActivity";

    @Override
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        if (intent == null) {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Intent is null. Ignoring to track or take action on push notification"
                            + " interaction.");
            finish();
            return;
        }
        final String action = intent.getAction();
        if (StringUtils.isNullOrEmpty(action)) {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Intent action is null or empty. Ignoring to track or take action on push"
                            + " notification interaction.");
            finish();
            return;
        }

        switch (action) {
            case CampaignPushConstants.NotificationAction.OPENED:
                handlePushOpen(intent);
                break;
            case CampaignPushConstants.NotificationAction.BUTTON_CLICKED:
                handlePushButtonClicked(intent);
                break;
            default:
                break;
        }
        finish();
    }

    /**
     * Handles the push notification open action.
     *
     * @param intent the intent received from the push notification interaction
     */
    private void handlePushOpen(final Intent intent) {
        CampaignClassic.trackNotificationReceive(getTrackInfo(intent));
        executePushAction(intent);
    }

    /**
     * Handles clicks on push notification custom buttons.
     *
     * @param intent the intent received from interacting with buttons on push notification
     */
    private void handlePushButtonClicked(final Intent intent) {
        CampaignClassic.trackNotificationClick(getTrackInfo(intent));
        executePushAction(intent);
    }

    /**
     * Retrieves the Campaign Classic push notification tracking information from the received
     * notification's {@link Intent}
     *
     * @param intent the intent received from the push notification
     * @return {@link Map<String, String>} containing the notification's tracking information
     */
    private Map<String, String> getTrackInfo(final Intent intent) {
        final Map<String, String> trackInfo = new HashMap<>();
        final Bundle extras = intent.getExtras();
        if (extras == null) return trackInfo;

        for (final String key : extras.keySet()) {
            final Object value = extras.get(key);
            if (value != null) {
                if (key.equals(CampaignPushConstants.Tracking.Keys.MESSAGE_ID)) {
                    trackInfo.put(key, value.toString());
                } else if (key.equals(CampaignPushConstants.Tracking.Keys.DELIVERY_ID)) {
                    trackInfo.put(key, value.toString());
                }
            }
        }
        return trackInfo;
    }

    /**
     * Reads the URI and executes the action that is configured for the push notification.
     *
     * <p>If no URI is configured, by default the application will be opened.
     *
     * @param intent the intent received from the push notification
     */
    private void executePushAction(final Intent intent) {
        final String actionUri =
                intent.getStringExtra(CampaignPushConstants.Tracking.Keys.ACTION_URI);
        if (StringUtils.isNullOrEmpty(actionUri)) {
            openApplication();
        } else {
            openUri(actionUri);
        }

        // remove the notification if sticky notifications are false
        final boolean isStickyNotification =
                intent.getBooleanExtra(CampaignPushConstants.PushPayloadKeys.STICKY, false);
        final String tag = intent.getStringExtra(CampaignPushConstants.PushPayloadKeys.TAG);
        if (isStickyNotification) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "the sticky notification setting is true, will not remove the notification"
                            + " with tag %s.",
                    tag);
            return;
        }

        final Context context =
                ServiceProvider.getInstance().getAppContextService().getApplicationContext();
        if (context == null) {
            return;
        }

        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);
        if (StringUtils.isNullOrEmpty(tag)) {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "the sticky notification setting is false but the tag is null or empty,"
                            + " default to removing all displayed notifications for %s.",
                    getApplication().getPackageName());
            notificationManager.cancelAll();
            return;
        }

        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "the sticky notification setting is false, removing notification with tag %s.",
                tag);
        notificationManager.cancel(tag.hashCode());
    }

    /**
     * Use this method to create an intent to open the application. If the application is already
     * open and in the foreground, the action will resume the current activity.
     */
    private void openApplication() {
        final Activity currentActivity =
                ServiceProvider.getInstance().getAppContextService().getCurrentActivity();
        final Intent launchIntent;
        if (currentActivity != null) {
            launchIntent = new Intent(currentActivity, currentActivity.getClass());
        } else {
            Log.debug(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "There is no active activity. Starting the launcher Activity.");
            launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        }
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launchIntent);
        } else {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unable to create an intent to open the application from the notification"
                            + " interaction.");
        }
    }

    /**
     * Use this method to create an intent to open the the provided URI.
     *
     * @param uri the uri to open
     */
    private void openUri(final String uri) {
        try {
            final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Unable to open the URI from the notification interaction. URI: %s",
                    uri);
        }
    }
}
