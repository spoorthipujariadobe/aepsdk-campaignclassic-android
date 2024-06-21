package com.adobe.campaignclassictestapp;

import android.util.Log;

import androidx.annotation.NonNull;

import com.adobe.marketing.mobile.AEPMessagingService;
import com.adobe.marketing.mobile.CampaignClassic;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class NotificationService extends FirebaseMessagingService {

	private static String LOG_TAG = "NotificationService";

	@Override
	public void onNewToken(@NonNull String token) {
		// Get updated InstanceID token.
		super.onNewToken(token);
		Log.d(LOG_TAG, "Refreshed firebase token: " + token);

		CampaignClassic.registerDevice(token, "ACC-Extension-Test-AndroidUser", null);
	}

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		super.onMessageReceived(remoteMessage);
		Log.d(LOG_TAG, "From: " + remoteMessage.getFrom());
		// Handle notification from ACC
		AEPMessagingService.handleRemoteMessage(this, remoteMessage);
	}
}