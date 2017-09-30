package pixento.nl.broadcasttomqtt;

import java.util.Date;

class BroadcastItem {
    /**
     * The action of the broadcast to filter for
     */
    public String action = "";

    /**
     * A human readable alias
     */
    public String alias = "";

    /**
     * Date of the last MQTT message with this broadcast. Used for the rate limit.
     */
    public Date last_executed;

    /**
     * Indicates the number of MQTT messages sent with this action
     */
    public int count_executed = 0;
    
    /**
     * Whether the filter is enabled
     */
    public boolean enabled = true;
    
    /**
     * Defines the rate limit for the current broadcast. The number indicates the minimum time
     * in seconds between MQTT messages. So 60s is once each minute.
     */
    public int rate_limit = 60;
    
    BroadcastItem() {
        
    }

    BroadcastItem(String action, String alias) {
        this(action, alias, 60);
    }

    BroadcastItem(String action, String alias, int rate_limit) {
        this.action = action;
        this.alias = alias;
        this.rate_limit = rate_limit;
    }
}
