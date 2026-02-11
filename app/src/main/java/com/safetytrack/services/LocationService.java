//services/LocationService.java
package com.safetytrack.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.safetytrack.R;
import com.safetytrack.receivers.SmsBroadcastReceiver;
import com.safetytrack.utils.FirebaseHelper;
import com.safetytrack.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LocationService extends Service {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "SafetyTrackJourneyChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long LOCATION_UPDATE_INTERVAL = 120000; // 2 minutes (120,000 ms)
    private static final String SMS_DELIVERED_ACTION = "com.safetytrack.SMS_DELIVERED";
    private static final String SMS_SENT_ACTION = "com.safetytrack.SMS_SENT";

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseHelper firebaseHelper;
    private SessionManager sessionManager;

    private Handler handler;
    private Runnable locationRunnable;

    private String currentTripId;
    private List<String> emergencyPhoneNumbers = new ArrayList<>();
    private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault());

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        firebaseHelper = new FirebaseHelper(this);
        sessionManager = SessionManager.getInstance(this);
        handler = new Handler(Looper.getMainLooper());

        // Load emergency contacts
        loadEmergencyContacts();

        createNotificationChannel();
        Log.d(TAG, "LocationService created");
    }

    private void loadEmergencyContacts() {
        emergencyPhoneNumbers.clear();
        firebaseHelper.loadContactsFromFirestore(new FirebaseHelper.FirebaseContactsListener() {
            @Override
            public void onSuccess(List<com.safetytrack.models.Contact> contacts) {
                for (com.safetytrack.models.Contact contact : contacts) {
                    if (contact.isSelected()) {
                        String phone = contact.getPhoneNumber();
                        if (phone != null && !phone.isEmpty()) {
                            emergencyPhoneNumbers.add(phone);
                        }
                    }
                }
                Log.d(TAG, "✅ Loaded " + emergencyPhoneNumbers.size() + " emergency contacts for service");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to load contacts: " + error);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "LocationService onStartCommand");

        if (intent != null) {
            currentTripId = intent.getStringExtra("tripId");
            Log.d(TAG, "Received tripId: " + currentTripId);

            // Load fresh contacts when service starts
            loadEmergencyContacts();
        }

        // Start foreground service with persistent notification
        startForeground(NOTIFICATION_ID, createNotification(
                "Journey Active",
                "📍 Sending SMS location every 2 minutes"
        ));

        // Start the 2-minute repeating location task
        startRepeatingLocationTask();

        return START_STICKY;
    }

    /**
     * ✅ 2-MINUTE AUTOMATIC REPEATING LOCATION TASK
     * Runs every 2 minutes to fetch and send SMS location
     */
    private void startRepeatingLocationTask() {
        locationRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "⏰ 2-minute timer triggered - fetching location");

                // Update notification with timestamp
                String currentTime = timeFormat.format(new Date());
                updateNotification("Journey Active", "📍 Last SMS sent: " + currentTime);

                // Fetch current location and send SMS
                fetchAndSendLocation();

                // Schedule next run in exactly 2 minutes
                handler.postDelayed(this, LOCATION_UPDATE_INTERVAL);
                Log.d(TAG, "⏳ Next SMS scheduled in 2 minutes");
            }
        };

        // Execute immediately, then every 2 minutes
        handler.post(locationRunnable);
        Log.d(TAG, "✅ 2-minute repeating SMS task started");
    }

    /**
     * Fetch current location and send SMS to all emergency contacts
     */
    private void fetchAndSendLocation() {
        if (!checkLocationPermission()) {
            Log.e(TAG, "❌ Location permission not granted");
            return;
        }

        if (emergencyPhoneNumbers.isEmpty()) {
            Log.e(TAG, "❌ No emergency contacts available");
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Log.d(TAG, "📍 Location fetched: " + location.getLatitude() + ", " + location.getLongitude());
                            sendLocationSms(location);
                        } else {
                            Log.w(TAG, "⚠️ Location is null, requesting new location");
                            requestNewLocation();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to get location: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "❌ Location permission error: " + e.getMessage());
        }
    }

    /**
     * Request a fresh location when last location is null
     */
    private void requestNewLocation() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setNumUpdates(1);

        if (checkLocationPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest,
                        new LocationCallback() {
                            @Override
                            public void onLocationResult(LocationResult locationResult) {
                                if (locationResult != null && locationResult.getLastLocation() != null) {
                                    Location location = locationResult.getLastLocation();
                                    Log.d(TAG, "📍 New location fetched: " + location.getLatitude() + ", " + location.getLongitude());
                                    sendLocationSms(location);
                                }
                                fusedLocationClient.removeLocationUpdates(this);
                            }
                        }, Looper.getMainLooper());
            } catch (SecurityException e) {
                Log.e(TAG, "❌ Location permission error: " + e.getMessage());
            }
        }
    }

    /**
     * ✅ AUTOMATIC SMS SENDING - COMPLETELY AUTOMATED
     * No app opens, no user interaction required
     */
    private void sendLocationSms(Location location) {
        if (emergencyPhoneNumbers.isEmpty()) {
            Log.e(TAG, "❌ No emergency contacts available");
            return;
        }

        if (!checkSmsPermission()) {
            Log.e(TAG, "❌ SMS permission not granted");
            return;
        }

        String message = generateSmsMessage(location);
        Log.d(TAG, "📱 SMS Message: " + message);

        try {
            SmsManager smsManager = SmsManager.getDefault();
            int successCount = 0;

            for (String phone : emergencyPhoneNumbers) {
                try {
                    // Create pending intents for sent and delivery confirmation
                    Intent sentIntent = new Intent(SMS_SENT_ACTION);
                    android.app.PendingIntent sentPI = android.app.PendingIntent.getBroadcast(
                            this, 0, sentIntent, android.app.PendingIntent.FLAG_IMMUTABLE);

                    Intent deliveredIntent = new Intent(SMS_DELIVERED_ACTION);
                    android.app.PendingIntent deliveredPI = android.app.PendingIntent.getBroadcast(
                            this, 0, deliveredIntent, android.app.PendingIntent.FLAG_IMMUTABLE);

                    // Send SMS automatically in background
                    smsManager.sendTextMessage(phone, null, message, sentPI, deliveredPI);
                    Log.d(TAG, "✅ SMS automatically sent to: " + phone);
                    successCount++;

                    // Save to Firestore
                    saveLocationUpdate(location, phone);

                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to send SMS to " + phone + ": " + e.getMessage());
                }
            }

            if (successCount > 0) {
                Log.d(TAG, "✅ Successfully sent " + successCount + " SMS messages automatically");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ SMS Manager error: " + e.getMessage());
        }
    }

    /**
     * Generate clean SMS message with Google Maps link
     */
    private String generateSmsMessage(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String time = timeFormat.format(new Date());
        String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;

        return "🚗 Journey Update:\n" +
                "My current location: " + mapsLink + "\n" +
                "🕒 Time: " + time + "\n" +
                "🔋 Battery: " + getBatteryLevel() + "%\n" +
                "Sent via SafetyTrack";
    }

    private void saveLocationUpdate(Location location, String phoneNumber) {
        Map<String, Object> update = new HashMap<>();
        update.put("timestamp", System.currentTimeMillis());
        update.put("latitude", location.getLatitude());
        update.put("longitude", location.getLongitude());
        update.put("message", generateSmsMessage(location));
        update.put("type", "journey");
        update.put("phoneNumber", phoneNumber);
        update.put("userId", sessionManager.getUserId());

        firebaseHelper.saveLocationUpdate(update, new FirebaseHelper.FirebaseCompleteListener() {
            @Override
            public void onSuccess(String message) {
                Log.d(TAG, "📍 Location saved to Firestore");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to save location: " + error);
            }
        });
    }

    /**
     * Stop the repeating location task
     */
    public void stopRepeatingTask() {
        if (handler != null && locationRunnable != null) {
            handler.removeCallbacks(locationRunnable);
            Log.d(TAG, "🛑 2-minute repeating SMS task stopped");
        }
    }

    private boolean checkLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean checkSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private float getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryIntent = registerReceiver(null, ifilter);
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (scale > 0) {
                return level * 100 / (float) scale;
            }
        }
        return -1;
    }

    private Notification createNotification(String title, String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return builder.build();
    }

    private void updateNotification(String title, String content) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SafetyTrack Journey Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background SMS location tracking for active journeys");
            channel.setSound(null, null);
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "LocationService onDestroy - stopping service");

        // Stop the repeating task
        stopRepeatingTask();

        // Show journey ended notification
        showJourneyEndedNotification();
    }

    private void showJourneyEndedNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Journey Ended")
                .setContentText("Your SMS location tracking has been stopped")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(2, builder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}