# Campaign Classic API reference

## Prerequisites

Refer to the [Getting Started Guide](getting-started.md).

## API reference

- [extensionVersion](#extensionversion)
- [registerDevice](#registerDevice)
- [trackNotificationReceive](#trackNotificationReceive)
- [trackNotificationClick](#trackNotificationClick)

## extensionVersion

The `extensionVersion` API returns the version of the Campaign Classic extension that is registered with the Mobile Core extension.

### Java

**Syntax**

```java
@NonNull public static String extensionVersion();
```

**Example**

```java
String campaignClassicExtensionVersion = CampaignClassic.extensionVersion();
```

## registerDevice

The `registerDevice` API lets you register a user device with Campaign Classic.

> **Note**
> To prepare your app to handle push notifications, see the tutorial on [setting up a Firebase Cloud Messaging client app on Android](https://firebase.google.com/docs/cloud-messaging/android/client). After you receive the Firebase Cloud Messaging (FCM) SDK registration token, send this token and the device information to Campaign Classic by using the `registerDevice` API.

The `registerDevice` API registers a device with your Campaign Classic registration server. It takes the FCM registration token as a parameter with a user key that identifies a user, such as an email address or a login name. You can also provide a map of the custom key-value pairs that you want to associate with the registration.

### Java

**Syntax**

```java
public static void registerDevice(@NonNull final String token, final String userKey, final Map<String, Object> additionalParams)
```

**Example**

```java
@Override
public void onNewToken(String token) {
    Log.d("TestApp", "Refreshed token: " + token);

  // If you want to send messages to this application instance or
  // manage this app's subscriptions on the server side, send the
  // Instance ID token to your app server.
  if (token != null) {
    Log.d("TestApp", "FCM SDK registration token received : " + token);
    // Create a map of additional parameters
    Map<String, Object> additionalParams = new HashMap<String, Object>();
    additionalParams.put("name", "John");
    additionalParams.put("serial", 12345);
    additionalParams.put("premium", true);
    // Send the registration info
    CampaignClassic.registerDevice(token, "john@example.com", additionalParams);
  }
}
```

## trackNotificationReceive

The `trackNotificationReceive` API sends the received push notification's tracking information to the configured Adobe Campaign Classic server.

### Java

> **Note**
> If `trackInfo` is null or does not contain the necessary tracking identifiers, `messageId` (`_mId`) and `deliveryId` (`_dId`), a track request is **not** sent.

**Syntax**

```java
public static void trackNotificationReceive(@NonNull final Map<String, String> trackInfo)
```

**Example**

```java
public class MyFirebaseMessagingService extends FirebaseMessagingService {
  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    Log.d("TestApp", "Receive message from: " + remoteMessage.getFrom());
    Map<String,String> payloadData = message.getData();

    // Check if message contains data payload.
    if (payloadData.size() > 0) {
      Map<String,String> trackInfo = new HashMap<>();
      trackInfo.put("_mId", payloadData.get("_mId"));
      trackInfo.put("_dId", payloadData.get("_dId"));

      // Send the tracking information for message received
      CampaignClassic.trackNotificationReceive(trackInfo);
    }
  }
}
```

## trackNotificationClick

The `trackNotificationClick` API sends the clicked push notification's tracking information to the configured Adobe Campaign Classic server. This API can be used to send tracking information when the notification is clicked, which may result in the application being opened.

### Java

> **Note**
> If `trackInfo` is null, or does not contain the necessary tracking identifiers, `messageId` (`_mId`) and `deliveryId` (`_dId`), a track request is **not** sent.
 
**Syntax**

```java
public static void trackNotificationClick(@NonNull final Map<String, String> trackInfo)
```

**Example**

```java
@Override
public void onResume() {
  super.onResume();
  // Perform any other app related tasks
  // The messageId (_mId) and deliveryId (_dId) can be passed in the intent extras.
  // This is assuming you extract the messageId and deliveryId from the
  // received push message and are including it in the intent (intent.putExtra())
  // of the displayed notification.

  Bundle extras = getIntent().getExtras();
  if (extras != null) {
    String deliveryId = extras.getString("_dId");
    String messageId = extras.getString("_mId");
    if (deliveryId != null && messageId != null) {
      Map<String,String> trackInfo = new HashMap<>();
      trackInfo.put("_mId", messageId);
      trackInfo.put("_dId", deliveryId);

      // Send the tracking information for message opening
      CampaignClassic.trackNotificationClick(trackInfo);
    }
  }
}
```


