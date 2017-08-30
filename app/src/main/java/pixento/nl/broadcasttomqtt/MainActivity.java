package pixento.nl.broadcasttomqtt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private MqttConnection connection;
    private ListView bcListView;
    private int i = 0;
    
    private static final String TAG = "MainActivity";
    static final String bcPrefsKey = "broadcast_items";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Get the preferences for the list of broadcasts to listen for
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    
        // No broadcasts set yet, this is the first run of the app!
        if(!prefs.contains(bcPrefsKey)) {
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
        if(!prefs.contains("pref_device_id")) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("pref_device_id", MqttConnection.getDefaultDeviceId());
            editor.commit();
        }
        
        // Get the broadcast items from preference manager
        Set<String> bcItemsSet = prefs.getStringSet(bcPrefsKey, new HashSet<String>());
        BroadcastItemList bcItems = new BroadcastItemList(bcItemsSet);
        
        // Create the listview
        bcListView = (ListView) findViewById(R.id.broadcast_list);
        BroadcastItemAdapter adapter = new BroadcastItemAdapter(this.getApplicationContext(), bcItems);
        bcListView.setAdapter(adapter);
        
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "ToDo :-D", Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        });

        // Instantiate the MQTT connection
        connection = MqttConnection.getInstance(this.getApplicationContext());
        
        // Start the service which registers the broadcastreceiver
        Intent serviceIntent = new Intent(this, MqttBroadcastService.class);
        startService(serviceIntent);
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            // Open the Settings window
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }
}
