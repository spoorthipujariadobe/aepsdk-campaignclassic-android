package com.adobe.marketing.mobile.campaignclassic.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adobe.marketing.mobile.AEPMessagingService
import com.adobe.marketing.mobile.notificationbuilder.NotificationBuilder
import com.adobe.marketing.mobile.notificationbuilder.NotificationConstructionFailedException
import com.adobe.marketing.mobile.notificationbuilder.PushTemplateConstants
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.util.StringUtils


internal class CampaignClassicPushBroadcastReceiver: BroadcastReceiver() {
    private val SELF_TAG = "CampaignClassicPushBroadcastReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action.isNullOrEmpty()) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(context)
        val tag = intent.getStringExtra(PushTemplateConstants.PushPayloadKeys.TAG)

        // this should never happen since tag is set to _mId in the push message
        if (StringUtils.isNullOrEmpty(tag)) {
            Log.warning(
                CampaignClassicConstants.LOG_TAG,
                SELF_TAG,
                "Ignoring notification action $action since tag is null or empty",
            )
            return
        }
        try {
            when (action) {
                PushTemplateConstants.IntentActions.FILMSTRIP_LEFT_CLICKED,
                PushTemplateConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED,
                PushTemplateConstants.IntentActions.MANUAL_CAROUSEL_LEFT_CLICKED,
                PushTemplateConstants.IntentActions.MANUAL_CAROUSEL_RIGHT_CLICKED,
                PushTemplateConstants.IntentActions.INPUT_RECEIVED,
                PushTemplateConstants.IntentActions.CATALOG_THUMBNAIL_CLICKED,
                PushTemplateConstants.IntentActions.RATING_ICON_CLICKED,
                PushTemplateConstants.IntentActions.TIMER_EXPIRED,
                PushTemplateConstants.IntentActions.SCHEDULED_NOTIFICATION_BROADCAST-> {
                    val builder: NotificationCompat.Builder = NotificationBuilder.constructNotificationBuilder(
                        intent,
                        CampaignClassicPushTrackerActivity::class.java,
                        CampaignClassicPushBroadcastReceiver::class.java
                    )
                    notificationManager.notify(tag.hashCode(), builder.build())
                }
                PushTemplateConstants.IntentActions.REMIND_LATER_CLICKED -> {
                    NotificationBuilder.handleRemindIntent(intent, CampaignClassicPushBroadcastReceiver::class.java)
                }
            }
        } catch (e: NotificationConstructionFailedException) {
            Log.error(CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "Failed to create a push notification, a notification construction failed:" +
                        "${e.localizedMessage} exception occurred"
            )
        } catch (e: IllegalArgumentException) {
            Log.error(CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "Failed to create a push notification, an illegal argument exception occurred:" +
                        "${e.localizedMessage} exception occurred"
            )
        }
    }
}