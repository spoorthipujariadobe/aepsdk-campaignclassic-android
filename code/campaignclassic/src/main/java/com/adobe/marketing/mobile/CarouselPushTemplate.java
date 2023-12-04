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
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DataReaderException;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class CarouselPushTemplate extends AEPPushTemplate {
    // Optional, Determines how the carousel will be operated. Valid values are "auto" or "manual".
    // Default is "auto".
    private final String carouselOperationMode; // auto or manual
    // Required, One or more Items in the carousel defined by the CarouselItem class
    private final ArrayList<CarouselItem> carouselItems = new ArrayList<>();
    // Required, "default" or "filmstrip"
    private String carouselLayoutType;

    class CarouselItem {
        // Required, URI to an image to be shown for the carousel item
        private final String imageUri;
        // Optional, caption to show when the carousel item is visible
        private final String captionText;
        // Optional, URI to handle when the item is touched by the user. If no uri is provided for
        // the item, adb_uri will be handled instead.
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

    @NonNull String getCarouselOperationMode() {
        return carouselOperationMode;
    }

    @NonNull String getCarouselLayoutType() {
        return carouselLayoutType;
    }

    @NonNull ArrayList<CarouselPushTemplate.CarouselItem> getCarouselItems() {
        return carouselItems;
    }

    CarouselPushTemplate(@NonNull final Map<String, String> messageData)
            throws IllegalArgumentException {
        super(messageData);

        try {
            this.carouselLayoutType =
                    DataReader.getString(
                            messageData, CampaignPushConstants.PushPayloadKeys.CAROUSEL_LAYOUT);
        } catch (final DataReaderException dataReaderException) {
            throw new IllegalArgumentException("Required field \"adb_car_layout\" not found.");
        }

        List<Map<String, String>> carouselItemMaps;
        try {
            carouselItemMaps =
                    DataReader.getTypedListOfMap(
                            String.class,
                            messageData,
                            CampaignPushConstants.PushPayloadKeys.CAROUSEL_ITEMS);
        } catch (final DataReaderException dataReaderException) {
            throw new IllegalArgumentException("Required field \"adb_items\" not found.");
        }

        this.carouselOperationMode =
                DataReader.optString(
                        messageData,
                        CampaignPushConstants.PushPayloadKeys.CAROUSEL_OPERATION_MODE,
                        CampaignPushConstants.DefaultValues.AUTO_CAROUSEL_MODE);
        for (final Map<String, String> carouselItemMap : carouselItemMaps) {
            // the image uri is required, do not create a CarouselItem if it is missing
            final String carouselImage =
                    carouselItemMap.get(CampaignPushConstants.PushPayloadKeys.CAROUSEL_ITEM_IMAGE);
            if (StringUtils.isNullOrEmpty(carouselImage)) {
                break;
            }
            final String text =
                    carouselItemMap.get(CampaignPushConstants.PushPayloadKeys.CAROUSEL_ITEM_TEXT);
            final String uri =
                    carouselItemMap.get(CampaignPushConstants.PushPayloadKeys.CAROUSEL_ITEM_URI);
            final CarouselItem carouselItem = new CarouselItem(carouselImage, text, uri);
            carouselItems.add(carouselItem);
        }
    }
}
