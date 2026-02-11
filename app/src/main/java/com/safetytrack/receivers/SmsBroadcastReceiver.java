package com.safetytrack.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

public class SmsBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String phone = intent.getStringExtra("phone");

        if (phone == null) phone = "Unknown";

        if ("com.safetytrack.SMS_SENT".equals(action)) {
            switch (getResultCode()) {
                case android.app.Activity.RESULT_OK:
                    Log.d(TAG, "✅ SMS sent successfully to: " + phone);
                    Toast.makeText(context, "SMS sent to " + phone, Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Log.e(TAG, "❌ SMS send failed (Generic) to: " + phone);
                    Toast.makeText(context, "SMS failed - Generic error", Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    Log.e(TAG, "❌ SMS send failed (No Service) to: " + phone);
                    Toast.makeText(context, "No network service", Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Log.e(TAG, "❌ SMS send failed (Null PDU) to: " + phone);
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Log.e(TAG, "❌ SMS send failed (Radio Off) to: " + phone);
                    Toast.makeText(context, "Airplane mode on", Toast.LENGTH_SHORT).show();
                    break;
            }
        } else if ("com.safetytrack.SMS_DELIVERED".equals(action)) {
            switch (getResultCode()) {
                case android.app.Activity.RESULT_OK:
                    Log.d(TAG, "✅ SMS delivered to: " + phone);
                    Toast.makeText(context, "SMS delivered", Toast.LENGTH_SHORT).show();
                    break;
                case android.app.Activity.RESULT_CANCELED:
                    Log.e(TAG, "❌ SMS delivery failed to: " + phone);
                    Toast.makeText(context, "SMS delivery failed", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }
}