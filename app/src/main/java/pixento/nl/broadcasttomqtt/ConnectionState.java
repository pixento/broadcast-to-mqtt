package pixento.nl.broadcasttomqtt;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by corniel on 6-9-2017.
 */

class ConnectionState {
    
    enum State {DISCONNECTED, CONNECTED, CONNECTING, CONNECTION_ERROR, AUTH_ERROR, HOST_UNKNOWN}
    
    private final Collection<ConnectionStateListener> listeners = new ArrayList<>();
    public State state = State.DISCONNECTED;
    
    public void set(State state) {
        this.state = state;
        for (ConnectionStateListener listener : listeners) {
            listener.onChange(state);
        }
    }
    
    public void registerListener(ConnectionStateListener listener) {
        listeners.add(listener);
    }
    
    public interface ConnectionStateListener {
        void onChange(State newState);
    }
}

