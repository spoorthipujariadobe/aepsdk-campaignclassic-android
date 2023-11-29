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
import android.app.NotificationManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DataReaderException;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;

import com.adobe.marketing.mobile.services.Log;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class is used to create push notification payload objects from a remote message.
 * It provides with functions for getting attributes of push payload (title, body, actions etc ...)
 */
public class AEPPushPayload {
    static final String SELF_TAG = "AEPPushPayload";

    static final class NotificationPriorities {
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

    @RequiresApi(api = Build.VERSION_CODES.N)
    static final Map<String, Integer> notificationImportanceMap = new HashMap<String, Integer>() {{
        put(NotificationPriorities.PRIORITY_MIN, NotificationManager.IMPORTANCE_MIN);
        put(NotificationPriorities.PRIORITY_LOW, NotificationManager.IMPORTANCE_LOW);
        put(NotificationPriorities.PRIORITY_DEFAULT, NotificationManager.IMPORTANCE_DEFAULT);
        put(NotificationPriorities.PRIORITY_HIGH, NotificationManager.IMPORTANCE_HIGH);
        put(NotificationPriorities.PRIORITY_MAX, NotificationManager.IMPORTANCE_MAX);
    }};


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static final Map<String, Integer> notificationVisibilityMap = new HashMap<String, Integer>() {{
        put(NotificationVisibility.PRIVATE, Notification.VISIBILITY_PRIVATE);
        put(NotificationVisibility.PUBLIC, Notification.VISIBILITY_PUBLIC);
        put(NotificationVisibility.SECRET, Notification.VISIBILITY_SECRET);
    }};

    static final Map<String, Integer> notificationPriorityMap = new HashMap<String, Integer>() {{
        put(NotificationPriorities.PRIORITY_MIN, Notification.PRIORITY_MIN);
        put(NotificationPriorities.PRIORITY_LOW, Notification.PRIORITY_LOW);
        put(NotificationPriorities.PRIORITY_DEFAULT, Notification.PRIORITY_DEFAULT);
        put(NotificationPriorities.PRIORITY_HIGH, Notification.PRIORITY_HIGH);
        put(NotificationPriorities.PRIORITY_MAX, Notification.PRIORITY_MAX);
    }};

    private static final int ACTION_BUTTON_CAPACITY = 3;
    private String title;
    private String body;
    private String sound;
    private int badgeCount;
    private int notificationPriority = Notification.PRIORITY_DEFAULT;
    private int notificationImportance = NotificationManager.IMPORTANCE_DEFAULT;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int notificationVisibility = Notification.VISIBILITY_PRIVATE;
    private String channelId;
    private String icon;
    private String imageUrl;
    private ActionType actionType;
    private String actionUri;
    private List<ActionButton> actionButtons = new ArrayList<>(ACTION_BUTTON_CAPACITY);
    private Map<String, String> data;
    private String messageId;
    private String deliveryId;

    // ================= push template data =================
    // Required, Version of the payload assigned by the authoring UI.
    private int payloadVersion;
    // Optional, Informs the reader of what properties may exist in the template object. Value matches pushTemplate enum below. 1 represents a "carousel" template.
    private PushTemplate pushTemplate;
    // Optional, Body of the message shown in the expanded message layout (setCustomBigContentView)
    private String expandedBodyText;
    // Optional, Text color for adb_body. Represented as six character hex, e.g. 00FF00
    private String expandedBodyTextColor;
    // Optional, Text color for adb_title. Represented as six character hex, e.g. 00FF00
    private String titleTextColor;
    // Optional, Color for the notification's small icon. Represented as six character hex, e.g. 00FF00
    private String smallIconColor;
    // Optional, Color for the notification's background. Represented as six character hex, e.g. 00FF00
    private String notificationBackgroundColor;

    // ================= basic push template specific data =================
    // Optional, If present, show a "remind later" button using the value provided as its label
    private String remindLaterText;
    // Optional, If present, schedule this notification to be re-delivered at this epoch timestamp (in seconds) provided.
    private long remindLaterTimestamp;

    // ================= carousel push template specific data =================
    // Optional, Determines how the carousel will be operated. Valid values are "auto" or "manual". Default is "auto".
    private String carouselOperationMode; // auto or manual
    // Required, "default" or "filmstrip"
    private String carouselLayoutType;
    // Required, One or more Items in the carousel defined by the CarouselItem class
    private ArrayList<CarouselItem> carouselItems = new ArrayList<>();

