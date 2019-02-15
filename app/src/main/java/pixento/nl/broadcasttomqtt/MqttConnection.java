package pixento.nl.broadcasttomqtt;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Properties;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Created by Corniël Joosse on 25-Aug-17.
 * Inspired by: https://github.com/eclipse/paho.mqtt.android/blob/master/paho.mqtt.android.example/src/main/java/paho/mqtt/java/example/PahoExampleActivity.java
 */

public class MqttConnection {
    
    ConnectionState connectionState = new ConnectionState();
    static String defaultBaseTopic = "android/broadcast";
    static final int CONNECT_RETRY_TIME = 5;
    
    private static MqttConnection instance = null;
    private MqttAndroidClient mqttAndroidClient;
    private SharedPreferences prefs;
    
    private LinkedList<MqttQueueItem> queue = new LinkedList<>();
    private static final int RETRY_TIME = 20;
    private static final String TAG = "MqttConnection";
    private static String clientId = "BroadcastToMQTTAndroid";
    
    private boolean keepAlive = false;
    private String serverUri;
    private String globalTopic;
    private String username;
    private String password;
    private int messageQOS = 0;
    private boolean reconnect = false;
    private boolean useTLS = false;
    
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
    
    /**
     * @return A client id based on the device name
     */
    static String getDefaultClientId() {
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
        //this.updateMqttClient(context);
        
        // Connect on instantiation
        //this.connect();
    }
    
