package pixento.nl.broadcasttomqtt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class RetryBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "RetryBroadcastReceiver";

    /**
     * This function is called if a retry broadcast is sent, so we are retrying to send the queue
     * of messages in MqttConnection
     * @param context
     * @param intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "Received intent, retrying all queued messages.");

        // Get the MqttConnection instance
        MqttConnection mqttConnection = MqttConnection.getInstance(context);

        // Try to publish all messages in the queue
        mqttConnection.publishAll();

        // Set another alarm if the queue is not empty
        if(!mqttConnection.isQueueEmpty()) {
            mqttConnection.setRetryAlarm(context);
        }
    }
}
