//SessionManager.java
package com.safetytrack.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.safetytrack.LoginActivity;

public class SessionManager {
    private static final String PREF_NAME = "SafetyTrackSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_USER_PHONE = "userPhone";
    private static final String KEY_SESSION_TOKEN = "sessionToken";
    private static final String KEY_LAST_LOGIN = "lastLogin";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private Context context;
    private static SessionManager instance;

    private SessionManager(Context context) {
        this.context = context;
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    // Create login session
    public void createLoginSession(String userId, String name, String email, String phone) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_PHONE, phone);
        editor.putString(KEY_SESSION_TOKEN, generateSessionToken());
        editor.putLong(KEY_LAST_LOGIN, System.currentTimeMillis());
        editor.apply();
    }

    // Check login status
    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // Get user details
    public String getUserId() {
        return pref.getString(KEY_USER_ID, "");
    }

    public String getUserName() {
        return pref.getString(KEY_USER_NAME, "User");
    }

    public String getUserEmail() {
        return pref.getString(KEY_USER_EMAIL, "");
    }

    public String getUserPhone() {
        return pref.getString(KEY_USER_PHONE, "");
    }

    // Update user details
    public void updateUserName(String name) {
        editor.putString(KEY_USER_NAME, name);
        editor.apply();
    }

    public void updateUserEmail(String email) {
        editor.putString(KEY_USER_EMAIL, email);
        editor.apply();
    }

    public void updateUserPhone(String phone) {
        editor.putString(KEY_USER_PHONE, phone);
        editor.apply();
    }

    // Logout - Clear session and navigate to Login
    public void logout() {
        editor.clear();
        editor.apply();

        // Navigate to Login with clear task
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    // Clear all session data without navigation
    public void clearSession() {
        editor.clear();
        editor.apply();
    }

    // Generate unique session token
    private String generateSessionToken() {
        return "session_" + System.currentTimeMillis() + "_" + Math.random();
    }

    // Get session token
    public String getSessionToken() {
        return pref.getString(KEY_SESSION_TOKEN, "");
    }

    // Get last login time
    public long getLastLogin() {
        return pref.getLong(KEY_LAST_LOGIN, 0);
    }
}