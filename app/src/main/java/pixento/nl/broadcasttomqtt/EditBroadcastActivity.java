package pixento.nl.broadcasttomqtt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import java.util.HashSet;

import static pixento.nl.broadcasttomqtt.MainActivity.bcPrefsKey;

public class EditBroadcastActivity extends AppCompatActivity {
    
    static final String EDIT_BROADCAST_ACTION = "nl.pixento.broadcasttomqtt.EDIT_BROADCAST_ITEM";
    
    SharedPreferences prefs;
    BroadcastItemList bcItems;
    BroadcastItem broadcast;
    EditText edit_alias;
    EditText edit_action;
    EditText edit_rate_limit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set the content and enable the toolbar as ActionBar
        setContentView(R.layout.activity_edit_broadcast);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    
        // Get the preferences and the list of broadcasts
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        bcItems = new BroadcastItemList(
            prefs.getStringSet(bcPrefsKey, new HashSet<String>())
        );
    
        // Get the broadcast item to edit
        Intent intent = getIntent();
        
        if (intent.hasExtra(EDIT_BROADCAST_ACTION)) {
            actionBar.setTitle("Edit broadcast");
            broadcast = bcItems.search(intent.getStringExtra(EDIT_BROADCAST_ACTION));
        }
        else {
            actionBar.setTitle("Add broadcast");
            broadcast = new BroadcastItem();
            bcItems.add(broadcast);
        }
        
        // Find the inputs, and set the values
        edit_alias = (EditText) findViewById(R.id.input_alias);
        edit_action = (EditText) findViewById(R.id.input_action);
        edit_rate_limit = (EditText) findViewById(R.id.input_rate_limit);
        edit_alias.setText(broadcast.alias);
        edit_action.setText(broadcast.action);
        edit_rate_limit.setText(Integer.toString(broadcast.rate_limit));
    }
    
    void saveBroadcast() {
        // Get the text inputs, and set to broadcast
        broadcast.alias = edit_alias.getText().toString();
        broadcast.action = edit_action.getText().toString();
        broadcast.rate_limit = Integer.parseInt(edit_rate_limit.getText().toString());

        // And save the list
        this.saveBroadcastPrefs();
    }

    void deleteBroadcast() {
        // Remove the broadcast from the list
        bcItems.remove(broadcast);

        // And save the list
        this.saveBroadcastPrefs();
    }

    void saveBroadcastPrefs() {
        // Save the prefs
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(bcPrefsKey, bcItems.toStringSet());
        editor.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_edit_broadcast, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        
        switch (id) {
            case R.id.action_delete:
                // Delete the item and navigate up
                this.deleteBroadcast();
                NavUtils.navigateUpFromSameTask(this);
                break;
            case R.id.action_save:
                // Save the broadcasts
                this.saveBroadcast();
                break;
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
