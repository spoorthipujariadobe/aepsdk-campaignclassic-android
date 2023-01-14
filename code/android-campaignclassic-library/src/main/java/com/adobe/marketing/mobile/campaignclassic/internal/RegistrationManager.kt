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
package com.adobe.marketing.mobile.campaignclassic.internal

import androidx.annotation.VisibleForTesting
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.ExtensionApi
import com.adobe.marketing.mobile.MobilePrivacyStatus
import com.adobe.marketing.mobile.services.DeviceInforming
import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.NamedCollection
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.Networking
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.util.UrlUtils
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import java.util.UUID

/**
 * Handles logic related to registering device token with Campaign Classic server
 */
internal class RegistrationManager {
    private val extensionApi: ExtensionApi
    private val deviceInfoService: DeviceInforming?
    private val dataStore: NamedCollection?
    private val networkService: Networking?

    constructor(extensionApi: ExtensionApi) {
        this.extensionApi = extensionApi
        deviceInfoService = ServiceProvider.getInstance().deviceInfoService
        dataStore = ServiceProvider.getInstance().dataStoreService?.getNamedCollection(
            CampaignClassicConstants.DATASTORE_KEY
        )
        networkService = ServiceProvider.getInstance().networkService
    }

    @VisibleForTesting
    constructor (
        extensionApi: ExtensionApi,
        deviceInfoService: DeviceInforming?,
        dataStore: NamedCollection?,
        networkService: Networking?
    ) {
        this.extensionApi = extensionApi
        this.deviceInfoService = deviceInfoService
        this.dataStore = dataStore
        this.networkService = networkService
    }

    companion object {
        private const val SELF_TAG = "RegistrationManager"
    }

    /**
     * Sends a device registration request to the configured Campaign Classic server.
     * <p>
     *  Register request is not sent under following conditions:
     * - Configuration is not available
     * - Privacy status is [MobilePrivacyStatus.OPT_OUT] or [MobilePrivacyStatus.UNKNOWN]
     * - Campaign Classic is not configured
     * - Registration information has not changed since the last request
     *
     * @param event incoming registration request [Event] containing all the device/user details
     */
    fun registerDevice(event: Event) {

        // retrieve the device token from the event
        // device token is the unique token received from Firebase service through the application
        // bail out from the registration request if device token is unavailable
        val registrationToken = event.deviceToken ?: run {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "registerDevice - Failed to process device registration request," +
                    "device token is not available."
            )
            return
        }

