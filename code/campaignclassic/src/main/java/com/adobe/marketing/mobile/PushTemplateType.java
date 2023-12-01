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

enum PushTemplateType {
    LEGACY("-1"),
    BASIC("0"),
    AUTO_CAROUSEL("1"),
    MANUAL_CAROUSEL("2"),
    INPUT_BOX("3"),
    UNKNOWN("4");

    final String value;

    PushTemplateType(final String value) {
        this.value = value;
    }

    String getValue() {
        return value;
    }

    static PushTemplateType fromString(final String value) {
        switch (value) {
            case "-1":
                return PushTemplateType.LEGACY;
            case "0":
                return PushTemplateType.BASIC;
            case "1":
                return PushTemplateType.AUTO_CAROUSEL;
            case "2":
                return PushTemplateType.MANUAL_CAROUSEL;
            case "3":
                return PushTemplateType.INPUT_BOX;
            default:
                return PushTemplateType.UNKNOWN;
        }
    }
}
