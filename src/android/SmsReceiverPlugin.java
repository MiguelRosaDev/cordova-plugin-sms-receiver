package ratson.cordova.sms_receiver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.tasks.Task;

public class SmsReceiverPlugin extends CordovaPlugin {
    private static final String ACTION_REQUEST_PERMISSION = "requestPermission";
    public final String ACTION_HAS_SMS_POSSIBILITY = "hasSMSPossibility";
    public final String ACTION_RECEIVE_SMS = "startReception";
    public final String ACTION_STOP_RECEIVE_SMS = "stopReception";
    private CallbackContext callbackReceive;
    private SmsReceiver smsReceiver = null;
    private boolean isReceiving = false;
    private int requestCode = 20160916;
    private static final int SMS_CONSENT_REQUEST = 2;

    public SmsReceiverPlugin() {
        super();
    }

    @Override
    public boolean execute(String action, JSONArray arg1,
                           final CallbackContext callbackContext) throws JSONException {

        if (ACTION_HAS_SMS_POSSIBILITY.equals(action)) {
            hasSmsPossibility(callbackContext);
            return true;
        } else if (ACTION_RECEIVE_SMS.equals(action)) {
            receiveSms(callbackContext);
            return true;
        } else if (ACTION_STOP_RECEIVE_SMS.equals(action)) {
            stopReceiveSms(callbackContext);
            return true;
        } else if (ACTION_REQUEST_PERMISSION.equals(action)) {
            requestPermission(Manifest.permission.RECEIVE_SMS, callbackContext);
            return true;
        }

        return false;
    }

    private void stopReceiveSms(CallbackContext callbackContext) {
        if (this.smsReceiver != null) {
            smsReceiver.stopReceiving();
        }

        this.isReceiving = false;

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(pluginResult);
    }

    private void receiveSms(CallbackContext callbackContext) {
        this.callbackReceive = callbackContext;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startSmsUserConsent();
        } else {
            startLegacySmsReceiver();
        }
    }

    private void startLegacySmsReceiver() {
        if (this.isReceiving) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(false);
            this.callbackReceive.sendPluginResult(pluginResult);
        }

        this.isReceiving = true;

        if (this.smsReceiver == null) {
            this.smsReceiver = new SmsReceiver();
            IntentFilter fp = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
            fp.setPriority(1000);
            this.cordova.getActivity().registerReceiver(this.smsReceiver, fp);
        }

        this.smsReceiver.startReceiving(this.callbackReceive);

        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.callbackReceive.sendPluginResult(pluginResult);
    }

    private void startSmsUserConsent() {
        SmsRetrieverClient client = SmsRetriever.getClient(cordova.getActivity());
        Task<Void> task = client.startSmsUserConsent(null);
        
        task.addOnSuccessListener(aVoid -> {
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackReceive.sendPluginResult(result);
        });
        
        task.addOnFailureListener(e -> {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Failed to start SMS user consent");
            callbackReceive.sendPluginResult(result);
        });
    }

    private void hasSmsPossibility(CallbackContext callbackContext) {
        Activity ctx = this.cordova.getActivity();
        if (ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
        } else {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, false));
        }
    }

    private boolean hasPermissionGranted(String type) {
        return this.cordova.hasPermission(type);
    }

    private void requestPermission(String type, CallbackContext callbackContext) {
        if (!hasPermissionGranted(type)) {
            this.cordova.requestPermission(this, requestCode, type);
        }
        callbackContext.success();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SMS_CONSENT_REQUEST) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
                JSONObject jsonObj = new JSONObject();
                try {
                    jsonObj.put("messageBody", message);
                    PluginResult result = new PluginResult(PluginResult.Status.OK, jsonObj);
                    result.setKeepCallback(true);
                    callbackReceive.sendPluginResult(result);
                } catch (JSONException e) {
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Error parsing SMS message");
                    callbackReceive.sendPluginResult(result);
                }
            } else {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, "User denied SMS consent");
                callbackReceive.sendPluginResult(result);
            }
        }
    }
}

