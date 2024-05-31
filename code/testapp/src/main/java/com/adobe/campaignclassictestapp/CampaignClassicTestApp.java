package com.adobe.campaignclassictestapp;

import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.CampaignClassic;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.LoggingMode;

import android.app.Application;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class CampaignClassicTestApp extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		MobileCore.setApplication(this);
		MobileCore.setLogLevel(LoggingMode.VERBOSE);

		try {
			List<Class<? extends Extension>> extensions = Arrays.asList(
					CampaignClassic.EXTENSION
			);
			MobileCore.registerExtensions(extensions, new AdobeCallback() {
				@Override
				public void call(Object o) {
					MobileCore.configureWithAppID("94f571f308d5/fa7306843722/launch-11179776ef9e-development");
					// listen for campaign response event
					MobileCore.registerEventListener(EventType.CAMPAIGN, EventSource.RESPONSE_CONTENT, new AdobeCallback<Event>() {
						@Override
						public void call(Event event) {
							Log.d("ACCRegistrationStatus", event.getEventData().get("registrationstatus").toString());
						}
					});
				}
			});
			Thread.sleep(1000);
		} catch (Exception e) {
			Log.e("CampaignClassicTestApp", e.getMessage());
		}

	}
}
