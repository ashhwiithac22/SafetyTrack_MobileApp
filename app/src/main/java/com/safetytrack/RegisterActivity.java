package com.safetytrack;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.safetytrack.utils.FirebaseHelper;
import com.safetytrack.utils.SessionManager;

public class RegisterActivity extends AppCompatActivity {
    private EditText etName, etEmail, etPhone, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvLoginLink;
    private CheckBox cbTerms;
    private ProgressBar progressBar;
    private FirebaseHelper firebaseHelper;
    private boolean locationPermissionGranted = false;
    private static final int LOCATION_PERMISSION_REQUEST = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firebaseHelper = new FirebaseHelper(this);
        initializeViews();
        setupListeners();
        setupTextFieldStyles();
    }

    private void initializeViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLoginLink = findViewById(R.id.tvLoginLink);
        cbTerms = findViewById(R.id.cbTerms);
        progressBar = findViewById(R.id.progressBar);

        // Hide progress bar initially
        progressBar.setVisibility(View.GONE);
    }

    private void setupTextFieldStyles() {
        // Set text colors for all EditText fields
        int textColor = getResources().getColor(R.color.text_field_text);
        int hintColor = getResources().getColor(R.color.text_field_hint);

        etName.setTextColor(textColor);
        etName.setHintTextColor(hintColor);

        etEmail.setTextColor(textColor);
        etEmail.setHintTextColor(hintColor);

        etPhone.setTextColor(textColor);
        etPhone.setHintTextColor(hintColor);

        etPassword.setTextColor(textColor);
        etPassword.setHintTextColor(hintColor);

        etConfirmPassword.setTextColor(textColor);
        etConfirmPassword.setHintTextColor(hintColor);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> registerUser());

        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        View backButton = findViewById(R.id.btnBack);
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        }
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(name)) {
            etName.setError("Enter your name");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Enter email address");
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter valid email address");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Enter phone number");
            etPhone.requestFocus();
            return;
        }

        if (phone.length() < 10) {
            etPhone.setError("Enter valid phone number (10 digits)");
            etPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Enter password");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Please accept terms & conditions", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading
        progressBar.setVisibility(View.VISIBLE);
        btnRegister.setEnabled(false);

        // Show location permission dialog
        showLocationPermissionDialog(name, email, phone, password);
    }

    private void showLocationPermissionDialog(String name, String email, String phone, String password) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Location Permission");
        builder.setMessage("Allow SafetyTrack to access your location for better safety features and journey tracking?");

        builder.setPositiveButton("Allow", (dialog, which) -> {
            locationPermissionGranted = true;
            requestLocationPermission(name, email, phone, password);
        });

        builder.setNegativeButton("Deny", (dialog, which) -> {
            locationPermissionGranted = false;
            proceedWithRegistration(name, email, phone, password);
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void requestLocationPermission(String name, String email, String phone, String password) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            proceedWithRegistration(name, email, phone, password);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);

            // Store data temporarily
            SharedPreferences prefs = getSharedPreferences("TempReg", MODE_PRIVATE);
            prefs.edit()
                    .putString("name", name)
                    .putString("email", email)
                    .putString("phone", phone)
                    .putString("password", password)
                    .apply();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true;
            } else {
                locationPermissionGranted = false;
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }

            // Retrieve temporary data
            SharedPreferences prefs = getSharedPreferences("TempReg", MODE_PRIVATE);
            String name = prefs.getString("name", "");
            String email = prefs.getString("email", "");
            String phone = prefs.getString("phone", "");
            String password = prefs.getString("password", "");

            if (!name.isEmpty() && !email.isEmpty() && !phone.isEmpty() && !password.isEmpty()) {
                proceedWithRegistration(name, email, phone, password);
            }
        }
    }

    private void proceedWithRegistration(String name, String email, String phone, String password) {
        firebaseHelper.registerUser(name, email, phone, password, locationPermissionGranted,
                new FirebaseHelper.FirebaseCompleteListener() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnRegister.setEnabled(true);

                            Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();

                            // Clear temporary data
                            getSharedPreferences("TempReg", MODE_PRIVATE).edit().clear().apply();

                            // Go to Login
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnRegister.setEnabled(true);

                            Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_LONG).show();

                            // Clear temporary data on error
                            getSharedPreferences("TempReg", MODE_PRIVATE).edit().clear().apply();
                        });
                    }
                });
    }
}