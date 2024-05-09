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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.adobe.marketing.mobile.services.ui.notification.NotificationBuilder;
import com.adobe.marketing.mobile.services.ui.notification.NotificationConstructionFailedException;
import com.adobe.marketing.mobile.util.StringUtils;

/** Broadcast receiver for handling custom push template notification interactions. */
public class AEPPushTemplateBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (StringUtils.isNullOrEmpty(action)) {
            return;
        }

        try {
            switch (action) {
                case CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED:
                case CampaignPushConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED:
                case CampaignPushConstants.IntentActions.MANUAL_CAROUSEL_LEFT_CLICKED:
                case CampaignPushConstants.IntentActions.MANUAL_CAROUSEL_RIGHT_CLICKED:
                case CampaignPushConstants.IntentActions.SCHEDULED_NOTIFICATION_BROADCAST:
                    NotificationBuilder.constructNotificationBuilder(intent, CampaignPushTrackerActivity.class, AEPPushTemplateBroadcastReceiver.class);
                    break;
                case CampaignPushConstants.IntentActions.REMIND_LATER_CLICKED:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        NotificationBuilder.constructNotificationBuilder(intent, CampaignPushTrackerActivity.class, AEPPushTemplateBroadcastReceiver.class);
                    }
                    break;
            }
        } catch (NotificationConstructionFailedException e) {
            throw new RuntimeException(e);
        }
    }
}