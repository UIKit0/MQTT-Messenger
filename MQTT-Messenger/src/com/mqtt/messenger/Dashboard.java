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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mqtt.messenger.MQTTService.LocalBinder;


public class Dashboard extends Activity {

	
	private MQTTService mqttService;
	static boolean active = false;
	public static String username, password;
	
	private boolean mBound = false;
	private boolean serverConnected = false;
	
	private static int registerResponse = 0;
	private static int loginResponse = 0;
	private static String phone_id;
	
	private StatusUpdateReceiver statusUpdateIntentReceiver;
    private MQTTMessageReceiver  messageIntentReceiver;
    
	private TextView messageView;
	private ScrollView scroller;
	private AlertDialog alert;
	private ProgressDialog pd;
	
	private Spinner spin;
    private ArrayAdapter<String> aa ;
    
	public String[] listOfTopics = null;
	public boolean listOfTopicsUpdated = false;

	
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        //basic stuff
    	super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dashpage);

		//Info Dialog Builder
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("\nAn Android Application for Realtime Messaging System using MQTT\n\nTeam Members:\n\nDinesh Babu K G\nJagadeesh M\nVasantharajan S");
		alert = builder.create();
		
		messageView = (TextView) findViewById(R.id.message);
		scroller = (ScrollView) findViewById(R.id.scrollView1);
	
       
			//Start the Service
			Intent svc = new Intent(Dashboard.this, MQTTService.class);
        
        	if(startService(svc)==null) 
        	{
	        username = getIntent().getStringExtra("username");
	        password = getIntent().getStringExtra("password");
	        MQTTService.username = username;
	        MQTTService.password = password;
	        phone_id = Settings.System.getString(getContentResolver(),Secure.ANDROID_ID);
	        
	        String action = getIntent().getStringExtra("action");
	        if(action.equals("1"))
	        	processLogin();
	        else
	        	processRegister();
        }
        
        else
        {
        	//restart the dashboard
        	username = MQTTService.username;
    		password = MQTTService.password;
        }
        
        //Bind to the Service
		 Intent intent = new Intent(this, MQTTService.class);
	     bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	     
	        //Register the Broadcast Receivers
	     	statusUpdateIntentReceiver = new StatusUpdateReceiver();
	        IntentFilter intentSFilter = new IntentFilter(MQTTService.MQTT_STATUS_INTENT);
	        registerReceiver(statusUpdateIntentReceiver, intentSFilter);
	        
	        messageIntentReceiver = new MQTTMessageReceiver();
	        IntentFilter intentCFilter = new IntentFilter(MQTTService.MQTT_MSG_RECEIVED_INTENT);
	        registerReceiver(messageIntentReceiver, intentCFilter);
	     
	        phone_id = Settings.System.getString(getContentResolver(),Secure.ANDROID_ID);
	
		 
		 //Clear all the existing messages!
		 messageView.setText("");
		 
		 
		Log.d("Debug","Exiting onCreate");
    }
    

public void processRegister(){
	
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
        			handlerReg.sendMessage(Message.obtain(handlerReg, 1));
        		else if(registerResponse==2)
        			handlerReg.sendMessage(Message.obtain(handlerReg, 2));
        	}
        }.start();
        
} 


private Handler handlerReg = new Handler() {
    @Override
    public void handleMessage(Message msg) {
            pd.dismiss();
            if(msg.what==1)
            {
            	Toast.makeText(getBaseContext(), "Registered & logged in successfully!", Toast.LENGTH_SHORT).show();
            }
            else if(msg.what==2)
            {
            	Toast.makeText(getBaseContext(), "Register Failed, Try Again later!", Toast.LENGTH_SHORT).show();
            	stopservice();
            	finish();
            }
    }
};
  
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
                        mqttService.publishToTopic("LOGIN", phone_id+"#"+username+"#"+password);
                        unbindService(this);
                    }
                    @Override
                    public void onServiceDisconnected(ComponentName name) {}
                },
                0); 
        }
        }.start(); 
        
        pd = ProgressDialog.show(this, "Logging in", "Please Wait..", true, false);
        
        new Thread() {
        	public void run(){
        		while(loginResponse==0){
        			try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        		}
        		if(loginResponse==1)
        			handlerLog.sendMessage(Message.obtain(handlerLog, 1));
        		else if(loginResponse==2)
        			handlerLog.sendMessage(Message.obtain(handlerLog, 2));
        	}
        }.start();
        
} 


