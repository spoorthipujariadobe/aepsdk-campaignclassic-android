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

package com.adobe.marketing.mobile;

/** Exception indicating that construction of a push notification failed. */
final class NotificationConstructionFailedException extends Exception {

    /**
     * Constructor.
     *
     * @param message {@link String} containing the message for the new exception
     */
    public NotificationConstructionFailedException(final String message) {
        super(message);
    }
}
