package com.mqtt.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.ibm.mqtt.MqttClient;
import com.ibm.mqtt.MqttException;
import com.ibm.mqtt.MqttNotConnectedException;
import com.ibm.mqtt.MqttPersistenceException;
import com.ibm.mqtt.MqttSimpleCallback;

public class StartPage extends Activity {

	public EditText uname,pwd;
	private String username,password;
	AlertDialog alert;
	private static int registerResponse = 0;
	private static int loginResponse = 0;
	private static String phone_id;
	private ProgressDialog pd;
	private MqttClient client;
	final static String broker = "tcp://test.mosquitto.org:1883";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		//Basic stuff
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);	//hide the title bar
		setContentView(R.layout.startpage);
		phone_id = Settings.System.getString(getContentResolver(),Secure.ANDROID_ID);
		
		if(MQTTService.SERVICE_STAT==true)
		{
			Intent i = new Intent(StartPage.this, Dashboard.class);
			startActivity(i);
			finish();
		}
		else
		{
			//Info Dialog Builder
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("\nAn Android Application for Realtime Messaging System using MQTT\n\nTeam Members:\n\nDinesh Babu K G\nJagadeesh M\nVasantharajan S");
			alert = builder.create();
			
		}
		
	}


	@Override
	public void onDestroy()
	{
		super.onDestroy();
		client.terminate();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.startmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.startitem1:	finish();
			break;
		case R.id.startitem2:	alert.show();
			break;
		default:
			break;
		}
		return true;
	}
	
	public void processLogin(View v) {
		
		uname = (EditText) findViewById(R.id.editText1);
		username = uname.getText().toString();
		pwd = (EditText) findViewById(R.id.editText2);
		password = pwd.getText().toString();		
		if( ((Button)v).getText().equals("Login"))
		{
			processLogin();
		}
		else if( ((Button)v).getText().equals("Register"))
		{
			processRegister();
		}
	}
	
	
	public void processRegister()
	{

	        String msg = phone_id+"#"+username+"#"+password;
	        
			try {
				client = (MqttClient) MqttClient.createMqttClient(broker, null);
				client.registerSimpleHandler(new MessageHandler());
				client.connect("Temp" + phone_id, true, (short) 240);
				client.subscribe(new String[]{phone_id}, new int[]{1});
				} catch (MqttException e) {
			// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
				try {
					client.publish("REGISTER", msg.getBytes() ,1, false);
				} catch (MqttNotConnectedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (MqttPersistenceException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (IllegalArgumentException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (MqttException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}	
				
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
	            	SharedPreferences myPrefs = StartPage.this.getSharedPreferences("myPrefs", MODE_PRIVATE);
			        SharedPreferences.Editor prefsEditor = myPrefs.edit();
			        prefsEditor.putString("username", username);
			        prefsEditor.putString("password", password);
			        prefsEditor.commit();
	            	Toast.makeText(getBaseContext(), "Registered Successfully!", Toast.LENGTH_SHORT).show();
	            	
	            	Intent i = new Intent(StartPage.this, Dashboard.class);
	            	startActivity(i);
	            	finish();

	            	
	            }
	            else if(msg.what==2)
	            {
	            	Toast.makeText(getBaseContext(), "Registration Failed, Try Again later", Toast.LENGTH_SHORT).show();
	            }
	    }
	};
	  
	public void processLogin()
	{
		String msg = phone_id+"#"+username+"#"+password;
		
		try {
			client = (MqttClient) MqttClient.createMqttClient(broker, null);
			client.registerSimpleHandler(new MessageHandler());
			client.connect("Temp" + phone_id, true, (short) 240);
			client.subscribe(new String[]{phone_id}, new int[]{1});
			} catch (MqttException e) {
		// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
			try {
				client.publish("LOGIN", msg.getBytes() ,1, false);
			} catch (MqttNotConnectedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (MqttPersistenceException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IllegalArgumentException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (MqttException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}	
			
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

	            	SharedPreferences myPrefs = StartPage.this.getSharedPreferences("myPrefs", MODE_PRIVATE);
			        SharedPreferences.Editor prefsEditor = myPrefs.edit();
			        prefsEditor.putString("username", username);
			        prefsEditor.putString("password", password);
			        prefsEditor.commit();
			        
			        Toast.makeText(getBaseContext(), "Login Correct", Toast.LENGTH_SHORT).show();
	            	
			        Intent i = new Intent(StartPage.this, Dashboard.class);
					startActivity(i);
					finish();
	            }
	            else if(msg.what==2)
	            {
	            	Toast.makeText(getBaseContext(), "Login Incorrect. Try Again!", Toast.LENGTH_SHORT).show();
	            }
	    }
	};

	//MQTT Functions
	final Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			if(msg.getData().getString("topic").equals(phone_id))
			{
					String newData = msg.getData().getString("message");
	            	if(newData.equals("REG_SUCCESS")) 
	            		registerResponse = 1;
	            	else if(newData.equals("REG_FAILURE")) 
	            		registerResponse = 2;
	            	else if(newData.equals("LOGIN_SUCCESS")) 
	            		loginResponse = 1;
	            	else if(newData.equals("LOGIN_FAILED")) 
	            		loginResponse = 2;
			}
		}
	};
	
	@SuppressWarnings("unused")
	private class MessageHandler implements MqttSimpleCallback 
	{
		public void publishArrived(String _topic, byte[] payload, int qos, boolean retained) throws Exception 
		{
			String _message = new String(payload);
			Bundle b = new Bundle();
			b.putString("topic", _topic);
			b.putString("message", _message);
			Message msg = handler.obtainMessage();
			msg.setData(b);
			handler.sendMessage(msg);
			Log.d("MQTT", _message);
		}

		public void connectionLost() throws Exception 
		{
			client = null;
			Log.v("HelloMQTT", "connection dropped");
		}
	}

}