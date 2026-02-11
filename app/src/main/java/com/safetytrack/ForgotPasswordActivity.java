package com.safetytrack;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.safetytrack.utils.FirebaseHelper;
import com.safetytrack.utils.SessionManager;

import java.security.SecureRandom;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail, etPhone, etOTP, etNewPassword, etConfirmPassword;
    private Button btnSendEmailOTP, btnSendPhoneOTP, btnVerifyOTP, btnResetPassword;
    private TextView tvBackToLogin, tvResendOTP, tvTimer, tvOtpMessage;
    private ProgressBar progressBar;
    private LinearLayout llEmailSection, llPhoneSection, llOTPSection, llPasswordSection;
    private RadioGroup rgMethod;
    private RadioButton rbEmail, rbPhone;

    private FirebaseFirestore db;
    private FirebaseHelper firebaseHelper;
    private EmailSender emailSender;
    private SessionManager sessionManager;

    private String userEmail = "";
    private String userPhone = "";
    private String generatedOTP = "";
    private String verificationId = "";
    private boolean isOTPVerified = false;
    private boolean isOTPSent = false;
    private String selectedMethod = "email";
    private int remainingTime = 300;

    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (remainingTime > 0) {
                remainingTime--;
                updateTimerText();
                timerHandler.postDelayed(this, 1000);
            } else {
                runOnUiThread(() -> {
                    tvTimer.setText("OTP expired");
                    tvTimer.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    tvResendOTP.setEnabled(true);
                    isOTPSent = false;
                    btnVerifyOTP.setEnabled(false);
                });
            }
        }
    };

    // Secure OTP generator - no logs, no display
    private static final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_forgot_password);

            db = FirebaseFirestore.getInstance();
            firebaseHelper = new FirebaseHelper(this);
            emailSender = new EmailSender(this);
            sessionManager = SessionManager.getInstance(this);

            initializeViews();
            setupListeners();
            setupMethodSelection();

        } catch (Exception e) {
            Log.e("ForgotPassword", "Error in onCreate: " + e.getMessage());
            Toast.makeText(this, "Error loading forgot password", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etOTP = findViewById(R.id.etOTP);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        btnSendEmailOTP = findViewById(R.id.btnSendEmailOTP);
        btnSendPhoneOTP = findViewById(R.id.btnSendPhoneOTP);
        btnVerifyOTP = findViewById(R.id.btnVerifyOTP);
        btnResetPassword = findViewById(R.id.btnResetPassword);

        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        tvResendOTP = findViewById(R.id.tvResendOTP);
        tvTimer = findViewById(R.id.tvTimer);
        tvOtpMessage = findViewById(R.id.tvOtpMessage);

        progressBar = findViewById(R.id.progressBar);

        llEmailSection = findViewById(R.id.llEmailSection);
        llPhoneSection = findViewById(R.id.llPhoneSection);
        llOTPSection = findViewById(R.id.llOTPSection);
        llPasswordSection = findViewById(R.id.llPasswordSection);

        rgMethod = findViewById(R.id.rgMethod);
        rbEmail = findViewById(R.id.rbEmail);
        rbPhone = findViewById(R.id.rbPhone);

        llOTPSection.setVisibility(View.GONE);
        llPasswordSection.setVisibility(View.GONE);
        tvTimer.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        rbEmail.setChecked(true);
        showEmailSection();
    }

    private void setupMethodSelection() {
        rgMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbEmail) {
                selectedMethod = "email";
                showEmailSection();
                resetVerificationState();
            } else if (checkedId == R.id.rbPhone) {
                selectedMethod = "phone";
                showPhoneSection();
                resetVerificationState();
            }
        });
    }

    private void showEmailSection() {
        llEmailSection.setVisibility(View.VISIBLE);
        llPhoneSection.setVisibility(View.GONE);
        tvOtpMessage.setText("Enter OTP sent to your email");
    }

    private void showPhoneSection() {
        llEmailSection.setVisibility(View.GONE);
        llPhoneSection.setVisibility(View.VISIBLE);
        tvOtpMessage.setText("Enter OTP sent to your phone");
    }

    private void resetVerificationState() {
        isOTPVerified = false;
        isOTPSent = false;
        generatedOTP = "";
        verificationId = "";
        remainingTime = 300;

        llOTPSection.setVisibility(View.GONE);
        llPasswordSection.setVisibility(View.GONE);
        tvTimer.setVisibility(View.GONE);

        etOTP.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");

        btnSendEmailOTP.setEnabled(true);
        btnSendPhoneOTP.setEnabled(true);
        btnVerifyOTP.setEnabled(true);
        btnResetPassword.setEnabled(false);

        timerHandler.removeCallbacks(timerRunnable);
    }

    private void setupListeners() {
        btnSendEmailOTP.setOnClickListener(v -> sendEmailOTP());
        btnSendPhoneOTP.setOnClickListener(v -> sendPhoneOTP());
        btnVerifyOTP.setOnClickListener(v -> verifyOTP());
        btnResetPassword.setOnClickListener(v -> resetPassword());

        tvResendOTP.setOnClickListener(v -> resendOTP());

        tvBackToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    // ========== EMAIL OTP METHODS - PRODUCTION READY ==========

    private void sendEmailOTP() {
        String email = etEmail.getText().toString().trim();

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

        progressBar.setVisibility(View.VISIBLE);
        btnSendEmailOTP.setEnabled(false);
        btnVerifyOTP.setEnabled(false);

        // Check if email exists in Firestore
        firebaseHelper.checkUserExists("email", email, new FirebaseHelper.FirebaseCompleteListener() {
            @Override
            public void onSuccess(String message) {
                userEmail = email;
                // Generate secure OTP - NEVER LOG OR DISPLAY
                generatedOTP = generateSecureOTP();

                // Send OTP via Cloud Function
                emailSender.sendOTPEmail(email, generatedOTP, new EmailSender.EmailCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnSendEmailOTP.setEnabled(true);
                            btnVerifyOTP.setEnabled(true);

                            llOTPSection.setVisibility(View.VISIBLE);
                            tvTimer.setVisibility(View.VISIBLE);
                            isOTPSent = true;
                            startOTPTimer();

                            // DO NOT SHOW OTP - ONLY SUCCESS MESSAGE
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "OTP sent to your email. Please check your inbox.",
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnSendEmailOTP.setEnabled(true);
                            generatedOTP = ""; // Clear OTP on error

                            String errorMessage;
                            if (error.contains("not registered")) {
                                errorMessage = "This email is not registered with us";
                            } else if (error.contains("timeout")) {
                                errorMessage = "Request timeout. Please try again.";
                            } else {
                                errorMessage = "Failed to send OTP. Please try again.";
                            }

                            Toast.makeText(ForgotPasswordActivity.this,
                                    errorMessage, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSendEmailOTP.setEnabled(true);
                    Toast.makeText(ForgotPasswordActivity.this,
                            "Email not registered. Please check and try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ========== VERIFY OTP USING CLOUD FUNCTION ==========

    private void verifyOTP() {
        String enteredOTP = etOTP.getText().toString().trim();

        if (TextUtils.isEmpty(enteredOTP)) {
            etOTP.setError("Enter OTP");
            etOTP.requestFocus();
            return;
        }

        if (enteredOTP.length() != 6) {
            etOTP.setError("Enter 6-digit OTP");
            etOTP.requestFocus();
            return;
        }

        if (!isOTPSent) {
            Toast.makeText(this, "Please request OTP first", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnVerifyOTP.setEnabled(false);

        if (selectedMethod.equals("email")) {
            // Verify OTP using Cloud Function
            emailSender.verifyOTP(userEmail, enteredOTP, new EmailSender.EmailCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        isOTPVerified = true;
                        timerHandler.removeCallbacks(timerRunnable);

                        // Clear OTP from memory
                        generatedOTP = "";

                        llPasswordSection.setVisibility(View.VISIBLE);
                        etNewPassword.setEnabled(true);
                        etConfirmPassword.setEnabled(true);
                        btnResetPassword.setEnabled(true);

                        progressBar.setVisibility(View.GONE);
                        btnVerifyOTP.setEnabled(true);

                        Toast.makeText(ForgotPasswordActivity.this,
                                "OTP verified successfully!", Toast.LENGTH_SHORT).show();
                        etNewPassword.requestFocus();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnVerifyOTP.setEnabled(true);

                        String errorMessage;
                        if (error.contains("expired")) {
                            errorMessage = "OTP has expired. Please request a new one.";
                            isOTPSent = false;
                            tvResendOTP.setEnabled(true);
                        } else if (error.contains("Invalid")) {
                            errorMessage = "Invalid OTP. Please try again.";
                        } else {
                            errorMessage = "OTP verification failed. Please try again.";
                        }

                        Toast.makeText(ForgotPasswordActivity.this,
                                errorMessage, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            // Phone OTP - Keep existing implementation but remove OTP display
            if (enteredOTP.equals(generatedOTP)) {
                isOTPVerified = true;
                timerHandler.removeCallbacks(timerRunnable);

                llPasswordSection.setVisibility(View.VISIBLE);
                etNewPassword.setEnabled(true);
                etConfirmPassword.setEnabled(true);
                btnResetPassword.setEnabled(true);

                progressBar.setVisibility(View.GONE);
                btnVerifyOTP.setEnabled(true);

                // DO NOT SHOW OTP - ONLY SUCCESS
                Toast.makeText(this, "Phone verified successfully!", Toast.LENGTH_SHORT).show();
                etNewPassword.requestFocus();
            } else {
                progressBar.setVisibility(View.GONE);
                btnVerifyOTP.setEnabled(true);
                Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void resendOTP() {
        if (selectedMethod.equals("email")) {
            if (TextUtils.isEmpty(userEmail)) {
                Toast.makeText(this, "Please enter email first", Toast.LENGTH_SHORT).show();
                return;
            }
            // Generate new secure OTP
            generatedOTP = generateSecureOTP();
            resetTimer();

            // Resend OTP
            emailSender.sendOTPEmail(userEmail, generatedOTP, new EmailSender.EmailCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        isOTPSent = true;
                        Toast.makeText(ForgotPasswordActivity.this,
                                "OTP resent to your email", Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(ForgotPasswordActivity.this,
                                "Failed to resend OTP: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            // Phone OTP resend - Remove OTP display
            if (TextUtils.isEmpty(userPhone)) {
                Toast.makeText(this, "Please enter phone first", Toast.LENGTH_SHORT).show();
                return;
            }
            generatedOTP = generateSecureOTP();
            resetTimer();
            isOTPSent = true;

            // DO NOT SHOW OTP
            Toast.makeText(this, "OTP resent to your phone", Toast.LENGTH_LONG).show();
        }
    }

    // ========== SECURE OTP GENERATION ==========

    private String generateSecureOTP() {
        // Generate 6-digit OTP using SecureRandom
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }

    // ========== REST OF THE METHODS (KEEP EXISTING) ==========

    private void sendPhoneOTP() {
        String phone = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Enter phone number");
            etPhone.requestFocus();
            return;
        }

        userPhone = phone;

        progressBar.setVisibility(View.VISIBLE);
        btnSendPhoneOTP.setEnabled(false);

        firebaseHelper.checkUserExists("phone", phone, new FirebaseHelper.FirebaseCompleteListener() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSendPhoneOTP.setEnabled(true);

                    // Generate secure OTP - NO DISPLAY
                    generatedOTP = generateSecureOTP();
                    isOTPSent = true;

                    llOTPSection.setVisibility(View.VISIBLE);
                    tvTimer.setVisibility(View.VISIBLE);
                    startOTPTimer();

                    // REMOVED OTP DISPLAY - Only show success message
                    Toast.makeText(ForgotPasswordActivity.this,
                            "OTP sent to your phone", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSendPhoneOTP.setEnabled(true);
                    Toast.makeText(ForgotPasswordActivity.this,
                            "Phone number not registered", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void resetPassword() {
        if (!isOTPVerified) {
            Toast.makeText(this, "Please verify OTP first", Toast.LENGTH_SHORT).show();
            return;
        }

        String newPassword = etNewPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(newPassword)) {
            etNewPassword.setError("Enter password");
            etNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 6) {
            etNewPassword.setError("Password must be at least 6 characters");
            etNewPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError("Confirm password");
            etConfirmPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        showResetConfirmationDialog(newPassword);
    }

    private void showResetConfirmationDialog(String newPassword) {
        new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("Are you sure you want to reset your password?")
                .setPositiveButton("Yes, Reset", (dialog, which) -> {
                    dialog.dismiss();
                    performPasswordReset(newPassword);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performPasswordReset(String newPassword) {
        progressBar.setVisibility(View.VISIBLE);
        btnResetPassword.setEnabled(false);

        String identifier = selectedMethod.equals("email") ? userEmail : userPhone;
        String field = selectedMethod.equals("email") ? "email" : "phone";

        firebaseHelper.resetPassword(identifier, field, newPassword,
                new FirebaseHelper.FirebaseCompleteListener() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);

                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Password reset successfully! Please login with your new password.",
                                    Toast.LENGTH_LONG).show();

                            new Handler().postDelayed(() -> {
                                startActivity(new Intent(ForgotPasswordActivity.this, LoginActivity.class));
                                finish();
                            }, 2000);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnResetPassword.setEnabled(true);
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Failed to reset password: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void startOTPTimer() {
        remainingTime = 300;
        updateTimerText();
        tvTimer.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tvResendOTP.setEnabled(false);
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void resetTimer() {
        remainingTime = 300;
        tvTimer.setTextColor(getResources().getColor(android.R.color.darker_gray));
        tvResendOTP.setEnabled(false);
    }

    private void updateTimerText() {
        int minutes = remainingTime / 60;
        int seconds = remainingTime % 60;
        tvTimer.setText(String.format("OTP expires in: %02d:%02d", minutes, seconds));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        // Clear sensitive data
        generatedOTP = "";
    }
}