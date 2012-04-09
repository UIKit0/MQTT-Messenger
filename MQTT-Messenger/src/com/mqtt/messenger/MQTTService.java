package com.mqtt.messenger;

import java.util.Calendar;

import java.util.Enumeration;
import java.util.Hashtable;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;

import com.ibm.mqtt.IMqttClient;
import com.ibm.mqtt.MqttClient;
import com.ibm.mqtt.MqttException;
import com.ibm.mqtt.MqttNotConnectedException;
import com.ibm.mqtt.MqttPersistence;
import com.ibm.mqtt.MqttPersistenceException;
import com.ibm.mqtt.MqttSimpleCallback;



public class MQTTService extends Service implements MqttSimpleCallback {


    //constants
    public static final String APP_ID = "com.mqtt.messenger";
    public static final String clientPrefix = "MESSENGER";
    
    // constants used to notify the Activity UI of received messages
    public static final String MQTT_MSG_RECEIVED_INTENT = "com.mqtt.messenger.MSGRECVD";
    public static final String MQTT_MSG_RECEIVED_TOPIC  = "com.mqtt.messenger.MSGRECVD_TOPIC";
    public static final String MQTT_MSG_RECEIVED_MSG    = "com.mqtt.messenger.MSGRECVD_MSGBODY";

    // constants used to tell the Activity UI the connection status
    public static final String MQTT_STATUS_INTENT = "com.mqtt.messenger.STATUS";
    public static final String MQTT_STATUS_MSG    = "com.mqtt.messenger.STATUS_MSG";

    // constant used internally to schedule the next ping event
    public static final String MQTT_PING_ACTION = "com.mqtt.messenger.PING";

    // constants used by status bar notifications
    public static final int MQTT_NOTIFICATION_ONGOING = 1;
    public static final int MQTT_NOTIFICATION_UPDATE  = 2;

    public static boolean SERVICE_STAT;
    
    // constants used to define MQTT connection status
    public enum MQTTConnectionStatus
    {
        INITIAL,                           
        CONNECTING,                        
        CONNECTED,                         
        NOTCONNECTED_WAITINGFORINTERNET,   
        NOTCONNECTED_USERDISCONNECT,       
        NOTCONNECTED_DATADISABLED,         
        NOTCONNECTED_UNKNOWNREASON         
    }

    // MQTT constants
    public static final int MAX_MQTT_CLIENTID_LENGTH = 22;

    /************************************************************************/
    /*    VARIABLES used to maintain state                                  */
    /************************************************************************/

    // status of MQTT client connection
    private MQTTConnectionStatus connectionStatus = MQTTConnectionStatus.INITIAL;

    /************************************************************************/
    /*    VARIABLES used to configure MQTT connection                       */
    /************************************************************************/


    private String          brokerHostName       = "test.mosquitto.org";			//Custom Server
    private String          initialTopicName     = "";					//The Phone_id which will be published from Dashboard Activity    

    // defaults - this sample uses very basic defaults for it's interactions
    //   with message brokers
    private int             brokerPortNumber     = 1883;
    
    private MqttPersistence usePersistence       = null;
    private boolean         cleanStart           = false;
    private int[]           qualitiesOfService   = { 0 } ;

    //  how often should the app ping the server to keep the connection alive?
    private short           keepAliveSeconds     = 20 * 60; 

    // This is how the Android client app will identify itself to the message Broker
    private String          mqttClientId = null; 

    //VARIABLES  - other local variables
    
    // connection to the message broker
    private IMqttClient mqttClient = null;

    // receiver that notifies the Service when the phone gets data connection
    private NetworkConnectionIntentReceiver netConnReceiver;

    // receiver that notifies the Service when the user changes data use preferences
    private BackgroundDataChangeIntentReceiver dataEnabledReceiver;

    // receiver that wakes the Service up when it's time to ping the server
    private PingSender pingSender;

    
    @Override
    public void onCreate()
    {
        super.onCreate();
        
    	Log.d("service","Entering oncreate");
    	
    	SERVICE_STAT = true;
    	
        // reset status variable to initial state
        connectionStatus = MQTTConnectionStatus.INITIAL;

        initialTopicName = Settings.System.getString(getContentResolver(),Secure.ANDROID_ID); //Unique Topic for this Client
        
        // register to be notified whenever the user changes their preferences relating to background data use 
        // so that we can respect the current preference
        dataEnabledReceiver = new BackgroundDataChangeIntentReceiver();
        registerReceiver(dataEnabledReceiver, new IntentFilter(ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED));

        // define the connection to the broker
        defineConnectionToBroker(brokerHostName);
        
        Log.d("service","Exiting oncreate");
    }

