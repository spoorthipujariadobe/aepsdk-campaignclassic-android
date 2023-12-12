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

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.services.DataStoring;
import com.adobe.marketing.mobile.services.DeviceInforming;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheEntry;
import com.adobe.marketing.mobile.services.caching.CacheExpiry;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.util.StringUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility class for building push notifications.
 *
 * <p>This class is used internally by the CampaignClassic extension's {@link
 * AEPPushNotificationBuilder} to build the push notification. This class is not intended to be used
 * by the customers.
 */
class CampaignPushUtils {
    private static final String SELF_TAG = "CampaignPushUtils";

    static Bitmap download(@NonNull final String url) {
        Bitmap bitmap = null;
        HttpURLConnection connection = null;
        InputStream inputStream = null;

        try {
            final URL imageUrl = new URL(url);
            connection = (HttpURLConnection) imageUrl.openConnection();
            inputStream = connection.getInputStream();
            bitmap = BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Failed to download push notification image from url (%s). Exception: %s",
                    url,
                    e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.warning(
                            CampaignPushConstants.LOG_TAG,
                            SELF_TAG,
                            "IOException during closing Input stream while push notification image"
                                    + " from url (%s). Exception: %s ",
                            url,
                            e.getMessage());
                }
            }

            if (connection != null) {
                connection.disconnect();
            }
        }

        return bitmap;
    }

    static int getDefaultAppIcon(@NonNull final Context context) {
        final String packageName = context.getPackageName();
        try {
            return context.getPackageManager().getApplicationInfo(packageName, 0).icon;
        } catch (PackageManager.NameNotFoundException e) {
            Log.warning(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Package manager NameNotFoundException while reading default application icon."
                            + " Exception: %s",
                    e.getMessage());
        }
        return -1;
    }

    /**
     * Returns the Uri for the sound file with the given name. The sound file must be in the res/raw
     * directory. The sound file should be in format of .mp3, .wav, or .ogg
     *
     * @param soundName the name of the sound file
     * @param context the application {@link Context}
     * @return the Uri for the sound file with the given name
     */
    static Uri getSoundUriForResourceName(
            final @NonNull String soundName, @NonNull final Context context) {
        return Uri.parse(
                ContentResolver.SCHEME_ANDROID_RESOURCE
                        + "://"
                        + context.getPackageName()
                        + "/raw/"
                        + soundName);
    }

    /**
     * Returns the resource id for the icon with the given name. The icon file must be in the
     * res/drawable directory. If the icon file is not found, 0 is returned.
     *
     * @param iconName the name of the icon file
     * @param context the application {@link Context}
     * @return the resource id for the icon with the given name
     */
    static int getSmallIconWithResourceName(
            @Nullable final String iconName, @NonNull final Context context) {
        if (StringUtils.isNullOrEmpty(iconName)) {
            return 0;
        }
        return context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
    }

    /**
     * Converts a {@code Bitmap} into an {@code InputStream} to be used in caching images.
     *
     * @param bitmap {@link Bitmap} to be converted into an {@link InputStream}
     * @return an {@code InputStream} created from the provided bitmap
     */
    static InputStream bitmapToInputStream(final Bitmap bitmap) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        final byte[] bitmapData = byteArrayOutputStream.toByteArray();
        return new ByteArrayInputStream(bitmapData);
    }

    /**
     * Writes a {@code InputStream} to the Campaign Classic extension's asset cache location.
     *
     * @param cacheService {@link CacheService} the AEPSDK cache service
     * @param bitmapInputStream {@link InputStream} created from a download {@link Bitmap}
     * @param imageUri {@code String} containing the image uri to be used a cache key
     */
    static void cacheBitmapInputStream(
            final CacheService cacheService,
            final InputStream bitmapInputStream,
            final String imageUri) {
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Caching image downloaded from %s.",
                imageUri);
        // cache push notification images for 3 days
        final CacheEntry cacheEntry =
                new CacheEntry(
                        bitmapInputStream,
                        CacheExpiry.after(
                                CampaignPushConstants.DefaultValues
                                        .PUSH_NOTIFICATION_IMAGE_CACHE_EXPIRY),
                        null);
        cacheService.set(CampaignPushUtils.getAssetCacheLocation(), imageUri, cacheEntry);
    }

    /**
     * Retrieves the Campaign Classic extension's asset cache location.
     *
     * @return {@code String} containing the Campaign Classic extension's asset cache location
     */
    static String getAssetCacheLocation() {
        final DeviceInforming deviceInfoService =
                ServiceProvider.getInstance().getDeviceInfoService();
        String assetCacheLocation = null;
        if (deviceInfoService != null) {
            final File applicationCacheDir = deviceInfoService.getApplicationCacheDir();
            if (applicationCacheDir != null) {
                assetCacheLocation =
                        applicationCacheDir
                                + File.separator
                                + CampaignPushConstants.CACHE_BASE_DIR
                                + File.separator
                                + CampaignPushConstants.PUSH_IMAGE_CACHE;
            }
        }
        return assetCacheLocation;
    }

    /**
     * Writes a small icon resourceId in the Campaign Classic extension's datastore.
     *
     * @param resourceId {@code int} containing a small icon resource id
     */
    static void setSmallIconIdInDatastore(final int resourceId) {
        final DataStoring dataStoreService = ServiceProvider.getInstance().getDataStoreService();
        if (dataStoreService == null) {
            return;
        }
        final NamedCollection campaignDatastore =
                dataStoreService.getNamedCollection(CampaignPushConstants.DATASTORE_NAME);
        campaignDatastore.setInt(
                CampaignPushConstants.SMALL_ICON_RESOURCE_ID_DATASTORE_KEY, resourceId);
    }

    /**
     * Retrieves a small icon resourceId from the Campaign Classic extension's datastore.
     *
     * @return {@code int} containing a retrieved small icon resource id
     */
    static int getSmallIconIdFromDatastore() {
        final DataStoring dataStoreService = ServiceProvider.getInstance().getDataStoreService();
        if (dataStoreService == null) {
            return 0;
        }
        final NamedCollection campaignDatastore =
                dataStoreService.getNamedCollection(CampaignPushConstants.DATASTORE_NAME);
        return campaignDatastore.getInt(
                CampaignPushConstants.SMALL_ICON_RESOURCE_ID_DATASTORE_KEY, 0);
    }
}
