package ratson.cordova.sms_receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;
import android.os.Build;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.api.CommonStatusCodes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {
    public static final String SMS_EXTRA_NAME = "pdus";
    private CallbackContext callbackReceive;
    private boolean isReceiving = true;

    // This broadcast boolean is used to continue or not the message broadcast
    // to the other BroadcastReceivers waiting for an incoming SMS (like the native SMS app)
    private boolean broadcast = false;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                if (status.getStatusCode() == CommonStatusCodes.SUCCESS) {
                    String message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE);
                    String otpCode = extractOtpFromMessage(message);

                    JSONObject jsonObj = new JSONObject();
                    try {
                        jsonObj.put("otpCode", otpCode);
                        jsonObj.put("messageBody", message);
                    } catch (Exception e) {
                        System.out.println("Error: " + e);
                    }
                    PluginResult result = new PluginResult(PluginResult.Status.OK, jsonObj);
                    result.setKeepCallback(true);
                    callbackReceive.sendPluginResult(result);
                }
            }
        } else {
            //Android 14 and bellow
            // Get the SMS map from Intent
            Bundle extras = intent.getExtras();
            if (extras != null) {
                // Get received SMS Array
                Object[] smsExtra = (Object[]) extras.get(SMS_EXTRA_NAME);

                for (int i = 0; i < smsExtra.length; i++) {
                    SmsMessage sms = SmsMessage.createFromPdu((byte[]) smsExtra[i]);
                    if (this.isReceiving && this.callbackReceive != null) {
                        JSONObject jsonObj = new JSONObject();
                        try {
                            jsonObj.put("messageBody", sms.getMessageBody());
                            jsonObj.put("originatingAddress", sms.getOriginatingAddress());
                        } catch (Exception e) {
                            System.out.println("Error: " + e);
                        }
                        PluginResult result = new PluginResult(PluginResult.Status.OK, jsonObj);
                        result.setKeepCallback(true);
                        callbackReceive.sendPluginResult(result);
                    }
                }

                // If the plugin is active and we don't want to broadcast to other receivers
                if (this.isReceiving && !broadcast) {
                    this.abortBroadcast();
                }
            }
        }
    }

    private String extractOtpFromMessage(String message) {
        // Extraia o código OTP usando regex (personalize conforme necessário)
        Pattern otpPattern = Pattern.compile("\\b\\d{6}\\b"); // Captura 6 dígitos
        Matcher matcher = otpPattern.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    public void broadcast(boolean v) {
        this.broadcast = v;
    }

    public void startReceiving(CallbackContext ctx) {
        this.callbackReceive = ctx;
        this.isReceiving = true;
    }

    public void stopReceiving() {
        this.callbackReceive = null;
        this.isReceiving = false;
    }
}

