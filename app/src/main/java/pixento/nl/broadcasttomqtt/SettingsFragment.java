package pixento.nl.broadcasttomqtt;


import android.os.Bundle;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment {

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        // When the user leaves the settings screen, update the prefs of MqttConnection
        MqttConnection mqttConnection = MqttConnection.getInstance();
        if(mqttConnection != null) {
            mqttConnection.updatePreferences(getActivity().getApplicationContext());
        }
    }
}
