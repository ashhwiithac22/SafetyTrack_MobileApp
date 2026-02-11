//services/JourneyDetector.java
package com.safetytrack.services;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.safetytrack.models.Trip;
import com.safetytrack.utils.FirebaseHelper;
import com.safetytrack.utils.SessionManager;

import java.util.Timer;
import java.util.TimerTask;

public class JourneyDetector {
    private static final String TAG = "JourneyDetector";
    private static final float MIN_SPEED_KPH = 10.0f;
    private static final long DETECTION_INTERVAL = 30000;

    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseHelper firebaseHelper;
    private SessionManager sessionManager;
    private Timer detectionTimer;
    private String currentTripId;
    private boolean isJourneyActive;

    public JourneyDetector(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.firebaseHelper = new FirebaseHelper(context);
        this.sessionManager = SessionManager.getInstance(context);
    }

    public void startDetection() {
        if (detectionTimer != null) {
            detectionTimer.cancel();
        }

        detectionTimer = new Timer();
        detectionTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkIfDriving();
            }
        }, 0, DETECTION_INTERVAL);
    }

    private void checkIfDriving() {
        // Check location permission first
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted. Skipping location check.");
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null && location.hasSpeed()) {
                            float speedKph = location.getSpeed() * 3.6f;

                            if (speedKph > MIN_SPEED_KPH && !isJourneyActive) {
                                startJourney(location);
                            } else if (speedKph < MIN_SPEED_KPH && isJourneyActive) {
                                checkJourneyEnd(location);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get location: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in location request: " + e.getMessage());
        }
    }

    private void startJourney(Location location) {
        isJourneyActive = true;
        Trip trip = new Trip(getUserId());
        trip.setStartLat(location.getLatitude());
        trip.setStartLng(location.getLongitude());

        firebaseHelper.startTrip(trip, new FirebaseHelper.FirebaseCompleteListener() {
            @Override
            public void onSuccess(String tripId) {
                currentTripId = tripId;
                Log.d(TAG, "Journey started: " + currentTripId);
                startLocationService();

                // Save trip ID
                SharedPreferences prefs = context.getSharedPreferences("SafetyTrack", Context.MODE_PRIVATE);
                prefs.edit().putString("currentTripId", currentTripId).apply();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error starting trip: " + error);
                isJourneyActive = false;
            }
        });
    }

    private void checkJourneyEnd(Location location) {
        Timer endTimer = new Timer();
        endTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Check permission again
                if (ContextCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Location permission not granted. Cannot check journey end.");
                    return;
                }

                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(newLocation -> {
                            if (newLocation != null && newLocation.hasSpeed()) {
                                float speedKph = newLocation.getSpeed() * 3.6f;
                                if (speedKph < MIN_SPEED_KPH) {
                                    endJourney(newLocation);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get location for journey end check: " + e.getMessage());
                        });
            }
        }, 300000); // 5 minutes
    }

    private void endJourney(Location location) {
        isJourneyActive = false;

        if (currentTripId != null) {
            firebaseHelper.updateTripStatus(currentTripId, "completed",
                    new FirebaseHelper.FirebaseCompleteListener() {
                        @Override
                        public void onSuccess(String message) {
                            Log.d(TAG, "Journey ended: " + currentTripId);
                            stopLocationService();

                            // Clear trip ID
                            SharedPreferences prefs = context.getSharedPreferences("SafetyTrack", Context.MODE_PRIVATE);
                            prefs.edit().remove("currentTripId").apply();
                        }

                        @Override
                        public void onError(String error) {
                            Log.e(TAG, "Error ending trip: " + error);
                        }
                    });
        }
    }

    private void startLocationService() {
        Intent serviceIntent = new Intent(context, LocationService.class);
        serviceIntent.putExtra("tripId", currentTripId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    private void stopLocationService() {
        Intent serviceIntent = new Intent(context, LocationService.class);
        context.stopService(serviceIntent);
    }

    private String getUserId() {
        return sessionManager.getUserId();
    }

    public void stopDetection() {
        if (detectionTimer != null) {
            detectionTimer.cancel();
            detectionTimer = null;
        }
    }
}