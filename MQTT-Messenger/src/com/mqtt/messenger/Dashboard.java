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
import android.content.SharedPreferences;
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
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.mqtt.MqttClient;
import com.ibm.mqtt.MqttException;
import com.ibm.mqtt.MqttSimpleCallback;
import com.mqtt.messenger.MQTTService.LocalBinder;


public class Dashboard extends Activity {

	
	private MQTTService mqttService;
	static boolean active = false;
	private boolean mBound = false;
	
	public static String username, password;
	public String[] listOfTopics = null;
	public boolean listOfTopicsUpdated = false;
	
	private StatusUpdateReceiver statusUpdateIntentReceiver;
    private MQTTMessageReceiver  messageIntentReceiver;
    
	private TextView messageView;
	private ScrollView scroller;
	private AlertDialog alert;
	private ProgressDialog pd;
	private Spinner spin;
    private ArrayAdapter<String> aa ;

	//For Sending Incoming Requests
	private MqttClient client;
	public String phone_id;
	static String broker, broker_incoming;
	int incomingPort;
	
	int pubQoS = 2;
	
	private String topicName;
	
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
    	
        //basic stuff
    	super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dashpage);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		//Info Dialog Builder
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("\nAn Android Application for Realtime Messaging System using MQTT\n\nGuide:\n\nProf R Mohan\n\nTeam:\n\nDinesh Babu K G\nJagadeesh M\nVasantharajan S");
		builder.setPositiveButton("Ok",null);
		alert = builder.create();
		messageView = (TextView) findViewById(R.id.message);
		scroller = (ScrollView) findViewById(R.id.scrollView1);
	
       
		SharedPreferences myPrefs = this.getSharedPreferences("myPrefs", MODE_PRIVATE);
    	username = myPrefs.getString("username", "nothing");
    	password = myPrefs.getString("password", "nothing");
    	broker 	 = myPrefs.getString("broker", "localhost");
    	incomingPort = myPrefs.getInt("port_in", 1883);
    	broker_incoming = "tcp://" + broker + ":" + incomingPort;
		
    	//Start the Service        		
    	Intent svc = new Intent(Dashboard.this, MQTTService.class);
    	startService(svc);		        

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
		
        //fetch old messages to do..
        for(String s : MQTTService.mqttMessages)
        {
        	messageView.append(s);
        }
        
}

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
            
            if(newTopic.equalsIgnoreCase(username)==true)
            {
            	if(newData.startsWith("TOPICS"))
            	{
            		listOfTopicsUpdated = true;
            		listOfTopics = newData.substring(7).split("\\#");
            		Toast.makeText(getBaseContext(), "Topics Updated!", Toast.LENGTH_SHORT).show();
            	}
            	else if(newData.equalsIgnoreCase("NO_TOPIC"))
            	{
            		listOfTopicsUpdated = true;
            		listOfTopics = null;
            		Toast.makeText(getBaseContext(), "No Topic Exists!", Toast.LENGTH_SHORT).show();
            	}
            	else if(newData.equalsIgnoreCase("TOPIC_CREATED"))
            	{
            		Toast.makeText(getBaseContext(), "Topic Created Successfully!", Toast.LENGTH_SHORT).show();
            	}
            	else if(newData.equalsIgnoreCase("TOPIC_FAILED"))
            	{
            		Toast.makeText(getBaseContext(), "Topic Creation Failed!", Toast.LENGTH_SHORT).show();
            	}
            	else if(newData.equalsIgnoreCase("MESSAGE_FILTERED"))
            	{
            		Toast.makeText(getBaseContext(), "Your Message has been Filtered!", Toast.LENGTH_SHORT).show();
            	}
            }
            else {

            	messageView.append("\nTopic     :" + newTopic + "\nMessage:" + newData +"\n\n");
                
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
	                    	
	                    	if(MQTTService.listOfTopicsSubscribed.size()>0)
	                    	{
		                    	LayoutInflater factory = LayoutInflater.from(this);
		                        final View textEntryView = factory.inflate(R.layout.dialogpublish, null);
		                        AlertDialog.Builder alert = new AlertDialog.Builder(this);           
		                    	
		                        spin = (Spinner) textEntryView.findViewById(R.id.spinnerTopic);
		                        
		                        String[] topicsSubbed = new String[ MQTTService.listOfTopicsSubscribed.size()];
		                        topicsSubbed = MQTTService.listOfTopicsSubscribed.toArray(topicsSubbed);
		                        
		                        aa = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, topicsSubbed);
		                        spin.setAdapter(aa);
		                        
		                        alert.setTitle("Publish Message");
		                    	alert.setView(textEntryView);
		                    
		                    	
		                    	alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		                    	public void onClick(DialogInterface dialog, int whichButton) {
		                    		
		                    		 
		                    		Spinner spin = (Spinner) textEntryView.findViewById(R.id.spinnerTopic);
		                    		EditText topicMessage = (EditText) textEntryView.findViewById(R.id.editMessage);
		                    		String finalTopic;
	                    			finalTopic = spin.getSelectedItem().toString();
	                    			
	                    			String msg2 = username+"#"+password+"#"+topicMessage.getText().toString();
	                    			String enc_msg = Encrypter.encrypt(msg2);
		                    		try {
		                    			client = (MqttClient) MqttClient.createMqttClient(broker_incoming, null);
		                    			client.registerSimpleHandler(new MessageHandler());
		                    			client.connect("Temp" + phone_id, true, (short) 240);
		                    			
		                    			client.publish(finalTopic, enc_msg.getBytes(), pubQoS, false);
		                    			mqttService.subscribeToTopic(finalTopic);
		                    			
		                    			} catch (MqttException e) {
		                    				e.printStackTrace();
		                    			}
		                    	  }
		                    	});
		                    	alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
		                    	  public void onClick(DialogInterface dialog, int whichButton) {
		                    	    
		                    	  }
		                    	});
		                    	alert.show();
	                    	}
	                    	else
	                    		Toast.makeText(getBaseContext(), "Not Subscribed to Any Topic!", Toast.LENGTH_SHORT).show();
	                    		break;
	                    case 2: 	          
	                    		pd = ProgressDialog.show(this, "Fetching Topics", "Please Wait..", true, false);
	                    		getListOfTopicsImmed();
	                    
	                    		break;
	                    case 3:
	                    	
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
		                        spin.setAdapter(aa);
		                        
		                        alert2.setTitle("Unsubscribe");
		                    	alert2.setView(textEntryView2);
		                    	
	
		                    	alert2.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		                    	public void onClick(DialogInterface dialog, int whichButton) {
		                    		
		                    		Spinner spin = (Spinner) textEntryView2.findViewById(R.id.spinnerTopic);
		                    		mqttService.unsubscribeToTopic(spin.getSelectedItem().toString());
		                    		MQTTService.removeTopicSubscribed(spin.getSelectedItem().toString());
		                    		Toast.makeText(getBaseContext(), "UnSubscribed Successfully!", Toast.LENGTH_SHORT).show();
		                    	  }
		                    	});
		                    	alert2.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
		                    	  public void onClick(DialogInterface dialog, int whichButton) {
		                    	
		                    	  }
		                    	});
		                    	alert2.show();
	                    	}
	                    		break;
	                    		
	                    case 4:
	                    	
	                    	LayoutInflater factory3 = LayoutInflater.from(this);
	                        final View textEntryView3 = factory3.inflate(R.layout.dialogcreatetopic, null);
	                        AlertDialog.Builder alert3 = new AlertDialog.Builder(this);           
	                        
	                        alert3.setTitle("Create Topic");
	                    	alert3.setView(textEntryView3);
	                    	                    	
	                    	alert3.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
	                    	public void onClick(DialogInterface dialog, int whichButton) {
	                    	
                    		 
                    		EditText topic = (EditText) textEntryView3.findViewById(R.id.editCreateTopic);
                    		
                    		topicName = topic.getText().toString();
                			String msg = username+"#"+password+"#"+topic.getText().toString();
                			String enc_msg = Encrypter.encrypt(msg);
                    		try {
                    			client = (MqttClient) MqttClient.createMqttClient(broker_incoming, null);
                    			client.registerSimpleHandler(new MessageHandler());
                    			client.connect("Temp" + phone_id, true, (short) 240);
                    			client.publish("CREATE_TOPIC", enc_msg.getBytes() , pubQoS, false);
                    			//mqttService.subscribeToTopic(topicName);
                    			} catch (MqttException e) {
                    				e.printStackTrace();
                    			}
	                    	

                    	  }
                    	});
                    	alert3.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    	  public void onClick(DialogInterface dialog, int whichButton) {
                    	  }
                    	});
                    	alert3.show();
	                    break;
	                    }
    }

    public void publishToIncoming(String topic, String message)
    {
    	String enc_msg = Encrypter.encrypt(message);
		try {
			client = (MqttClient) MqttClient.createMqttClient(broker_incoming, null);
			client.registerSimpleHandler(new MessageHandler());
			client.connect("jynx" + phone_id, true, (short) 240);
			client.publish(topic, enc_msg.getBytes(), pubQoS, false);
			} catch (MqttException e) {
				e.printStackTrace();
			}
    }
    
    public void getListOfTopicsImmed()
    {
    		publishToIncoming("TOPICS", username+"#"+password+"#REQUEST");
            new Thread() {
            	public void run(){
            		int t = 0;
            		while(listOfTopicsUpdated==false && t<=5000){
            			try {
    						Thread.sleep(100);
    						t += 100;
    					} catch (InterruptedException e) {
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					}
            		}
            		if(listOfTopicsUpdated==true)
            			handlerTopImmed.sendMessage(Message.obtain(handlerTopImmed, 1));
            		else
            			handlerTopImmed.sendMessage(Message.obtain(handlerTopImmed, 2));
            	}
            }.start();
    		
    }
    
    private Handler handlerTopImmed = new Handler() {
        @Override
        public void handleMessage(Message msg) {
                
        		pd.dismiss();
        		listOfTopicsUpdated = false;
        		
        		if(msg.what==1)
        		{
	        		if(listOfTopics!=null){
		        		LayoutInflater factory1 = LayoutInflater.from(Dashboard.this);
		                final View textEntryView1 = factory1.inflate(R.layout.dialogsubscribe, null);
		                AlertDialog.Builder alert1 = new AlertDialog.Builder(Dashboard.this);
		                
		                spin = (Spinner) textEntryView1.findViewById(R.id.spinnerTopic);
		                aa = new ArrayAdapter<String>(Dashboard.this, android.R.layout.simple_spinner_item, listOfTopics);
		                spin.setAdapter(aa);
		                
		                alert1.setTitle("Subscribe");
		            	alert1.setView(textEntryView1);
		            	
		            	alert1.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		            	public void onClick(DialogInterface dialog, int whichButton) {
		            		// Do something with value!
		            		 
		            		Spinner spin = (Spinner) textEntryView1.findViewById(R.id.spinnerTopic);
		            		mqttService.subscribeToTopic( spin.getSelectedItem().toString());
		            		MQTTService.addTopicSubscribed(spin.getSelectedItem().toString());
		            		Toast.makeText(getBaseContext(), "Subscribed Successfully!", Toast.LENGTH_SHORT).show();
		            	  }
		            	});
		            	alert1.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
		            	  public void onClick(DialogInterface dialog, int whichButton) {
		            	    // Canceled.
		            	  }
		            	});
		            	alert1.show(); 
	        		}
        		}
        		else if(msg.what==2)
        		{
        			Toast.makeText(getBaseContext(), "Fetching Topics Timedout, Try Again Later!", Toast.LENGTH_SHORT).show();
        		}
        }
    };
    
	@SuppressWarnings("unused")
	private class MessageHandler implements MqttSimpleCallback 
	{
		public void publishArrived(String _topic, byte[] payload, int qos, boolean retained) throws Exception 
		{
			//no work to do
		}

		public void connectionLost() throws Exception 
		{
			client = null;
			Log.v("HelloMQTT", "connection dropped");
		}
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
        
						        SharedPreferences myPrefs = this.getSharedPreferences("myPrefs", MODE_PRIVATE);
						        SharedPreferences.Editor prefsEditor = myPrefs.edit();
						        prefsEditor.putString("username", "nothing");
						        prefsEditor.putString("password", "nothing");
						        prefsEditor.commit();
						    	
        						finish();
								break;
								
        case R.id.dashitem5:	alert.show();
        						break;
        						
        case R.id.dashitem6:	performMQTTAction(4);
        						break;
        						
            default:     		break;
        }
        return true;
    }
    
}      