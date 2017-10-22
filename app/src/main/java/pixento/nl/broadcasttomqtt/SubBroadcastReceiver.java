package pixento.nl.broadcasttomqtt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static pixento.nl.broadcasttomqtt.MainActivity.bcPrefsKey;

/**
 * The BroadcastReceiver for all broadcasts we're subscribed to.
 * Forwards the broadcast messages using MQTT.
 */
public class SubBroadcastReceiver extends BroadcastReceiver {
    
    private static final String TAG = "SubBroadcastReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        // Get the action
        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        // Get the preferences manager
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        // Get the broadcast items from preference manager
        Set<String> bcItemsSet = prefs.getStringSet(bcPrefsKey, new HashSet<String>());
        BroadcastItemList bcItems = new BroadcastItemList(bcItemsSet);
        
        // Find the broadcast item of the current intent, and get a reference
        BroadcastItem current = bcItems.search(action);
        
        Log.v(TAG, "Received bc: " + action);
    
        // Determine if we should ignore the broadcast due to the rate limit
        if(current.last_executed != null) {
            int seconds_ago = (int) (new Date().getTime() - current.last_executed.getTime()) / 1000;
            Log.v(TAG, "Last bc received: " + seconds_ago + "seconds ago");
            if (seconds_ago < current.rate_limit) {
                // abort, we reached the rate limit
                return;
            }
        }
    
        // Update counter and last executed date
        current.count_executed++;
        current.last_executed = new Date();
        
        // Create a JSON object with all data from the intent
        JSONObject payload = new JSONObject();
        try {
            // Add the action and alias
            payload.put("action", action);
            payload.put("alias", current.alias);
            payload.put("count", current.count_executed);

            // if no extras have been added to the intent, extras = null
            if(extras != null) {
                for (String key : extras.keySet()) {
                    payload.put(key, extras.get(key));
                }
            }
    
            // Create a string from the JSON object
            current.last_payload = payload.toString(3);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        // Save the edited list, and set MqttBroadcastService to not update it's filter
        SharedPreferences.Editor editor = prefs.edit();
        MqttBroadcastService.shouldUpdateFilter = false;
        editor.putStringSet(bcPrefsKey, bcItems.toStringSet());
        editor.commit();
        
        // Get the MqttConnection instance and enqueue the message
        MqttConnection connection = MqttConnection.getInstance(context);
        connection.enqueue(payload, current.topic);

        // Set an retry alarm in case some messages were not sent
        connection.setRetryAlarm(context);
    }
}
