package com.safetytrack;

import androidx.appcompat.widget.SwitchCompat;
import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.safetytrack.models.Contact;
import com.safetytrack.services.JourneyDetector;
import com.safetytrack.services.LocationService;
import com.safetytrack.utils.FirebaseHelper;
import com.safetytrack.utils.NotificationHelper;
import com.safetytrack.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final int SMS_PERMISSION_REQUEST = 1002;
    private static final int LOCATION_SETTINGS_REQUEST = 1003;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1004;
    private static final long LOCATION_UPDATE_INTERVAL = 120000;
    private static final float MIN_DISTANCE_CHANGE = 10;

    // UI Components
    private TextView tvStatus, tvLastUpdate, tvUserName, tvBatteryStatus, tvGpsStatus, tvInternetStatus;
    private TextView tvContactsCount, tvActiveTime, tvDistance, tvEmergencyMessage;
    private Button btnStartStop, btnSelectContacts, btnTripHistory, btnLogout;
    private Button btnSafeArrival, btnManualUpdate;
    private SwitchCompat switchAutoTrack;
    private MaterialCardView cardStatus, cardContacts, cardStats, cardEmergency;
    private LinearLayout layoutIndicators;
    private ImageView ivGps, ivInternet, ivBattery;
    private ProgressBar progressBar;
    private FloatingActionButton btnSOS;

    // Services
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseHelper firebaseHelper;
    private JourneyDetector journeyDetector;
    private LocationCallback locationCallback;
    private SessionManager sessionManager;

    // State variables
    private boolean isTracking = false;
    private boolean isJourneyActive = false;
    private long journeyStartTime = 0;
    private double totalDistance = 0;
    private Location lastLocation;
    private List<Contact> emergencyContacts = new ArrayList<>();
    private List<String> emergencyPhoneNumbers = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        initializeSession();
        initializeViews();
        initializeServices();
        setupListeners();
        loadUserInfo();
        loadEmergencyContactsFromFirestore();
        setupLocationUpdates();
        startStatusIndicators();
        checkPermissions();
    }

    private void initializeSession() {
        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void initializeViews() {
        try {
            // Status and Info
            tvUserName = findViewById(R.id.tvUserName);
            tvStatus = findViewById(R.id.tvStatus);
            tvLastUpdate = findViewById(R.id.tvLastUpdate);
            tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
            tvGpsStatus = findViewById(R.id.tvGpsStatus);
            tvInternetStatus = findViewById(R.id.tvInternetStatus);
            tvContactsCount = findViewById(R.id.tvContactsCount);
            tvActiveTime = findViewById(R.id.tvActiveTime);
            tvDistance = findViewById(R.id.tvDistance);
            tvEmergencyMessage = findViewById(R.id.tvEmergencyMessage);

            // Cards - MaterialCardView
            cardStatus = findViewById(R.id.cardStatus);
            cardContacts = findViewById(R.id.cardContacts);
            cardStats = findViewById(R.id.cardStats);
            cardEmergency = findViewById(R.id.cardEmergency);

            // Buttons
            btnStartStop = findViewById(R.id.btnStartStop);
            btnSOS = findViewById(R.id.btnSOS);
            btnSelectContacts = findViewById(R.id.btnSelectContacts);
            btnTripHistory = findViewById(R.id.btnTripHistory);
            btnLogout = findViewById(R.id.btnLogout);
            btnSafeArrival = findViewById(R.id.btnSafeArrival);
            btnManualUpdate = findViewById(R.id.btnManualUpdate);

            // Toggle and Indicators
            switchAutoTrack = findViewById(R.id.switchAutoTrack);
            layoutIndicators = findViewById(R.id.layoutIndicators);
            ivGps = findViewById(R.id.ivGps);
            ivInternet = findViewById(R.id.ivInternet);
            ivBattery = findViewById(R.id.ivBattery);
            progressBar = findViewById(R.id.progressBar);

            progressBar.setVisibility(View.GONE);
            Log.d(TAG, "Views initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
        }
    }

    private void initializeServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        firebaseHelper = new FirebaseHelper(this);
        journeyDetector = new JourneyDetector(this);
    }

    private void setupListeners() {
        btnStartStop.setOnClickListener(v -> toggleJourneyTracking());
        btnSOS.setOnClickListener(v -> triggerEmergency());

        btnSelectContacts.setOnClickListener(v -> {
            Intent intent = new Intent(this, ContactsActivity.class);
            startActivityForResult(intent, 100);
        });

        btnTripHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, TripHistoryActivity.class);
            startActivity(intent);
        });

        btnSafeArrival.setOnClickListener(v -> sendSafeArrivalAlert());
        btnManualUpdate.setOnClickListener(v -> sendManualLocationUpdate());
        btnLogout.setOnClickListener(v -> logoutUser());

        switchAutoTrack.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enableAutoTracking();
            } else {
                disableAutoTracking();
            }
        });

        cardContacts.setOnClickListener(v -> {
            Intent intent = new Intent(this, ContactsActivity.class);
            startActivityForResult(intent, 100);
        });
    }

    // ========== PERMISSION METHODS ==========

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST);
    }

    private boolean checkSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSmsPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.SEND_SMS},
                SMS_PERMISSION_REQUEST);
    }

    private boolean checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void checkPermissions() {
        if (!checkLocationPermission()) {
            requestLocationPermission();
        }
        if (!checkSmsPermission()) {
            // SMS permission will be requested when needed
        }
        if (!checkNotificationPermission()) {
            requestNotificationPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
                if (isTracking) {
                    startLocationUpdates();
                }
            } else {
                Toast.makeText(this, "Location permission is required for tracking", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == SMS_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "SMS permission is required for alerts", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted");
            }
        }
    }

    // ========== CONTACT METHODS ==========

    private void loadUserInfo() {
        String userName = sessionManager.getUserName();
        tvUserName.setText("Welcome, " + userName);
    }

    // ✅ FIXED: Properly loads contacts and selected state from Firestore
    private void loadEmergencyContactsFromFirestore() {
        progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "Loading contacts from Firestore...");

        firebaseHelper.loadContactsFromFirestore(new FirebaseHelper.FirebaseContactsListener() {
            @Override
            public void onSuccess(List<Contact> contacts) {
                progressBar.setVisibility(View.GONE);
                emergencyContacts.clear();
                emergencyPhoneNumbers.clear();

                // Store ALL contacts, not just selected ones
                emergencyContacts.addAll(contacts);
                Log.d(TAG, "Total contacts loaded from Firestore: " + contacts.size());

                // Build phone numbers list ONLY from selected contacts
                int selectedCount = 0;
                for (Contact contact : contacts) {
                    if (contact.isSelected()) {
                        selectedCount++;
                        String phone = contact.getPhoneNumber();
                        if (phone != null && !phone.isEmpty()) {
                            emergencyPhoneNumbers.add(phone);
                            Log.d(TAG, "Loaded selected contact: " + phone + " (" + contact.getName() + ")");
                        }
                    }
                }

                updateContactsUI();
                saveContactsToPreferences();

                Log.d(TAG, "Loaded " + emergencyPhoneNumbers.size() + " selected emergency contacts out of " + contacts.size() + " total");

                // Show contact count in UI
                Toast.makeText(DashboardActivity.this,
                        "Contacts: " + selectedCount + " selected", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Error loading contacts from Firestore: " + error);
                loadEmergencyContactsFromPreferences();

                Toast.makeText(DashboardActivity.this,
                        "Using local contacts: " + emergencyPhoneNumbers.size(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadEmergencyContactsFromPreferences() {
        SharedPreferences prefs = getSharedPreferences("SafetyTrack", MODE_PRIVATE);
        String contactsJson = prefs.getString("emergencyContacts", "");
        emergencyPhoneNumbers.clear();

        if (!contactsJson.isEmpty()) {
            String[] contactsArray = contactsJson.split(",");
            for (String contact : contactsArray) {
                if (!contact.trim().isEmpty()) {
                    String[] parts = contact.split("\n");
                    if (parts.length >= 2) {
                        String phone = parts[1].replaceAll("[^0-9+]", "");
                        if (!phone.isEmpty()) {
                            emergencyPhoneNumbers.add(phone);
                        }
                    }
                }
            }
        }
        updateContactsUI();
        Log.d(TAG, "Loaded " + emergencyPhoneNumbers.size() + " contacts from preferences");
    }

    private void saveContactsToPreferences() {
        SharedPreferences prefs = getSharedPreferences("SafetyTrack", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        StringBuilder contactsBuilder = new StringBuilder();
        for (Contact contact : emergencyContacts) {
            if (contact.isSelected()) {
                contactsBuilder.append(contact.toString()).append(",");
            }
        }
        editor.putString("emergencyContacts", contactsBuilder.toString());
        editor.apply();
        Log.d(TAG, "Saved " + emergencyPhoneNumbers.size() + " contacts to preferences");
    }

    private void updateContactsUI() {
        int selectedCount = emergencyPhoneNumbers.size();
        if (selectedCount == 0) {
            tvContactsCount.setText("0 contacts");
            tvContactsCount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            tvContactsCount.setText(selectedCount + " contact" + (selectedCount > 1 ? "s" : ""));
            tvContactsCount.setTextColor(ContextCompat.getColor(this, R.color.primary));
        }
        Log.d(TAG, "UI updated - Selected contacts: " + selectedCount);
    }

    // ========== LOCATION METHODS ==========

    private void setupLocationUpdates() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastLocation = location;
                    updateLocationUI(location);

                    if (isTracking && !emergencyPhoneNumbers.isEmpty()) {
                        sendLocationToContacts(location);
                    }

                    if (journeyStartTime > 0) {
                        updateStatsUI(location);
                    }
                    updateEmergencyMessagePreview();
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(LOCATION_UPDATE_INTERVAL)
                .setFastestInterval(60000)
                .setSmallestDisplacement(MIN_DISTANCE_CHANGE)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Location updates started");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission error: " + e.getMessage());
        }
    }

    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates stopped");
        }
    }

    private void getCurrentLocation(LocationCallback callback) {
        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onLocationResult(LocationResult.create(List.of(location)));
                        } else {
                            requestNewLocation(callback);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get location: " + e.getMessage());
                        Toast.makeText(this, "Unable to get location. Please check GPS.", Toast.LENGTH_SHORT).show();
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission error: " + e.getMessage());
        }
    }

    private void requestNewLocation(LocationCallback callback) {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setNumUpdates(1);

        if (checkLocationPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper());
            } catch (SecurityException e) {
                Log.e(TAG, "Location permission error: " + e.getMessage());
            }
        }
    }

    private String generateGoogleMapsLink(Location location) {
        return "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
    }

    // ========== JOURNEY TRACKING ==========

    private void toggleJourneyTracking() {
        if (!isLocationEnabled()) {
            showEnableGPSDialog();
            return;
        }

        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        if (!isTracking) {
            // ✅ FIXED: Check if contacts are selected
            if (emergencyPhoneNumbers.isEmpty()) {
                showNoContactsDialog();
                return;
            }
            startJourneyTracking();
        } else {
            stopJourneyTracking();
        }
    }

    private void startJourneyTracking() {
        isTracking = true;
        journeyStartTime = System.currentTimeMillis();

        btnStartStop.setText("STOP JOURNEY");
        btnStartStop.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.error_red));
        tvStatus.setText("Journey Active");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green));

        cardStatus.setStrokeColor(ContextCompat.getColorStateList(this, R.color.success_green));
        cardStatus.setStrokeWidth(2);

        getCurrentLocation(new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastLocation = location;
                    // ✅ Send location immediately when journey starts
                    if (!emergencyPhoneNumbers.isEmpty()) {
                        sendLocationToContacts(location);
                    }
                    updateEmergencyMessagePreview();
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        });

        startLocationUpdates();

        if (switchAutoTrack.isChecked()) {
            journeyDetector.startDetection();
        }

        startLocationService();
        Toast.makeText(this, "Journey started", Toast.LENGTH_SHORT).show();
    }

    private void stopJourneyTracking() {
        isTracking = false;
        isJourneyActive = false;

        btnStartStop.setText("START JOURNEY");
        btnStartStop.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
        tvStatus.setText("Ready");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

        cardStatus.setStrokeWidth(0);

        stopLocationUpdates();
        journeyDetector.stopDetection();
        stopLocationService();

        if (journeyStartTime > 0) {
            sendSafeArrivalAlert();
        }

        journeyStartTime = 0;
        totalDistance = 0;
        updateStatsUI(null);

        Toast.makeText(this, "Journey stopped", Toast.LENGTH_SHORT).show();
    }

    // ========== MESSAGE SENDING METHODS ==========

    private void sendLocationToContacts(Location location) {
        // ✅ FIXED: Check if contacts exist
        if (emergencyPhoneNumbers.isEmpty()) {
            Log.e(TAG, "Cannot send location: No emergency contacts selected");
            Toast.makeText(this, "No emergency contacts selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String userName = sessionManager.getUserName();
        String message = generateJourneyStartMessage(location, userName);

        Log.d(TAG, "Sending location to " + emergencyPhoneNumbers.size() + " contacts");
        Log.d(TAG, "Message: " + message);

        // Send SMS to all contacts
        if (checkSmsPermission()) {
            sendSmsToAllContacts(message);
        } else {
            Log.d(TAG, "SMS permission not granted, requesting...");
            requestSmsPermission();
            // Still try to send WhatsApp
        }

        // Send WhatsApp to all contacts
        sendWhatsAppToAllContacts(message);

        // Save to Firestore
        saveLocationUpdate(location, message);

        // Show notification
        try {
            NotificationHelper.showJourneyStartedNotification(this, String.valueOf(emergencyPhoneNumbers.size()));
        } catch (Exception e) {
            Log.e(TAG, "Notification error: " + e.getMessage());
        }

        tvLastUpdate.setText("Last: " + timeFormat.format(new Date()));
        Toast.makeText(this, "📍 Location shared with " + emergencyPhoneNumbers.size() + " contact(s)", Toast.LENGTH_SHORT).show();
    }

    private void sendSmsToAllContacts(String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            int successCount = 0;
            for (String phone : emergencyPhoneNumbers) {
                try {
                    smsManager.sendTextMessage(phone, null, message, null, null);
                    Log.d(TAG, "✅ SMS sent to: " + phone);
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to send SMS to " + phone + ": " + e.getMessage());
                }
            }
            if (successCount > 0) {
                Toast.makeText(this, "SMS sent to " + successCount + " contact(s)", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS Manager error: " + e.getMessage());
            Toast.makeText(this, "Failed to send SMS. Please check permission.", Toast.LENGTH_LONG).show();
        }
    }

    private void sendWhatsAppToAllContacts(String message) {
        int successCount = 0;
        for (String phone : emergencyPhoneNumbers) {
            if (sendViaWhatsApp(message, phone)) {
                successCount++;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (successCount > 0) {
            Toast.makeText(this, "WhatsApp opened for " + successCount + " contact(s)", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean sendViaWhatsApp(String message, String phoneNumber) {
        try {
            phoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
            if (phoneNumber.startsWith("+")) {
                phoneNumber = phoneNumber.substring(1);
            }

            String url = "https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=" + Uri.encode(message);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
            Log.d(TAG, "✅ WhatsApp opened for: " + phoneNumber);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ WhatsApp failed for " + phoneNumber + ": " + e.getMessage());
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.setPackage("com.whatsapp");
                shareIntent.putExtra(Intent.EXTRA_TEXT, message);
                startActivity(shareIntent);
                Log.d(TAG, "✅ WhatsApp fallback opened for: " + phoneNumber);
                return true;
            } catch (Exception ex) {
                Log.e(TAG, "❌ WhatsApp not installed");
                Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    // ========== MESSAGE GENERATION ==========

    private String generateJourneyStartMessage(Location location, String userName) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String time = new SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(new Date());
        String mapsLink = generateGoogleMapsLink(location);

        return "🚗 Journey Started\n" +
                "I have started my journey.\n\n" +
                "📍 Location: " + mapsLink + "\n" +
                "🕒 Time: " + time + "\n" +
                "🔋 Battery: " + getBatteryLevel() + "%\n\n" +
                "Sent via SafetyTrack";
    }

    private String generateEmergencyMessage(String userName) {
        if (lastLocation == null) {
            return "🚨 EMERGENCY! " + userName + " needs help!";
        }

        double lat = lastLocation.getLatitude();
        double lng = lastLocation.getLongitude();
        String time = new SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(new Date());
        String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;

        return "🚨 SOS EMERGENCY!\n\n" +
                userName + " needs immediate assistance!\n\n" +
                "📍 Location: " + mapsLink + "\n" +
                "🕒 Time: " + time + "\n" +
                "🔋 Battery: " + getBatteryLevel() + "%\n\n" +
                "Please call or check on them NOW!";
    }

    // ========== UI UPDATE METHODS ==========

    private void startStatusIndicators() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateStatusIndicators();
                handler.postDelayed(this, 5000);
            }
        }, 1000);
    }

    private void updateStatusIndicators() {
        boolean isGpsEnabled = isLocationEnabled();
        tvGpsStatus.setText(isGpsEnabled ? "GPS Active" : "GPS Off");
        int gpsColor = isGpsEnabled ? R.color.success_green : R.color.error_red;
        tvGpsStatus.setTextColor(ContextCompat.getColor(this, gpsColor));
        ivGps.setColorFilter(ContextCompat.getColor(this, gpsColor));

        boolean isInternetAvailable = isNetworkAvailable();
        tvInternetStatus.setText(isInternetAvailable ? "Online" : "Offline");
        int internetColor = isInternetAvailable ? R.color.success_green : R.color.error_red;
        tvInternetStatus.setTextColor(ContextCompat.getColor(this, internetColor));
        ivInternet.setColorFilter(ContextCompat.getColor(this, internetColor));

        int batteryLevel = getBatteryLevel();
        tvBatteryStatus.setText(batteryLevel + "%");

        int batteryColor;
        if (batteryLevel > 50) {
            batteryColor = R.color.success_green;
        } else if (batteryLevel > 20) {
            batteryColor = R.color.warning_orange;
        } else {
            batteryColor = R.color.error_red;
        }
        tvBatteryStatus.setTextColor(ContextCompat.getColor(this, batteryColor));
        ivBattery.setColorFilter(ContextCompat.getColor(this, batteryColor));

        if (batteryLevel < 20 && isTracking) {
            sendLowBatteryAlert();
        }
    }

    private void updateLocationUI(Location location) {
        tvLastUpdate.setText("Last: " + timeFormat.format(new Date()));
        updateEmergencyMessagePreview();
    }

    private void updateStatsUI(Location location) {
        if (journeyStartTime > 0) {
            long duration = System.currentTimeMillis() - journeyStartTime;
            String durationText = String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(duration),
                    TimeUnit.MILLISECONDS.toMinutes(duration) % 60);

            tvActiveTime.setText(durationText);

            if (location != null && lastLocation != null) {
                float distance = lastLocation.distanceTo(location);
                totalDistance += distance / 1000;
                tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", totalDistance));
            }
        }
    }

    private void updateEmergencyMessagePreview() {
        if (lastLocation != null) {
            String userName = sessionManager.getUserName();
            String preview = generateJourneyStartMessage(lastLocation, userName);
            tvEmergencyMessage.setText(preview);
        } else {
            tvEmergencyMessage.setText("Start a journey to share your location");
        }
    }

    // ========== EMERGENCY METHODS ==========

    private void triggerEmergency() {
        if (emergencyPhoneNumbers.isEmpty()) {
            showNoContactsDialog();
            return;
        }

        if (lastLocation == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Getting Location")
                    .setMessage("Please wait while we get your current location...")
                    .setPositiveButton("OK", null)
                    .show();

            getCurrentLocation(new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        lastLocation = location;
                        runOnUiThread(() -> showEmergencyConfirmationDialog());
                    }
                    fusedLocationClient.removeLocationUpdates(this);
                }
            });
        } else {
            showEmergencyConfirmationDialog();
        }
    }

    private void showEmergencyConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🚨 EMERGENCY ALERT")
                .setMessage("Send SOS to all emergency contacts?")
                .setPositiveButton("SEND SOS", (dialog, which) -> sendEmergencyAlert())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendEmergencyAlert() {
        String userName = sessionManager.getUserName();
        String emergencyMessage = generateEmergencyMessage(userName);

        if (checkSmsPermission()) {
            sendSmsToAllContacts(emergencyMessage);
        } else {
            requestSmsPermission();
        }

        sendWhatsAppToAllContacts(emergencyMessage);

        btnSOS.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.white));
        new Handler().postDelayed(() -> {
            btnSOS.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.sos_red));
        }, 500);

        Toast.makeText(this, "SOS sent to " + emergencyPhoneNumbers.size() + " contacts!", Toast.LENGTH_LONG).show();

        if (lastLocation != null) {
            saveLocationUpdate(lastLocation, emergencyMessage);
        }
    }

    // ========== OTHER ALERTS ==========

    private void sendSafeArrivalAlert() {
        if (emergencyPhoneNumbers.isEmpty()) return;

        String userName = sessionManager.getUserName();
        String message = "✅ Safe Arrival\n" +
                userName + " has safely reached their destination.\n" +
                "Time: " + timeFormat.format(new Date()) + "\n\n" +
                "Sent via SafetyTrack";

        if (checkSmsPermission()) {
            sendSmsToAllContacts(message);
        }
        Toast.makeText(this, "Safe arrival message sent", Toast.LENGTH_SHORT).show();
    }

    private void sendManualLocationUpdate() {
        if (emergencyPhoneNumbers.isEmpty()) {
            showNoContactsDialog();
            return;
        }

        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        getCurrentLocation(new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastLocation = location;
                    sendLocationToContacts(location);
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        });
    }

    private void sendLowBatteryAlert() {
        if (emergencyPhoneNumbers.isEmpty() || !isTracking) return;

        String userName = sessionManager.getUserName();
        String message = "⚠️ Low Battery Alert\n" +
                userName + "'s phone battery is at " + getBatteryLevel() + "%.\n" +
                "Journey tracking may be interrupted.\n\n" +
                "Sent via SafetyTrack";

        if (checkSmsPermission()) {
            sendSmsToAllContacts(message);
        }
    }

    // ========== AUTO TRACKING ==========

    private void enableAutoTracking() {
        if (!checkLocationPermission()) {
            switchAutoTrack.setChecked(false);
            requestLocationPermission();
            return;
        }
        journeyDetector.startDetection();
        Toast.makeText(this, "Auto-detection enabled", Toast.LENGTH_SHORT).show();
    }

    private void disableAutoTracking() {
        journeyDetector.stopDetection();
        Toast.makeText(this, "Auto-detection disabled", Toast.LENGTH_SHORT).show();
    }

    // ========== FIRESTORE METHODS ==========

    private void saveLocationUpdate(Location location, String message) {
        Map<String, Object> update = new HashMap<>();
        update.put("timestamp", System.currentTimeMillis());
        update.put("latitude", location.getLatitude());
        update.put("longitude", location.getLongitude());
        update.put("message", message);
        update.put("type", isTracking ? "journey" : "sos");
        update.put("userId", sessionManager.getUserId());

        firebaseHelper.saveLocationUpdate(update, new FirebaseHelper.FirebaseCompleteListener() {
            @Override
            public void onSuccess(String message) {
                Log.d(TAG, "Location saved to Firestore");
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to save location: " + error);
            }
        });
    }

    // ========== LOCATION SERVICE ==========

    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.putExtra("tripId", "trip_" + System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        stopService(serviceIntent);
    }

    // ========== HELPER METHODS ==========

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private int getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                return (int) (level * 100 / (float) scale);
            }
        }
        return 100;
    }

    // ========== DEBUG METHODS ==========

    private void debugContactState() {
        Log.d(TAG, "=== CONTACT DEBUG ===");
        Log.d(TAG, "Total contacts in memory: " + emergencyContacts.size());
        Log.d(TAG, "Selected phone numbers: " + emergencyPhoneNumbers.size());
        for (int i = 0; i < emergencyPhoneNumbers.size(); i++) {
            Log.d(TAG, "  " + (i+1) + ". " + emergencyPhoneNumbers.get(i));
        }
        Log.d(TAG, "=====================");
    }

    // ========== DIALOGS ==========

    private void showEnableGPSDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable GPS")
                .setMessage("GPS is disabled. Please enable GPS to track your journey.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivityForResult(intent, LOCATION_SETTINGS_REQUEST);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNoContactsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Contacts")
                .setMessage("Please select emergency contacts before starting a journey.")
                .setPositiveButton("Select Contacts", (dialog, which) -> {
                    Intent intent = new Intent(this, ContactsActivity.class);
                    startActivityForResult(intent, 100);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ========== LOGOUT ==========

    private void logoutUser() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    if (isTracking) {
                        stopJourneyTracking();
                    }
                    sessionManager.logout();
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ========== ACTIVITY LIFE CYCLE ==========

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            loadEmergencyContactsFromFirestore();
        } else if (requestCode == LOCATION_SETTINGS_REQUEST) {
            if (isLocationEnabled()) {
                Toast.makeText(this, "GPS enabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEmergencyContactsFromFirestore();
        updateStatusIndicators();
        debugContactState();

        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("SafetyTrack", MODE_PRIVATE);
        boolean wasTracking = prefs.getBoolean("isTracking", false);
        if (wasTracking && !isTracking) {
            startJourneyTracking();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences prefs = getSharedPreferences("SafetyTrack", MODE_PRIVATE);
        prefs.edit().putBoolean("isTracking", isTracking).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        handler.removeCallbacksAndMessages(null);
    }
}