package pixento.nl.broadcasttomqtt;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
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

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Created by Corniël Joosse on 25-Aug-17.
 * Inspired by: https://github.com/eclipse/paho.mqtt.android/blob/master/paho.mqtt.android.example/src/main/java/paho/mqtt/java/example/PahoExampleActivity.java
 */

public class MqttConnection {
    
    private static MqttConnection instance = null;
    private MqttAndroidClient mqttAndroidClient;
    private SharedPreferences prefs;
    private LinkedList<BroadcastMessage> queue = new LinkedList<BroadcastMessage>();
    
    private String serverUri;
    // private final String clientId = "BroadcastToMQTTAndroid";
    private final String clientId = "BroadcastToMQTTAndroid";
    private String publishTopic = "android/broadcast";
    private static final String TAG = "MqttConnection";
    
    public static MqttConnection getInstance(Context context) {
        if (instance == null) {
            instance = new MqttConnection(context);
        }
        return instance;
    }
    
    private MqttConnection(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("pref_host", "");
        String port = prefs.getString("pref_port", "1883");
        serverUri = "tcp://" + server + ":" + port;
    
        this.enqueue("pixento.nl.broadcasttomqtt.START");
        
        
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
    
    /**
     * Connect to the configured MQTT server
     */
    public void connect() {
        String username = prefs.getString("pref_username", "");
        String password = prefs.getString("pref_password", "");
        
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
                    // subscribeToTopic();
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
    
    public boolean isConnected() {
        return mqttAndroidClient != null && mqttAndroidClient.isConnected();
    }
    
    public boolean isInstantiated() {
        return mqttAndroidClient != null;
    }
    
    
    public void enqueue(String message) {
        queue.add(new BroadcastMessage(message));
        this.publishAll();
    }
    
    public void disconnect() {
        try {
            mqttAndroidClient.disconnect();
            Log.i(TAG, "Disconnected MQTT client");
        } catch (MqttException e) {
            Log.i(TAG, "Error disconnecting: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void publishAll() {
        // Wait for instantiation
        if(!this.isInstantiated()) {
            return;
        }
        
        // Connect if disconnected, and return, this function will be called again after connection.
        if(!this.isConnected() && this.isInstantiated()) {
            this.connect();
            return;
        }
        
        // Walk through the queue and publish all messages
        ListIterator<BroadcastMessage> queueIterator = queue.listIterator();
        while(queueIterator.hasNext()) {
            if(this.publish(queueIterator.next())) {
                queueIterator.remove();
            }
        }
        
        // Disconnect after sending the queue
        this.disconnect();
    }
    
    private boolean publish(BroadcastMessage message) {
        try {
            // Create the MQTT message, and publish!
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(message.event.getBytes());
            mqttMessage.setQos(1);
            
            mqttAndroidClient.publish(publishTopic, mqttMessage);
                        
            // Some nice logging, yeah!
            Log.i(TAG, "Message Published: "+ message.toString());
            if (!this.isConnected()) {
                Log.i(TAG, mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
            
            // Return true on success!
            return true;
        } catch (MqttException e) {
            // Oepsidaisy
            Log.i(TAG, "Error Publishing: " + e.getMessage());
            e.printStackTrace();
            
            return false;
        }
    }
    
    
    private class BroadcastMessage {
        String event;
        String payload;  // Not used for now, to add broadcast extra's in future
        
        
        BroadcastMessage(String event, String payload) {
            this.event = event;
            this.payload = payload;
        }
        
        BroadcastMessage(String event) {
            this.event = event;
            this.payload = "";
        }
        
        @Override
        public String toString() {
            return "<" + event + ", " + payload + ">";
        }
    }
}
