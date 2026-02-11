package com.safetytrack.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class PermissionManager {
    private Activity activity;
    private FusedLocationProviderClient fusedLocationClient;

    public interface PermissionCallback {
        void onPermissionResult(boolean granted);
    }

    public interface LocationCallback {
        void onLocationReceived(Location location);
        void onLocationError(String error);
    }

    public PermissionManager(Activity activity) {
        this.activity = activity;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    public void requestContactsPermission(PermissionCallback callback) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            callback.onPermissionResult(true);
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_CONTACTS}, 100);
            // Note: You need to handle onRequestPermissionsResult in Activity
            callback.onPermissionResult(false);
        }
    }

    public void requestLocationPermission(PermissionCallback callback) {
        if (hasLocationPermission()) {
            callback.onPermissionResult(true);
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            callback.onPermissionResult(false);
        }
    }

    public void getCurrentLocation(LocationCallback callback) {
        if (!hasLocationPermission()) {
            callback.onLocationError("Location permission not granted");
            return;
        }

        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(activity, location -> {
                        if (location != null) {
                            callback.onLocationReceived(location);
                        } else {
                            callback.onLocationError("Unable to get location");
                        }
                    })
                    .addOnFailureListener(e -> {
                        callback.onLocationError("Location error: " + e.getMessage());
                    });
        } catch (SecurityException e) {
            callback.onLocationError("Location permission denied");
        }
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean hasContactsPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }
}