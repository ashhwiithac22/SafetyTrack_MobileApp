//FirebaseHelper.java
package com.safetytrack.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.safetytrack.models.Contact;
import com.safetytrack.models.Trip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseHelper {
    private static final String TAG = "FirebaseHelper";
    private static final String USERS_COLLECTION = "users";
    private static final String TRIPS_COLLECTION = "trips";
    private static final String LOCATIONS_COLLECTION = "locations";
    private static final String CONTACTS_COLLECTION = "emergency_contacts";

    private FirebaseFirestore db;
    private Context context;
    private SessionManager sessionManager;

    public interface FirebaseAuthListener {
        void onSuccess(String userId);
        void onError(String error);
        void onShowProgress();
    }

    public interface FirebaseCompleteListener {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface FirebaseContactsListener {
        void onSuccess(List<Contact> contacts);
        void onError(String error);
    }

    public FirebaseHelper(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = SessionManager.getInstance(context);
    }

    // ========== SESSION METHODS ==========

    public boolean isUserLoggedIn() {
        return sessionManager.isLoggedIn();
    }

    public void logout() {
        sessionManager.logout();
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    // ========== USER METHODS ==========

    public void loginUser(String phone, String password, FirebaseAuthListener listener) {
        listener.onShowProgress();

        db.collection(USERS_COLLECTION)
                .whereEqualTo("phone", phone)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        String storedPassword = document.getString("password");
                        String userId = document.getId();
                        String name = document.getString("name");
                        String email = document.getString("email");

                        if (storedPassword != null && storedPassword.equals(password)) {
                            SessionManager sessionManager = SessionManager.getInstance(context);
                            sessionManager.createLoginSession(userId, name, email, phone);
                            listener.onSuccess(userId);
                        } else {
                            listener.onError("Invalid password");
                        }
                    } else {
                        listener.onError("Phone number not registered");
                    }
                })
                .addOnFailureListener(e -> {
                    listener.onError("Error: " + e.getMessage());
                });
    }

    public void registerUser(String name, String email, String phone, String password,
                             boolean locationPermissionGranted, FirebaseCompleteListener listener) {
        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("phone", phone);
        user.put("password", password);
        user.put("locationPermission", locationPermissionGranted);
        user.put("createdAt", System.currentTimeMillis());
        user.put("updatedAt", System.currentTimeMillis());

        db.collection(USERS_COLLECTION)
                .whereEqualTo("phone", phone)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        listener.onError("User with this phone number already exists");
                    } else {
                        db.collection(USERS_COLLECTION)
                                .whereEqualTo("email", email)
                                .limit(1)
                                .get()
                                .addOnCompleteListener(emailTask -> {
                                    if (emailTask.isSuccessful() && emailTask.getResult() != null && !emailTask.getResult().isEmpty()) {
                                        listener.onError("User with this email already exists");
                                    } else {
                                        db.collection(USERS_COLLECTION)
                                                .add(user)
                                                .addOnSuccessListener(documentReference -> {
                                                    listener.onSuccess("Registration successful! Please login.");
                                                })
                                                .addOnFailureListener(e -> {
                                                    listener.onError("Registration failed: " + e.getMessage());
                                                });
                                    }
                                });
                    }
                });
    }

    public void resetPassword(String identifier, String field, String newPassword, FirebaseCompleteListener listener) {
        db.collection(USERS_COLLECTION)
                .whereEqualTo(field, identifier)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        String documentId = task.getResult().getDocuments().get(0).getId();

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("password", newPassword);
                        updates.put("updatedAt", System.currentTimeMillis());

                        db.collection(USERS_COLLECTION)
                                .document(documentId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    listener.onSuccess("Password reset successfully");
                                })
                                .addOnFailureListener(e -> {
                                    listener.onError("Failed to reset password: " + e.getMessage());
                                });
                    } else {
                        listener.onError("User not found");
                    }
                });
    }

    public void checkUserExists(String field, String value, FirebaseCompleteListener listener) {
        db.collection(USERS_COLLECTION)
                .whereEqualTo(field, value)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        listener.onSuccess("exists");
                    } else {
                        listener.onError("not found");
                    }
                });
    }

    public void getUserDetails(String userId, FirebaseCompleteListener listener) {
        db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String email = documentSnapshot.getString("email");
                        String phone = documentSnapshot.getString("phone");

                        Map<String, Object> userData = new HashMap<>();
                        userData.put("name", name);
                        userData.put("email", email);
                        userData.put("phone", phone);

                        listener.onSuccess(userData.toString());
                    } else {
                        listener.onError("User not found");
                    }
                })
                .addOnFailureListener(e -> {
                    listener.onError("Error: " + e.getMessage());
                });
    }

    // ========== CONTACT METHODS - FIRESTORE STORAGE ==========
    // ✅ FIXED: Now properly saves and loads selected state

    public void saveContactsToFirestore(List<Contact> contacts, FirebaseCompleteListener listener) {
        String userId = sessionManager.getUserId();
        if (userId.isEmpty()) {
            listener.onError("User not logged in");
            return;
        }

        List<Map<String, Object>> contactsList = new ArrayList<>();
        for (Contact contact : contacts) {
            Map<String, Object> contactMap = new HashMap<>();
            contactMap.put("name", contact.getName());
            contactMap.put("phoneNumber", contact.getPhoneNumber());
            contactMap.put("rawInfo", contact.getRawContactInfo());
            contactMap.put("isSelected", true); // ✅ CRITICAL FIX - Save selected state
            contactMap.put("addedAt", System.currentTimeMillis());
            contactsList.add(contactMap);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("contacts", contactsList);
        data.put("updatedAt", System.currentTimeMillis());

        db.collection(CONTACTS_COLLECTION)
                .document(userId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Contacts saved to Firestore for user: " + userId);
                    listener.onSuccess("Contacts saved successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save contacts: " + e.getMessage());
                    listener.onError("Failed to save contacts: " + e.getMessage());
                });
    }

    public void loadContactsFromFirestore(FirebaseContactsListener listener) {
        String userId = sessionManager.getUserId();
        if (userId.isEmpty()) {
            listener.onError("User not logged in");
            return;
        }

        db.collection(CONTACTS_COLLECTION)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    List<Contact> contacts = new ArrayList<>();
                    if (documentSnapshot.exists()) {
                        List<Map<String, Object>> contactsList = (List<Map<String, Object>>) documentSnapshot.get("contacts");
                        if (contactsList != null) {
                            for (Map<String, Object> contactMap : contactsList) {
                                String name = (String) contactMap.get("name");
                                String phoneNumber = (String) contactMap.get("phoneNumber");
                                String rawInfo = (String) contactMap.get("rawInfo");
                                Boolean isSelected = (Boolean) contactMap.get("isSelected"); // ✅ CRITICAL FIX - Load selected state

                                Contact contact = new Contact(name, phoneNumber);
                                if (rawInfo != null) {
                                    contact.setRawContactInfo(rawInfo);
                                }
                                if (isSelected != null) {
                                    contact.setSelected(isSelected); // ✅ Set selected state
                                }
                                contacts.add(contact);
                            }
                        }
                        Log.d(TAG, "Loaded " + contacts.size() + " contacts from Firestore");
                    }
                    listener.onSuccess(contacts);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load contacts: " + e.getMessage());
                    listener.onError("Failed to load contacts: " + e.getMessage());
                });
    }

    // ========== TRIP METHODS ==========

    public void startTrip(Trip trip, FirebaseCompleteListener listener) {
        Map<String, Object> tripData = new HashMap<>();
        tripData.put("userId", trip.getUserId());
        tripData.put("startLat", trip.getStartLat());
        tripData.put("startLng", trip.getStartLng());
        tripData.put("startTime", trip.getStartTime());
        tripData.put("status", "active");
        tripData.put("createdAt", System.currentTimeMillis());

        db.collection(TRIPS_COLLECTION)
                .add(tripData)
                .addOnSuccessListener(documentReference -> {
                    String tripId = documentReference.getId();
                    listener.onSuccess(tripId);
                })
                .addOnFailureListener(e -> {
                    listener.onError("Failed to start trip: " + e.getMessage());
                });
    }

    public void updateTripStatus(String tripId, String status, FirebaseCompleteListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        if (status.equals("completed")) {
            updates.put("endTime", System.currentTimeMillis());
        }

        db.collection(TRIPS_COLLECTION)
                .document(tripId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    listener.onSuccess("Trip status updated to " + status);
                })
                .addOnFailureListener(e -> {
                    listener.onError("Failed to update trip status: " + e.getMessage());
                });
    }

    // ========== LOCATION METHODS ==========

    public void logLocation(String tripId, double latitude, double longitude, float batteryLevel) {
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("tripId", tripId);
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("batteryLevel", batteryLevel);
        locationData.put("userId", sessionManager.getUserId());

        db.collection(LOCATIONS_COLLECTION)
                .add(locationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Location logged successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to log location: " + e.getMessage());
                });
    }

    public void saveLocationUpdate(Map<String, Object> locationData, FirebaseCompleteListener listener) {
        String userId = sessionManager.getUserId();
        if (userId.isEmpty()) {
            listener.onError("User not logged in");
            return;
        }

        locationData.put("userId", userId);

        db.collection(LOCATIONS_COLLECTION)
                .add(locationData)
                .addOnSuccessListener(documentReference -> {
                    listener.onSuccess("Location saved");
                })
                .addOnFailureListener(e -> {
                    listener.onError("Failed to save location: " + e.getMessage());
                });
    }

    // ========== OTP METHODS ==========

    public void cleanupOTP(String email) {
        db.collection("otps")
                .document(email)
                .delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "OTP cleaned up for: " + email))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to cleanup OTP: " + e.getMessage()));
    }
}