    @Override
    public void onStart(final Intent intent, final int startId)
    {
        // This is the old onStart method that will be called on the pre-2.0
        // platform.  On 2.0 or later we override onStartCommand() so this
        // method will not be called.        

        new Thread(new Runnable() {
            @Override
            public void run() {
                handleStart(intent, startId);
            }
        }, "MQTTservice").start();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId)
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                handleStart(intent, startId);
            }
        }, "MQTTservice").start();

        // return START_NOT_STICKY - we want this Service to be left running
        //  unless explicitly stopped, and it's process is killed, we want it to
        //  be restarted
        return START_STICKY;
    }

    synchronized void handleStart(Intent intent, int startId)
    {
        // before we start - check for a couple of reasons why we should stop
        if (mqttClient == null)
        {
            // we were unable to define the MQTT client connection, so we stop immediately - there is nothing that we can do
            stopSelf();
            return;
        }

        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        
        if (cm.getBackgroundDataSetting() == false) // respect the user's request not to use data!
        {
            // user has disabled background data
            connectionStatus = MQTTConnectionStatus.NOTCONNECTED_DATADISABLED;

            // update the app to show that the connection has been disabled
            broadcastServiceStatus("Not connected - background data disabled");

            // we have a listener running that will notify us when this
            //   preference changes, and will call handleStart again when it
            //   is - letting us pick up where we leave off now
            return;
        }

        // the Activity UI has started the MQTT service - this may be starting
        //  the Service new for the first time, or after the Service has been
        //  running for some time (multiple calls to startService don't start
        //  multiple Services, but it does call this method multiple times)
        // if we have been running already, we re-send any stored data
        rebroadcastStatus();
        rebroadcastReceivedMessages();

        // if the Service was already running and we're already connected - we don't need to do anything
        if (isAlreadyConnected() == false)
        {
            // set the status to show we're trying to connect
            connectionStatus = MQTTConnectionStatus.CONNECTING;

            // Ongoing notification in the Status Bar showing that the Service is Running
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification notification = new Notification(R.drawable.ic_launcher, "MQTT", System.currentTimeMillis());
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            notification.flags |= Notification.FLAG_NO_CLEAR;
            Intent notificationIntent = new Intent(this, Dashboard.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            notification.setLatestEventInfo(this, "MQTT", "MQTT Service is running", contentIntent);
            nm.notify(MQTT_NOTIFICATION_ONGOING, notification);

            // before we attempt to connect - we check if the phone has a working data connection
            if (isOnline())
            {
                if (connectToBroker())
                {
                    //subscribeToTopic(initialTopicName);
                	subscribeToTopic(initialTopicName);
                }
            }
            else
            {
                connectionStatus = MQTTConnectionStatus.NOTCONNECTED_WAITINGFORINTERNET;
                broadcastServiceStatus("Waiting for network connection");
            }
        }

       /*
        * changes to the phone's network - such as bouncing between WiFi
        *  and mobile data networks - can break the MQTT connection
        * the MQTT connectionLost can be a bit slow to notice, so we use
        *  Android's inbuilt notification system to be informed of
        *  network changes - so we can reconnect immediately, without
        *  having to wait for the MQTT timeout
        */
        if (netConnReceiver == null)
        {
            netConnReceiver = new NetworkConnectionIntentReceiver();
            registerReceiver(netConnReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        }

        // creates the intents that are used to wake up the phone when it is time to ping the server
        if (pingSender == null)
        {
            pingSender = new PingSender();
            registerReceiver(pingSender, new IntentFilter(MQTT_PING_ACTION));
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        SERVICE_STAT = false;
       
        disconnectFromBroker();

        broadcastServiceStatus("Disconnected");

        if (dataEnabledReceiver != null)
        {
            unregisterReceiver(dataEnabledReceiver);
            dataEnabledReceiver = null;
        }
    }

    /************************************************************************/
    /*    METHODS - broadcasts and notifications                            */
    /************************************************************************/

    // methods used to notify the Activity UI of something that has happened
    //  so that it can be updated to reflect status and the data received
    //  from the server

    private void broadcastServiceStatus(String statusDescription)
    {
        // inform the app (for times when the Activity UI is running /
        //   active) of the current MQTT connection status so that it
        //   can update the UI accordingly
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MQTT_STATUS_INTENT);
        broadcastIntent.putExtra(MQTT_STATUS_MSG, statusDescription);
        sendBroadcast(broadcastIntent);
    }

    private void broadcastReceivedMessage(String topic, String message)
    {
        // pass a message received from the MQTT server on to the Activity UI
        //   (for times when it is running / active) so that it can be displayed
        //   in the app GUI
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MQTT_MSG_RECEIVED_INTENT);
        broadcastIntent.putExtra(MQTT_MSG_RECEIVED_TOPIC, topic);
        broadcastIntent.putExtra(MQTT_MSG_RECEIVED_MSG,   message);
        sendBroadcast(broadcastIntent);
    }

    // methods used to notify the user of what has happened for times when
    //  the app Activity UI isn't running

    private void notifyUser(String alert, String title, String body)
    {
    	
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = new Notification(android.R.drawable.ic_dialog_email, alert,System.currentTimeMillis());
        notification.defaults |= Notification.DEFAULT_LIGHTS;
        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.defaults |= Notification.DEFAULT_VIBRATE;
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        Intent notificationIntent = new Intent(this, Dashboard.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,notificationIntent,PendingIntent.FLAG_UPDATE_CURRENT);
        notification.setLatestEventInfo(this, title, body, contentIntent);
        nm.notify(MQTT_NOTIFICATION_UPDATE, notification);
    }
    
    //Binding Methods
    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        MQTTService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MQTTService.this;
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // public methods that can be used by Activities that bind to the Service

    public void publishToTopic(String topicName, String message)
    {
        boolean published = false;

        if (isAlreadyConnected() == false)
        {
            // quick sanity check - don't try and subscribe if we
            //  don't have a connection

            Log.e("mqtt", "Unable to publish as we are not connected");
        }
        else
        {
            try
            {
                mqttClient.publish(topicName, message.getBytes() ,1, false);
                published = true;
            }
            catch (MqttNotConnectedException e)
            {
                Log.e("mqtt", "publish failed - MQTT not connected", e);
            }
            catch (IllegalArgumentException e)
            {
                Log.e("mqtt", "publish failed - illegal argument", e);
            }
            catch (MqttException e)
            {
                Log.e("mqtt", "publish failed - MQTT exception", e);
            }
        }

        if (published == false)
        {
            //
            // inform the app of the failure to subscribe so that the UI can
            //  display an error
            broadcastServiceStatus("Unable to publish");

            //
            // inform the user (for times when the Activity UI isn't running)
            notifyUser("Unable to publish", "MQTT", "Unable to publish");
        }
    }
    
    public void subscribeToTopic(String topicName)
    {
        boolean subscribed = false;

        if (isAlreadyConnected() == false)
        {
            // quick sanity check - don't try and subscribe if we
            //  don't have a connection

            Log.e("mqtt", "Unable to subscribe as we are not connected");
        }
        else
        {
            try
            {
                String[] topics = { topicName };
                mqttClient.subscribe(topics, qualitiesOfService);

                subscribed = true;
            }
            catch (MqttNotConnectedException e)
            {
                Log.e("mqtt", "subscribe failed - MQTT not connected", e);
            }
            catch (IllegalArgumentException e)
            {
                Log.e("mqtt", "subscribe failed - illegal argument", e);
            }
            catch (MqttException e)
            {
                Log.e("mqtt", "subscribe failed - MQTT exception", e);
            }
        }

        if (subscribed == false)
        {
            //
            // inform the app of the failure to subscribe so that the UI can
            //  display an error
            broadcastServiceStatus("Unable to subscribe");

            //
            // inform the user (for times when the Activity UI isn't running)
            notifyUser("Unable to subscribe", "MQTT", "Unable to subscribe");
        }
    }
    
    public void unsubscribeToTopic(String topicName)
    {
        boolean unsubscribed = false;

        if (isAlreadyConnected() == false)
        {
            // quick sanity check - don't try and subscribe if we
            //  don't have a connection

            Log.e("mqtt", "Unable to unsubscribe as we are not connected");
        }
        else
        {
            try
            {
                String[] topics = { topicName };
                mqttClient.unsubscribe(topics);
                unsubscribed = true;
            }
            catch (MqttNotConnectedException e)
            {
                Log.e("mqtt", "unsubscribe failed - MQTT not connected", e);
            }
            catch (IllegalArgumentException e)
            {
                Log.e("mqtt", "unsubscribe failed - illegal argument", e);
            }
            catch (MqttException e)
            {
                Log.e("mqtt", "unsubscribe failed - MQTT exception", e);
            }
        }

        if (unsubscribed == false)
        {
            //
            // inform the app of the failure to subscribe so that the UI can
            //  display an error
            broadcastServiceStatus("Unable to unsubscribe");

            //
            // inform the user (for times when the Activity UI isn't running)
            notifyUser("Unable to unsubscribe", "MQTT", "Unable to unsubscribe");
        }
    }


    public MQTTConnectionStatus getConnectionStatus()
    {
        return connectionStatus;
    }    

    public void rebroadcastStatus()
    {
        String status = "";
        switch (connectionStatus)
        {
            case INITIAL:
                status = "Please wait";
                break;
            case CONNECTING:
                status = "Connecting...";
                break;
            case CONNECTED:
                status = "Connected";
                break;
            case NOTCONNECTED_UNKNOWNREASON:
                status = "Not connected - waiting for network connection";
                break;
            case NOTCONNECTED_USERDISCONNECT:
                status = "Disconnected";
                break;
            case NOTCONNECTED_DATADISABLED:
                status = "Not connected - background data disabled";
                break;
            case NOTCONNECTED_WAITINGFORINTERNET:
                status = "Unable to connect";
                break;
        }

        // inform the app that the Service has successfully connected
        broadcastServiceStatus(status);
    }

    public void disconnect()
    {
        disconnectFromBroker();

        // set status
        connectionStatus = MQTTConnectionStatus.NOTCONNECTED_USERDISCONNECT;

        // inform the app that the app has successfully disconnected
        broadcastServiceStatus("Disconnected");
    }

    // METHODS - MQTT methods inherited from MQTT classes

    /*
     * callback - method called when we no longer have a connection to the
     *  message broker server
     */
    public void connectionLost() throws Exception
    {
        // we protect against the phone switching off while we're doing this
        //  by requesting a wake lock - we request the minimum possible wake
        //  lock - just enough to keep the CPU running until we've finished
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
        wl.acquire();

        //
        // have we lost our data connection?
        //

        if (isOnline() == false)
        {
            connectionStatus = MQTTConnectionStatus.NOTCONNECTED_WAITINGFORINTERNET;

            // inform the app that we are not connected any more
            broadcastServiceStatus("Connection lost - no network connection");

            //
            // inform the user (for times when the Activity UI isn't running)
            //   that we are no longer able to receive messages
            notifyUser("Connection lost - no network connection","MQTT", "Connection lost - no network connection");

            //
            // wait until the phone has a network connection again, when we
            //  the network connection receiver will fire, and attempt another
            //  connection to the broker
        }
        else
        {
            //
            // we are still online
            //   the most likely reason for this connectionLost is that we've
            //   switched from wifi to cell, or vice versa
            //   so we try to reconnect immediately
            // 

            connectionStatus = MQTTConnectionStatus.NOTCONNECTED_UNKNOWNREASON;

            // inform the app that we are not connected any more, and are
            //   attempting to reconnect
            broadcastServiceStatus("Connection lost - reconnecting...");

            // try to reconnect
            if (connectToBroker()) {
                subscribeToTopic(initialTopicName);
            }
        }

        // we're finished - if the phone is switched off, it's okay for the CPU
        //  to sleep now
        wl.release();
    }

    /*
     *   callback - called when we receive a message from the server
     */
    public void publishArrived(String topic, byte[] payloadbytes, int qos, boolean retained)
    {
        // we protect against the phone switching off while we're doing this
        //  by requesting a wake lock - we request the minimum possible wake
        //  lock - just enough to keep the CPU running until we've finished
    	
    	Log.d("ServiceMQTT","Entering publishArrived!");
    	
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
        wl.acquire();

        //
        //  I'm assuming that all messages I receive are being sent as strings
        //   this is not an MQTT thing - just me making as assumption about what
        //   data I will be receiving - your app doesn't have to send/receive
        //   strings - anything that can be sent as bytes is valid
        String messageBody = new String(payloadbytes);

        //
        //  for times when the app's Activity UI is not running, the Service
        //   will need to safely store the data that it receives
        if (addReceivedMessageToStore(topic, messageBody))
        {
            // this is a new message - a value we haven't seen before

            //
            // inform the app (for times when the Activity UI is running) of the
            //   received message so the app UI can be updated with the new data
            broadcastReceivedMessage(topic, messageBody);

            //
            // inform the user (for times when the Activity UI isn't running)
            //   that there is new data available
            
            /*if(!topic.equals(initialTopicName))	//Check if the Topic is NOT a Server Response
            	notifyUser("New Message received", topic, messageBody);*/
        }

        // receiving this message will have kept the connection alive for us, so
        //  we take advantage of this to postpone the next scheduled ping
        scheduleNextPing();

        // we're finished - if the phone is switched off, it's okay for the CPU
        //  to sleep now
        wl.release();
    }

    /************************************************************************/
    /*    METHODS - wrappers for some of the MQTT methods that we use       */
    /************************************************************************/

    /*
     * Create a client connection object that defines our connection to a
     *   message broker server
     */
    private void defineConnectionToBroker(String brokerHostName)
    {
        String mqttConnSpec = "tcp://" + brokerHostName + "@" + brokerPortNumber;

        try
        {
            // define the connection to the broker
            mqttClient = MqttClient.createMqttClient(mqttConnSpec, usePersistence);

            // register this client app has being able to receive messages
            mqttClient.registerSimpleHandler(this);
        }
        catch (MqttException e)
        {
            // something went wrong!
            mqttClient = null;
            connectionStatus = MQTTConnectionStatus.NOTCONNECTED_UNKNOWNREASON;

            //
            // inform the app that we failed to connect so that it can update
            //  the UI accordingly
            broadcastServiceStatus("Invalid connection parameters");

            //
            // inform the user (for times when the Activity UI isn't running)
            //   that we failed to connect
            notifyUser("Unable to connect", "MQTT", "Unable to connect");
        }
    }

    /*
     * (Re-)connect to the message broker
     */
    private boolean connectToBroker()
    {
        try
        {
            // try to connect
            mqttClient.connect(generateClientId(), cleanStart, keepAliveSeconds);

            //
            // inform the app that the app has successfully connected
            broadcastServiceStatus("Connected");

            // we are connected
            connectionStatus = MQTTConnectionStatus.CONNECTED;

            // we need to wake up the phone's CPU frequently enough so that the
            //  keep alive messages can be sent
            // we schedule the first one of these now
            scheduleNextPing();

            return true;
        }
        catch (MqttException e)
        {
            // something went wrong!

            connectionStatus = MQTTConnectionStatus.NOTCONNECTED_UNKNOWNREASON;

            //
            // inform the app that we failed to connect so that it can update
            //  the UI accordingly
            broadcastServiceStatus("Unable to connect");

            //
            // inform the user (for times when the Activity UI isn't running)
            //   that we failed to connect
            notifyUser("Unable to connect", "MQTT", "Unable to connect - will retry later");        

            // if something has failed, we wait for one keep-alive period before
            //   trying again
            // in a real implementation, you would probably want to keep count
            //  of how many times you attempt this, and stop trying after a
            //  certain number, or length of time - rather than keep trying
            //  forever.
            // a failure is often an intermittent network issue, however, so
            //  some limited retry is a good idea
            scheduleNextPing();

            return false;
        }
    }

       /*
     * Terminates a connection to the message broker.
     */
    private void disconnectFromBroker()
    {
        // if we've been waiting for an Internet connection, this can be
        //  cancelled - we don't need to be told when we're connected now
        try
        {
            if (netConnReceiver != null)
            {
                unregisterReceiver(netConnReceiver);
                netConnReceiver = null;
            }

            if (pingSender != null)
            {
                unregisterReceiver(pingSender);
                pingSender = null;
            }
        }
        catch (Exception eee)
        {
            // probably because we hadn't registered it
            Log.e("mqtt", "unregister failed", eee);
        }

        try
        {
            if (mqttClient != null)
            {
                mqttClient.disconnect();
            }
        }
        catch (MqttPersistenceException e)
        {
            Log.e("mqtt", "disconnect failed - persistence exception", e);
        }
        finally
        {
            mqttClient = null;
        }

        // we can now remove the ongoing notification that warns users that
        //  there was a long-running ongoing service running
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancelAll();
    }

    /*
     * Checks if the MQTT client thinks it has an active connection
     */
    public boolean isAlreadyConnected()
    {
        return ((mqttClient != null) && (mqttClient.isConnected() == true));
    }

    private class BackgroundDataChangeIntentReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context ctx, Intent intent)
        {
            // we protect against the phone switching off while we're doing this
            //  by requesting a wake lock - we request the minimum possible wake
            //  lock - just enough to keep the CPU running until we've finished
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
            wl.acquire();

            ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if (cm.getBackgroundDataSetting())
            {
                // user has allowed background data - we start again - picking
                //  up where we left off in handleStart before
                defineConnectionToBroker(brokerHostName);
                handleStart(intent, 0);
            }
            else
            {
                // user has disabled background data
                connectionStatus = MQTTConnectionStatus.NOTCONNECTED_DATADISABLED;

                // update the app to show that the connection has been disabled
                broadcastServiceStatus("Not connected - background data disabled");

                // disconnect from the broker
                disconnectFromBroker();
            }            

            // we're finished - if the phone is switched off, it's okay for the CPU
            //  to sleep now
            wl.release();
        }
    }

    /*
     * Called in response to a change in network connection - after losing a
     *  connection to the server, this allows us to wait until we have a usable
     *  data connection again
     */
    private class NetworkConnectionIntentReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context ctx, Intent intent)
        {
            // we protect against the phone switching off while we're doing this
            //  by requesting a wake lock - we request the minimum possible wake
            //  lock - just enough to keep the CPU running until we've finished
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
            wl.acquire();

            if (isOnline())
            {
                // we have an internet connection - have another try at connecting
                if (connectToBroker())
                {
                    // we subscribe to a topic - registering to receive push
                    //  notifications with a particular key
                    subscribeToTopic(initialTopicName);
                }
            }

            // we're finished - if the phone is switched off, it's okay for the CPU
            //  to sleep now
            wl.release();
        }
    }

    /*
     * Schedule the next time that you want the phone to wake up and ping the message broker server
     */
    private void scheduleNextPing()
    {

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,  new Intent(MQTT_PING_ACTION),PendingIntent.FLAG_UPDATE_CURRENT);

        Calendar wakeUpTime = Calendar.getInstance();
        wakeUpTime.add(Calendar.SECOND, keepAliveSeconds);

        AlarmManager aMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        aMgr.set(AlarmManager.RTC_WAKEUP, wakeUpTime.getTimeInMillis(),pendingIntent);
    }

    /*
     * Used to implement a keep-alive protocol at this Service level - it sends a PING message to the server,
     * then schedules another ping after an interval defined by "keepAliveSeconds"
     */
    public class PingSender extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            try
            {
                mqttClient.ping();
            }
            catch (MqttException e)
            {
                Log.e("mqtt", "ping failed - MQTT exception", e);
                
                try {
                    mqttClient.disconnect();
                }
                catch (MqttPersistenceException e1) {
                    Log.e("mqtt", "disconnect failed - persistence exception", e1);
                }
                if (connectToBroker()) {
                    subscribeToTopic(initialTopicName);
                }
            }
            scheduleNextPing();
        }
    }

    /*  DATA STORAGE OF RECEIVED MESSAGES */

    //Local HashTable to Hold The MQTT Messages Sent out From the Server
    private Hashtable<String, String> dataCache = new Hashtable<String, String>(); 

    private boolean addReceivedMessageToStore(String key, String value)
    {
        String previousValue = null;

        if (value.length() == 0)
        {
            previousValue = dataCache.remove(key);
        }
        else
        {
            previousValue = dataCache.put(key, value);
        }
        //  we return true if the received message is NEW
        return ((previousValue == null) || (previousValue.equals(value) == false));
    }

    // Provide a public interface, so Activities that bind to the Service can request access to previously received messages
    //This Method broadcasts all the previously Received Messages
    public void rebroadcastReceivedMessages()
    {
        Enumeration<String> e = dataCache.keys();
        while(e.hasMoreElements())
        {
            String nextKey = e.nextElement();
            String nextValue = dataCache.get(nextKey);

            broadcastReceivedMessage(nextKey, nextValue);
        }
    }


    /* METHODS - internal utility methods */
    
    //Generate a Unique Client ID based on ANDROID_ID
    private String generateClientId()
    {
        if (mqttClientId == null)
        {
            String android_id = Settings.System.getString(getContentResolver(),Secure.ANDROID_ID);
            mqttClientId = clientPrefix + android_id;
            if (mqttClientId.length() > MAX_MQTT_CLIENTID_LENGTH) {
                mqttClientId = mqttClientId.substring(0, MAX_MQTT_CLIENTID_LENGTH);
            }
        }
        return mqttClientId;
    }

    //Check whether the device is connected to the Internet
    private boolean isOnline()
    {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        if(cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isAvailable() && cm.getActiveNetworkInfo().isConnected())
        {
            return true;
        }
        return false;
    }
}
