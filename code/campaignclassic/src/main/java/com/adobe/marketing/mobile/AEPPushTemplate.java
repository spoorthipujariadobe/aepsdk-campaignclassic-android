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

import android.app.NotificationManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DataReaderException;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class AEPPushTemplate {

    /** Enum to denote the type of action */
    enum ActionType {
        DEEPLINK,
        WEBURL,
        DISMISS,
        OPENAPP,
        NONE
    }

    /** Class representing the action button with label, link and type */
    static class ActionButton {
        private final String label;
        private final String link;
        private final ActionType type;

        ActionButton(final String label, final String link, final String type) {
            this.label = label;
            this.link = link;
            this.type = getActionTypeFromString(type);
        }

        String getLabel() {
            return label;
        }

        String getLink() {
            return link;
        }

        ActionType getType() {
            return type;
        }
    }

    static final class ActionButtonType {
        static final String DEEPLINK = "DEEPLINK";
        static final String WEBURL = "WEBURL";
        static final String DISMISS = "DISMISS";
        static final String OPENAPP = "OPENAPP";
    }

    static final class ActionButtons {
        static final String LABEL = "label";
        static final String URI = "uri";
        static final String TYPE = "type";
    }

    static final class NotificationPriority {
        static int from(final String priority) {
            if (priority == null) return NotificationCompat.PRIORITY_DEFAULT;
            final Integer resolvedPriority = notificationPriorityMap.get(priority);
            if (resolvedPriority == null) return NotificationCompat.PRIORITY_DEFAULT;
            return resolvedPriority;
        }

        static final String PRIORITY_DEFAULT = "PRIORITY_DEFAULT";
        static final String PRIORITY_MIN = "PRIORITY_MIN";
        static final String PRIORITY_LOW = "PRIORITY_LOW";
        static final String PRIORITY_HIGH = "PRIORITY_HIGH";
        static final String PRIORITY_MAX = "PRIORITY_MAX";
    }

    static final class NotificationVisibility {
        static final String PUBLIC = "PUBLIC";
        static final String PRIVATE = "PRIVATE";
        static final String SECRET = "SECRET";
    }

    static final String SELF_TAG = "AEPPushTemplate";
    // Legacy push payload values
    private static final int ACTION_BUTTON_CAPACITY = 3;
    private final String title;
    private final String body;
    private final String sound;
    private int badgeCount = 0;
    private int notificationPriority = NotificationCompat.PRIORITY_DEFAULT;
    private int notificationImportance = NotificationManager.IMPORTANCE_DEFAULT;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int notificationVisibility = NotificationCompat.VISIBILITY_PRIVATE;

    private final String channelId;
    private final String icon;
    private final String imageUrl;
    private final AEPPushTemplate.ActionType actionType;
    private final String actionUri;
    private final String actionButtonsString;
    private final Map<String, String> data;
    private final String messageId;
    private final String deliveryId;

    // push template payload values
    // Required, Version of the payload assigned by the authoring UI.
    private final int payloadVersion;
    // Optional, Body of the message shown in the expanded message layout (setCustomBigContentView)
    private final String expandedBodyText;
    // Optional, Text color for adb_body. Represented as six character hex, e.g. 00FF00
    private final String expandedBodyTextColor;
    // Optional, Text color for adb_title. Represented as six character hex, e.g. 00FF00
    private final String titleTextColor;
    // Optional, Color for the notification's small icon. Represented as six character hex, e.g.
    // 00FF00
    private final String smallIconColor;
    // Optional, Color for the notification's background. Represented as six character hex, e.g.
    // 00FF00
    private final String notificationBackgroundColor;
    // Optional, If present, show a "remind later" button using the value provided as its label
    private final String remindLaterText;
    // Optional, If present, schedule this notification to be re-delivered at this epoch timestamp
    // (in seconds) provided.
    private final long remindLaterTimestamp;
    // Optional, If present and a notification with the same tag is already being shown, the new
    // notification replaces the existing one in the notification drawer.
    private final String tag;
    // Optional, If present sets the "ticker" text, which is sent to accessibility services.
    private final String ticker;
    // Optional, when set to false or unset, the notification is automatically dismissed when the
    // user clicks it in the panel. When set to true, the notification persists even when the user
    // clicks it.
    private final boolean sticky;

    @RequiresApi(api = Build.VERSION_CODES.N)
    static final Map<String, Integer> notificationImportanceMap =
            new HashMap<String, Integer>() {
                {
                    put(NotificationPriority.PRIORITY_MIN, NotificationManager.IMPORTANCE_MIN);
                    put(NotificationPriority.PRIORITY_LOW, NotificationManager.IMPORTANCE_LOW);
                    put(
                            NotificationPriority.PRIORITY_DEFAULT,
                            NotificationManager.IMPORTANCE_DEFAULT);
                    put(NotificationPriority.PRIORITY_HIGH, NotificationManager.IMPORTANCE_HIGH);
                    put(NotificationPriority.PRIORITY_MAX, NotificationManager.IMPORTANCE_MAX);
                }
            };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static final Map<String, Integer> notificationVisibilityMap =
            new HashMap<String, Integer>() {
                {
                    put(NotificationVisibility.PRIVATE, NotificationCompat.VISIBILITY_PRIVATE);
                    put(NotificationVisibility.PUBLIC, NotificationCompat.VISIBILITY_PUBLIC);
                    put(NotificationVisibility.SECRET, NotificationCompat.VISIBILITY_SECRET);
                }
            };

    static final Map<String, Integer> notificationPriorityMap =
            new HashMap<String, Integer>() {
                {
                    put(NotificationPriority.PRIORITY_MIN, NotificationCompat.PRIORITY_MIN);
                    put(NotificationPriority.PRIORITY_LOW, NotificationCompat.PRIORITY_LOW);
                    put(NotificationPriority.PRIORITY_DEFAULT, NotificationCompat.PRIORITY_DEFAULT);
                    put(NotificationPriority.PRIORITY_HIGH, NotificationCompat.PRIORITY_HIGH);
                    put(NotificationPriority.PRIORITY_MAX, NotificationCompat.PRIORITY_MAX);
                }
            };

    AEPPushTemplate(@NonNull final Map<String, String> messageData)
            throws IllegalArgumentException {
        this.data = messageData;
        if (data == null) {
            throw new IllegalArgumentException(
                    "Payload extraction failed because data provided is null.");
        }

        // fast fail if required data is not present
        try {
            this.title = DataReader.getString(data, CampaignPushConstants.PushPayloadKeys.TITLE);
        } catch (final DataReaderException dataReaderException) {
            throw new IllegalArgumentException("Required field \"adb_title\" not found.");
        }

        try {
            final String bodyText =
                    DataReader.optString(
                            data,
                            CampaignPushConstants.PushPayloadKeys.BODY,
                            DataReader.getString(
                                    data, CampaignPushConstants.PushPayloadKeys.ACC_PAYLOAD_BODY));
            if (StringUtils.isNullOrEmpty(bodyText)) {
                throw new DataReaderException("Required field \"adb_body\" or \"_msg\" not found.");
            }
            this.body = bodyText;
        } catch (final DataReaderException dataReaderException) {
            throw new IllegalArgumentException(dataReaderException.getMessage());
        }

        try {
            this.messageId =
                    DataReader.getString(data, CampaignPushConstants.Tracking.Keys.MESSAGE_ID);
        } catch (final DataReaderException dataReaderException) {
            throw new IllegalArgumentException("Required field \"_mId\" not found.");
        }

        try {
            this.deliveryId =
                    DataReader.getString(data, CampaignPushConstants.Tracking.Keys.DELIVERY_ID);
        } catch (final DataReaderException dataReaderException) {
            throw new IllegalArgumentException("Required field \"_dId\" not found.");
        }

        // optional push template data
        this.payloadVersion =
                Integer.parseInt(
                        DataReader.optString(
                                data,
                                CampaignPushConstants.PushPayloadKeys.VERSION,
                                CampaignPushConstants.DefaultValues.LEGACY_PAYLOAD_VERSION_STRING));
        this.sound = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.SOUND, null);
        this.imageUrl =
                DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.IMAGE_URL, null);
        this.channelId =
                DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.CHANNEL_ID, null);
        this.actionUri =
                DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.ACTION_URI, null);
        this.icon = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.ICON, null);
        this.expandedBodyText =
                DataReader.optString(
                        data, CampaignPushConstants.PushPayloadKeys.EXPANDED_BODY_TEXT, null);
        this.expandedBodyTextColor =
                DataReader.optString(
                        data, CampaignPushConstants.PushPayloadKeys.EXPANDED_BODY_TEXT_COLOR, null);
        this.titleTextColor =
                DataReader.optString(
                        data, CampaignPushConstants.PushPayloadKeys.TITLE_TEXT_COLOR, null);
        this.smallIconColor =
                DataReader.optString(
                        data, CampaignPushConstants.PushPayloadKeys.SMALL_ICON_COLOR, null);
        this.notificationBackgroundColor =
                DataReader.optString(
                        data,
                        CampaignPushConstants.PushPayloadKeys.NOTIFICATION_BACKGROUND_COLOR,
                        null);
        this.remindLaterText =
                DataReader.optString(
                        messageData, CampaignPushConstants.PushPayloadKeys.REMIND_LATER_TEXT, "");
        final String timestampString =
                DataReader.optString(
                        data, CampaignPushConstants.PushPayloadKeys.REMIND_LATER_TIMESTAMP, null);
        this.remindLaterTimestamp =
                StringUtils.isNullOrEmpty(timestampString)
                        ? CampaignPushConstants.DefaultValues.DEFAULT_REMIND_LATER_TIMESTAMP
                        : Long.parseLong(timestampString);
        this.tag = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.TAG, null);
        this.ticker =
                DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.TICKER, null);
        final String stickyValue =
                DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.STICKY, null);
        this.sticky =
                StringUtils.isNullOrEmpty(stickyValue) ? false : Boolean.parseBoolean(stickyValue);

        try {
            final String count = data.get(CampaignPushConstants.PushPayloadKeys.BADGE_NUMBER);
            if (StringUtils.isNullOrEmpty(count)) {
                this.badgeCount = Integer.parseInt(count);
            }
        } catch (final NumberFormatException e) {
            Log.debug(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Exception in converting notification badge count to int - %s",
                    e.getLocalizedMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.notificationImportance =
                    getNotificationImportanceFromString(
                            data.get(CampaignPushConstants.PushPayloadKeys.NOTIFICATION_PRIORITY));
        } else {
            this.notificationPriority =
                    NotificationPriority.from(
                            data.get(CampaignPushConstants.PushPayloadKeys.NOTIFICATION_PRIORITY));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.notificationVisibility =
                    getNotificationVisibilityFromString(
                            data.get(
                                    CampaignPushConstants.PushPayloadKeys.NOTIFICATION_VISIBILITY));
        }

        this.actionType =
                getActionTypeFromString(
                        data.get(CampaignPushConstants.PushPayloadKeys.ACTION_TYPE));
        this.actionButtonsString = data.get(CampaignPushConstants.PushPayloadKeys.ACTION_BUTTONS);
    }

    @NonNull String getTitle() {
        return title;
    }

    @NonNull String getBody() {
        return body;
    }

    @Nullable String getSound() {
        return sound;
    }

    int getBadgeCount() {
        return badgeCount;
    }

    int getNotificationPriority() {
        return notificationPriority;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    int getNotificationVisibility() {
        return notificationVisibility;
    }

    int getNotificationImportance() {
        return notificationImportance;
    }

    @Nullable String getChannelId() {
        return channelId;
    }

    @Nullable String getIcon() {
        return icon;
    }

    @Nullable String getImageUrl() {
        return imageUrl;
    }

    @NonNull String getMessageId() {
        return messageId;
    }

    @NonNull String getDeliveryId() {
        return deliveryId;
    }

    @Nullable String getExpandedBodyText() {
        return expandedBodyText;
    }

    @Nullable String getExpandedBodyTextColor() {
        return expandedBodyTextColor;
    }

    @Nullable String getTitleTextColor() {
        return titleTextColor;
    }

    @Nullable String getSmallIconColor() {
        return smallIconColor;
    }

    @Nullable String getNotificationBackgroundColor() {
        return notificationBackgroundColor;
    }

    @Nullable String getRemindLaterText() {
        return remindLaterText;
    }

    long getRemindLaterTimestamp() {
        return remindLaterTimestamp;
    }

    @Nullable String getNotificationTag() {
        return tag;
    }

    @Nullable String getNotificationTicker() {
        return ticker;
    }

    boolean isNotificationSticky() {
        return sticky;
    }

    /**
     * @return an {@link AEPPushTemplate.ActionType}
     */
    @Nullable AEPPushTemplate.ActionType getActionType() {
        return actionType;
    }

    @Nullable String getActionUri() {
        return actionUri;
    }

    @Nullable String getActionButtonsString() {
        return actionButtonsString;
    }

    @NonNull Map<String, String> getData() {
        return data;
    }

    /**
     * Convenience method to modify the notification data payload. This is used in the following
     * scenarios: - Setting a carousel image URI as the data map's image URI to allow a basic push
     * template notification to be shown in a fallback situation.
     *
     * @param key {@code String} value containing the key to modify
     * @param value {@code String} value containing the new value to be used
     */
    void modifyData(final String key, final String value) {
        data.put(key, value);
    }

    int getPayloadVersion() {
        return payloadVersion;
    }

    private static ActionType getActionTypeFromString(final String type) {
        if (StringUtils.isNullOrEmpty(type)) {
            return ActionType.NONE;
        }

        switch (type) {
            case ActionButtonType.DEEPLINK:
                return ActionType.DEEPLINK;
            case ActionButtonType.WEBURL:
                return ActionType.WEBURL;
            case ActionButtonType.DISMISS:
                return ActionType.DISMISS;
            case ActionButtonType.OPENAPP:
                return ActionType.OPENAPP;
        }

        return ActionType.NONE;
    }

    static List<ActionButton> getActionButtonsFromString(final String actionButtons) {
        if (actionButtons == null) {
            Log.debug(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Exception in converting actionButtons json string to json object, Error :"
                            + " actionButtons is null");
            return null;
        }
        List<ActionButton> actionButtonList = new ArrayList<>(ACTION_BUTTON_CAPACITY);
        try {
            final JSONArray jsonArray = new JSONArray(actionButtons);
            for (int i = 0; i < jsonArray.length(); i++) {
                final JSONObject jsonObject = jsonArray.getJSONObject(i);
                final ActionButton button = getActionButton(jsonObject);
                if (button == null) continue;
                actionButtonList.add(button);
            }
        } catch (final JSONException e) {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Exception in converting actionButtons json string to json object, Error : %s",
                    e.getLocalizedMessage());
            return null;
        }
        return actionButtonList;
    }

    private static ActionButton getActionButton(final JSONObject jsonObject) {
        try {
            final String label = jsonObject.getString(ActionButtons.LABEL);
            if (label.isEmpty()) {
                Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Label is empty");
                return null;
            }
            String uri = null;
            final String type = jsonObject.getString(ActionButtons.TYPE);
            if (type.equals(ActionButtonType.WEBURL) || type.equals(ActionButtonType.DEEPLINK)) {
                uri = jsonObject.optString(ActionButtons.URI);
            }
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Creating an ActionButton with label (%s), uri (%s), and type (%s)",
                    label,
                    uri,
                    type);
            return new ActionButton(label, uri, type);
        } catch (final JSONException e) {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Exception in converting actionButtons json string to json object, Error : %s",
                    e.getLocalizedMessage());
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private int getNotificationImportanceFromString(final String priority) {
        if (StringUtils.isNullOrEmpty(priority)) return NotificationManager.IMPORTANCE_DEFAULT;
        final Integer resolvedImportance = notificationImportanceMap.get(priority);
        if (resolvedImportance == null) return NotificationManager.IMPORTANCE_DEFAULT;
        return resolvedImportance;
    }

    /**
     * Returns the notification visibility from the string. If the string is null or not a valid
     * visibility, returns Notification.VISIBILITY_PRIVATE.
     *
     * @param visibility {@link String} representing the visibility of the notification
     * @return {@code int} representing the visibility of the notification
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int getNotificationVisibilityFromString(final String visibility) {
        if (StringUtils.isNullOrEmpty(visibility)) return NotificationCompat.VISIBILITY_PRIVATE;
        final Integer resolvedVisibility = notificationVisibilityMap.get(visibility);
        if (resolvedVisibility == null) return NotificationCompat.VISIBILITY_PRIVATE;
        return resolvedVisibility;
    }
}