    // ================= input field push template specific data =================
    // Optional, Placeholder text for the text input field
    private String inputFieldText;
    // Optional, Once feedback has been submitted, use this text as the notification's body
    private String feedbackReceivedText;
    // Optional, Once feedback has been submitted, use this as the notification's image
    private String feedbackReceivedImage;

    private class CarouselItem {
        // Required, URI to an image to be shown for the carousel item
        private final String imageUri;
        // Optional, caption to show when the carousel item is visible
        private final String captionText;
        // Optional, URI to handle when the item is touched by the user. If no uri is provided for the item, adb_uri will be handled instead.
        private final String interactionUri;

        CarouselItem(final String imageUri, final String captionText, final String interactionUri) {
            this.imageUri = imageUri;
            this.captionText = captionText;
            this.interactionUri = interactionUri;
        }

        public String getImageUri() {
            return imageUri;
        }

        public String getCaptionText() {
            return captionText;
        }

        public String getInteractionUri() {
            return interactionUri;
        }
    }

    /**
     * Constructor
     * <p>
     * Provides the AEPPushPayload object
     *
     * @param message {@link RemoteMessage} object received from {@link com.google.firebase.messaging.FirebaseMessagingService}
     */
    public AEPPushPayload(final RemoteMessage message) {
        if (message == null) {
            Log.error(CampaignPushConstants.LOG_TAG, SELF_TAG, "Failed to create AEPPushPayload, remote message is null");
            return;
        }

        final Map<String, String> messageData = message.getData();
        if (MapUtils.isNullOrEmpty(messageData)) {
            Log.error(CampaignPushConstants.LOG_TAG, SELF_TAG, "Failed to create AEPPushPayload, remote message data payload is null or empty");
            return;
        }

        this.messageId = messageData.get(CampaignPushConstants.Tracking.Keys.MESSAGE_ID);
        if (StringUtils.isNullOrEmpty(messageId)) {
            Log.error(CampaignPushConstants.LOG_TAG, SELF_TAG, "Failed to create AEPPushPayload, message id is null or empty");
            return;
        }

        this.deliveryId = messageData.get(CampaignPushConstants.Tracking.Keys.DELIVERY_ID);
        if (StringUtils.isNullOrEmpty(messageId)) {
            Log.error(CampaignPushConstants.LOG_TAG, SELF_TAG, "Failed to create AEPPushPayload, delivery id is null or empty");
            return;
        }

        init(messageData);
    }

    /**
     * Constructor
     * <p>
     * Provides the AEPPushPayload object
     *
     * @param data {@link Map} map which indicates the data part of {@link RemoteMessage}
     */
    public AEPPushPayload(final Map<String, String> data) {
        init(data);
    }

