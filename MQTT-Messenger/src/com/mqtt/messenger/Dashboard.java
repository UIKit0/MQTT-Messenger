package com.mqtt.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mqtt.messenger.MQTTService.LocalBinder;

public class Dashboard extends Activity {

	
	private StatusUpdateReceiver statusUpdateIntentReceiver;
    private MQTTMessageReceiver  messageIntentReceiver;
    
	private String username, password;	
	private TextView messageView;
	private ScrollView scroller;
	private AlertDialog alert;
	private ProgressDialog pd;
    
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
			Intent svc = new Intent(this, MQTTService.class);
	        startService(svc);
	        	        
			//Following stuff only for Logging in for the First Time
	        username = getIntent().getStringExtra("username");
	        password = getIntent().getStringExtra("password");
			pd = ProgressDialog.show(this, "Registering", "Please wait..", true,false);
			 /* new Thread() { 
				 int flag;
				 public void run() { 
					 
					 } }.start();	//Register in a new thread! */
			pd.dismiss();
		}
		Log.d("Debug","Exiting onCreate");
    }
    
    //Menu Functions 
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dashmenu, menu);
        return true;
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
    
    public void stopservice() //UI to be implemented Later..
    {
        Intent svc = new Intent(this, MQTTService.class);
        stopService(svc); 
    }
    
    
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
            
            //Process the Received Message..
            Log.d("NotificationReceiver", newTopic+" "+newData);
            messageView.append("\nTopic: " + newTopic + "\nMessage: " + newData +"\n\n");
    		scroller.post(new Runnable() {
				   public void run() {
				        scroller.scrollTo(messageView.getMeasuredWidth(), messageView.getMeasuredHeight());
				    }
				});
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.dashitem1:		//Start a new activity to publish!
        	bindService(new Intent(this, MQTTService.class),
    	            new ServiceConnection() {
    	                @SuppressWarnings("unchecked")
    	                @Override
    	                public void onServiceConnected(ComponentName className, final IBinder service)
    	                {
    	                    MQTTService mqttService = ((LocalBinder<MQTTService>)service).getService();
    	                    mqttService.publishToTopic("nittrichy", "hello world");
    	                    unbindService(this);
    	                }
    	                @Override
    	                public void onServiceDisconnected(ComponentName name) {}
    	            },0);
	        						break;
        						
        case R.id.dashitem2:	
        	bindService(new Intent(this, MQTTService.class),
    	            new ServiceConnection() {
    	                @SuppressWarnings("unchecked")
    	                @Override
    	                public void onServiceConnected(ComponentName className, final IBinder service)
    	                {
    	                    MQTTService mqttService = ((LocalBinder<MQTTService>)service).getService();
    	                    mqttService.subscribeToTopic("atkal");
    	                    unbindService(this);
    	                }
    	                @Override
    	                public void onServiceDisconnected(ComponentName name) {}
    	            },0); 
        						break;
        
        case R.id.dashitem3:	

        	bindService(new Intent(this, MQTTService.class),
    	            new ServiceConnection() {
    	                @SuppressWarnings("unchecked")
    	                @Override
    	                public void onServiceConnected(ComponentName className, final IBinder service)
    	                {
    	                    MQTTService mqttService = ((LocalBinder<MQTTService>)service).getService();
    	                    mqttService.unsubscribeToTopic("atkal");
    	                    unbindService(this);
    	                }
    	                @Override
    	                public void onServiceDisconnected(ComponentName name) {}
    	            },0); 
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
    
    public void getPendingMessages(){
    	bindService(new Intent(this, MQTTService.class),
	            new ServiceConnection() {
	                @SuppressWarnings("unchecked")
	                @Override
	                public void onServiceConnected(ComponentName className, final IBinder service)
	                {
	                    MQTTService mqttService = ((LocalBinder<MQTTService>)service).getService();
	                    mqttService.rebroadcastReceivedMessages();
	                    unbindService(this);
	                }
	                @Override
	                public void onServiceDisconnected(ComponentName name) {}
	            },
	            0); 
    }
      
}	