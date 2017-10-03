package pixento.nl.broadcasttomqtt;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import static pixento.nl.broadcasttomqtt.MainActivity.bcPrefsKey;

public class EditBroadcastActivity extends AppCompatActivity {
    
    static final String EDIT_BROADCAST_ACTION = "nl.pixento.broadcasttomqtt.EDIT_BROADCAST_ITEM";
    
    SharedPreferences prefs;
    BroadcastItemList bcItems;
    BroadcastItem broadcast;
    Switch edit_enabled;
    EditText edit_alias;
    EditText edit_action;
    EditText edit_rate_limit;
    TextView view_last_payload;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set the content and enable the toolbar as ActionBar
        setContentView(R.layout.activity_edit_broadcast);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        
        // Set the onclick listener for the test button
        Button testSendButton = findViewById(R.id.button_test_message);
        testSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditBroadcastActivity.this.sendTestMessageClick(view);
            }
        });
        
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
        } else {
            actionBar.setTitle("Add broadcast");
            broadcast = new BroadcastItem();
            bcItems.add(broadcast);
        }
        
        // Find the inputs, and set the values
        edit_enabled = (Switch) findViewById(R.id.input_enabled);
        edit_alias = (EditText) findViewById(R.id.input_alias);
        edit_action = (EditText) findViewById(R.id.input_action);
        edit_rate_limit = (EditText) findViewById(R.id.input_rate_limit);
        view_last_payload = findViewById(R.id.view_last_payload);
        
        edit_enabled.setChecked(broadcast.enabled);
        edit_alias.setText(broadcast.alias);
        edit_action.setText(broadcast.action);
        edit_rate_limit.setText(Integer.toString(broadcast.rate_limit));
        if(!TextUtils.isEmpty(broadcast.last_payload)) {
            view_last_payload.setText(broadcast.last_payload);
        }
    }
    
    boolean saveBroadcast() {
        if (!validateInput()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialog_enter_bcdata_message)
                   .setNegativeButton(R.string.ok, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) { }
                   });
            builder.create().show();
            return false;
        } else {
            // Get the text inputs, and set to broadcast
            broadcast.enabled = edit_enabled.isChecked();
            broadcast.alias = edit_alias.getText().toString();
            broadcast.action = edit_action.getText().toString();
            broadcast.rate_limit = Integer.parseInt(edit_rate_limit.getText().toString());
            
            // And save the list
            this.saveBroadcastPrefs();
            return true;
        }
    }
    
    void deleteBroadcast() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_delete_bc_message)
               .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // Remove the broadcast from the list
                       bcItems.remove(broadcast);
    
                       // And save the list
                       EditBroadcastActivity.this.saveBroadcastPrefs();
    
                       // Leave this screen
                       EditBroadcastActivity.super.onBackPressed();
                   }
               })
               .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) { }
               });
        builder.create().show();
    }
    
    void saveBroadcastPrefs() {
        // Save the prefs
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(bcPrefsKey, bcItems.toStringSet());
        editor.commit();
    }
    
    boolean validateInput() {
        if (edit_action.getText().toString().isEmpty() ||
            edit_alias.getText().toString().isEmpty()) {
            return false;
        } else {
            return true;
        }
    }
    
    boolean inputHasChanged() {
        if(edit_enabled.isChecked() != broadcast.enabled ||
            !edit_action.getText().toString().equals(broadcast.action) ||
            !edit_alias.getText().toString().equals(broadcast.alias) ||
            Integer.parseInt(edit_rate_limit.getText().toString()) != broadcast.rate_limit
            ) {
            return true;
        }
        else {
            return false;
        }
    }
    
    /**
     * The function called as onClick handler of the test button.
     * onClick handler is set in layout file
     *
     * @param view
     */
    void sendTestMessageClick(View view) {
        // Check the input
        if (!validateInput()) {
            Toast.makeText(this, "Enter alias and action", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Create the test message
        JSONObject payload = new JSONObject();
        try {
            // Add the action and alias
            payload.put("action", edit_action.getText().toString());
            payload.put("alias", edit_alias.getText().toString());
            payload.put("count", 1);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        // Get the MqttConnection instance and enqueue the message
        MqttConnection connection = MqttConnection.getInstance(view.getContext());
        connection.enqueue(payload);
        
        Toast.makeText(this, "Test message enqueued", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onBackPressed() {
        if(inputHasChanged()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialog_backbutton_message)
                   .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           // Save and leave screen
                           if(EditBroadcastActivity.this.saveBroadcast()) {
                               EditBroadcastActivity.super.onBackPressed();
                           }
                       }
                   })
                   .setNegativeButton(R.string.dont_save, new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           // Do not save, leave the screen
                           EditBroadcastActivity.super.onBackPressed();
                       }
                   });
            builder.create().show();
        }
        else {
            super.onBackPressed();
        }
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
                //super.onBackPressed();
                break;
            case R.id.action_save:
                // Save the broadcasts
                if (this.saveBroadcast()) {
                    super.onBackPressed();
                }
                break;
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                // NavUtils.navigateUpFromSameTask(this);
                onBackPressed();
                return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