    public PushTemplate getPushTemplate() {
        return pushTemplate;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getSound() {
        return sound;
    }

    public int getBadgeCount() {
        return badgeCount;
    }

    public int getNotificationPriority() {
        return notificationPriority;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public int getNotificationVisibility() {
        return notificationVisibility;
    }

    public int getNotificationImportance() {
        return notificationImportance;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getIcon() {
        return icon;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    /**
     * @return an {@link ActionType}
     */
    public ActionType getActionType() {
        return actionType;
    }

    public String getActionUri() {
        return actionUri;
    }

    /**
     * Returns list of action buttons which provides label, action type and action link
     *
     * @return List of {@link ActionButton}
     */
    public List<ActionButton> getActionButtons() {
        return actionButtons;
    }

    public Map<String, String> getData() {
        return data;
    }

    public int getPayloadVersion() {
        return payloadVersion;
    }

    public String getExpandedBodyText() {
        return expandedBodyText;
    }

    public String getExpandedBodyTextColor() {
        return expandedBodyTextColor;
    }

    public String getTitleTextColor() {
        return titleTextColor;
    }

    public String getSmallIconColor() {
        return smallIconColor;
    }

    public String getNotificationBackgroundColor() {
        return notificationBackgroundColor;
    }

    public String getRemindLaterText() {
        return remindLaterText;
    }

    public long getRemindLaterTimestamp() {
        return remindLaterTimestamp;
    }

    public String getCarouselOperationMode() {
        return carouselOperationMode;
    }

    public String getCarouselLayoutType() {
        return carouselLayoutType;
    }

    public ArrayList<CarouselItem> getCarouselItems() {
        return carouselItems;
    }

    public String getInputFieldText() {
        return inputFieldText;
    }

    public String getFeedbackReceivedText() {
        return feedbackReceivedText;
    }

    public String getFeedbackReceivedImage() {
        return feedbackReceivedImage;
    }

    private void init(final Map<String, String> data) {
        this.data = data;
        if (data == null) {
            Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Payload extraction failed because data provided is null");
            return;
        }
        this.title = data.get(CampaignPushConstants.PushPayloadKeys.TITLE);
        this.body = data.get(CampaignPushConstants.PushPayloadKeys.BODY);
        this.sound = data.get(CampaignPushConstants.PushPayloadKeys.SOUND);
        this.channelId = data.get(CampaignPushConstants.PushPayloadKeys.CHANNEL_ID);
        this.icon = data.get(CampaignPushConstants.PushPayloadKeys.ICON);
        this.actionUri = data.get(CampaignPushConstants.PushPayloadKeys.ACTION_URI);
        this.imageUrl = data.get(CampaignPushConstants.PushPayloadKeys.IMAGE_URL);

        // required push template data
        this.pushTemplate = PushTemplate.fromInt(Integer.parseInt(Objects.requireNonNull(data.get(CampaignPushConstants.PushPayloadKeys.TEMPLATE_TYPE))));
        if (!pushTemplate.equals(PushTemplate.UNKNOWN)) {
            // get required data first and fast fail if not present
            try {
                this.payloadVersion = Integer.parseInt(DataReader.getString(data, CampaignPushConstants.PushPayloadKeys.VERSION));
            } catch (final DataReaderException dataReaderException) {
                Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Required field \"adb_version\" not found.");
                return;
            }

            if (pushTemplate.equals(PushTemplate.AUTO_CAROUSEL) || pushTemplate.equals(PushTemplate.MANUAL_CAROUSEL)) {
                try {
                    this.carouselLayoutType = DataReader.getString(data, CampaignPushConstants.PushPayloadKeys.CAROUSEL_LAYOUT);
                    final List<Map<String, String>> carouselItemMaps = DataReader.getTypedListOfMap(String.class, data, CampaignPushConstants.PushPayloadKeys.CAROUSEL_ITEMS);
                    for (final Map<String, String> carouselItemMap : carouselItemMaps) {
                        final String carouselImage = carouselItemMap.get(CampaignPushConstants.PushPayloadKeys.CAROUSEL_ITEM_IMAGE);
                        if (StringUtils.isNullOrEmpty(carouselImage)) {
                            break;
                        }
                        final String text = carouselItemMap.get(CampaignPushConstants.PushPayloadKeys.CAROUSEL_ITEM_TEXT);
                        final String uri = carouselItemMap.get(CampaignPushConstants.PushPayloadKeys.CAROUSEL_ITEM_URI);
                        final CarouselItem carouselItem = new CarouselItem(carouselImage, text, uri);
                        carouselItems.add(carouselItem);

                    }
                } catch (final DataReaderException dataReaderException) {
                    Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Required fields \"adb_car_layout\" or \"adb_items\" not found.");
                    return;
                }
            }

            // optional push template data
            this.expandedBodyText = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.EXPANDED_BODY_TEXT, "");
            this.expandedBodyTextColor = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.EXPANDED_BODY_TEXT_COLOR, "");
            this.titleTextColor = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.TITLE_TEXT_COLOR, "");
            this.smallIconColor = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.SMALL_ICON_COLOR, "");
            this.notificationBackgroundColor = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.NOTIFICATION_BACKGROUND_COLOR, "");
            this.remindLaterText = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.REMIND_LATER_TEXT, "");
            this.remindLaterTimestamp = Long.parseLong(DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.REMIND_LATER_TIMESTAMP, ""));
            this.carouselOperationMode = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.CAROUSEL_OPERATION_MODE, "");
            this.inputFieldText = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.INPUT_FIELD_TEXT, "");
            this.feedbackReceivedText = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.FEEDBACK_RECEIVED_TEXT, "");
            this.feedbackReceivedImage = DataReader.optString(data, CampaignPushConstants.PushPayloadKeys.FEEDBACK_RECEIVED_IMAGE, "");
        }

        try {
            String count = data.get(CampaignPushConstants.PushPayloadKeys.BADGE_NUMBER);
            if (count != null) {
                this.badgeCount = Integer.parseInt(count);
            }
        } catch (final NumberFormatException e) {
            Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Exception in converting notification badge count to int - %s", e.getLocalizedMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.notificationImportance = getNotificationImportanceFromString(data.get(CampaignPushConstants.PushPayloadKeys.NOTIFICATION_PRIORITY));
        } else {
            this.notificationPriority = getNotificationPriorityFromString(data.get(CampaignPushConstants.PushPayloadKeys.NOTIFICATION_PRIORITY));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.notificationVisibility = getNotificationVisibilityFromString(data.get(CampaignPushConstants.PushPayloadKeys.NOTIFICATION_VISIBILITY));
        }

        this.actionType = getActionTypeFromString(data.get(CampaignPushConstants.PushPayloadKeys.ACTION_TYPE));
        this.actionButtons = getActionButtonsFromString(data.get(CampaignPushConstants.PushPayloadKeys.ACTION_BUTTONS));
    }

    private int getNotificationPriorityFromString(final String priority) {
        if (priority == null) return Notification.PRIORITY_DEFAULT;
        final Integer resolvedPriority = notificationPriorityMap.get(priority);
        if (resolvedPriority == null) return Notification.PRIORITY_DEFAULT;
        return resolvedPriority;
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    private int getNotificationImportanceFromString(final String priority) {
        if (StringUtils.isNullOrEmpty(priority)) return Notification.PRIORITY_DEFAULT;
        final Integer resolvedImportance = notificationImportanceMap.get(priority);
        if (resolvedImportance == null) return Notification.PRIORITY_DEFAULT;
        return resolvedImportance;
    }

    // Returns the notification visibility from the string
    // If the string is null or not a valid visibility, returns Notification.VISIBILITY_PRIVATE
    //
    // @param visibility string representing the visibility of the notification
    // @return int representing the visibility of the notification
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int getNotificationVisibilityFromString(final String visibility) {
        if (StringUtils.isNullOrEmpty(visibility)) return Notification.VISIBILITY_PRIVATE;
        final Integer resolvedVisibility = notificationVisibilityMap.get(visibility);
        if (resolvedVisibility == null) return Notification.VISIBILITY_PRIVATE;
        return resolvedVisibility;
    }

    private ActionType getActionTypeFromString(final String type) {
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

    private List<ActionButton> getActionButtonsFromString(final String actionButtons) {
        if (actionButtons == null) {
            Log.debug(CampaignPushConstants.LOG_TAG, SELF_TAG, "Exception in converting actionButtons json string to json object, Error : actionButtons is null");
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
            Log.warning(CampaignPushConstants.LOG_TAG, SELF_TAG, "Exception in converting actionButtons json string to json object, Error : %s", e.getLocalizedMessage());
            return null;
        }
        return actionButtonList;
    }

    private ActionButton getActionButton(final JSONObject jsonObject) {
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

            Log.trace(CampaignPushConstants.LOG_TAG, SELF_TAG, "Creating an ActionButton with label (%s), uri (%s), and type (%s)", label, uri, type);
            return new ActionButton(label, uri, type);
        } catch (final JSONException e) {
            Log.warning(CampaignPushConstants.LOG_TAG, SELF_TAG, "Exception in converting actionButtons json string to json object, Error : %s", e.getLocalizedMessage());
            return null;
        }
    }

    /**
     * Enum to denote the type of action
     */
    public enum ActionType {
        DEEPLINK, WEBURL, DISMISS, OPENAPP, NONE
    }

    /**
     * Class representing the action button with label, link and type
     */
    public class ActionButton {
        private final String label;
        private final String link;
        private final ActionType type;


        public ActionButton(final String label, final String link, final String type) {
            this.label = label;
            this.link = link;
            this.type = getActionTypeFromString(type);
        }

        public String getLabel() {
            return label;
        }

        public String getLink() {
            return link;
        }

        public ActionType getType() {
            return type;
        }
    }

    // Check if the push notification is silent push notification.
    boolean isSilentPushMessage() {
        return data != null && title == null && body == null;
    }
}
