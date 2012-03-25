package com.mqtt.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mqtt.messenger.MQTTService.LocalBinder;

public class Dashboard extends Activity {

	
	private MQTTService mqttService;
	private boolean mBound = false;
	
	private StatusUpdateReceiver statusUpdateIntentReceiver;
    private MQTTMessageReceiver  messageIntentReceiver;
    
	private String username, password, phone_id;
	private TextView messageView;
	private ScrollView scroller;
	private AlertDialog alert;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        //basic stuff
    	super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dashpage);

		//Info Dialog Builder
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("\nMQTT Messenger Application: Version 1.0\n");
		alert = builder.create();
		
		messageView = (TextView) findViewById(R.id.message);
		scroller = (ScrollView) findViewById(R.id.scrollView1);
		
        statusUpdateIntentReceiver = new StatusUpdateReceiver();
        IntentFilter intentSFilter = new IntentFilter(MQTTService.MQTT_STATUS_INTENT);
        registerReceiver(statusUpdateIntentReceiver, intentSFilter);
        
        messageIntentReceiver = new MQTTMessageReceiver();
        IntentFilter intentCFilter = new IntentFilter(MQTTService.MQTT_MSG_RECEIVED_INTENT);
        registerReceiver(messageIntentReceiver, intentCFilter);
        
		if(MQTTService.SERVICE_STAT==false)	//only if the service has been shutdown..
		{
	        //Start the Service
			Intent svc = new Intent(Dashboard.this, MQTTService.class);
	        startService(svc);
	        	        
			//Following stuff only for Logging in for the First Time
	        username = getIntent().getStringExtra("username");
	        password = getIntent().getStringExtra("password");
	        phone_id = Settings.System.getString(getContentResolver(),Secure.ANDROID_ID);
	        //Handle Registration
		}
		
		 //Bind to the Service
		 Intent intent = new Intent(this, MQTTService.class);
	     bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	     
		Log.d("Debug","Exiting onCreate");
    }
    
    
    @Override 
    public void onStop(){
    	super.onStop();
    	 if (mBound) {
             unbindService(mConnection);
             mBound = false;
         }
    }
        
    @Override
    protected void onDestroy()
    {
    	Log.d("Debug","Entering onDestroy");
    	super.onDestroy();
        unregisterReceiver(statusUpdateIntentReceiver);
        unregisterReceiver(messageIntentReceiver);
        Log.d("Debug","Exiting onDestroy");
    } 
    
    //For Binding
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            LocalBinder binder = (LocalBinder) service;
            mqttService = binder.getService();
            mBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
    
    //Broadcast Receiver Definitions
    public class StatusUpdateReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Bundle notificationData = intent.getExtras();
            String newStatus = notificationData.getString(MQTTService.MQTT_STATUS_MSG);
            Log.d("StatusReceiver", newStatus);
        }
    }
    public class MQTTMessageReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Bundle notificationData = intent.getExtras();
            String newTopic = notificationData.getString(MQTTService.MQTT_MSG_RECEIVED_TOPIC);
            String newData  = notificationData.getString(MQTTService.MQTT_MSG_RECEIVED_MSG);
            
            Log.d("NotificationReceiver", newTopic+" "+newData);
            
            messageView.append("\nTopic: " + newTopic + "\nMessage: " + newData +"\n\n");
    		scroller.post(new Runnable() {
				   public void run() {
				        scroller.scrollTo(messageView.getMeasuredWidth(), messageView.getMeasuredHeight());
				    }
				});
        }
    }
    
    //Extra Utility Methods
    public void stopservice() 
    {
        Intent svc = new Intent(this, MQTTService.class);
        stopService(svc); 
    }
    
    
    
    public void performMQTTAction(final int id)
    {
    
	                    switch(id)
	                    {
	                    case 1: mqttService.publishToTopic("nittrichy", "hello world");
	                    		break;
	                    case 2: mqttService.subscribeToTopic("atkal");
	                    		break;
	                    case 3: mqttService.unsubscribeToTopic("atkal");
	                    		break;
	                    }
    }

    
    
    public void getPendingMessages() {
	                    mqttService.rebroadcastReceivedMessages();
	                }
    
    
    
    //Menu Functions 
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dashmenu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.dashitem1:	//Start a new activity to publish!
        						performMQTTAction(1);
	        					break;
        						
        case R.id.dashitem2:	performMQTTAction(2);
        						break;
        
        case R.id.dashitem3:	performMQTTAction(3);
        						break;
       
        case R.id.dashitem4:	stopservice();
        						finish();
								break;
								
        case R.id.dashitem5:	alert.show();
        						break;
            default:     		break;
        }
        return true;
    }
}      