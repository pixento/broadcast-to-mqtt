package pixento.nl.broadcasttomqtt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.HashSet;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private BroadcastItemAdapter adapter;
    private BroadcastItemList bcItems = new BroadcastItemList();
    private ListView bcListView;
    SharedPreferences prefs;

    private static final String TAG = "MainActivity";
    static final String bcPrefsKey = "broadcast_items";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Get the preferences for the list of broadcasts to listen for
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        
        // No broadcasts set yet, this is the first run of the app!
        if (!prefs.contains(bcPrefsKey)) {
            // Add some default broadcasts to the config as example
            BroadcastItemList defaultBroadcastItems = new BroadcastItemList();
            defaultBroadcastItems.add(new BroadcastItem("com.sonyericsson.alarm.ALARM_ALERT", "Sony alarm"));
            defaultBroadcastItems.add(new BroadcastItem("com.android.deskclock.ALARM_ALERT", "Android alarm"));
            defaultBroadcastItems.add(new BroadcastItem("com.android.alarmclock.ALARM_ALERT", "Android alarm"));
            defaultBroadcastItems.add(new BroadcastItem("com.samsung.sec.android.clockpackage.alarm.ALARM_ALERT", "Samsung alarm"));

            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(bcPrefsKey, defaultBroadcastItems.toStringSet());
            editor.commit();
        }

        // Make sure a device id is set
        if (!prefs.contains("pref_device_id")) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("pref_device_id", MqttConnection.getDefaultDeviceId());
            editor.commit();
        }

        // Create the listview
        bcListView = (ListView) findViewById(R.id.broadcast_list);
        adapter = new BroadcastItemAdapter(this, bcItems);
        bcListView.setAdapter(adapter);
        bcListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
                Intent intent = new Intent(MainActivity.this, EditBroadcastActivity.class);
                BroadcastItem item = (BroadcastItem) adapterView.getItemAtPosition(pos);
                intent.putExtra(EditBroadcastActivity.EDIT_BROADCAST_ACTION, item.action);
                startActivity(intent);
            }
        });

        // Update the content of the listview
        this.updateBCListView(prefs);

        // Create the floating action button
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Snackbar.make(view, "ToDo :-D", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                Intent intent = new Intent(MainActivity.this, EditBroadcastActivity.class);
                startActivity(intent);
            }
        });

        // Instantiate the MQTT connection and register for connection state changes
        MqttConnection connection = MqttConnection.getInstance(this.getApplicationContext());
        connection.setKeepAlive(true);
        this.updateConnectionStateView(connection.connectionState.state);
        connection.connectionState.registerListener(new ConnectionState.ConnectionStateListener() {
            @Override
            public void onChange(ConnectionState.State newState) {
                MainActivity.this.updateConnectionStateView(newState);
            }
        });


        // Start the service which registers the broadcastreceiver
        Intent serviceIntent = new Intent(this, MqttBroadcastService.class);
        startService(serviceIntent);
    }
    
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.v(TAG, "Preferences changed, updating BC list");
        if (key.equals(bcPrefsKey)) {
            MainActivity.this.updateBCListView(sharedPreferences);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
    
        // Register prefs change listener and update list anyway
        prefs.registerOnSharedPreferenceChangeListener(this);
        this.updateBCListView(prefs);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
    
        // Unregister prefs change listener
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }
    
    private void updateConnectionStateView(ConnectionState.State state) {
        final ImageView icon = (ImageView) findViewById(R.id.connection_icon);
        final ProgressBar connecting = (ProgressBar) findViewById(R.id.progress_connecting);
        final TextView description = (TextView) findViewById(R.id.connection_state);

        // Set icon
        switch (state) {
            case CONNECTED:
                icon.setImageDrawable(
                    ContextCompat.getDrawable(getApplicationContext(), R.drawable.lan_connect)
                );
                break;
            case CONNECTION_ERROR:
            case CONNECTING:
            case DISCONNECTED:
            case HOST_UNKNOWN:
                icon.setImageDrawable(
                    ContextCompat.getDrawable(getApplicationContext(), R.drawable.lan_disconnect)
                );
                break;
        }

        // Set loading indicator
        switch (state) {
            case CONNECTED:
            case CONNECTION_ERROR:
            case DISCONNECTED:
            case HOST_UNKNOWN:
                connecting.setVisibility(View.GONE);
                break;
            case CONNECTING:
                connecting.setVisibility(View.VISIBLE);
                break;

        }

        // Set text
        switch (state) {
            case CONNECTED:
                description.setText(R.string.connection_connected);
                break;
            case CONNECTION_ERROR:
                description.setText(R.string.connection_connection_error);
                break;
            case CONNECTING:
                description.setText(R.string.connection_connecting);
                break;
            case DISCONNECTED:
                description.setText(R.string.connection_disconnected);
                break;
            case HOST_UNKNOWN:
                description.setText(R.string.connection_unknown_host);
                break;
        }

    }

    private void updateBCListView(SharedPreferences prefs) {
        // Get the broadcast items from preference manager
        bcItems = new BroadcastItemList(
                prefs.getStringSet(bcPrefsKey, new HashSet<String>())
        );

        if (adapter != null) {
            // Update the dataset
            adapter.updateDataSet(bcItems);

            // Notify the change
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings:
                // Open the Settings window
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy called");

        // Set the MqttConnection to not keep the connection alive
        MqttConnection connection = MqttConnection.getInstance(this.getApplicationContext());
        connection.setKeepAlive(false);
    }
    
}
