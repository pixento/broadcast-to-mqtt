package pixento.nl.broadcasttomqtt;

import java.util.Date;

/**
 * Created by corniel on 30-8-2017.
 */

public class BroadcastItem {
    public String action = "";
    public String alias = "";
    public Date last_executed;
    public int count_executed;
    
    BroadcastItem() {
        
    }
    
    BroadcastItem(String action, String alias) {
        this.action = action;
        this.alias = alias;
    }
}
