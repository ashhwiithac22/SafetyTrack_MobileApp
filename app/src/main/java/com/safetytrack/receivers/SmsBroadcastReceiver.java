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

        if ("com.safetytrack.SMS_SENT".equals(action)) {
            switch (getResultCode()) {
                case android.app.Activity.RESULT_OK:
                    Log.d(TAG, "✅ SMS sent successfully");
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Log.e(TAG, "❌ SMS send failed: Generic failure");
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    Log.e(TAG, "❌ SMS send failed: No service");
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Log.e(TAG, "❌ SMS send failed: Null PDU");
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Log.e(TAG, "❌ SMS send failed: Radio off");
                    break;
            }
        } else if ("com.safetytrack.SMS_DELIVERED".equals(action)) {
            switch (getResultCode()) {
                case android.app.Activity.RESULT_OK:
                    Log.d(TAG, "✅ SMS delivered to recipient");
                    break;
                case android.app.Activity.RESULT_CANCELED:
                    Log.e(TAG, "❌ SMS delivery failed");
                    break;
            }
        }
    }
}