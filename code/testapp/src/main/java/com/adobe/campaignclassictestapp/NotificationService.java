package com.adobe.campaignclassictestapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.adobe.marketing.mobile.AEPMessagingService;
import com.adobe.marketing.mobile.CampaignClassic;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class NotificationService extends FirebaseMessagingService {

	private static String LOG_TAG = "NotificationService";
	private static String DELIVERYID = "_dId";
	private static String MESSAGEID = "_mId";
	private static String NOTIFICATION_TEXT = "notificationText";
	private static String NOTIFICATION_URL = "NotificationUrl";

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
		if (AEPMessagingService.handleRemoteMessage(this, remoteMessage)) {
			// Campaign extension has handled the notification
		} else {
			// Handle notification from other sources
		}
	}
}