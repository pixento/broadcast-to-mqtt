package pixento.nl.broadcasttomqtt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

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
        Log.v(TAG, "Receveid bc: "+ action);

        MqttConnection connection = new MqttConnection(context);

        connection.publish(action);
    }
}
