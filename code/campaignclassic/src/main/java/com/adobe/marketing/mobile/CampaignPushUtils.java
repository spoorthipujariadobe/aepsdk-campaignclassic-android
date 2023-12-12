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

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
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
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.UrlUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Downloads an image using the provided uri {@code String}. Prior to downloading, the image uri
     * is used o retrieve a {@code CacheResult} containing a previously cached image. If no cache
     * result is returned then a call to {@link CampaignPushUtils#download(String)} is made to
     * download then cache the image.
     *
     * <p>If a valid cache result is returned then no image is downloaded. Instead, a {@code Bitmap}
     * is created from the cache result and returned by this method.
     *
     * @param cacheService the AEPSDK {@link CacheService} to use for caching or retrieving
     *     downloaded image assets
     * @param uri {@code String} containing an image asset url
     * @return {@link Bitmap} containing the image referenced by the {@code String} uri
     */
    static Bitmap downloadImage(final CacheService cacheService, final String uri) {
        if (StringUtils.isNullOrEmpty(uri)) {
            return null;
        }
        final String cacheLocation = CampaignPushUtils.getAssetCacheLocation();
        final CacheResult cacheResult = cacheService.get(cacheLocation, uri);
        Bitmap pushImage = null;
        if (cacheResult == null) {
            if (UrlUtils.isValidUrl(uri)) { // we need to download the images first
                final Bitmap image = CampaignPushUtils.download(uri);
                if (image != null) {
                    Log.trace(
                            CampaignPushConstants.LOG_TAG,
                            SELF_TAG,
                            "Successfully download image from %s",
                            uri);
                    // scale down the bitmap to 300dp x 200dp as we don't want to use a full
                    // size image due to memory constraints
                    pushImage =
                            Bitmap.createScaledBitmap(
                                    image,
                                    CampaignPushConstants.DefaultValues.CAROUSEL_MAX_BITMAP_WIDTH,
                                    CampaignPushConstants.DefaultValues.CAROUSEL_MAX_BITMAP_HEIGHT,
                                    false);

                    // write bitmap to cache
                    try (final InputStream bitmapInputStream =
                            CampaignPushUtils.bitmapToInputStream(pushImage)) {
                        CampaignPushUtils.cacheBitmapInputStream(
                                cacheService, bitmapInputStream, uri);
                    } catch (final IOException exception) {
                        Log.trace(
                                CampaignPushConstants.LOG_TAG,
                                SELF_TAG,
                                "Exception occurred creating an input stream from a"
                                        + " bitmap: %s.",
                                exception.getLocalizedMessage());
                    }
                }
            }
        } else { // we have previously downloaded the image
            Log.trace(CampaignPushConstants.LOG_TAG, SELF_TAG, "Found cached image for %s.", uri);
            pushImage = BitmapFactory.decodeStream(cacheResult.getData());
        }
        return pushImage;
    }

    /**
     * Creates a pending intent for the provided image interaction uri {@code String}.
     *
     * @param context the current app {@link Context}
     * @param uri {@code String} containing an image interaction uri
     * @return {@link PendingIntent} created from the provided uri
     */
    static PendingIntent createPendingIntentFromImageInteractionUri(
            final Context context, final String uri) {
        if (StringUtils.isNullOrEmpty(uri)) {
            Log.trace(
                    CampaignPushConstants.LOG_TAG,
                    SELF_TAG,
                    "Click action uri not found, will not create PendingIntent.");
            return null;
        }
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Creating pending intent for click action %s.",
                uri);
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Calculates a new left, center, and right index given the current center index, total number
     * of images, and the intent action.
     *
     * @param centerIndex {@code int} containing the current center image index
     * @param listSize {@code int} containing the total number of images
     * @param action {@code String} containing the action found in the broadcast {@link Intent}
     * @return {@link List<Integer>} containing the new calculated indices
     */
    static List<Integer> calculateNewIndices(
            final int centerIndex, final int listSize, final String action) {
        final List<Integer> newIndices = new ArrayList<>();
        int newCenterIndex = 0;
        int newLeftIndex = 0;
        int newRightIndex = 0;
        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Current center index is %d and list size is %d.",
                centerIndex,
                listSize);
        if (action.equals(CampaignPushConstants.IntentActions.FILMSTRIP_LEFT_CLICKED)) {
            newCenterIndex = centerIndex - 1;
            newLeftIndex = newCenterIndex - 1 < 0 ? listSize - 1 : newCenterIndex - 1;
            newRightIndex = centerIndex;

            if (newCenterIndex < 0) {
                newCenterIndex = listSize - 1;
                newLeftIndex = newCenterIndex - 1;
                newRightIndex = 0;
            }
        } else if (action.equals(CampaignPushConstants.IntentActions.FILMSTRIP_RIGHT_CLICKED)) {
            newCenterIndex = centerIndex + 1;
            newLeftIndex = centerIndex;
            newRightIndex = newCenterIndex + 1 == listSize ? 0 : newCenterIndex + 1;

            if (newCenterIndex == listSize) {
                newCenterIndex = 0;
                newLeftIndex = listSize - 1;
                newRightIndex = 1;
            }
        }

        newIndices.add(newLeftIndex);
        newIndices.add(newCenterIndex);
        newIndices.add(newRightIndex);

        Log.trace(
                CampaignPushConstants.LOG_TAG,
                SELF_TAG,
                "Calculated new indices. New center index is %d, new left index is %d, and new"
                        + " right index is %d.",
                newCenterIndex,
                newLeftIndex,
                newRightIndex);

        return newIndices;
    }
}
