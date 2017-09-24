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
    
    public ConnectionState connectionState = new ConnectionState();
    
    private static MqttConnection instance = null;
    private MqttAndroidClient mqttAndroidClient;
    private SharedPreferences prefs;
    private LinkedList<MqttQueueItem> queue = new LinkedList<>();
    
    private static final int RETRY_TIME = 20;
    private static final String TAG = "MqttConnection";
    private static final String clientId = "BroadcastToMQTTAndroid";
    private static String basePublishTopic = "android/broadcast";
    
    private boolean keepAlive = false;
    private String serverUri;
    private String publishTopic;
    private String username;
    private String password;
    private boolean reconnect = false;
        
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
    
    static String getDefaultDeviceId() {
        return android.os.Build.MODEL.replaceAll("(\\s+)", "-").toLowerCase();
    }
    
    void setKeepAlive(boolean keepAlive) {
        Log.v(TAG, "Set keep-alive to " + keepAlive);
        this.keepAlive = keepAlive;
        if (keepAlive) {
            this.connect();
        } else {
            this.disconnect();
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
    
    private MqttConnection(Context context) {
        // Get the server uri etc from prefs, and enqueue first event
        this.updatePreferences(context);
        this.enqueue("pixento.nl.broadcasttomqtt.START");
        this.setRetryAlarm(context);
        
        // Create the MQTT client and set the callback class
        this.updateMqttClient(context);
        
        // Connect on instantiation
        this.connect();
    }
    
    void updatePreferences(Context context) {
        Log.v(TAG, "Getting the updated preferences");
        
        // Disconnect the client
        this.disconnect();
        
        // Get the preferences needed
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("pref_host", "").trim();
        String port = prefs.getString("pref_port", "1883").trim();
        String deviceid = prefs.getString("pref_device_id", MqttConnection.getDefaultDeviceId());
        
        // Set the topic and server uri
        publishTopic = TextUtils.join("/", new String[] {basePublishTopic, deviceid});
        serverUri = server.isEmpty() ? "" : "tcp://" + server + ":" + port;
        
        // Get the username and password
        username = prefs.getString("pref_username", "").trim();
        password = prefs.getString("pref_password", "").trim();
        
        // Update the client with the new url etc.
        this.updateMqttClient(context);
        
        // Then reconnect, but wait for the previous client to be disconnected
        this.reconnect = true;
        if (keepAlive && this.connectionState.state == ConnectionState.State.DISCONNECTED) {
            this.connect();
        }
    }
    
    /**
     * Create the MQTT client and set the callback functions
     *
     * @param context
     */
    private void updateMqttClient(Context context) {
        Log.v(TAG, "Updating MQTT client");
        
        mqttAndroidClient = new MqttAndroidClient(context, serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                MqttConnection.this.connectionState.set(ConnectionState.State.CONNECTED);
                Log.i(TAG, "Connected to: " + serverURI);
                
                MqttConnection.this.publishAll();
            }
            
            @Override
            public void connectionLost(Throwable cause) {
                MqttConnection.this.connectionState.set(ConnectionState.State.DISCONNECTED);
                Log.i(TAG, "The Connection was lost.");
                
                if (MqttConnection.this.reconnect) {
                    MqttConnection.this.connect();
                }
            }
            
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Log.i(TAG, "Incoming message: " + new String(message.getPayload()));
            }
            
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                
            }
        });
        
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
        // Reset reconnection flag
        this.reconnect = false;
        
        // Determine connection state and whether we have enough details
        if (serverUri.isEmpty()) {
            MqttConnection.this.connectionState.set(ConnectionState.State.HOST_UNKNOWN);
            return;
        }
        MqttConnection.this.connectionState.set(ConnectionState.State.CONNECTING);
        
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(this.keepAlive);
        mqttConnectOptions.setKeepAliveInterval(10);
        mqttConnectOptions.setConnectionTimeout(10);
        mqttConnectOptions.setCleanSession(false);
        
        if (!username.isEmpty()) {
            try {
                mqttConnectOptions.setUserName(username);
                mqttConnectOptions.setPassword(password.toCharArray());
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        
        try {
            Log.i(TAG, "Connecting to " + mqttAndroidClient.getServerURI());
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
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    Log.i(TAG, "Failed to connect to " + serverUri + ": " + e.getCause());
                    MqttConnection.this.connectionState.set(ConnectionState.State.CONNECTION_ERROR);
                }
            });
            
            
        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Disconnect the MQTT client if the client is connected.
     */
    void disconnect() {
        if (this.isInstantiated()) {
            try {
                // Set state to disconnect even when disconnect throws an exception
                MqttConnection.this.connectionState.set(ConnectionState.State.DISCONNECTED);
                
                // Do the disconnectioning
                mqttAndroidClient.disconnect();
                Log.i(TAG, "Disconnected MQTT client");
            } catch (MqttException | NullPointerException | IllegalArgumentException e) {
                Log.i(TAG, "Error disconnecting: " + e.getMessage());
            }
        }
    }
    
    void enqueue(JSONObject json) {
        Log.v(TAG, "Enqueueing new message: " + json.toString());
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
        
        // Disconnect after sending the queue, only if the queue is empty and keepAlive is false
        if (queue.isEmpty() && !this.keepAlive) {
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
            
            return false;
        }
    }
}
