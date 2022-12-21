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


package com.adobe.marketing.mobile.campaignclassic;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.HashMap;
import java.util.Map;

/**
 * CampaignClassicMapSerializer class
 */
// Reference impl: Refer https://git.corp.adobe.com/Campaign/mobile-sdk/blob/master/android/app/src/main/java/com/neolane/android/v1/MapSerializer.java
class CampaignClassicMapSerializer {

	private CampaignClassicMapSerializer() {}

	/**
	 * Serialize provided input map to XML string.
	 * <p>
	 * The input {@code Map<String, Object>} with key-value pairs is serialized to a {@code String} in the format
	 * {@literal <additionalParameters><param name=\"key1\" value=\"value1\"/><param name=\"key2\" value=\"value2\"/>...</additionalParameters>}
	 * <p>
	 * Null keys and values are ignored when serializing the given {@code Map<String, Object>}.
	 *
	 * @param input {@code Map<String, Object>} containing key-value pairs
	 * @return escaped {@link String} in Campaign Classic XML format
	 */
	public static String serializeMap(final Map<String, Object> input) {
		final Map<String, Object> validInput = (input == null) ? new HashMap<String, Object>() : input;
		StringBuilder sb = new StringBuilder();
		sb.append("<additionalParameters>");

		for (Map.Entry<String, Object> entry : validInput.entrySet()) {
			final String key = entry.getKey();
			final Object value = entry.getValue();

			if (key != null && value != null) {
				sb.append(serializeObject(value, key));
			}
		}

		sb.append("</additionalParameters>");
		return sb.toString();
	}

	/**
	 * Serialize provided {@code Object} to {@code String}.
	 * <p>
	 * The {@code Object} key-value pairs are serialized to a {@code String} in the format
	 * {@literal <param name=\"key\" value=\"value\"/>}
	 * <p>
	 * This method returns the {@code String} representation of the provided {@code Object}. If the {@code Object}
	 * contains a {@code Boolean}, {@code String} "true" or "false" is returned.
	 *
	 * @param o {@link Object} containing value corresponding to the given {@code key}
	 * @param key {@link String} containing the key value
	 * @return escaped {@code String} in Campaign Classic XML format containing given key-value.
	 */
	private static String serializeObject(final Object o, final String key) {
		String value;

		if (o instanceof String) {
			value = (String) o;
		} else if (o instanceof Integer) {
			value = String.valueOf((Integer) o);
		} else if (o instanceof Float) {
			value = String.valueOf((Float) o);
		} else if (o instanceof Double) {
			value = String.valueOf((Double) o);
		} else if (o instanceof Long) {
			value = String.valueOf((Long) o);
		} else if (o instanceof Byte) {
			value = String.valueOf(Integer.valueOf((Byte) o));
		} else if (o instanceof Character) {
			value = String.valueOf(Integer.valueOf((Character) o));
		} else if (o instanceof Short) {
			value = String.valueOf((Short) o);
		} else if (o instanceof Boolean) {
			Boolean b = (Boolean) o;
			value = b ? "true" : "false";
		} else {
			//unknown type
			value = o.toString();
		}

		return "<param name=\"" +  escapeXMLAttrString(key) + "\" value=\"" + escapeXMLAttrString(value) + "\"/>";
	}

	/**
	 * Escape provided XML {@code String} attribute.
	 *
	 * @param attributeString the provided {@link String} to escape
	 * @return the escaped {@code String}
	 */
	private static String escapeXMLAttrString(final String attributeString) {
		StringBuilder result = new StringBuilder();
		StringCharacterIterator iter = new StringCharacterIterator(attributeString);
		char c =  iter.current();

		while (c != CharacterIterator.DONE) {
			result.append(escapeXMLAttrChar(c));
			c = iter.next();
		}

		return result.toString();
	}

	/**
	 * Escape provided XML {@code char} attribute.
	 *
	 * @param attributeChar {@code char} XML attribute char to escape
	 * @return escaped {@link String} value of the XML attribute char
	 */
	private static String escapeXMLAttrChar(final char attributeChar) {
		switch (attributeChar) {
			case '\r':
				return "";

			case '\n':
				return "";

			case '<':
				return "&lt;";

			case '>':
				return "&gt;";

			case '"':
				return "&quot;";

			case '\'':
				return "&#39;";

			case '&':
				return "&amp;";

			default:
				return String.valueOf(attributeChar);
		}
	}
}
