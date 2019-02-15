package pixento.nl.broadcasttomqtt;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * The MqttBroadcastService runs in the background and makes sure that the dynamically registered
 * broadcast receiver will keep working even if the app is closed, and that messages still will
 * be forwarded to MQTT.
 */
public class MqttBroadcastService extends Service {
    
    private BroadcastReceiver broadcastReceiver;
    private SharedPreferences prefs;
    private OnSharedPreferenceChangeListener changeListener;
    
    public static boolean shouldUpdateFilter = true;
    
    //static final String updateIntentFilter = "update_intent_filter";
    private static final String TAG = "MqttBroadcastService";
    private static final String CHANNEL_ID = "MqttBroadcastNotification";
    static final String notificationPrefsKey = "pref_persistent_notification";
    static final int notificationId = 123;
    
    public MqttBroadcastService() { }
    
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Get the broadcasts from prefs and update the filter
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        changeListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(MainActivity.bcPrefsKey)) {
                    MqttBroadcastService.this.updateIntentFilter(sharedPreferences);
                }
                
                if (key.equals(notificationPrefsKey)) {
                    MqttBroadcastService.this.updateNotification(sharedPreferences);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(changeListener);
        
        this.updateIntentFilter(prefs);
        this.updateNotification(prefs);
        
        Log.v(TAG, "Started MqttBroadcastService");
    }
    
    public static void startService(Context context, Intent serviceIntent) {
        if(serviceIntent == null) {
            serviceIntent = new Intent(context, MqttBroadcastService.class);
        }
        
        // If preference 'pref_persistent_notification' is true, start as foreground service
        // A foreground service always has a notification
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(notificationPrefsKey, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        }
        else {
            context.startService(serviceIntent);
        }
    }
    
    private void updateIntentFilter(SharedPreferences prefs) {
        Log.v(TAG, "Updating the broadcast receiver's filter");
        
        if (!shouldUpdateFilter) {
            // Do not update the filter, and reset the flag
            shouldUpdateFilter = true;
            return;
        }
        
        // Get the broadcast items from the preferences
        Set<String> prefBroadcastItems = prefs.getStringSet(MainActivity.bcPrefsKey, null);
        if (prefBroadcastItems == null) {
            prefBroadcastItems = new HashSet<>();
        }
        BroadcastItemList bcItems = new BroadcastItemList(prefBroadcastItems);
        
        // Unregister the filter if it is registered
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
        
        // Create the intent filters from the set of bc's from the prefs
        IntentFilter filter = new IntentFilter();
        for (BroadcastItem item : bcItems) {
            if (item.enabled) {
                filter.addAction(item.action);
            }
        }
        
        // Register the BroadcastReceiver
        broadcastReceiver = new SubBroadcastReceiver();
        registerReceiver(broadcastReceiver, filter);
    }
    
    private void updateNotification(SharedPreferences prefs) {
        Log.v(TAG, "Updating the broadcast receiver service notification");
        this.createNotificationChannel();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        
        // Check if persistent notification should be created
        if (prefs.getBoolean(notificationPrefsKey, false)) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent startAppIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(getString(R.string.notification_title))
                .setColor(ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null))
                .setShowWhen(false)
                .setContentIntent(startAppIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true);
            
            // notificationId is a unique int for each notification that you must define
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(notificationId, mBuilder.build());
            }
            else {
                notificationManager.notify(notificationId, mBuilder.build());
            }
        }
        else {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                stopForeground(STOP_FOREGROUND_DETACH);
            }
            else {
                notificationManager.cancel(notificationId);
            }
        }
    }
    
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_NONE;
            
            // Create the channel
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Remove the prefs change listener
        prefs.unregisterOnSharedPreferenceChangeListener(changeListener);
        
        // Make sure to unregister the receiver if the service stops
        unregisterReceiver(broadcastReceiver);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context context = this.getApplicationContext();
        
        // If the intent is send to retry sending, do this!
        if (intent != null && intent.getBooleanExtra("retry_sending", false)) {
            Log.v(TAG, "Received intent, retrying all queued messages.");
            
            // Get the MqttConnection instance
            MqttConnection mqttConnection = MqttConnection.getInstance(context);
            
            // Try to publish all messages in the queue
            mqttConnection.publishAll();
            
            // Set another alarm if the queue is not empty
            if (!mqttConnection.isQueueEmpty()) {
                mqttConnection.setRetryAlarm(context);
            }
        }
        
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }
}
