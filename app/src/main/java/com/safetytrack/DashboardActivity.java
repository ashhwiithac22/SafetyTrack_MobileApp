package com.safetytrack;

import androidx.appcompat.widget.SwitchCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
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
    private static final int VOICE_PERMISSION_REQUEST = 1005;
    private static final long LOCATION_UPDATE_INTERVAL = 120000;
    private static final float MIN_DISTANCE_CHANGE = 10;

    // UI Components
    private TextView tvStatus, tvLastUpdate, tvUserName, tvBatteryStatus, tvGpsStatus, tvInternetStatus, tvSimStatus;
    private TextView tvContactsCount, tvActiveTime, tvDistance, tvEmergencyMessage, tvSmsLength;
    private TextView tvVoiceStatus;
    private Button btnStartStop, btnSelectContacts, btnTripHistory, btnLogout;
    private Button btnSafeArrival, btnManualUpdate;
    private SwitchCompat switchAutoTrack;
    private MaterialCardView cardStatus, cardContacts, cardStats, cardEmergency;
    private LinearLayout layoutIndicators;
    private ImageView ivGps, ivInternet, ivBattery, ivSim;
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
    private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault());

    // Active Time Counter
    private Runnable activeTimeRunnable;
    private Handler activeTimeHandler = new Handler(Looper.getMainLooper());

    // ========== NLP VOICE SOS RECOGNITION ==========
    private SpeechRecognizer speechRecognizer;
    private boolean isVoiceListening = false;
    private boolean isVoiceSOSAvailable = false;

    // ========== PERIODIC SMS HANDLER ==========
    private Handler periodicSmsHandler = new Handler(Looper.getMainLooper());
    private Runnable periodicSmsRunnable;

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
        checkVoiceRecognitionAvailability();

        // UNCOMMENT TO TEST SMS WITHOUT LINK
         testSmsWithoutLink();
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
            tvUserName = findViewById(R.id.tvUserName);
            tvStatus = findViewById(R.id.tvStatus);
            tvLastUpdate = findViewById(R.id.tvLastUpdate);
            tvBatteryStatus = findViewById(R.id.tvBatteryStatus);
            tvGpsStatus = findViewById(R.id.tvGpsStatus);
            tvInternetStatus = findViewById(R.id.tvInternetStatus);
            tvSimStatus = findViewById(R.id.tvSimStatus);

            try {
                tvVoiceStatus = findViewById(R.id.tvVoiceStatus);
            } catch (Exception e) {
                tvVoiceStatus = null;
            }

            tvContactsCount = findViewById(R.id.tvContactsCount);
            tvActiveTime = findViewById(R.id.tvActiveTime);
            tvDistance = findViewById(R.id.tvDistance);
            tvEmergencyMessage = findViewById(R.id.tvEmergencyMessage);
            tvSmsLength = findViewById(R.id.tvSmsLength);

            cardStatus = findViewById(R.id.cardStatus);
            cardContacts = findViewById(R.id.cardContacts);
            cardStats = findViewById(R.id.cardStats);
            cardEmergency = findViewById(R.id.cardEmergency);

            btnStartStop = findViewById(R.id.btnStartStop);
            btnSOS = findViewById(R.id.btnSOS);
            btnSelectContacts = findViewById(R.id.btnSelectContacts);
            btnTripHistory = findViewById(R.id.btnTripHistory);
            btnLogout = findViewById(R.id.btnLogout);
            btnSafeArrival = findViewById(R.id.btnSafeArrival);
            btnManualUpdate = findViewById(R.id.btnManualUpdate);

            switchAutoTrack = findViewById(R.id.switchAutoTrack);
            layoutIndicators = findViewById(R.id.layoutIndicators);
            ivGps = findViewById(R.id.ivGps);
            ivInternet = findViewById(R.id.ivInternet);
            ivBattery = findViewById(R.id.ivBattery);
            ivSim = findViewById(R.id.ivSim);
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

    // ========== VOICE RECOGNITION AVAILABILITY CHECK ==========

    private void checkVoiceRecognitionAvailability() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            isVoiceSOSAvailable = true;
            Log.d(TAG, "🎤 Voice recognition is available");
        } else {
            isVoiceSOSAvailable = false;
            Log.e(TAG, "❌ Voice recognition NOT available");
            Toast.makeText(this, "🎤 Voice SOS not available", Toast.LENGTH_LONG).show();
        }
    }

    // ========== NLP VOICE SOS IMPLEMENTATION ==========

    private void initVoiceSOS() {
        if (!isVoiceSOSAvailable) {
            Log.e(TAG, "❌ Cannot initialize Voice SOS");
            return;
        }

        try {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
                speechRecognizer = null;
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

            if (speechRecognizer == null) {
                Log.e(TAG, "❌ Failed to create SpeechRecognizer");
                return;
            }

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "🎤 Voice SOS is READY and LISTENING...");
                    runOnUiThread(() -> {
                        updateVoiceStatus(true);
                        Toast.makeText(DashboardActivity.this,
                                "🎤 Voice SOS Active - Say HELP, DANGER, EMERGENCY",
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "🎤 Speech detected");
                }

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "🎤 Speech ended");
                }

                @Override
                public void onError(int error) {
                    String errorMessage = getErrorText(error);
                    Log.e(TAG, "🎤 Speech error: " + errorMessage);
                    isVoiceListening = false;
                    updateVoiceStatus(false);

                    if (isTracking && isVoiceSOSAvailable) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> startVoiceListening(), 1000);
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && matches.size() > 0) {
                        String spokenText = matches.get(0).toLowerCase();
                        Log.d(TAG, "🎤 USER SAID: \"" + spokenText + "\"");

                        runOnUiThread(() -> {
                            Toast.makeText(DashboardActivity.this,
                                    "🎤 You said: \"" + spokenText + "\"",
                                    Toast.LENGTH_SHORT).show();
                        });

                        // 🚨 NLP KEYWORD DETECTION - IMMEDIATE SOS 🚨
                        if (spokenText.contains("help") ||
                                spokenText.contains("danger") ||
                                spokenText.contains("risk") ||
                                spokenText.contains("emergency") ||
                                spokenText.contains("sos") ||
                                spokenText.contains("save me") ||
                                spokenText.contains("accident") ||
                                spokenText.contains("crash") ||
                                spokenText.contains("attack") ||
                                spokenText.contains("need assistance")) {

                            Log.e(TAG, "🚨🚨🚨 SOS KEYWORD DETECTED! \"" + spokenText + "\" 🚨🚨🚨");

                            runOnUiThread(() -> {
                                new AlertDialog.Builder(DashboardActivity.this)
                                        .setTitle("🚨🚨🚨 EMERGENCY SOS 🚨🚨🚨")
                                        .setMessage("Voice keyword detected: \"" + spokenText + "\"\n\nSending emergency alert to all contacts!")
                                        .setPositiveButton("SEND SOS", (dialog, which) -> triggerEmergency())
                                        .setNegativeButton("Cancel", null)
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .show();
                            });
                        }
                    }
                    isVoiceListening = false;
                    updateVoiceStatus(false);

                    if (isTracking && isVoiceSOSAvailable) {
                        startVoiceListening();
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            Log.d(TAG, "🎤 Voice SOS initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "🎤 Failed to initialize Voice SOS: " + e.getMessage());
        }
    }

    private void startVoiceListening() {
        if (!isTracking) {
            Log.d(TAG, "🎤 Not listening - journey not active");
            return;
        }

        if (!isVoiceSOSAvailable) {
            Log.d(TAG, "🎤 Voice SOS not available");
            return;
        }

        if (isVoiceListening) {
            Log.d(TAG, "🎤 Already listening");
            return;
        }

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "🎤 Requesting audio permission");
                requestAudioPermission();
                return;
            }

            if (speechRecognizer == null) {
                initVoiceSOS();
                if (speechRecognizer == null) return;
            }

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            speechRecognizer.startListening(intent);
            isVoiceListening = true;
            updateVoiceStatus(true);
            Log.d(TAG, "🎤 Voice SOS listening STARTED");

        } catch (SecurityException e) {
            Log.e(TAG, "🎤 Missing RECORD_AUDIO permission");
            requestAudioPermission();
        } catch (Exception e) {
            Log.e(TAG, "🎤 Error starting voice recognition: " + e.getMessage());
        }
    }

    private void stopVoiceListening() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                isVoiceListening = false;
                updateVoiceStatus(false);
                Log.d(TAG, "🎤 Voice SOS listening STOPPED");
            } catch (Exception e) {
                Log.e(TAG, "🎤 Error stopping voice recognition: " + e.getMessage());
            }
        }
    }

    private void updateVoiceStatus(boolean isListening) {
        if (tvVoiceStatus != null) {
            if (isListening) {
                tvVoiceStatus.setText("🎤 Voice SOS: ACTIVE");
                tvVoiceStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green));
            } else {
                tvVoiceStatus.setText("🎤 Voice SOS: Ready");
                tvVoiceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        }
    }

    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                VOICE_PERMISSION_REQUEST);
    }

    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: return "Audio error";
            case SpeechRecognizer.ERROR_CLIENT: return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK: return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "No speech match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER: return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Speech timeout";
            default: return "Unknown error: " + errorCode;
        }
    }

    // ========== SIM STATUS INDICATOR ==========

    private void updateSimStatus() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                int simState = tm.getSimState();
                if (simState == TelephonyManager.SIM_STATE_READY) {
                    tvSimStatus.setText("SIM Ready");
                    tvSimStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green));
                    ivSim.setColorFilter(ContextCompat.getColor(this, R.color.success_green));
                } else {
                    tvSimStatus.setText("No SIM");
                    tvSimStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red));
                    ivSim.setColorFilter(ContextCompat.getColor(this, R.color.error_red));
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Telephony permission error: " + e.getMessage());
        }
    }

    // ========== SMS CHARACTER COUNTER ==========

    private void updateSmsCharacterCount(String message) {
        if (tvSmsLength != null) {
            int length = message.length();
            int smsCount = 1;
            if (length > 160) {
                smsCount = (int) Math.ceil(length / 153.0);
            }
            tvSmsLength.setText(length + "/" + (smsCount * 153) + " chars (" + smsCount + " SMS)");

            int color = length > 160 ?
                    ContextCompat.getColor(this, R.color.warning_orange) :
                    ContextCompat.getColor(this, R.color.success_green);
            tvSmsLength.setTextColor(color);
        }
    }

    // ========== TEST METHOD - SMS WITHOUT LINK ==========
    private void testSmsWithoutLink() {
        String testNumber = "+919345048662"; // CHANGE THIS TO YOUR NUMBER
        String testMessage = "✅ TEST SMS from SafetyTrack - NO LINK - " +
                new SimpleDateFormat("hh:mm:ss", Locale.getDefault()).format(new Date());

        try {
            SmsManager smsManager = SmsManager.getDefault();
            Intent sentIntent = new Intent("com.safetytrack.SMS_SENT");
            sentIntent.putExtra("phone", testNumber);
            sentIntent.putExtra("type", "TEST");
            PendingIntent sentPI = PendingIntent.getBroadcast(
                    this, 9999, sentIntent, PendingIntent.FLAG_IMMUTABLE);

            smsManager.sendTextMessage(testNumber, null, testMessage, sentPI, null);
            Log.d(TAG, "📱 TEST SMS sent to: " + testNumber);
            Toast.makeText(this, "📱 Test SMS sent - CHECK YOUR INBOX!", Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SMS permission denied");
            requestSmsPermission();
        } catch (Exception e) {
            Log.e(TAG, "❌ SMS failed: " + e.getMessage());
            Toast.makeText(this, "SMS failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        if (!checkLocationPermission()) requestLocationPermission();
        if (!checkSmsPermission()) requestSmsPermission();
        if (!checkNotificationPermission()) requestNotificationPermission();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
                    if (isTracking) startLocationUpdates();
                }
                break;
            case SMS_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show();
                }
                break;
            case NOTIFICATION_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Notification permission granted");
                }
                break;
            case VOICE_PERMISSION_REQUEST:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "🎤 Audio permission granted", Toast.LENGTH_SHORT).show();
                    if (isTracking) startVoiceListening();
                }
                break;
        }
    }

    // ========== CONTACT METHODS ==========

    private void loadUserInfo() {
        String userName = sessionManager.getUserName();
        tvUserName.setText("Welcome, " + userName);
    }

    private void loadEmergencyContactsFromFirestore() {
        progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "Loading contacts from Firestore...");

        firebaseHelper.loadContactsFromFirestore(new FirebaseHelper.FirebaseContactsListener() {
            @Override
            public void onSuccess(List<Contact> contacts) {
                progressBar.setVisibility(View.GONE);
                emergencyContacts.clear();
                emergencyPhoneNumbers.clear();

                emergencyContacts.addAll(contacts);
                Log.d(TAG, "Total contacts: " + contacts.size());

                for (Contact contact : contacts) {
                    if (contact.isSelected()) {
                        String phone = contact.getPhoneNumber();
                        if (phone != null && !phone.isEmpty()) {
                            String cleanPhone = phone.replaceAll("[^0-9]", "");

                            if (cleanPhone.length() == 10) {
                                cleanPhone = "+91" + cleanPhone;
                            } else if (cleanPhone.length() == 12 && cleanPhone.startsWith("91")) {
                                cleanPhone = "+" + cleanPhone;
                            } else if (cleanPhone.length() > 12) {
                                cleanPhone = "+" + cleanPhone.replaceAll("^\\+?", "");
                            }

                            cleanPhone = cleanPhone.replace("++", "+").replace("+91+91", "+91");
                            emergencyPhoneNumbers.add(cleanPhone);
                            Log.d(TAG, "✅ Contact: " + cleanPhone);
                        }
                    }
                }

                updateContactsUI();
                saveContactsToPreferences();
                Log.d(TAG, "Loaded " + emergencyPhoneNumbers.size() + " contacts");
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Error loading contacts: " + error);
                loadEmergencyContactsFromPreferences();
            }
        });
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
                        String phone = parts[1].replaceAll("[^0-9]", "");

                        if (phone.length() == 10) phone = "+91" + phone;
                        else if (phone.length() == 12 && phone.startsWith("91")) phone = "+" + phone;
                        else if (phone.length() > 12) phone = "+" + phone.replaceAll("^\\+?", "");

                        phone = phone.replace("++", "+").replace("+91+91", "+91");
                        emergencyPhoneNumbers.add(phone);
                    }
                }
            }
        }
        updateContactsUI();
    }

    private void updateContactsUI() {
        int selectedCount = emergencyPhoneNumbers.size();

        if (selectedCount == 0) {
            tvContactsCount.setText("0");
            tvContactsCount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            tvContactsCount.setText(String.valueOf(selectedCount));
            tvContactsCount.setTextColor(ContextCompat.getColor(this, R.color.white));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tvContactsCount.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
            }
        }

        TextView tvNoContacts = findViewById(R.id.tvNoContacts);
        if (tvNoContacts != null) {
            if (selectedCount == 0) {
                tvNoContacts.setText("No contacts selected");
                tvNoContacts.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            } else {
                tvNoContacts.setText(selectedCount + " Contact" + (selectedCount > 1 ? "s" : "") + " Selected");
                tvNoContacts.setTextColor(ContextCompat.getColor(this, R.color.primary));
            }
            tvNoContacts.setVisibility(View.VISIBLE);
        }
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
                    updateEmergencyMessagePreview();
                    if (journeyStartTime > 0) updateStatsUI(location);
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
                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
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

    // ========== FIXED: SMS WITHOUT LINKS - JIO FRIENDLY ==========
    // THESE MESSAGES CONTAIN NO CLICKABLE LINKS - JIO WILL DELIVER THEM!

    private String generateEmergencyMessage(Location location, String userName) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        return "🚨🚨🚨 EMERGENCY ALERT 🚨🚨🚨\n" +
                userName + " is in DANGER!\n" +
                "📍 Location: " + lat + ", " + lng + "\n" +
                "Google Maps: maps.google.com/?q=" + lat + "," + lng + "\n" +
                "PLEASE RESPOND IMMEDIATELY!\n" +
                "Sent via SafetyTrack";
    }

    private String generateSafeArrivalMessage(Location location, String userName) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String time = timeFormat.format(new Date());

        return "🛡️🛡️🛡️ SAFE ARRIVAL 🛡️🛡️🛡️\n" +
                userName + " has arrived safely.\n" +
                "📍 Final Location: " + lat + ", " + lng + "\n" +
                "Google Maps: maps.google.com/?q=" + lat + "," + lng + "\n" +
                "🕒 Time: " + time + "\n" +
                "🔋 Battery: " + getBatteryLevel() + "%\n" +
                "Sent via SafetyTrack";
    }

    private String generateJourneyUpdateMessage(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String time = timeFormat.format(new Date());

        return "📍 Journey Update\n" +
                "📍 Location: " + lat + ", " + lng + "\n" +
                "Google Maps: maps.google.com/?q=" + lat + "," + lng + "\n" +
                "🕒 Time: " + time + "\n" +
                "🔋 Battery: " + getBatteryLevel() + "%\n" +
                "Sent via SafetyTrack";
    }

    // ========== SMS SENDING METHODS ==========

    private void sendSmsToContacts(String message, String type) {
        if (emergencyPhoneNumbers.isEmpty()) {
            Log.e(TAG, "No contacts to send SMS");
            Toast.makeText(this, "No emergency contacts selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkSmsPermission()) {
            Log.e(TAG, "SMS permission not granted");
            requestSmsPermission();
            return;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            int successCount = 0;

            for (String phone : emergencyPhoneNumbers) {
                try {
                    int requestCode = phone.hashCode();

                    if (message.length() > 160) {
                        ArrayList<String> messageParts = smsManager.divideMessage(message);
                        smsManager.sendMultipartTextMessage(phone, null, messageParts, null, null);
                        Log.d(TAG, "✅ " + type + " multipart SMS sent to: " + phone);
                    } else {
                        smsManager.sendTextMessage(phone, null, message, null, null);
                        Log.d(TAG, "✅ " + type + " SMS sent to: " + phone);
                    }
                    successCount++;

                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to send SMS to " + phone + ": " + e.getMessage());
                }
            }

            if (successCount > 0) {
                String toastMsg = "";
                switch (type) {
                    case "SOS":
                        toastMsg = "🚨 SOS Emergency Alert";
                        break;
                    case "SAFE_ARRIVAL":
                        toastMsg = "🛡️ Safe Arrival Notification";
                        break;
                    case "JOURNEY_UPDATE":
                        toastMsg = "📍 Journey Update";
                        break;
                    default:
                        toastMsg = "📨 Message";
                }
                Toast.makeText(this, toastMsg + " sent to " + successCount + " contacts", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS Manager error: " + e.getMessage());
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ========== PERIODIC SMS UPDATES ==========

    private void startPeriodicSmsUpdates() {
        if (periodicSmsRunnable != null) {
            periodicSmsHandler.removeCallbacks(periodicSmsRunnable);
        }

        periodicSmsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTracking && lastLocation != null && !emergencyPhoneNumbers.isEmpty()) {
                    String message = generateJourneyUpdateMessage(lastLocation);
                    sendSmsToContacts(message, "JOURNEY_UPDATE");
                    saveLocationUpdate(lastLocation, "Periodic update sent");
                    Log.d(TAG, "Periodic SMS update sent");
                }

                // Schedule next update in 2 minutes (120,000 ms)
                if (isTracking) {
                    periodicSmsHandler.postDelayed(this, 120000);
                }
            }
        };

        // Start first update after 2 minutes
        periodicSmsHandler.postDelayed(periodicSmsRunnable, 120000);
        Log.d(TAG, "Periodic SMS updates scheduled (every 2 minutes)");
    }

    private void stopPeriodicSmsUpdates() {
        if (periodicSmsRunnable != null) {
            periodicSmsHandler.removeCallbacks(periodicSmsRunnable);
            periodicSmsRunnable = null;
            Log.d(TAG, "Periodic SMS updates stopped");
        }
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
        if (!checkSmsPermission()) {
            requestSmsPermission();
            Toast.makeText(this, "SMS permission required", Toast.LENGTH_LONG).show();
            return;
        }

        isTracking = true;
        journeyStartTime = System.currentTimeMillis();

        btnStartStop.setText("🏍️🏍️🏍️ STOP JOURNEY");
        btnStartStop.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.error_red));
        tvStatus.setText("Journey Active 🏍️");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green));
        cardStatus.setStrokeColor(ContextCompat.getColorStateList(this, R.color.success_green));
        cardStatus.setStrokeWidth(2);

        startActiveTimeCounter();
        startPeriodicSmsUpdates(); // START 2-MINUTE SMS UPDATES

        getCurrentLocation(new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastLocation = location;
                    saveLocationUpdate(location, "Journey started 🏍️");
                    updateEmergencyMessagePreview();
                    tvLastUpdate.setText("Last: " + timeFormat.format(new Date()));
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        });

        startLocationService();

        if (switchAutoTrack.isChecked()) {
            journeyDetector.startDetection();
        }

        // Initialize and start Voice SOS
        if (isVoiceSOSAvailable) {
            initVoiceSOS();
            new Handler(Looper.getMainLooper()).postDelayed(() -> startVoiceListening(), 500);
        }

        Toast.makeText(this, "🏍️ Journey started - SMS every 2 minutes", Toast.LENGTH_LONG).show();
    }

    private void stopJourneyTracking() {
        isTracking = false;
        isJourneyActive = false;
        stopVoiceListening();
        stopPeriodicSmsUpdates(); // STOP 2-MINUTE SMS UPDATES

        btnStartStop.setText("🏍️🏍️🏍️ START JOURNEY");
        btnStartStop.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
        tvStatus.setText("Ready");
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        cardStatus.setStrokeWidth(0);

        stopActiveTimeCounter();
        stopLocationUpdates();
        journeyDetector.stopDetection();
        fetchFinalLocationAndSendSafeArrival();
        stopLocationService();

        journeyStartTime = 0;
        totalDistance = 0;
        updateStatsUI(null);
        Toast.makeText(this, "Journey stopped", Toast.LENGTH_SHORT).show();
    }

    // ========== ACTIVE TIME COUNTER ==========

    private void startActiveTimeCounter() {
        if (activeTimeRunnable != null) {
            activeTimeHandler.removeCallbacks(activeTimeRunnable);
        }

        activeTimeRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTracking && journeyStartTime > 0) {
                    long duration = System.currentTimeMillis() - journeyStartTime;
                    String durationText = String.format("%02d:%02d",
                            TimeUnit.MILLISECONDS.toHours(duration),
                            TimeUnit.MILLISECONDS.toMinutes(duration) % 60);
                    tvActiveTime.setText(durationText);
                    activeTimeHandler.postDelayed(this, 1000);
                }
            }
        };
        activeTimeHandler.post(activeTimeRunnable);
    }

    private void stopActiveTimeCounter() {
        if (activeTimeRunnable != null) {
            activeTimeHandler.removeCallbacks(activeTimeRunnable);
            activeTimeRunnable = null;
        }
        tvActiveTime.setText("00:00");
    }

    // ========== LOCATION SERVICE METHODS ==========

    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.putExtra("tripId", "trip_" + System.currentTimeMillis());

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "✅ LocationService started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting LocationService: " + e.getMessage());
        }
    }

    private void stopLocationService() {
        try {
            Intent serviceIntent = new Intent(this, LocationService.class);
            stopService(serviceIntent);
            Log.d(TAG, "🛑 LocationService stopped");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping LocationService: " + e.getMessage());
        }
    }

    // ========== SAFE ARRIVAL METHODS ==========

    private void fetchFinalLocationAndSendSafeArrival() {
        if (emergencyPhoneNumbers.isEmpty()) return;
        if (!checkSmsPermission()) return;

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        getCurrentLocation(new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastLocation = location;
                    String message = generateSafeArrivalMessage(location, sessionManager.getUserName());
                    sendSmsToContacts(message, "SAFE_ARRIVAL");
                    saveLocationUpdate(location, "Safe arrival sent");
                } else if (lastLocation != null) {
                    String message = generateSafeArrivalMessage(lastLocation, sessionManager.getUserName());
                    sendSmsToContacts(message, "SAFE_ARRIVAL");
                    saveLocationUpdate(lastLocation, "Safe arrival sent (cached)");
                }

                if (progressBar != null) progressBar.setVisibility(View.GONE);
                fusedLocationClient.removeLocationUpdates(this);
            }
        });
    }

    // ========== SOS BUTTON ==========

    private void triggerEmergency() {
        if (emergencyPhoneNumbers.isEmpty()) {
            showNoContactsDialog();
            return;
        }

        // Button animation
        btnSOS.animate().scaleX(1.3f).scaleY(1.3f).setDuration(300)
                .withEndAction(() -> btnSOS.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()).start();

        if (!checkSmsPermission()) {
            requestSmsPermission();
            Toast.makeText(this, "SMS permission required for SOS", Toast.LENGTH_LONG).show();
            return;
        }

        if (!checkLocationPermission()) {
            requestLocationPermission();
            Toast.makeText(this, "Location permission required for SOS", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "🚨 EMERGENCY SOS TRIGGERED! Sending alert...", Toast.LENGTH_SHORT).show();

        if (lastLocation != null) {
            String message = generateEmergencyMessage(lastLocation, sessionManager.getUserName());
            sendSmsToContacts(message, "SOS");
            saveLocationUpdate(lastLocation, "🚨 SOS ALERT SENT");
        } else {
            Toast.makeText(this, "🚨 Getting current location...", Toast.LENGTH_SHORT).show();
            getCurrentLocation(new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        lastLocation = location;
                        String message = generateEmergencyMessage(location, sessionManager.getUserName());
                        sendSmsToContacts(message, "SOS");
                        saveLocationUpdate(location, "🚨 SOS ALERT SENT");
                    } else {
                        // Send SOS without location
                        String message = "🚨🚨🚨 EMERGENCY ALERT 🚨🚨🚨\n" +
                                sessionManager.getUserName() + " is in DANGER!\n" +
                                "📍 Location: UNAVAILABLE\n" +
                                "PLEASE RESPOND IMMEDIATELY!\n" +
                                "Sent via SafetyTrack";
                        sendSmsToContacts(message, "SOS");
                    }
                    fusedLocationClient.removeLocationUpdates(this);
                }
            });
        }
    }

    // ========== SAFE ARRIVAL BUTTON ==========

    private void sendSafeArrivalAlert() {
        if (emergencyPhoneNumbers.isEmpty()) {
            Toast.makeText(this, "No contacts selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isTracking) {
            stopJourneyTracking();
        } else {
            fetchFinalLocationAndSendSafeArrival();
        }
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
        if (batteryLevel > 50) batteryColor = R.color.success_green;
        else if (batteryLevel > 20) batteryColor = R.color.warning_orange;
        else batteryColor = R.color.error_red;

        tvBatteryStatus.setTextColor(ContextCompat.getColor(this, batteryColor));
        ivBattery.setColorFilter(ContextCompat.getColor(this, batteryColor));

        updateSimStatus();

        if (batteryLevel < 20 && isTracking) sendLowBatteryAlert();
    }

    private void updateLocationUI(Location location) {
        tvLastUpdate.setText("Last: " + timeFormat.format(new Date()));
        updateEmergencyMessagePreview();
    }

    private void updateStatsUI(Location location) {
        if (journeyStartTime > 0) {
            long duration = System.currentTimeMillis() - journeyStartTime;
            tvActiveTime.setText(String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toHours(duration),
                    TimeUnit.MILLISECONDS.toMinutes(duration) % 60));

            if (location != null && lastLocation != null) {
                totalDistance += lastLocation.distanceTo(location) / 1000;
                tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", totalDistance));
            }
        }
    }

    private void updateEmergencyMessagePreview() {
        if (lastLocation != null) {
            tvEmergencyMessage.setText(generateJourneyUpdateMessage(lastLocation));
            updateSmsCharacterCount(generateJourneyUpdateMessage(lastLocation));
        } else {
            tvEmergencyMessage.setText("Start a journey to share your location");
        }
    }

    // ========== OTHER ALERTS ==========

    private void sendManualLocationUpdate() {
        if (emergencyPhoneNumbers.isEmpty()) {
            showNoContactsDialog();
            return;
        }

        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        Toast.makeText(this, "📍 Getting current location...", Toast.LENGTH_SHORT).show();

        getCurrentLocation(new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastLocation = location;
                    saveLocationUpdate(location, "Manual update");
                    tvLastUpdate.setText("Last: " + timeFormat.format(new Date()));
                    updateEmergencyMessagePreview();
                    Toast.makeText(DashboardActivity.this, "📍 Location updated", Toast.LENGTH_SHORT).show();
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        });
    }

    private void sendLowBatteryAlert() {
        if (emergencyPhoneNumbers.isEmpty() || !isTracking) return;

        String message = "⚠️ Low Battery Alert\nBattery: " + getBatteryLevel() + "%\nJourney may be interrupted.\nSent via SafetyTrack";

        if (checkSmsPermission()) {
            try {
                SmsManager smsManager = SmsManager.getDefault();
                for (String phone : emergencyPhoneNumbers) {
                    smsManager.sendTextMessage(phone, null, message, null, null);
                }
                Log.d(TAG, "Low battery alert sent");
            } catch (Exception e) {
                Log.e(TAG, "SMS error: " + e.getMessage());
            }
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
                Log.d(TAG, "Location saved: " + message);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to save: " + error);
            }
        });
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

    // ========== DIALOGS ==========

    private void showEnableGPSDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable GPS")
                .setMessage("Please enable GPS to track your journey.")
                .setPositiveButton("Settings", (dialog, which) ->
                        startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), LOCATION_SETTINGS_REQUEST))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNoContactsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Contacts")
                .setMessage("Please select emergency contacts before starting a journey.")
                .setPositiveButton("Select Contacts", (dialog, which) ->
                        startActivity(new Intent(this, ContactsActivity.class)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ========== LOGOUT ==========

    private void logoutUser() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    if (isTracking) stopJourneyTracking();
                    stopVoiceListening();
                    if (speechRecognizer != null) {
                        speechRecognizer.destroy();
                        speechRecognizer = null;
                    }
                    sessionManager.logout();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
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
        } else if (requestCode == LOCATION_SETTINGS_REQUEST && isLocationEnabled()) {
            Toast.makeText(this, "GPS enabled", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEmergencyContactsFromFirestore();
        updateStatusIndicators();

        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
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
        stopPeriodicSmsUpdates();
        handler.removeCallbacksAndMessages(null);
        activeTimeHandler.removeCallbacksAndMessages(null);
        periodicSmsHandler.removeCallbacksAndMessages(null);
        stopActiveTimeCounter();
        stopVoiceListening();

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }

        Log.d(TAG, "DashboardActivity destroyed");
    }
}