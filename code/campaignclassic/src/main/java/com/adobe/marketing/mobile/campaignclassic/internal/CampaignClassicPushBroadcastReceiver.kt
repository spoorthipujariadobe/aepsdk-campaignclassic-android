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

package com.adobe.marketing.mobile.campaignclassic.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adobe.marketing.mobile.notificationbuilder.NotificationBuilder
import com.adobe.marketing.mobile.notificationbuilder.NotificationConstructionFailedException
import com.adobe.marketing.mobile.notificationbuilder.PushTemplateConstants
import com.adobe.marketing.mobile.notificationbuilder.RemindLaterHandler
import com.adobe.marketing.mobile.services.Log

internal class CampaignClassicPushBroadcastReceiver : BroadcastReceiver() {
    private val SELF_TAG = "CampaignClassicPushBroadcastReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action.isNullOrEmpty()) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(context)
        val tag = intent.getStringExtra(PushTemplateConstants.PushPayloadKeys.TAG)

        // this should never happen since tag is set to _mId in the push message
        if (tag.isNullOrEmpty()) {
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
                PushTemplateConstants.IntentActions.SCHEDULED_NOTIFICATION_BROADCAST -> {
                    val builder: NotificationCompat.Builder = NotificationBuilder.constructNotificationBuilder(
                        intent,
                        CampaignClassicPushTrackerActivity::class.java,
                        CampaignClassicPushBroadcastReceiver::class.java
                    )
                    notificationManager.notify(tag.hashCode(), builder.build())
                }
                PushTemplateConstants.IntentActions.REMIND_LATER_CLICKED -> {
                    RemindLaterHandler.handleRemindIntent(intent, CampaignClassicPushBroadcastReceiver::class.java)
                }
            }
        } catch (e: NotificationConstructionFailedException) {
            Log.error(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "Failed to create a push notification, a notification construction failed:" +
                    "${e.localizedMessage} exception occurred"
            )
        } catch (e: IllegalArgumentException) {
            Log.error(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "Failed to create a push notification, an illegal argument exception occurred:" +
                    "${e.localizedMessage} exception occurred"
            )
        }
    }
}
