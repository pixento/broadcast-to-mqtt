package pixento.nl.broadcasttomqtt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        // Check if the intent action is correct
        if(intent.getAction().equals("android.intent.action.BOOT_COMPLETED") ||
            intent.getAction().equals("android.intent.action.QUICKBOOT_POWERON") ||
            intent.getAction().equals("com.htc.intent.action.QUICKBOOT_POWERON")) {
            
            // Get the preferences needed
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean start_at_boot = prefs.getBoolean("pref_start_at_boot", false);
            
            Log.i(TAG, "BOOT_COMPLETED intent received, starting MQTT receiver: " + start_at_boot);
            
            if (start_at_boot) {
                // Start the service which registers the broadcast receiver
                MqttBroadcastService.startService(context, null);
            }
        }
    }
}