private Handler handlerLog = new Handler() {
    @Override
    public void handleMessage(Message msg) {
            pd.dismiss();
            if(msg.what==1)
            {
            	Toast.makeText(getBaseContext(), "Logged in successfully!", Toast.LENGTH_SHORT).show();
        		mqttService.subscribeToTopic(username);
        		getListOfTopics();
            }
            else if(msg.what==2)
            {
            	Toast.makeText(getBaseContext(), "Login Incorrect. Try Again!", Toast.LENGTH_SHORT).show();
            	stopservice();
            	finish();
            }
    }
};

	@Override
	public void onStart()
	{
		super.onStart();
		active = true;
	}
    @Override  
    public void onStop(){
    	super.onStop();
    	 if (mBound) {
             unbindService(mConnection);
             mBound = false;
         }
    	 active = false;
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
            
            if(newTopic.equals(phone_id))	//Handle Response from MQTT Server with Topic name as UNIQUE_ANDROID_ID
            {
            	if(newData.equals("REG_SUCCESS")) 
            		registerResponse = 1;
            	else if(newData.equals("REG_FAILURE")) 
            		registerResponse = 2;
            	else if(newData.equals("LOGIN_SUCCESS")) 
            		loginResponse = 1;
            	else if(newData.equals("LOGIN_FAILED")) 
            		loginResponse = 2;
            	
            }
            else if(newTopic.equals(username))
            {
            	if(newData.startsWith("TOPICS"))
            	{
            		listOfTopicsUpdated = true;
            		listOfTopics = newData.substring(7).split("\\#");
            		Toast.makeText(getBaseContext(), "Topics Updated!", Toast.LENGTH_SHORT).show();
            	}
            }
            else {
            //change newTopic to Original Topic name..
            	String origTopic = null;
            	origTopic = newTopic;
            messageView.append("\nTopic: " + origTopic + "\nMessage: " + newData +"\n\n");
    		scroller.post(new Runnable() {
				   public void run() {
				        scroller.scrollTo(messageView.getMeasuredWidth(), messageView.getMeasuredHeight());
				    }
				}); 
    		}
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
	                    case 1:
	                    	LayoutInflater factory = LayoutInflater.from(this);
	                        final View textEntryView = factory.inflate(R.layout.dialogpublish, null);
	                        AlertDialog.Builder alert = new AlertDialog.Builder(this);           
	                    	
	                        spin = (Spinner) textEntryView.findViewById(R.id.spinnerTopic);
	                        aa = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, listOfTopics);
	                        //aa.setDropDownViewResource(android.R.layout.simple_spinner_item);
	                        spin.setAdapter(aa);
	                        
	                        alert.setTitle("Publish Message");
	                    	alert.setView(textEntryView);
	                    
	                    	
	                    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
	                    	public void onClick(DialogInterface dialog, int whichButton) {
	                    		// Do something with value!
	                    		 
	                    		Spinner spin = (Spinner) textEntryView.findViewById(R.id.spinnerTopic);
	                    		EditText topicText = (EditText) textEntryView.findViewById(R.id.editTopic);
	                    		EditText topicMessage = (EditText) textEntryView.findViewById(R.id.editMessage);
	                    		String finalTopic;
	                    		if(topicText.getText().toString().isEmpty()==true)
	                    			finalTopic = spin.getSelectedItem().toString();
	                    		else
	                    		{
	                    			finalTopic = topicText.getText().toString();
	                    			mqttService.publishToTopic("CREATE_TOPIC", username+"#"+password+"#"+finalTopic);
	                    			getListOfTopics();
	                    		}
		                    	mqttService.publishToTopic(finalTopic, username+"#"+password+"#"+topicMessage.getText().toString());
	                    	  }
	                    	});
	                    	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
	                    	  public void onClick(DialogInterface dialog, int whichButton) {
	                    	    // Canceled...
	                    	  }
	                    	});
	                    	alert.show();
	                    	
	                    		break;
	                    case 2: 	          
	                    		pd = ProgressDialog.show(this, "Fetching Topics", "Please Wait..", true, false);
	                    		getListOfTopicsImmed();
	                    
	                    		break;
	                    case 3: //get list of topics from server and display multiselect in dialog to unsub..
	                    	
	                    	if(MQTTService.listOfTopicsSubscribed.isEmpty()==true)
	                    	{
	                    		Toast.makeText(getBaseContext(), "Not Subscribed to Any Topic!", Toast.LENGTH_SHORT).show();
	                    	}
	                    	else
	                    	{
		                    	LayoutInflater factory2 = LayoutInflater.from(this);
		                        final View textEntryView2 = factory2.inflate(R.layout.dialogunsubscribe, null);
		                        AlertDialog.Builder alert2 = new AlertDialog.Builder(this);           
		                    	
		                        spin = (Spinner) textEntryView2.findViewById(R.id.spinnerTopic);
		                        
		                        String[] topicsSubbed = new String[ MQTTService.listOfTopicsSubscribed.size()];
		                        topicsSubbed = MQTTService.listOfTopicsSubscribed.toArray(topicsSubbed);
		                        
		                        aa = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, topicsSubbed);	
		                        //aa.setDropDownViewResource(android.R.layout.simple_spinner_item);
		                        spin.setAdapter(aa);
		                        
		                        alert2.setTitle("Unsubscribe");
		                    	alert2.setView(textEntryView2);
		                    	
	
		                    	alert2.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		                    	public void onClick(DialogInterface dialog, int whichButton) {
		                    		// Do something with value!
		                    		Spinner spin = (Spinner) textEntryView2.findViewById(R.id.spinnerTopic);
		                    		mqttService.unsubscribeToTopic(spin.getSelectedItem().toString());
		                    		MQTTService.removeTopicSubscribed(spin.getSelectedItem().toString());
		                    	  }
		                    	});
		                    	alert2.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
		                    	  public void onClick(DialogInterface dialog, int whichButton) {
		                    	    // Canceled.
		                    	  }
		                    	});
		                    	alert2.show();
	                    	}
	                    	
	                    		break;
	                    }
    }

    public void getListOfTopics()
    {
    		
    		mqttService.publishToTopic( "TOPICS", username+"#"+password+"#REQUEST");
            new Thread() {
            	public void run(){
            		while(listOfTopicsUpdated==false){
            			try {
    						Thread.sleep(100);
    					} catch (InterruptedException e) {
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					}
            		}
            			handlerTop.sendEmptyMessage(0);
            	}
            }.start();
    		
    }
    
    public void getListOfTopicsImmed()
    {
    		mqttService.publishToTopic( "TOPICS", username+"#"+password+"#REQUEST");
            new Thread() {
            	public void run(){
            		while(listOfTopicsUpdated==false){
            			try {
    						Thread.sleep(100);
    					} catch (InterruptedException e) {
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					}
            		}
            			handlerTopImmed.sendEmptyMessage(0);
            	}
            }.start();
    		
    }
    
    private Handler handlerTopImmed = new Handler() {
        @Override
        public void handleMessage(Message msg) {
                
        		pd.dismiss();
        		
        		listOfTopicsUpdated = false;
                
                LayoutInflater factory1 = LayoutInflater.from(Dashboard.this);
                final View textEntryView1 = factory1.inflate(R.layout.dialogsubscribe, null);
                AlertDialog.Builder alert1 = new AlertDialog.Builder(Dashboard.this);
                
                spin = (Spinner) textEntryView1.findViewById(R.id.spinnerTopic);
                aa = new ArrayAdapter<String>(Dashboard.this, android.R.layout.simple_spinner_item, listOfTopics);
                //aa.setDropDownViewResource(android.R.layout.simple_spinner_item);
                spin.setAdapter(aa);
                
                alert1.setTitle("Subscribe");
            	alert1.setView(textEntryView1);
            	
            	alert1.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            	public void onClick(DialogInterface dialog, int whichButton) {
            		// Do something with value!
            		 
            		Spinner spin = (Spinner) textEntryView1.findViewById(R.id.spinnerTopic);
            		mqttService.subscribeToTopic( spin.getSelectedItem().toString());
            		MQTTService.addTopicSubscribed(spin.getSelectedItem().toString());
            	  }
            	});
            	alert1.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            	  public void onClick(DialogInterface dialog, int whichButton) {
            	    // Canceled.
            	  }
            	});
            	alert1.show(); 
                
        }
    };
    
    private Handler handlerTop = new Handler() {
        @Override
        public void handleMessage(Message msg) {
                listOfTopicsUpdated = false;
        }
    };
    
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