        // bail out if the privacy is not opted In
        val configData = CampaignClassicConfiguration(event, extensionApi)
        if (configData.privacyStatus != MobilePrivacyStatus.OPT_IN) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "registerDevice - Failed to process device registration request," +
                    "MobilePrivacyStatus is not optedIn."
            )
            return
        }

        // bail out if marketing server or integration key is not available
        val marketingServer = configData.marketingServer ?: run {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "registerDevice - Failed to process device registration request," +
                    "Marketing server is not configured."
            )
            return
        }

        val integrationKey = configData.integrationKey ?: run {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "registerDevice - Failed to process device registration request," +
                    "Integration key is not configured."
            )
            return
        }

        // retrieve the userKey from the event
        // userKey is a string containing user identifier e.g. email
        val userKey = event.userKey ?: ""
        val additionalParametersString =
            CampaignClassicMapSerializer.serializeMap(event.additionalParameters)
        val deviceUniqueId = deviceInfoService?.deviceUniqueId
        val deviceUuid = if (deviceUniqueId != null) UUID(
            deviceUniqueId.hashCode().toLong(),
            deviceUniqueId.hashCode().toLong()
        ).toString() else ""

        val registrationInfoHash: String? = try {
            getSha256Hash(registrationToken + userKey + additionalParametersString.hashCode() + deviceUuid)
        } catch (ex: Exception) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "registerDevice - Failed to process device registration request," +
                    "Cannot create registration info hash. Error $ex.message"
            )
            null
        }

        // bail out, if the registration request data has not changed
        if (!hasRegistrationInfoChanged(registrationInfoHash)) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "registerDevice - Not sending device registration request," +
                    "there is no change in registration info."
            )
            return
        }

        val payload = prepareRegistrationParams(registrationToken, integrationKey, userKey, additionalParametersString)
        val registerURL = String.format(CampaignClassicConstants.REGISTER_API_URL_BASE, marketingServer)

        // make the network request
        sendRegistrationRequest(registerURL, payload, configData.timeout, registrationInfoHash)
    }

    /**
     * Clears the stored registration data hash from persistence.
     */
    fun clearRegistrationData() {
        updateDataStoreWithRegistrationInfo(null)
    }

    // ========================================================
    // private methods
    // ========================================================

    /**
     * Checks if persisted registration information has changed in SDK.
     *
     * @param registrationInfoHash [String] containing hash of registration token, userkey and additional params
     * @return returns true if persisted registration data hash is different from
     * registration data hash received from current event, false otherwise
     */
    private fun hasRegistrationInfoChanged(registrationInfoHash: String?): Boolean {
        if (dataStore == null) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "hasRegistrationInfoChanged - Cannot get registration info, Data store is not available."
            )
            return true
        }

        val storedRegistrationInfoHash: String? = dataStore.getString(
            CampaignClassicConstants.DataStoreKeys.TOKEN_HASH, null
        )

        if (registrationInfoHash == storedRegistrationInfoHash) {
            Log.trace(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "hasRegistrationInfoChanged - Registration information has not changed in Data store."
            )
            return false
        }
        return true
    }

    /**
     * Prepares payload string for the device registration request.
     *
     * @param registrationToken [String] containing the registration token
     * @param integrationKey [String] containing integration key
     * @param userKey [String] containing the user identifier e.g. email
     * @param additionalParameters xml [String] containing additional parameters to be sent in the request
     * @return [String] containing url encoded parameters to be sent in device registration POST payload
     */
    private fun prepareRegistrationParams(
        registrationToken: String,
        integrationKey: String,
        userKey: String,
        additionalParameters: String
    ): String {
        return java.lang.String.format(
            CampaignClassicConstants.REGISTER_PARAMS_FORMAT,
            UrlUtils.urlEncode(registrationToken),
            UrlUtils.urlEncode(integrationKey),
            UrlUtils.urlEncode(userKey),
            UrlUtils.urlEncode(deviceInfoService?.deviceUniqueId ?: ""),
            UrlUtils.urlEncode(deviceInfoService?.device ?: ""),
            UrlUtils.urlEncode(deviceInfoService?.deviceName ?: ""),
            UrlUtils.urlEncode(deviceInfoService?.deviceBrand ?: ""),
            UrlUtils.urlEncode(deviceInfoService?.deviceManufacturer ?: ""),
            UrlUtils.urlEncode(CampaignClassicConstants.REGISTER_PARAM_DEVICE_OS_NAME),
            UrlUtils.urlEncode(deviceInfoService?.operatingSystemName + " " + deviceInfoService?.operatingSystemVersion),
            UrlUtils.urlEncode(formatLocale(deviceInfoService?.activeLocale)),
            UrlUtils.urlEncode(additionalParameters)
        )
    }

    /**
     * Sends a registration request to the configured Campaign Classic registration server as specified by the `requestUrl`.
     *
     * @param requestUrl [String] containing Campaign Classic registration server url to connect to
     * @param payload `String` containing the url encoded registration payload
     * @param requestTimeout `int` containing the request timeout to use for the connection
     * @param registrationHash [String] containing the SHA256 hashed device registration information.
     * @return true if registration request was successful, false otherwise
     */
    private fun sendRegistrationRequest(requestUrl: String, payload: String, requestTimeout: Int, registrationHash: String?) {
        if (networkService == null) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "sendRegistrationRequest - Cannot send request, Network service is not available."
            )
            return
        }

        val headers = buildHeaders(payload)
        val postBody = payload.toByteArray(StandardCharsets.UTF_8)
        val networkRequest = NetworkRequest(
            requestUrl, HttpMethod.POST, postBody, headers,
            requestTimeout, requestTimeout
        )

        // send registration request
        Log.trace(CampaignClassicConstants.LOG_TAG, SELF_TAG, "sendRegistrationRequest - Registration request was sent with url $requestUrl")
        networkService.connectAsync(networkRequest) {
            if (it.responseCode == HttpURLConnection.HTTP_OK) {
                Log.debug(CampaignClassicConstants.LOG_TAG, SELF_TAG, "sendRegistrationRequest - Registration successful.")
                updateDataStoreWithRegistrationInfo(registrationHash)
            } else {
                Log.debug(
                    CampaignClassicConstants.LOG_TAG, SELF_TAG,
                    "sendRegistrationRequest - Unsuccessful Registration request with connection status ${it.responseCode}"
                )
            }
            it.close()
        }
    }

    private fun buildHeaders(payload: String): Map<String, String> {
        return mapOf(
            CampaignClassicConstants.EventDataKeys.CampaignClassic.HTTP_HEADER_KEY_CONTENT_TYPE
                to "${CampaignClassicConstants.EventDataKeys.CampaignClassic.HTTP_HEADER_CONTENT_TYPE_WWW_FORM_URLENCODED};${CampaignClassicConstants.EventDataKeys.CampaignClassic.HTTP_HEADER_CONTENT_TYPE_UTF8_CHARSET}",
            CampaignClassicConstants.EventDataKeys.CampaignClassic.HTTP_HEADER_KEY_CONTENT_LENGTH to payload.length.toString()
        )
    }

    /**
     * Updates `DataStore` with registration information.
     *
     * @param registrationHash [String] encrypted registration information to be persisted in `DataStore`
     */
    private fun updateDataStoreWithRegistrationInfo(registrationHash: String?) {
        if (dataStore == null) {
            Log.debug(
                CampaignClassicConstants.LOG_TAG, SELF_TAG,
                "updateDataStoreWithRegistrationInfo - Cannot set registration info, data store is not available."
            )
            return
        }

        if (!registrationHash.isNullOrBlank()) {
            dataStore.setString(CampaignClassicConstants.DataStoreKeys.TOKEN_HASH, registrationHash)
        } else {
            dataStore.remove(CampaignClassicConstants.DataStoreKeys.TOKEN_HASH)
        }
    }

    private fun formatLocale(locale: Locale?): String {
        return locale?.toString()?.replace('_', '-') ?: ""
    }

    /**
     * Generate `SHA256` hash for the provided input `String`.
     *
     * @param input [String] to be encoded
     * @return `String` containing `SHA256` hash for the input
     * @throws NoSuchAlgorithmException if algorithm implementation is not supported
     * @throws UnsupportedEncodingException if the provided charset is not supported
     */
    @Throws(NoSuchAlgorithmException::class, UnsupportedEncodingException::class)
    private fun getSha256Hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val textBytes = input.toByteArray(charset("iso-8859-1"))
        md.update(textBytes, 0, textBytes.size)
        val sb = StringBuilder()
        for (b in md.digest()) {
            sb.append(java.lang.String.format(CampaignClassicConstants.HEX_CONVERSION_FORMAT_STRING, b))
        }
        return sb.toString()
    }
}
