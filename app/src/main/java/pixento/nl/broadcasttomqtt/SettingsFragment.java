package pixento.nl.broadcasttomqtt;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.EditText;

import java.util.Arrays;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "SettingsFragment";

    SharedPreferences sharedPreferences;
    Boolean prefsChanged = false;

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
    public void onResume() {
        super.onResume();

        // Get the prefs manager and start listening for changes
        sharedPreferences = getPreferenceManager().getSharedPreferences();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        // Update the summaries
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        for(int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
            setSummary(getPreferenceScreen().getPreference(i));
        }
    }

    @Override
    public void onPause() {
        // Log message
        Log.d(TAG, "Leaving Settings activity");
        
        // Unregister listeners
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    
        super.onPause();
        
        // When the user leaves the settings screen, update the prefs of MqttConnection if changed
        MqttConnection mqttConnection = MqttConnection.getInstance();
        if(prefsChanged && mqttConnection != null) {
            mqttConnection.updatePreferences(getActivity().getApplicationContext());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.v(TAG, "Setting changed: "+ key);
        Preference pref = getPreferenceScreen().findPreference(key);
        setSummary(pref);
    
        prefsChanged = true;
    }

    private void setSummary(Preference pref) {
        if (pref instanceof PreferenceGroup) {
            PreferenceGroup preferenceGroup = (PreferenceGroup) pref;
            for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
                setSummary(preferenceGroup.getPreference(i));
            }
        }
        else if (pref instanceof EditTextPreference) {
            updateSummary((EditTextPreference) pref);
        } else if (pref instanceof ListPreference) {
            updateSummary((ListPreference) pref);
        } else if (pref instanceof MultiSelectListPreference) {
            updateSummary((MultiSelectListPreference) pref);
        }
    }

    private void updateSummary(MultiSelectListPreference pref) {
        pref.setSummary(Arrays.toString(pref.getValues().toArray()));
    }

    private void updateSummary(ListPreference pref) {
        if(pref.getValue() != null) {
            pref.setSummary(pref.getValue());
        }
    }

    private void updateSummary(EditTextPreference pref) {
        if(pref.getText() != null) {
            EditText editor = pref.getEditText();
            String summary_text = editor
                .getTransformationMethod()
                .getTransformation(pref.getText(), editor).toString();
            pref.setSummary(summary_text);
        }
    }
}