    /**
     * Get the update preferences and re-init the mqtt client.
     * @param context
     */
    void updatePreferences(final Context context) {
        Log.v(TAG, "Getting the updated preferences. connected: "+ (isConnected()));
        
        // Get the preferences needed
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("pref_host", "").trim();
        String port = prefs.getString("pref_port", "1883").trim();
        clientId = prefs.getString("pref_client_id", MqttConnection.getDefaultClientId()).trim();
        useTLS = prefs.getBoolean("pref_tls", false);
    
        // Set the topic and server uri
        String defaultTopic = TextUtils.join("/", new String[] {defaultBaseTopic, clientId});
        globalTopic = prefs.getString("pref_mqtt_topic", defaultTopic);
        String protocol = useTLS ? "ssl" : "tcp";
        serverUri = server.isEmpty() ? "" : protocol + "://" + server + ":" + port;
        messageQOS = Integer.parseInt(prefs.getString("pref_mqtt_qos", "0"));
        
        // Get the username and password
        username = prefs.getString("pref_username", "").trim();
        password = prefs.getString("pref_password", "").trim();
        
        if (isConnected() || (isInstantiated() && keepAlive)) {
            try {
                // Do the disconnectioning
                mqttAndroidClient.disconnect(100, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        // Set state to disconnect only when really disconnected
                        MqttConnection.this.connectionState.set(ConnectionState.State.DISCONNECTED);
                        Log.i(TAG, "Disconnected MQTT client");
    
                        // Update the client with the new url etc.
                        MqttConnection.this.updateMqttClient(context);
    
                        // Connect again
                        MqttConnection.this.delayedConnect(200);
                    }
    
                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                        Log.i(TAG, "Error disconnecting: " + e.getMessage());
                    }
                });
                
                
            } catch (MqttException | NullPointerException | IllegalArgumentException e) {
                Log.i(TAG, "Error disconnecting: " + e.getMessage());
            }
        } else {
            // Update the client with the new url etc.
            MqttConnection.this.updateMqttClient(context);
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
    
    void delayedConnect(int delayMs) {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Check if still not connected
                if(MqttConnection.this.connectionState.state != ConnectionState.State.CONNECTED) {
                    MqttConnection.this.connect();
                }
            }
        }, delayMs);
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
        mqttConnectOptions.setAutomaticReconnect(false);
        mqttConnectOptions.setKeepAliveInterval(10);
        mqttConnectOptions.setConnectionTimeout(10);
        mqttConnectOptions.setCleanSession(true);
        
        // Setup TLS if enabled in the settings
        if (useTLS) {
            if (!TextUtils.isEmpty(System.getProperty("com.ibm.ssl.protocol"))) {
                // get all com.ibm.ssl properties from the system properties
                // and set them as the SSL properties to use.
                
                Properties sslProperties = new Properties();
                addSystemProperty("com.ibm.ssl.protocol", sslProperties);
                addSystemProperty("com.ibm.ssl.contextProvider", sslProperties);
                addSystemProperty("com.ibm.ssl.keyStore", sslProperties);
                addSystemProperty("com.ibm.ssl.keyStorePassword", sslProperties);
                addSystemProperty("com.ibm.ssl.keyStoreType", sslProperties);
                addSystemProperty("com.ibm.ssl.keyStoreProvider", sslProperties);
                addSystemProperty("com.ibm.ssl.trustStore", sslProperties);
                addSystemProperty("com.ibm.ssl.trustStorePassword", sslProperties);
                addSystemProperty("com.ibm.ssl.trustStoreType", sslProperties);
                addSystemProperty("com.ibm.ssl.trustStoreProvider", sslProperties);
                addSystemProperty("com.ibm.ssl.enabledCipherSuites", sslProperties);
                addSystemProperty("com.ibm.ssl.keyManager", sslProperties);
                addSystemProperty("com.ibm.ssl.trustManager", sslProperties);
                mqttConnectOptions.setSSLProperties(sslProperties);
            } else {
                
                // use standard JSSE available in the runtime and
                // use TLSv1.2 which is the default for a secured mosquitto
                try {
                    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                    sslContext.init(
                        null,
                        new TrustManager[] {getVeryTrustingTrustManager()},
                        new java.security.SecureRandom()
                    );
                    SSLSocketFactory socketFactory = sslContext.getSocketFactory();
                    mqttConnectOptions.setSocketFactory(socketFactory);
                } catch (Exception e) {
                    connectionState.set(ConnectionState.State.CONNECTION_ERROR);
                }
            }
            
        }
        
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
                    
                    // Reconnect in x s if keep-alive is true
                    if(keepAlive) {
                        delayedConnect(CONNECT_RETRY_TIME * 1000);
                    }
                }
            });
            
            
        } catch (MqttException ex) {
            ex.printStackTrace();
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
        }
    }
    
    private Properties addSystemProperty(String key, Properties props) {
        String value = System.getProperty(key);
        if (!TextUtils.isEmpty(value)) {
            props.put(key, value);
        }
        return props;
    }
    
    /**
     * Create a trust manager which is not too concerned about validating certificates.
     *
     * @return a trusting trust manager
     */
    private TrustManager getVeryTrustingTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                
            }
            
            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                
            }
            
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        
    }
    
    /**
     * Disconnect the MQTT client if the client is connected.
     */
    void disconnect() {
        if (this.isInstantiated()) {
            try {
                // Do the disconnectioning, do it also forcibly in case normal disconnect fails
                mqttAndroidClient.disconnect();
                Log.i(TAG, "Disconnected MQTT client");
    
                // Set state to disconnect only when really disconnected
                MqttConnection.this.connectionState.set(ConnectionState.State.DISCONNECTED);
            } catch (MqttException | NullPointerException | IllegalArgumentException e) {
                Log.i(TAG, "Error disconnecting: " + e.getMessage());
                
                // Last chance: forcibly disconnect... :-)
                try {
                    mqttAndroidClient.disconnectForcibly();
                    Log.i(TAG, "Disconnected MQTT client forcibly");
    
                    // Set state to disconnect only when really disconnected
                    MqttConnection.this.connectionState.set(ConnectionState.State.DISCONNECTED);
                } catch (MqttException | NullPointerException | IllegalArgumentException | UnsupportedOperationException e2) {
                    Log.i(TAG, "Error disconnecting forcibly: " + e2.getMessage());
                }
            }
        }
    }
    
    void enqueue(JSONObject json) {
        this.enqueue(json, "");
    }
    
    void enqueue(JSONObject json, String topic) {
        Log.v(TAG, "Enqueueing new message: " + json.toString());
        queue.add(new MqttQueueItem(json, topic));
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
        
        // Connect if disconnected, and return, this function will be called again after connecting.
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
            mqttMessage.setQos(messageQOS);
            
            // Determine the topic and publish the message
            String topic = message.topic.isEmpty() ?
                globalTopic :
                message.topic;
            mqttAndroidClient.publish(topic, mqttMessage);
            
            // Some nice logging, yeah!
            Log.i(TAG, String.format(
                "Message Published (QOS: %d): %s", messageQOS, message.toString()
            ));
            
            // Return true on success!
            return true;
        } catch (MqttException e) {
            // Oepsidaisy
            Log.i(TAG, "Error Publishing: " + e.getMessage());
            
            return false;
        }
    }
}
