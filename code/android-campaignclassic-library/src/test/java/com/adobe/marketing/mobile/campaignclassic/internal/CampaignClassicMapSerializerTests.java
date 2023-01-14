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

package com.adobe.marketing.mobile.campaignclassic.internal;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class CampaignClassicMapSerializerTests {
	@Test
	public void serializeMap_when_nullMap() {
		// test
		final String result = CampaignClassicMapSerializer.serializeMap(null);

		// verify
		Assert.assertEquals("<additionalParameters></additionalParameters>", result);
	}

	@Test
	public void serializeMap_when_emptyMap() {
		// setup
		final Map<String, Object> additionalParams = new HashMap<>();

		// test
		final String result = CampaignClassicMapSerializer.serializeMap(additionalParams);

		// verify
		Assert.assertEquals("<additionalParameters></additionalParameters>", result);
	}

	@Test
	public void serializeMap_happy() {
		// setup
		final Map<String, Object> additionalParams = new HashMap<String, Object>() {
			{
				put("company", "xxx.corp");
				put("isRegistered", true);
				put("serial", 12345);
				put("float", 3.14F);
				put("double", 6.75D);
				put("long", 500L);
				put("byte", (byte)100);
				put("char", 'a');
				put("short", (short)200);
			}
		};

		// test
		final String result = CampaignClassicMapSerializer.serializeMap(additionalParams);

		// verify
		Assert.assertEquals("<additionalParameters><param name=\"serial\" value=\"12345\"/><param name=\"double\" value=\"6.75\"/><param name=\"byte\" value=\"100\"/><param name=\"char\" value=\"97\"/><param name=\"short\" value=\"200\"/><param name=\"company\" value=\"xxx.corp\"/><param name=\"isRegistered\" value=\"true\"/><param name=\"float\" value=\"3.14\"/><param name=\"long\" value=\"500\"/></additionalParameters>",
					 result);
	}

	@Test
	public void serializeMap_Object() {
		// setup
		final Map<String, Object> additionalParams = new HashMap<String, Object>() {
			{
				put("object", new Object());
			}
		};

		// test
		final String result = CampaignClassicMapSerializer.serializeMap(additionalParams);

		// verify
		Assert.assertTrue(result.matches("<additionalParameters><param name=\"object\" value=\"java.lang.Object@(.*)\"/></additionalParameters>"));
	}

	@Test
	public void serializeMap_when_SymbolsToEscapeInMap() {
		// setup
		final Map<String, Object> additionalParams = new HashMap<String, Object>() {
			{
				put("greeting", "'h>e&l\"l<o'\r\n");
			}
		};

		// test
		final String result = CampaignClassicMapSerializer.serializeMap(additionalParams);

		// verify
		Assert.assertEquals("<additionalParameters><param name=\"greeting\" value=\"&#39;h&gt;e&amp;l&quot;l&lt;o&#39;\"/></additionalParameters>",
					 result);
	}

	@Test
	public void serializeMap_when_nullValueInMap() {
		// setup
		final Map<String, Object> additionalParams = new HashMap<String, Object>() {
			{
				put("key", null);
			}
		};

		// test
		final String result = CampaignClassicMapSerializer.serializeMap(additionalParams);

		// verify
		Assert.assertEquals("<additionalParameters></additionalParameters>", result);
	}

	@Test
	public void serializeMap_when_EmptyKeyValueInMap() {
		// setup
		final Map<String, Object> additionalParams = new HashMap<String, Object>() {
			{
				put("", "");
			}
		};

		// test
		final String result = CampaignClassicMapSerializer.serializeMap(additionalParams);

		// verify
		Assert.assertEquals("<additionalParameters><param name=\"\" value=\"\"/></additionalParameters>", result);
	}
}
