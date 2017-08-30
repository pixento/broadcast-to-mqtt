package pixento.nl.broadcasttomqtt;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by corniel on 30-8-2017.
 */

public class BroadcastItemList extends ArrayList<BroadcastItem> {
    
    public BroadcastItemList() {
        
    }
    
    BroadcastItemList(Set<String> jsonItems) {
        Gson gson = new Gson();
        
        for (String jsonItem : jsonItems) {
            try {
                BroadcastItem bcItem = gson.fromJson(jsonItem, BroadcastItem.class);
                this.add(bcItem);
            } catch (JsonSyntaxException e) {
                // Do not add invalid json objects
                // Probably jsonItem is a string with only the action, convert...
                this.add(new BroadcastItem(jsonItem, jsonItem));
            }
        }
    }
    
    public Set<String> toStringSet() {
        Gson gson = new Gson();
        Set<String> bcItems = new HashSet<>();
        
        for (BroadcastItem broadcastItem : this) {
            bcItems.add(gson.toJson(broadcastItem));
        }
        
        return bcItems;
    }
    
    public BroadcastItem search(String action) {
        for (BroadcastItem broadcastItem : this) {
            if(broadcastItem.action.equals(action)) {
                return broadcastItem;
            }
        }
        return null;
    }
}
