package pixento.nl.broadcasttomqtt;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Created by Corniël Joosse on 25-Aug-17.
 * Inspired by: https://github.com/eclipse/paho.mqtt.android/blob/master/paho.mqtt.android.example/src/main/java/paho/mqtt/java/example/PahoExampleActivity.java
 */

public class MqttConnection {

    private static final int RETRY_TIME = 10;
    private static MqttConnection instance = null;
    private MqttAndroidClient mqttAndroidClient;
    private SharedPreferences prefs;
    private LinkedList<MqttQueueItem> queue = new LinkedList<>();

    private String serverUri;
    private static final String TAG = "MqttConnection";
    private final String clientId = "BroadcastToMQTTAndroid";

    private String basePublishTopic = "android/broadcast";
    private String publishTopic;
    private String username;
    private String password;

    static MqttConnection getInstance() {
        if (instance != null) {
            return instance;
        }
        return null;
    }

    static MqttConnection getInstance(Context context) {
        if (instance == null) {
            instance = new MqttConnection(context);
        }
        return instance;
    }

    public static String getDefaultDeviceId() {
        return android.os.Build.MODEL.replaceAll("(\\s+)", "-").toLowerCase();
    }

    private MqttConnection(Context context) {
        // Get the server uri etc from prefs, and enqueue first event
        this.updatePreferences(context);
        this.enqueue("pixento.nl.broadcasttomqtt.START");
        this.setRetryAlarm(context);
        // Create the MQTT client and set the callback class
        mqttAndroidClient = new MqttAndroidClient(context, serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                // Log nice things
                if (reconnect) {
                    Log.i(TAG, "Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    // subscribeToTopic();
                } else {
                    Log.i(TAG, "Connected to: " + serverURI);
                }

                instance.publishAll();
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.i(TAG, "The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.i(TAG, "Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        // Connect on instantiation
        this.connect();
    }

    void updatePreferences(Context context) {
        Log.v(TAG, "Getting the updated preferences");

        if (context != null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context);
        }

        // Get the preferences needed
        String server = prefs.getString("pref_host", "");
        String port = prefs.getString("pref_port", "1883");
        String deviceid = prefs.getString("pref_device_id", MqttConnection.getDefaultDeviceId());

        // Set the topic and server uri
        publishTopic = TextUtils.join("/", new String[]{basePublishTopic, deviceid});
        serverUri = "tcp://" + server + ":" + port;

        // Get the username and password
        username = prefs.getString("pref_username", "");
        password = prefs.getString("pref_password", "");

        // Disconnect, and try to publish all messages, in case the credentials/server settings
        // were wrong
        if (this.isInstantiated()) {
            if (this.isConnected()) {
                this.disconnect();
            }
            this.publishAll();
        }
    }

    /**
     * Schedules a broadcast intent for message queue retrying
     *
     * @param context
     */
    void setRetryAlarm(Context context) {
        Log.v(TAG, "Setting retry alarm in " + RETRY_TIME + "s");

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Create the intent
        Intent intent = new Intent(context, RetryBroadcastReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);

        // Set the alarm (not exact)
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000 * RETRY_TIME, pi);
    }

    /**
     * Connect to the configured MQTT server
     */
    void connect() {
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        //mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setKeepAliveInterval(10);
        mqttConnectOptions.setCleanSession(false);
        try {
            mqttConnectOptions.setUserName(username);
            mqttConnectOptions.setPassword(password.toCharArray());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        try {
            Log.i(TAG, "Connecting to " + serverUri);
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.i(TAG, "Failed to connect to: " + serverUri);
                    exception.printStackTrace();
                }
            });


        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    boolean isConnected() {
        return this.isInstantiated() && mqttAndroidClient.isConnected();
    }

    boolean isInstantiated() {
        return mqttAndroidClient != null;
    }

    boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    void enqueue(JSONObject json) {

        queue.add(new MqttQueueItem(json));
        this.publishAll();
    }

    void enqueue(String message) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("action", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        this.enqueue(payload);
    }

    /**
     * Disconnect the MQTT client if the client is connected.
     */
    void disconnect() {
        if(this.isConnected()) {
            try {
                mqttAndroidClient.disconnect();
                Log.i(TAG, "Disconnected MQTT client");
            } catch (MqttException e) {
                Log.i(TAG, "Error disconnecting: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    void publishAll() {
        // Wait for instantiation
        if (!this.isInstantiated() || queue.isEmpty()) {
            return;
        }

        // Connect if disconnected, and return, this function will be called again after connection.
        if (!this.isConnected() && this.isInstantiated()) {
            this.connect();
        }

        // Walk through the queue and publish all messages that are fresh/not tried-out
        ListIterator<MqttQueueItem> queueIterator = queue.listIterator();
        while (queueIterator.hasNext()) {
            MqttQueueItem item = queueIterator.next();

            Log.v(TAG, item.toString());
            if (!item.isFresh() || this.publish(item)) {
                // Remove messages that have been send or have timed out
                queueIterator.remove();
                Log.v(TAG, "Removed message due to freshness or is published");
            } else if (item.retries-- < 1) {
                // Remove messages that have tried enough
                queueIterator.remove();
                Log.v(TAG, "Removed message due to retries");
            }
        }

        // Disconnect after sending the queue, only if the queue is empty
        if(queue.isEmpty()) {
            this.disconnect();
        }
    }

    private boolean publish(MqttQueueItem message) {
        // Leave message in queue if not connected
        if (!this.isConnected()) {
            return false;
        }

        try {
            // Create the MQTT message, and publish!
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(message.payload.toString().getBytes());
            mqttMessage.setQos(2);

            mqttAndroidClient.publish(publishTopic, mqttMessage);

            // Some nice logging, yeah!
            Log.i(TAG, "Message Published: " + message.toString());

            // Return true on success!
            return true;
        } catch (MqttException e) {
            // Oepsidaisy
            Log.i(TAG, "Error Publishing: " + e.getMessage());
            // e.printStackTrace();

            return false;
        }
    }
}
