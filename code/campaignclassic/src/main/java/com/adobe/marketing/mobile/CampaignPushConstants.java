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

    final class NotificationAction {
        static final String DISMISSED = "Notification Dismissed";
        static final String OPENED = "Notification Opened";
        static final String BUTTON_CLICKED = "Notification Button Clicked";

        private NotificationAction() {}
    }

    final class Tracking {
        final class Keys {
            static final String ACTION_ID = "actionId";
            static final String ACTION_URI = "actionUri";
            static final String DELIVERY_ID = "_dId";
            static final String MESSAGE_ID = "_mId";

            private Keys() {}
        }

        private Tracking() {}
    }

    final class DefaultValues {
        static final String LEGACY_PAYLOAD_VERSION_STRING = "0";
        static final String AUTO_CAROUSEL_MODE = "auto";
        static final String FILMSTRIP_CAROUSEL_MODE = "filmstrip";
        static final int AUTO_CAROUSEL_MINIMUM_IMAGE_COUNT = 1;
        static final int FILMSTRIP_CAROUSEL_MINIMUM_IMAGE_COUNT = 3;
        static final int CAROUSEL_MAX_BITMAP_WIDTH = 300;
        static final int CAROUSEL_MAX_BITMAP_HEIGHT = 200;

        private DefaultValues() {}
    }

    final class IntentActions {
        static final String FILMSTRIP_LEFT_CLICKED = "filmstrip_left";
        static final String FILMSTRIP_RIGHT_CLICKED = "filmstrip_right";

        private IntentActions() {}
    }

    final class IntentKeys {
        static final String BACKGROUND_COLOR = "bgColor";
        static final String TITLE_COLOR = "titleColor";
        static final String BODY_COLOR = "bodyColor";
        static final String LEFT_IMAGE = "leftImage";
        static final String CENTER_IMAGE = "centerImage";
        static final String RIGHT_IMAGE = "rightImage";
        static final String LEFT_CAPTION = "leftCaption";
        static final String CENTER_CAPTION = "centerCaption";
        static final String RIGHT_CAPTION = "rightCaption";
        static final String SMALL_TITLE_TEXT = "smallLayoutTitleText";
        static final String SMALL_BODY_TEXT = "smallLayoutBodyText";
        static final String EXPANDED_BODY_TEXT = "smallLayoutBodyText";
        static final String CHANNEL_ID = "channelId";

        private IntentKeys() {}
    }

    final class PushPayloadKeys {
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
