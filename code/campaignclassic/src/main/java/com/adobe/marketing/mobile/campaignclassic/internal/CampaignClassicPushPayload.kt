package com.adobe.marketing.mobile.campaignclassic.internal

import com.adobe.marketing.mobile.notificationbuilder.NotificationPriority
import com.adobe.marketing.mobile.notificationbuilder.NotificationVisibility
import com.adobe.marketing.mobile.notificationbuilder.PushTemplateConstants
import com.adobe.marketing.mobile.util.DataReader
import com.adobe.marketing.mobile.util.MapUtils
import com.adobe.marketing.mobile.util.StringUtils
import com.google.firebase.messaging.RemoteMessage


internal class CampaignClassicPushPayload {
    val messageData: MutableMap<String?, String?>
    val messageId: String?
    val deliveryId: String?
    var tag: String?

    private val ACC_PAYLOAD_BODY = "_msg"

    /**
     * Constructor
     *
     *
     * Provides the CampaignClassicPushPayload object
     *
     * @param message [RemoteMessage] object received from [     ]
     * @throws IllegalArgumentException if the message, message data, message id, or delivery id is
     * null
     */
    @Throws(IllegalArgumentException::class)
    constructor(message: RemoteMessage?) : this(message?.data) {
        // migrate any ACC push notification object payload keys if needed
        val notification = message?.notification ?: return
        convertNotificationPayloadData(notification)
    }

    /**
     * Constructor
     *
     *
     * Provides the CampaignClassicPushPayload object
     *
     * @param messageData [,][<] containing the message data present in a
     * notification received from [com.google.firebase.messaging.FirebaseMessagingService]
     * @throws IllegalArgumentException if the message data, message id, or delivery id is null
     */
    @Throws(IllegalArgumentException::class)
    constructor(remoteMessageData: Map<String, String>?) {
        if (remoteMessageData.isNullOrEmpty()) {
            throw IllegalArgumentException("Failed to create CampaignClassicPushPayload, remote message data payload is null or"
                    + " empty.")
        }
        messageId = remoteMessageData[CampaignClassicConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_MESSAGE_ID]
        if (StringUtils.isNullOrEmpty(messageId)) {
            throw IllegalArgumentException("Failed to create CampaignClassicPushPayload, message id is null or empty.")
        }
        deliveryId = remoteMessageData[CampaignClassicConstants.EventDataKeys.CampaignClassic.TRACK_INFO_KEY_DELIVERY_ID]
        if (StringUtils.isNullOrEmpty(deliveryId)) {
            throw IllegalArgumentException("Failed to create CampaignClassicPushPayload, delivery id is null or empty.")
        }

        this.messageData = remoteMessageData.toMutableMap()

        // get the tag from the payload. if no tag was present in the payload use the message id
        // instead as its guaranteed to always be present.
        tag = if (!StringUtils.isNullOrEmpty(messageData[PushTemplateConstants.PushPayloadKeys.TAG]))
            messageData[PushTemplateConstants.PushPayloadKeys.TAG] else messageId

        // convert _msg to adb_body if needed
        if (StringUtils.isNullOrEmpty(messageData[PushTemplateConstants.PushPayloadKeys.BODY])) {
            this.messageData[PushTemplateConstants.PushPayloadKeys.BODY] = DataReader.optString(messageData, ACC_PAYLOAD_BODY, null)
        }
    }

    private fun convertNotificationPayloadData(notification: RemoteMessage.Notification) {
        // Migrate the 13 ACC KVP to "adb" prefixed keys.
        // Note, the key value pairs present in the data payload are preferred over the notification
        // key value pairs.
        // The notification key value pairs will only be added to the message data if the
        // corresponding key
        // does not have a value.
        // message.android.notification.icon to adb_small_icon
        // message.android.notification.sound to adb_sound
        // message.android.notification.tag	to adb_tag
        // message.android.notification.click_action to adb_uri
        // message.android.notification.channel_id to adb_channel_id
        // message.android.notification.ticker to adb_ticker (NEW)
        // message.android.notification.sticky to adb_sticky (NEW)
        // message.android.notification.visibility to adb_n_visibility
        // message.android.notification.notification_priority to adb_n_priority
        // message.android.notification.notification_count to adb_n_count
        // message.notification.body to adb_body
        // message.notification.title to adb_title
        // message.notification.image to adb_image
        if (StringUtils.isNullOrEmpty(messageData[PushTemplateConstants.PushPayloadKeys.TAG])) {
            tag = notification.tag
            messageData[PushTemplateConstants.PushPayloadKeys.TAG] = tag
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.SMALL_ICON]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.SMALL_ICON] = notification.icon
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.SOUND]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.SOUND] = notification.sound
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.ACTION_URI]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.ACTION_URI] = notification.clickAction
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.CHANNEL_ID]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.CHANNEL_ID] = notification.channelId
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.TICKER]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.TICKER] = notification.ticker
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.STICKY]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.STICKY] =
                notification.sticky.toString()
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.VISIBILITY]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.VISIBILITY] =
                NotificationVisibility.fromValue(notification.visibility).stringValue
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.PRIORITY]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.PRIORITY] =
                NotificationPriority.fromValue(notification.notificationPriority).stringValue
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.BADGE_COUNT]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.BADGE_COUNT] =
                notification.notificationCount.toString()
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.BODY]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.BODY] = notification.body
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.TITLE]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.TITLE] = notification.title
        }
        if (StringUtils.isNullOrEmpty(
                messageData[PushTemplateConstants.PushPayloadKeys.IMAGE_URL]
            )
        ) {
            messageData[PushTemplateConstants.PushPayloadKeys.IMAGE_URL] =
                notification.imageUrl.toString()
        }
    }
}