package com.safetytrack.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
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
import com.safetytrack.utils.FirebaseHelper;
import com.safetytrack.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LocationService extends Service {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "SafetyTrackJourneyChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long LOCATION_UPDATE_INTERVAL = 120000; // 2 minutes
    private static final String SMS_SENT_ACTION = "com.safetytrack.SMS_SENT";
    private static final String SMS_DELIVERED_ACTION = "com.safetytrack.SMS_DELIVERED";

    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseHelper firebaseHelper;
    private SessionManager sessionManager;

    private Handler handler;
    private Runnable locationRunnable;

    private String currentTripId;
    private List<String> emergencyPhoneNumbers = new ArrayList<>();
    private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault());
    private boolean isSmsPermissionGranted = false;
    private boolean contactsLoaded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        firebaseHelper = new FirebaseHelper(this);
        sessionManager = SessionManager.getInstance(this);
        handler = new Handler(Looper.getMainLooper());

        checkSmsPermission();
        createNotificationChannel();

        Log.d(TAG, "LocationService created");
    }

    private void checkSmsPermission() {
        isSmsPermissionGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "SMS Permission granted: " + isSmsPermissionGranted);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "LocationService onStartCommand");

        if (intent != null) {
            currentTripId = intent.getStringExtra("tripId");
            Log.d(TAG, "Received tripId: " + currentTripId);
        }

        // Check SMS permission again
        checkSmsPermission();

        startForeground(NOTIFICATION_ID, createNotification(
                "Journey Active",
                "📍 Starting service..."
        ));

        // ✅ CRITICAL FIX: Load contacts BEFORE starting timer
        loadEmergencyContactsAndStart();

        return START_STICKY;
    }

    /**
     * ✅ Load contacts first, then start the repeating task
     */
    private void loadEmergencyContactsAndStart() {
        emergencyPhoneNumbers.clear();
        contactsLoaded = false;

        Log.d(TAG, "Loading emergency contacts...");

        firebaseHelper.loadContactsFromFirestore(new FirebaseHelper.FirebaseContactsListener() {
            @Override
            public void onSuccess(List<com.safetytrack.models.Contact> contacts) {
                emergencyPhoneNumbers.clear();

                for (com.safetytrack.models.Contact contact : contacts) {
                    if (contact.isSelected()) {
                        String phone = contact.getPhoneNumber();
                        if (phone != null && !phone.isEmpty()) {
                            // Clean phone number
                            String cleanPhone = phone.replaceAll("[^0-9+]", "");
                            if (!cleanPhone.isEmpty()) {
                                // Ensure country code
                                if (!cleanPhone.startsWith("+")) {
                                    cleanPhone = "+91" + cleanPhone; // India default
                                }
                                emergencyPhoneNumbers.add(cleanPhone);
                                Log.d(TAG, "Added contact: " + cleanPhone);
                            }
                        }
                    }
                }

                contactsLoaded = true;
                Log.d(TAG, "✅ Loaded " + emergencyPhoneNumbers.size() + " emergency contacts");

                // ✅ NOW start the repeating task
                startRepeatingLocationTask();

                // Update notification
                updateNotification("Journey Active",
                        "📍 Tracking " + emergencyPhoneNumbers.size() + " contacts");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Failed to load contacts: " + error);
                contactsLoaded = true; // Still mark as loaded to prevent hanging

                // Try to load from preferences as fallback
                loadContactsFromPreferences();
            }
        });
    }

    private void loadContactsFromPreferences() {
        android.content.SharedPreferences prefs = getSharedPreferences("SafetyTrack", MODE_PRIVATE);
        String contactsJson = prefs.getString("emergencyContacts", "");

        if (!contactsJson.isEmpty()) {
            String[] contactsArray = contactsJson.split(",");
            for (String contact : contactsArray) {
                if (!contact.trim().isEmpty()) {
                    String[] parts = contact.split("\n");
                    if (parts.length >= 2) {
                        String phone = parts[1].replaceAll("[^0-9+]", "");
                        if (!phone.isEmpty()) {
                            if (!phone.startsWith("+")) {
                                phone = "+91" + phone;
                            }
                            emergencyPhoneNumbers.add(phone);
                            Log.d(TAG, "Added contact from prefs: " + phone);
                        }
                    }
                }
            }
            Log.d(TAG, "✅ Loaded " + emergencyPhoneNumbers.size() + " contacts from preferences");
        }

        startRepeatingLocationTask();
    }

    private void startRepeatingLocationTask() {
        if (locationRunnable != null) {
            handler.removeCallbacks(locationRunnable);
        }

        locationRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "⏰ 2-minute timer triggered");

                String currentTime = timeFormat.format(new Date());
                updateNotification("Journey Active",
                        "📍 Last SMS: " + currentTime + " - " + emergencyPhoneNumbers.size() + " contacts");

                if (emergencyPhoneNumbers.isEmpty()) {
                    Log.e(TAG, "❌ No emergency contacts available - reloading");
                    loadEmergencyContactsAndStart();
                    return;
                }

                fetchAndSendLocation();

                handler.postDelayed(this, LOCATION_UPDATE_INTERVAL);
            }
        };

        // Execute immediately
        handler.post(locationRunnable);
        Log.d(TAG, "✅ 2-minute repeating SMS task started");
    }

    private void fetchAndSendLocation() {
        if (!checkLocationPermission()) {
            Log.e(TAG, "❌ Location permission not granted");
            return;
        }

        if (emergencyPhoneNumbers.isEmpty()) {
            Log.e(TAG, "❌ No emergency contacts available");
            return;
        }

        if (!isSmsPermissionGranted) {
            Log.e(TAG, "❌ SMS permission not granted");
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
                                    Log.d(TAG, "📍 New location fetched");
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
     * ✅ SMS SENDING - Now with proper contact loading
     */
    private void sendLocationSms(Location location) {
        if (emergencyPhoneNumbers.isEmpty()) {
            Log.e(TAG, "❌ No contacts to send SMS to");
            return;
        }

        String message = generateSmsMessage(location);
        Log.d(TAG, "📱 Sending SMS to " + emergencyPhoneNumbers.size() + " contacts");
        Log.d(TAG, "📱 Message: " + message);

        try {
            SmsManager smsManager = SmsManager.getDefault();
            int successCount = 0;

            for (String phone : emergencyPhoneNumbers) {
                try {
                    if (phone == null || phone.trim().isEmpty()) {
                        continue;
                    }

                    int requestCode = phone.hashCode();

                    Intent sentIntent = new Intent(SMS_SENT_ACTION);
                    sentIntent.putExtra("phone", phone);
                    sentIntent.putExtra("timestamp", System.currentTimeMillis());

                    Intent deliveredIntent = new Intent(SMS_DELIVERED_ACTION);
                    deliveredIntent.putExtra("phone", phone);
                    deliveredIntent.putExtra("timestamp", System.currentTimeMillis());

                    android.app.PendingIntent sentPI = android.app.PendingIntent.getBroadcast(
                            this,
                            requestCode,
                            sentIntent,
                            android.app.PendingIntent.FLAG_IMMUTABLE
                    );

                    android.app.PendingIntent deliveredPI = android.app.PendingIntent.getBroadcast(
                            this,
                            requestCode,
                            deliveredIntent,
                            android.app.PendingIntent.FLAG_IMMUTABLE
                    );

                    // Send SMS
                    smsManager.sendTextMessage(phone, null, message, sentPI, deliveredPI);
                    Log.d(TAG, "✅ SMS sent to: " + phone);
                    successCount++;

                } catch (SecurityException e) {
                    Log.e(TAG, "❌ SMS permission denied for " + phone);
                    isSmsPermissionGranted = false;
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to send SMS to " + phone + ": " + e.getMessage());
                }
            }

            if (successCount > 0) {
                Log.d(TAG, "✅ Successfully sent " + successCount + " SMS messages");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ SMS Manager error: " + e.getMessage());
        }
    }

    private String generateSmsMessage(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String time = timeFormat.format(new Date());
        String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;

        return "Journey Update: My current location " + mapsLink + " at " + time + ". Battery " + getBatteryLevel() + "%";
    }

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
            channel.setDescription("Background SMS location tracking");
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRepeatingTask();
        Log.d(TAG, "LocationService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}