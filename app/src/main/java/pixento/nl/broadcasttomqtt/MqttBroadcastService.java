package pixento.nl.broadcasttomqtt;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.HashSet;

/**
 * The MqttBroadcastService runs in the background and makes sure that the dynamically registered
 * broadcast receiver will keep working even if the app is closed, and that messages still will
 * be forwarded to MQTT.
 */
public class MqttBroadcastService extends Service {

    private BroadcastReceiver broadcastReceiver;
    private SharedPreferences prefs;
    private OnSharedPreferenceChangeListener changeListener;

    private static final String TAG = "MqttBroadcastService";

    public MqttBroadcastService() { }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Get the broadcasts from prefs and update the filter
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        changeListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if(key.equals(MainActivity.bcPrefsKey)) {
                    MqttBroadcastService.this.updateIntentFilter(sharedPreferences);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(changeListener);
        this.updateIntentFilter(prefs);

        Log.v(TAG, "Started MqttBroadcastService");
    }

    private void updateIntentFilter(SharedPreferences prefs) {
        Log.v(TAG, "Updating the broadcast receiver's filter");

        BroadcastItemList bcItems = new BroadcastItemList(
                prefs.getStringSet(MainActivity.bcPrefsKey, new HashSet<String>())
        );

        // Create the intent filters from the set of bc's from the prefs
        IntentFilter filter = new IntentFilter();
        for (BroadcastItem item : bcItems) {
            filter.addAction(item.action);
        }

        // Unregister the filter if it is registered
        if(broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }

        // Register the BroadcastReceiver
        broadcastReceiver = new SubBroadcastReceiver();
        registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove the prefs change listener
        prefs.unregisterOnSharedPreferenceChangeListener(changeListener);

        // Make sure to unregister the receiver if the service stops
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
}
