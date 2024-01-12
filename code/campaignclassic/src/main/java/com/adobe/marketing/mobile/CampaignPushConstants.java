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

/**
 * This class holds all constant values used only by the Campaign Classic extension for handling
 * push notifications
 */
final class CampaignPushConstants {

    private CampaignPushConstants() {}

    static final String LOG_TAG = "CampaignClassicExtension";
    static final String CACHE_BASE_DIR = "campaignclassic";
    static final String PUSH_IMAGE_CACHE = "pushimagecache";

    static final class NotificationAction {
        static final String DISMISSED = "Notification Dismissed";
        static final String OPENED = "Notification Opened";
        static final String BUTTON_CLICKED = "Notification Button Clicked";

        private NotificationAction() {}
    }

    static final class Tracking {
        static final class Keys {
            static final String ACTION_ID = "actionId";
            static final String ACTION_URI = "actionUri";
            static final String DELIVERY_ID = "_dId";
            static final String MESSAGE_ID = "_mId";

            private Keys() {}
        }

        private Tracking() {}
    }

    static final class DefaultValues {
        static final String SILENT_NOTIFICATION_CHANNEL_ID = "silent";
        static final String LEGACY_PAYLOAD_VERSION_STRING = "0";
        static final int CAROUSEL_MAX_BITMAP_WIDTH = 300;
        static final int CAROUSEL_MAX_BITMAP_HEIGHT = 200;
        static final String AUTO_CAROUSEL_MODE = "auto";
        static final String MANUAL_CAROUSEL_MODE = "manual";
        static final String FILMSTRIP_CAROUSEL_MODE = "filmstrip";
        static final int AUTO_CAROUSEL_MINIMUM_IMAGE_COUNT = 1;
        static final int MANUAL_CAROUSEL_MINIMUM_IMAGE_COUNT = 1;
        static final int CENTER_INDEX = 1;
        static final int FILMSTRIP_CAROUSEL_MINIMUM_IMAGE_COUNT = 3;
        static final int ACTION_BUTTON_CAPACITY = 3;
        // TODO: revisit this value. should cache time be configurable rather than have a static
        // value?
        static final long PUSH_NOTIFICATION_IMAGE_CACHE_EXPIRY_IN_MILLISECONDS =
                259200000; // 3 days
        static final long DEFAULT_REMIND_LATER_TIMESTAMP =
                -1L; // -1L means that no remind later timestamp was found in the action button
        // payload

        private DefaultValues() {}
    }

    static final class IntentActions {
        static final String FILMSTRIP_LEFT_CLICKED = "filmstrip_left";
        static final String FILMSTRIP_RIGHT_CLICKED = "filmstrip_right";
        static final String REMIND_LATER_CLICKED = "remind_clicked";
        static final String SCHEDULED_NOTIFICATION_BROADCAST = "scheduled_notification_broadcast";
        static final String MANUAL_CAROUSEL_LEFT_CLICKED = "manual_left";
        static final String MANUAL_CAROUSEL_RIGHT_CLICKED = "manual_right";

        private IntentActions() {}
    }

    static final class IntentKeys {
        static final String CENTER_IMAGE_INDEX = "centerImageIndex";
        static final String IMAGE_URI = "imageUri";
        static final String IMAGE_URLS = "imageUrls";
        static final String IMAGE_CAPTIONS = "imageCaptions";
        static final String IMAGE_CLICK_ACTIONS = "imageClickActions";
        static final String ACTION_URI = "actionUri";
        static final String CHANNEL_ID = "channelId";
        static final String CUSTOM_SOUND = "customSound";
        static final String TITLE_TEXT = "titleText";
        static final String BODY_TEXT = "bodyText";
        static final String EXPANDED_BODY_TEXT = "expandedBodyText";
        static final String NOTIFICATION_BACKGROUND_COLOR = "notificationBackgroundColor";
        static final String TITLE_TEXT_COLOR = "titleTextColor";
        static final String EXPANDED_BODY_TEXT_COLOR = "expandedBodyTextColor";
        static final String MESSAGE_ID = "messageId";
        static final String DELIVERY_ID = "deliveryId";
        static final String BADGE_COUNT = "badgeCount";
        static final String SMALL_ICON = "smallIcon";
        static final String SMALL_ICON_COLOR = "smallIconColor";
        static final String VISIBILITY = "visibility";
        static final String IMPORTANCE = "importance";
        static final String REMIND_TS = "remindTimestamp";
        static final String REMIND_LABEL = "remindLaterLabel";
        static final String ACTION_BUTTONS_STRING = "actionButtonsString";

        private IntentKeys() {}
    }

    static final class MethodNames {
        static final String SET_BACKGROUND_COLOR = "setBackgroundColor";
        static final String SET_TEXT_COLOR = "setTextColor";

        private MethodNames() {}
    }

    static final class FriendlyViewNames {
        static final String NOTIFICATION_BACKGROUND = "notification background";
        static final String NOTIFICATION_TITLE = "notification title";
        static final String NOTIFICATION_BODY_TEXT = "notification body text";

        private FriendlyViewNames() {}
    }

    static final class PushPayloadKeys {
        public static final String TEMPLATE_TYPE = "adb_template_type";
        public static final String TITLE = "adb_title";
        public static final String BODY = "adb_body";
        public static final String SOUND = "adb_sound";
        public static final String BADGE_NUMBER = "adb_n_count";
        public static final String NOTIFICATION_VISIBILITY = "adb_n_visibility";
        public static final String NOTIFICATION_PRIORITY = "adb_n_priority";
        public static final String CHANNEL_ID = "adb_channel_id";
        public static final String ICON = "adb_icon";
        public static final String IMAGE_URL = "adb_image";
        public static final String ACTION_TYPE = "adb_a_type";
        public static final String ACTION_URI = "adb_uri";
        public static final String ACTION_BUTTONS = "adb_act";
        public static final String VERSION = "adb_version";
        public static final String CAROUSEL_LAYOUT = "adb_car_layout";
        public static final String CAROUSEL_ITEMS = "adb_items";
        public static final String CAROUSEL_ITEM_IMAGE = "img";
        public static final String CAROUSEL_ITEM_TEXT = "txt";
        public static final String CAROUSEL_ITEM_URI = "uri";
        public static final String EXPANDED_BODY_TEXT = "adb_body_ex";
        public static final String EXPANDED_BODY_TEXT_COLOR = "adb_clr_body";
        public static final String TITLE_TEXT_COLOR = "adb_clr_title";
        public static final String SMALL_ICON_COLOR = "adb_clr_icon";
        public static final String NOTIFICATION_BACKGROUND_COLOR = "adb_clr_bg";
        public static final String REMIND_LATER_TEXT = "adb_rem_txt";
        public static final String REMIND_LATER_TIMESTAMP = "adb_rem_ts";
        public static final String CAROUSEL_OPERATION_MODE = "adb_car_mode";
        public static final String INPUT_FIELD_TEXT = "adb_input_txt";
        public static final String FEEDBACK_RECEIVED_TEXT = "adb_feedback_txt";
        public static final String FEEDBACK_RECEIVED_IMAGE = "adb_feedback_img";

        private PushPayloadKeys() {}
    }
}
