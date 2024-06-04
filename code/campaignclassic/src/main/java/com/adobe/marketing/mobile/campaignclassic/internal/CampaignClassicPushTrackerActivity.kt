package com.adobe.marketing.mobile.campaignclassic.internal

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.adobe.marketing.mobile.CampaignClassic
import com.adobe.marketing.mobile.notificationbuilder.PushTemplateConstants
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.util.StringUtils

internal class CampaignClassicPushTrackerActivity: Activity() {
    private val SELF_TAG = "CampaignClassicPushTrackerActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent == null) {
            Log.warning(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG, "Intent is null. Ignoring to track or take action on push notification"
                        + " interaction."
            )
            finish()
            return
        }
        val action = intent.action
        if (StringUtils.isNullOrEmpty(action)) {
            Log.warning(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG, "Intent action is null or empty. Ignoring to track or take action on push"
                        + " notification interaction."
            )
            finish()
            return
        }
        when (action) {
            PushTemplateConstants.NotificationAction.CLICKED -> handlePushClicked(intent)
            else -> {}
        }
        finish()
    }

    /**
     * Handles clicks on push notification.
     *
     * @param intent the intent received from interacting with buttons on push notification
     */
    private fun handlePushClicked(intent: Intent) {
        CampaignClassic.trackNotificationClick(getTrackInfo(intent))
        executePushAction(intent)
    }

    /**
     * Retrieves the Campaign Classic push notification tracking information from the received
     * notification's [Intent]
     *
     * @param intent the intent received from the push notification
     * @return [,][<] containing the notification's tracking information
     */
    private fun getTrackInfo(intent: Intent): Map<String, String> {
        val trackInfo = mutableMapOf<String, String>()
        val extras = intent.extras ?: return trackInfo
        for (key in extras.keySet()) {
            val value = extras.getString(key)
            if (value != null) {
                if (key == CampaignClassicConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID) {
                    trackInfo[key] = value
                } else if (key == CampaignClassicConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID) {
                    trackInfo[key] = value
                }
            }
        }
        return trackInfo
    }

    /**
     * Reads the URI and executes the action that is configured for the push notification.
     *
     *
     * If no URI is configured, by default the application will be opened.
     *
     * @param intent the intent received from the push notification
     */
    private fun executePushAction(intent: Intent) {
        val actionUri =
            intent.getStringExtra(PushTemplateConstants.TrackingKeys.ACTION_URI)
        if (actionUri.isNullOrEmpty()) {
            openApplication()
        } else {
            openUri(actionUri)
        }

        // remove the notification if sticky notifications are false
        val isStickyNotification = intent.getStringExtra(PushTemplateConstants.PushPayloadKeys.STICKY)?.toBoolean() ?: false
        val tag = intent.getStringExtra(PushTemplateConstants.PushPayloadKeys.TAG)
        if (isStickyNotification) {
            Log.trace(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG,
                "the sticky notification setting is true, will not remove the notification"
                        + " with tag %s.",
                tag
            )
            return
        }
        val context = ServiceProvider.getInstance().appContextService.applicationContext
            ?: return
        val notificationManager = NotificationManagerCompat.from(context)
        if (StringUtils.isNullOrEmpty(tag)) {
            Log.warning(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG,
                "he sticky notification setting is false but the tag is null or empty,"
                        + " default to removing all displayed notifications for %s.",
                application.packageName
            )
            notificationManager.cancelAll()
            return
        }
        Log.trace(
            CampaignClassicConstants.LOG_TAG,
            SELF_TAG,
            "the sticky notification setting is false, removing notification with tag %s.",
            tag
        )
        notificationManager.cancel(tag.hashCode())
    }

    /**
     * Use this method to create an intent to open the application. If the application is already
     * open and in the foreground, the action will resume the current activity.
     */
    private fun openApplication() {
        val currentActivity = ServiceProvider.getInstance().appContextService.currentActivity
        val launchIntent = if (currentActivity != null) {
            Intent(currentActivity, currentActivity.javaClass)
        } else {
            Log.debug(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG,
                "There is no active activity. Starting the launcher Activity."
            )
            packageManager.getLaunchIntentForPackage(packageName)
        }
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (intent.extras != null) {
                launchIntent.putExtras(intent.extras!!)
            }
            startActivity(launchIntent)
        } else {
            Log.warning(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG, "Unable to create an intent to open the application from the notification"
                        + " interaction."
            )
        }
    }

    /**
     * Use this method to create an intent to open the the provided URI.
     *
     * @param uri the uri to open
     */
    private fun openUri(uri: String) {
        try {
            val deeplinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            deeplinkIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (intent.extras != null) {
                deeplinkIntent.putExtras(intent.extras!!)
            }
            startActivity(deeplinkIntent)
        } catch (e: ActivityNotFoundException) {
            Log.warning(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG,
                "Unable to open the URI from the notification interaction. URI: %s",
                uri
            )
        }
    }
}