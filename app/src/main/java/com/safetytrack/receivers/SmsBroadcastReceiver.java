//smsreceiver
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
        String type = intent.getStringExtra("type");

        if (phone == null) phone = "Unknown";
        if (type == null) type = "SMS";

        if ("com.safetytrack.SMS_SENT".equals(action)) {
            switch (getResultCode()) {
                case android.app.Activity.RESULT_OK:
                    Log.d(TAG, "✅ " + type + " SMS sent successfully to: " + phone);
                    if (type.equals("SOS")) {
                        Toast.makeText(context, "🚨 SOS sent to " + phone, Toast.LENGTH_SHORT).show();
                    } else if (type.equals("SAFE_ARRIVAL")) {
                        Toast.makeText(context, "🛡️ Safe arrival sent to " + phone, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "📍 SMS sent to " + phone, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Log.e(TAG, "❌ " + type + " SMS send failed (Generic) to: " + phone);
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    Log.e(TAG, "❌ " + type + " SMS send failed (No Service) to: " + phone);
                    Toast.makeText(context, "No network service", Toast.LENGTH_SHORT).show();
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Log.e(TAG, "❌ " + type + " SMS send failed (Null PDU) to: " + phone);
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Log.e(TAG, "❌ " + type + " SMS send failed (Radio Off) to: " + phone);
                    Toast.makeText(context, "Airplane mode on", Toast.LENGTH_SHORT).show();
                    break;
            }
        } else if ("com.safetytrack.SMS_DELIVERED".equals(action)) {
            switch (getResultCode()) {
                case android.app.Activity.RESULT_OK:
                    Log.d(TAG, "✅ " + type + " SMS delivered to: " + phone);
                    break;
                case android.app.Activity.RESULT_CANCELED:
                    Log.e(TAG, "❌ " + type + " SMS delivery failed to: " + phone);
                    break;
            }
        }
    }
}