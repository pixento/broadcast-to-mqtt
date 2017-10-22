package pixento.nl.broadcasttomqtt;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;

/**
 * Contains the JSONObject of the message and information about freshness
 */
class MqttQueueItem {
    JSONObject payload;
    String topic = "";
    int retries;
    
    private Date timeout;
    
    MqttQueueItem(JSONObject payload, String topic, int retries, int timeout) {
        this.payload = payload;
        this.topic = topic;
        this.retries = retries;
        this.setTimeout(timeout);
    }
    
    MqttQueueItem(JSONObject payload, int retries) {
        this(payload, "", retries, 30);
    }
    
    MqttQueueItem(JSONObject payload, String topic) {
        this(payload, topic, 3, 30);
    }
    
    MqttQueueItem(JSONObject payload) {
        this(payload, "", 3, 30);
    }
    
    void setTimeout(int timeout) {
        // Set the timeout to `timeout` seconds from now
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, timeout);
        
        this.timeout = calendar.getTime();
    }
    
    boolean isFresh() {
        return new Date().before(this.timeout);
    }
    
    @Override
    public String toString() {
        return "MqttQueueItem {retries: " + this.retries +
            ", timeout: " + this.timeout.toString() +
            ", payload: " + this.payload.toString() +
            ", topic: " + this.topic +
            "}";
    }
}

