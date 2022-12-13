package com.adobe.campaignclassictestapp;

import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.CampaignClassic;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobilePrivacyStatus;

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
					MobileCore.configureWithAppID("");
				}
			});
			Thread.sleep(1000);
		} catch (Exception e) {
			Log.e("CampaignClassicTestApp", e.getMessage());
		}

	}
}
