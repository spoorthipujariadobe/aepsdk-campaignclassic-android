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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.util.DataReader;
import java.util.Map;

class InputBoxPushTemplate extends AEPPushTemplate {
    // Optional, Placeholder text for the text input field
    private final String inputFieldText;
    // Optional, Once feedback has been submitted, use this text as the notification's body
    private final String feedbackReceivedText;
    // Optional, Once feedback has been submitted, use this as the notification's image
    private final String feedbackReceivedImage;
    // Optional, If present, show a "remind later" button using the value provided as its label
    private final String remindLaterText;
    // Optional, If present, schedule this notification to be re-delivered at this epoch timestamp
    // (in seconds) provided.
    private final long remindLaterTimestamp;

    InputBoxPushTemplate(@NonNull final Map<String, String> messageData) {
        super(messageData);
        this.remindLaterText =
                DataReader.optString(
                        messageData, CampaignPushConstants.PushPayloadKeys.REMIND_LATER_TEXT, "");
        this.remindLaterTimestamp =
                Long.parseLong(
                        DataReader.optString(
                                messageData,
                                CampaignPushConstants.PushPayloadKeys.REMIND_LATER_TIMESTAMP,
                                ""));
        this.inputFieldText =
                DataReader.optString(
                        messageData, CampaignPushConstants.PushPayloadKeys.INPUT_FIELD_TEXT, "");
        this.feedbackReceivedText =
                DataReader.optString(
                        messageData,
                        CampaignPushConstants.PushPayloadKeys.FEEDBACK_RECEIVED_TEXT,
                        "");
        this.feedbackReceivedImage =
                DataReader.optString(
                        messageData,
                        CampaignPushConstants.PushPayloadKeys.FEEDBACK_RECEIVED_IMAGE,
                        "");
    }

    @Nullable String getRemindLaterText() {
        return remindLaterText;
    }

    long getRemindLaterTimestamp() {
        return remindLaterTimestamp;
    }

    @Nullable String getInputFieldText() {
        return inputFieldText;
    }

    @Nullable String getFeedbackReceivedText() {
        return feedbackReceivedText;
    }

    @Nullable String getFeedbackReceivedImage() {
        return feedbackReceivedImage;
    }
}
