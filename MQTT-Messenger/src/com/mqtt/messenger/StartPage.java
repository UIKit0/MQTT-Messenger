package com.mqtt.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class StartPage extends Activity {

	public EditText uname,pwd;
	private String username,password;
	AlertDialog alert;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		//Basic stuff
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);	//hide the title bar
		setContentView(R.layout.startpage);
		
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
			
			  Bundle extras = getIntent().getExtras();
			    if(extras != null)
			    {
			        Toast.makeText(this, extras.getString("login"), Toast.LENGTH_SHORT);
			    }
		}
		
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
		Intent i = new Intent(StartPage.this, Dashboard.class);
		i.putExtra("username", username);
		i.putExtra("password", password);
		if( ((Button)v).getText().equals("Login"))
			i.putExtra("action","1");
		else if( ((Button)v).getText().equals("Register"))
			i.putExtra("action","2");
		
		startActivity(i);
		finish();
	}

}