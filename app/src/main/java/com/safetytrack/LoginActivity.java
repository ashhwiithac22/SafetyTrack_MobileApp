package com.safetytrack;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.safetytrack.utils.FirebaseHelper;
import com.safetytrack.utils.SessionManager;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText etPhone, etPassword;
    private Button btnLogin;
    private TextView tvRegisterLink, tvForgotPassword;
    private ProgressBar progressBar;
    private FirebaseHelper firebaseHelper;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // CRITICAL: Set content view FIRST
            setContentView(R.layout.activity_login);
            Log.d(TAG, "Layout inflated successfully");

            // Initialize SessionManager
            sessionManager = SessionManager.getInstance(this);

            // Initialize FirebaseHelper
            firebaseHelper = new FirebaseHelper(this);

            // Initialize views
            initializeViews();

            // Setup listeners
            setupListeners();

            // Check login state AFTER everything is initialized
            checkLoginState();

        } catch (Exception e) {
            Log.e(TAG, "FATAL ERROR: " + e.getMessage(), e);
            Toast.makeText(this, "App failed to start: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeViews() {
        try {
            etPhone = findViewById(R.id.etPhone);
            etPassword = findViewById(R.id.etPassword);
            btnLogin = findViewById(R.id.btnLogin);
            tvRegisterLink = findViewById(R.id.tvRegisterLink);
            tvForgotPassword = findViewById(R.id.tvForgotPassword);
            progressBar = findViewById(R.id.progressBar);

            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }

            Log.d(TAG, "Views initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage());
        }
    }

    private void setupListeners() {
        try {
            if (btnLogin != null) {
                btnLogin.setOnClickListener(v -> {
                    if (isNetworkAvailable()) {
                        loginUser();
                    } else {
                        Toast.makeText(LoginActivity.this, "No internet connection. Please check your network.", Toast.LENGTH_LONG).show();
                    }
                });
            }

            if (tvRegisterLink != null) {
                tvRegisterLink.setOnClickListener(v -> {
                    startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
                });
            }

            if (tvForgotPassword != null) {
                tvForgotPassword.setOnClickListener(v -> {
                    startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
                });
            }

            Log.d(TAG, "Listeners setup successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up listeners: " + e.getMessage());
        }
    }

    private void checkLoginState() {
        try {
            if (sessionManager != null && sessionManager.isLoggedIn()) {
                Log.d(TAG, "User already logged in, navigating to Dashboard");
                Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking login state: " + e.getMessage());
        }
    }

    private void loginUser() {
        // Validate inputs
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Phone number is required");
            etPhone.requestFocus();
            return;
        }

        if (phone.length() < 10) {
            etPhone.setError("Please enter a valid 10-digit phone number");
            etPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        // Show progress
        showProgress(true);

        // Check if FirebaseHelper is initialized
        if (firebaseHelper == null) {
            showProgress(false);
            Toast.makeText(this, "Firebase not initialized. Please restart app.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            firebaseHelper.loginUser(phone, password, new FirebaseHelper.FirebaseAuthListener() {
                @Override
                public void onSuccess(String userId) {
                    runOnUiThread(() -> {
                        showProgress(false);
                        Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();

                        // Navigate to Dashboard
                        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        showProgress(false);

                        // User-friendly error messages
                        String errorMessage;
                        if (error.contains("network") || error.contains("INTERNET")) {
                            errorMessage = "Network error. Please check your internet connection.";
                        } else if (error.contains("not registered")) {
                            errorMessage = "Phone number not registered. Please sign up first.";
                        } else if (error.contains("Invalid password")) {
                            errorMessage = "Incorrect password. Please try again.";
                        } else {
                            errorMessage = "Login failed: " + error;
                        }

                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onShowProgress() {
                    runOnUiThread(() -> showProgress(true));
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Login exception: " + e.getMessage());
            showProgress(false);
            Toast.makeText(this, "Login error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showProgress(boolean show) {
        try {
            if (progressBar != null) {
                progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
            if (btnLogin != null) {
                btnLogin.setEnabled(!show);
                btnLogin.setAlpha(show ? 0.5f : 1.0f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing progress: " + e.getMessage());
        }
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Network check error: " + e.getMessage());
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reset UI state
        showProgress(false);
        if (etPhone != null) etPhone.setText("");
        if (etPassword != null) etPassword.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up references
        firebaseHelper = null;
    }
}