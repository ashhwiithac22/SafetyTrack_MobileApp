package com.safetytrack;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class EmailSender {

    public interface EmailCallback {
        void onSuccess();
        void onError(String error);
    }

    private static final String TAG = "EmailSender";
    private Context context;

    public EmailSender(Context context) {
        this.context = context;
    }

    // SIMULATED EMAIL SENDING - WORKS IMMEDIATELY
    public void sendOTPEmail(String email, String otp, EmailCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            Log.d(TAG, "📧 SIMULATED: Email sent to " + email);
            Log.d(TAG, "🔐 SIMULATED OTP: " + otp);

            // Show OTP in Toast for demo purposes
            Toast.makeText(context, "📧 DEMO MODE - OTP: " + otp, Toast.LENGTH_LONG).show();

            if (callback != null) {
                callback.onSuccess();
            }
        }, 1500);
    }

    // SIMULATED OTP VERIFICATION
    public void verifyOTP(String email, String otp, EmailCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            Log.d(TAG, "✅ SIMULATED: OTP verified for " + email);

            if (callback != null) {
                callback.onSuccess();
            }
        }, 1000);
    }

    // SIMULATED OTP STORAGE
    public void storeOTP(String email, String otp, EmailCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            Log.d(TAG, "💾 SIMULATED: OTP stored for " + email);

            if (callback != null) {
                callback.onSuccess();
            }
        }, 500);
    }
}