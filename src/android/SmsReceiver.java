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
import com.google.android.gms.common.api.Status;

public class SmsReceiver extends BroadcastReceiver {
    public static final String SMS_EXTRA_NAME = "pdus";
    private CallbackContext callbackReceive;
    private boolean isReceiving = true;
    private boolean broadcast = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                if (status.getStatusCode() == CommonStatusCodes.SUCCESS) {
                    Intent consentIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
                    if (consentIntent != null) {
                        try {
                            ((Activity) context).startActivityForResult(consentIntent, SmsReceiverPlugin.SMS_CONSENT_REQUEST);
                        } catch (Exception e) {
                            sendError("Error starting SMS consent intent: " + e.getMessage());
                        }
                    }
                }
            }
        } else {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                Object[] smsExtra = (Object[]) extras.get(SMS_EXTRA_NAME);

                for (int i = 0; i < smsExtra.length; i++) {
                    SmsMessage sms = SmsMessage.createFromPdu((byte[]) smsExtra[i]);
                    if (this.isReceiving && this.callbackReceive != null) {
                        sendResult(sms.getMessageBody(), sms.getOriginatingAddress());
                    }
                }

                if (this.isReceiving && !broadcast) {
                    this.abortBroadcast();
                }
            }
        }
    }

    private void sendResult(String messageBody, String originatingAddress) {
        if (this.callbackReceive != null) {
            JSONObject jsonObj = new JSONObject();
            try {
                jsonObj.put("messageBody", messageBody);
                jsonObj.put("originatingAddress", originatingAddress);
            } catch (Exception e) {
                System.out.println("Error: " + e);
            }
            PluginResult result = new PluginResult(PluginResult.Status.OK, jsonObj);
            result.setKeepCallback(true);
            this.callbackReceive.sendPluginResult(result);
        }
    }

    private void sendError(String errorMessage) {
        if (this.callbackReceive != null) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, errorMessage);
            result.setKeepCallback(true);
            this.callbackReceive.sendPluginResult(result);
        }
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

