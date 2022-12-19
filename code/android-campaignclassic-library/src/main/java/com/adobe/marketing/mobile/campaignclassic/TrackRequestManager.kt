/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */
package com.adobe.marketing.mobile.campaignclassic

import androidx.annotation.VisibleForTesting
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.MobilePrivacyStatus
import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.Networking
import com.adobe.marketing.mobile.services.ServiceProvider
import java.net.HttpURLConnection
import java.util.regex.Pattern

/**
 * Handles logic related to notification tracking
 *
 */
internal class TrackRequestManager {
    private val UUID_PATTERN = Pattern.compile("^(?i)[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}$")
    private val extensionApi: ExtensionApi
    private val networkService: Networking?

    constructor(extensionApi: ExtensionApi) {
        this.extensionApi = extensionApi
        networkService = ServiceProvider.getInstance().networkService
    }

    @VisibleForTesting
    constructor(extensionApi: ExtensionApi, networkService: Networking?) {
        this.extensionApi = extensionApi
        this.networkService = networkService
    }

    companion object {
        private const val SELF_TAG = "TrackRequestManager"
    }

    /**
     * Sends a track request to the configured Campaign Classic tracking server upon notification receive or click.
     * <p>
     * Track request is not sent under following conditions:
     * - Configuration is not available
     * - Privacy status is [MobilePrivacyStatus.OPT_OUT] or [MobilePrivacyStatus.UNKNOWN]
     * - Tracking server is missing in configuration
     * - Tracking identifiers messageId `_mId` and deliveryId `_dId` are missing in current event data
     *
     * @param event incoming track [Event]
     * @param tagId [String] indicating whether it is a notification receive or notification click request
     */
    fun handleTrackRequest(event: Event, tagId: String) {
        val configData = CampaignClassicConfiguration(event, extensionApi)

        // bail if privacy status is not opted in
        if (configData.privacyStatus != MobilePrivacyStatus.OPT_IN) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "handleTrackRequest - Failed to process TrackNotification request," +
                    "MobilePrivacyStatus is not optedIn."
            )
            return
        }

        val trackingServer = configData.trackingServer ?: run {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "handleTrackRequest - Failed to process TrackNotification request, Configuration not available."
            )
            return
        }

        val deliveryId = event.deliveryId ?: run {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "handleTrackRequest - Failed to process TrackNotification request," +
                    "trackingInfo deliveryId is null (missing key `_dId` from tracking Info) or empty."
            )
            return
        }

        var messageId = event.messageId ?: run {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "handleTrackRequest - Failed to process TrackNotification request," +
                    "trackingInfo messageId is null (missing key `_mId` from tracking Info) or empty."
            )
            return
        }

        // V8 message Id is received in UUID format while V7 still comes as an integer(decimal) represented as a string.
        // No transformation is required for the V8 UUID however for V7, message Id is parsed as an integer and converted to hex string.
        if (!messageId.isValidUUID()) {
            try {
                messageId = java.lang.String.format("%x", messageId.toInt())
            } catch (ex: NumberFormatException) {
                Log.debug(
                    CampaignClassicConstants.LOG_TAG, SELF_TAG,
                    "handleTrackRequest - Failed to process TrackNotification request," +
                        "messageId $messageId could not be parsed as a UUID or a decimal (integer)." +
                        "Error ${ex.message}"
                )
                return
            }
        }

        // create URL
        val trackUrl = java.lang.String.format(
            CampaignClassicConstants.TRACKING_API_URL_BASE, trackingServer,
            messageId, deliveryId, tagId
        )

        // send network request
        sendTrackingRequest(trackUrl, configData.timeout)
    }

    // ========================================================
    // private methods
    // ========================================================
    /**
     * Sends a notification track request to the configured Campaign Classic tracking server as specified by the `trackUrl`.
     *
     * @param trackUrl [String] containing the Campaign Classic tracking server url to connect to
     * @param requestTimeout `int` containing the request timeout to use for the connection
     */
    private fun sendTrackingRequest(trackUrl: String, requestTimeout: Int) {
        if (networkService == null) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "sendTrackingRequest - Cannot send request, Network service is not available"
            )
            return
        }

        val networkRequest = NetworkRequest(
            trackUrl, HttpMethod.GET, null, null,
            requestTimeout, requestTimeout
        )

        // send tracking request
        Log.trace(
            CampaignClassicConstants.LOG_TAG, SELF_TAG,
            "sendTrackingRequest - TrackingNotification network call initiated with URL :  $trackUrl."
        )
        networkService.connectAsync(networkRequest) {
            if (it != null) {
                if (it.responseCode == HttpURLConnection.HTTP_OK) {
                    Log.trace(
                        CampaignClassicConstants.LOG_TAG, SELF_TAG,
                        "sendTrackingRequest - Connection successful ${it.responseMessage}."
                    )
                } else {
                    Log.warning(
                        CampaignClassicConstants.LOG_TAG, SELF_TAG,
                        "sendTrackingRequest - Connection failed ${it.responseMessage}."
                    )
                }

                // close the connection
                it.close()
            }
        }
    }

    /**
     * Match the [String] against a compiled UUID pattern
     * &#39;^(?i)[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}$&#39;.
     *
     * @return true if string matches UUID format, false otherwise
     */
    private fun String.isValidUUID(): Boolean {
        return UUID_PATTERN.matcher(this).matches()
    }
}
