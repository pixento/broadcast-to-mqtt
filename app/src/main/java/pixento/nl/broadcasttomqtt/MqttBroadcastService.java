package pixento.nl.broadcasttomqtt;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

/**
 * The MqttBroadcastService runs in the background and makes sure that the dynamically registered
 * broadcast receiver will keep working even if the app is closed, and that messages still will
 * be forwarded to MQTT.
 */
public class MqttBroadcastService extends Service {

    private BroadcastReceiver broadcastReceiver;

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

        // Create the intent filters
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.sonyericsson.alarm.ALARM_ALERT");
        filter.addAction("com.android.deskclock.ALARM_ALERT");
        filter.addAction("com.android.alarmclock.ALARM_ALERT");

        // Register the BroadcastReceiver
        broadcastReceiver = new SubBroadcastReceiver();
        registerReceiver(broadcastReceiver, filter);

        Log.v(TAG, "Started MqttBroadcastService");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

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
