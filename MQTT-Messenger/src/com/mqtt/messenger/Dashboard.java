package com.mqtt.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.mqtt.messenger.MQTTService.LocalBinder;

public class Dashboard extends Activity {

	
	private MQTTService mqttService;
	private boolean mBound = false;
	private boolean serverConnected = false;
	private String username, password, phone_id;
	private int registerResponse = 0;
	private StatusUpdateReceiver statusUpdateIntentReceiver;
    private MQTTMessageReceiver  messageIntentReceiver;
    
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
        
        
		 //Bind to the Service
		 Intent intent = new Intent(this, MQTTService.class);
	     bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	     
        if(MQTTService.SERVICE_STAT==false)	//only if the service has been shutdown..
		{
	        //Start the Service
			Intent svc = new Intent(Dashboard.this, MQTTService.class);
	        startService(svc);
	        	        
		     
			//Following stuff only for Logging in for the First Time
	        username = getIntent().getStringExtra("username");
	        password = getIntent().getStringExtra("password");
	        phone_id = Settings.System.getString(getContentResolver(),Secure.ANDROID_ID);
	        
	        processLogin();

		}
	     
		Log.d("Debug","Exiting onCreate");
    }
    
    public void processLogin(){
    	
    	//wait till service is connected
    	new Thread() {
	        public void run() {
	        	while(!serverConnected){
		        	try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	        	}
	        	
	        //publish register message
	        bindService(new Intent(Dashboard.this, MQTTService.class),
	                new ServiceConnection() {
	                    @Override
	                    public void onServiceConnected(ComponentName className, final IBinder service)
	                    {
	                        MQTTService mqttService = ((LocalBinder)service).getService();
	                        mqttService.publishToTopic("REGISTER", phone_id+"#"+username+"#"+password);
	                        unbindService(this);
	                    }
	                    @Override
	                    public void onServiceDisconnected(ComponentName name) {}
	                },
	                0); 
	        }
	        }.start();
	        
	        pd = ProgressDialog.show(this, "Registering", "Please Wait..", true, false);
	        pd.setOnKeyListener(new DialogInterface.OnKeyListener() {
	            @Override
	            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
	                if (keyCode == KeyEvent.KEYCODE_SEARCH && event.getRepeatCount() == 0) {
	                    return true; // Pretend we processed it
	                }
	                return false; // Any other keys are still processed as normal
	            }
	        });
	        timerDelayRemoveDialog(10000,pd);	//timeout after 10 seconds
	        //wait for response and proceed accordingly
	        new Thread() {
	        	public void run(){
	        		while(registerResponse==0){
	        			try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
	        		}
	        		if(registerResponse==1)
	        			handler.sendMessage(Message.obtain(handler, 1));
	        		else if(registerResponse==2)
	        			handler.sendMessage(Message.obtain(handler, 2));
	        	}
	        }.start();
	        
    }
    
    public void timerDelayRemoveDialog(long time, final ProgressDialog d){
        Handler handler = new Handler(); 
        handler.postDelayed(new Runnable() {           
            public void run() {
            	if(d.isShowing())
                {
            		d.dismiss();
            		Toast.makeText(getBaseContext(), "Connection Timed out! Try Again!", Toast.LENGTH_SHORT).show();
            		stopservice();
            		finish();
                }
            }
        }, time); 
    }
    
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
                pd.dismiss();
                if(msg.what==1)
                {
                	Toast.makeText(getBaseContext(), "Login Success!", Toast.LENGTH_SHORT).show();
                }
                else if(msg.what==2)
                {
                	Toast.makeText(getBaseContext(), "Login Failed! Try Again!", Toast.LENGTH_SHORT).show();
                	stopservice();
                	finish();

                }
        }
};
    
/* @Override 
	public void onResume(){
		super.onResume();
		if(loggedIn==false)
			processLogin();
	}	 */

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
            if(newStatus.equals("Connected"))
            	serverConnected = true;
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
            
            if(newTopic.equals(phone_id))	//Handle Response from MQTT Server
            {
            	if(newData.equals("REGISTER_SUCCESS"))
            		registerResponse=1;
            	else if(newData.equals("REGISTER_FAILURE"))
            		registerResponse=2;
            }
            
            else {
            messageView.append("\nTopic: " + newTopic + "\nMessage: " + newData +"\n\n");
    		scroller.post(new Runnable() {
				   public void run() {
				        scroller.scrollTo(messageView.getMeasuredWidth(), messageView.getMeasuredHeight());
				    }
				}); }
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