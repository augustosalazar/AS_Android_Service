package com.augustosalazar.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GpsService extends Service {

    private NotificationManager mNotificationManager;
    private static final String TAG = GpsService.class.getSimpleName();
    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 1000;
    private static final float LOCATION_DISTANCE = 10f;
    private List<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler());
    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_SET_INT_VALUE = 3;
    public static final int MSG_SET_STRING_VALUE = 4;
    private int counter = 0, incrementBy = 1;
    private static boolean isRunning = false;
    Notification notification;


    private static final String FILE_HEADER = "TIMESTAMP,LAT,LONG,ALT,SPEED";
    public static final String FILE_PATH_ROOT = Environment.getExternalStorageDirectory().getPath();
    public static final String APP_FOLDER_NAME = "/ServiceTest/";
    public static final String APP_FOLDER_PATH = FILE_PATH_ROOT
            + APP_FOLDER_NAME;
    private File mValidationFile;
    private BufferedWriter mValidationWriter;
    private StringBuilder mStringValidation;



    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;
        int value = 0;

        public LocationListener(String provider) {
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.e(TAG, "onLocationChanged: " + location);
            mLastLocation.set(location);
            sendMessageToUI(location.getLatitude(), location.getLongitude());
            counter = counter + 1;
            updateNotification();
            writeToFile(location);

        }

        @Override
        public void onProviderDisabled(String provider) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }
    }

    LocationListener[] mLocationListeners = new LocationListener[]{
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(TAG, "onBind");
        return mMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate");
        initializeLocationManager();
        showNotification();
        initFile();
        isRunning = true;
        counter = 0;

        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }
    }


    private void initFile(){
        String fileName;

        fileName = APP_FOLDER_PATH + "FINAL_RAW_DATA.csv";
        mValidationFile = new File(fileName);

        if (!mValidationFile.exists()) {
            try {
                mValidationFile.getParentFile().mkdirs();
                mValidationFile.createNewFile();
                mValidationWriter = new BufferedWriter(new FileWriter(
                        mValidationFile));
                Log.d(TAG, "New VALIDATION file created OK!");
            } catch (IOException e) {
                Log.d(TAG, "Error at creating " + fileName);
                e.printStackTrace();
            }
        } else {
            try {
                mValidationWriter = new BufferedWriter(new FileWriter(
                        mValidationFile, true));
            } catch (IOException e) {
                Log.d(TAG, "Error at opening " + fileName);
                e.printStackTrace();
            }
            Log.d(TAG, "VALIDATION file opened succesfully!");
        }
    }

    private void writeToFile(Location location){
        if (mStringValidation == null)
            mStringValidation = new StringBuilder();

        mStringValidation.setLength(0);
        mStringValidation.append(String.valueOf(System.currentTimeMillis()));
        mStringValidation.append("," + String.valueOf(location.getLatitude()));
        mStringValidation.append("," + String.valueOf(location.getLongitude()));
        mStringValidation.append("," + String.valueOf(location.getAltitude()));
        mStringValidation.append("," + String.valueOf(location.getSpeed()));

        try {
            if (mValidationWriter != null) {
                mValidationWriter.newLine();
                mValidationWriter.write(mStringValidation.toString());
            }
        } catch (IOException e) {
            Log.d(TAG,
                    "Error at writing in VALIDATION: "
                            + mStringValidation.toString());
            e.printStackTrace();
        }
    }

    public void closeFile(){

        if (mValidationWriter != null) {
            try {
                mValidationWriter.close();
                mValidationWriter = null;
            } catch (IOException e) {
                Log.d(TAG, "Error at closing LOCATION BufferedWriter");
                e.printStackTrace();
            }
        }
    }


    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onDestroy() {
        Log.e(TAG, "onDestroy");
        super.onDestroy();
        if (mLocationManager != null) {
            for (int i = 0; i < mLocationListeners.length; i++) {
                try {
                    mLocationManager.removeUpdates(mLocationListeners[i]);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listners, ignore", ex);
                }
            }
        }
        mNotificationManager.cancel(R.mipmap.ic_launcher); // Cancel the persistent notification
        isRunning = false;
        closeFile();
    }

    private void showNotification() {
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Set the icon, scrolling text and timestamp
        notification = new Notification(R.mipmap.ic_launcher, "Servicio Iniciado", System.currentTimeMillis());
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, "Service", "Servicio Iniciado", contentIntent);
        // Send the notification.
        // We use a layout id because it is a unique number.  We use it later to cancel.
        mNotificationManager.notify(R.mipmap.ic_launcher, notification);

    }

    private void updateNotification() {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        notification.setLatestEventInfo(this, "Service", "Servicio Iniciado " + counter, contentIntent);
        mNotificationManager.notify(R.mipmap.ic_launcher, notification);
    }

    private void initializeLocationManager() {
        Log.e(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }


    private void sendMessageToUI(double lat, double longi) {
        Log.d(TAG, "Sending");
        Iterator<Messenger> messengerIterator = mClients.iterator();
        while (messengerIterator.hasNext()) {
            Messenger messenger = messengerIterator.next();
            try {
                // Send data as an Integer
                //messenger.send(Message.obtain(null, MSG_SET_INT_VALUE, intvaluetosend, 0));

                // Send data as a String
                Bundle bundle = new Bundle();
                bundle.putString("lat", lat + "");
                bundle.putString("long", longi + "");
                Message msg = Message.obtain(null, MSG_SET_STRING_VALUE);
                msg.setData(bundle);
                messenger.send(msg);

                Log.d(TAG, "Send Ok");
            } catch (RemoteException e) {
                // The client is dead. Remove it from the list.
                Log.d(TAG, "The client is dead. Remove it from the list.");
                mClients.remove(messenger);
            }
        }
    }


    /**
     * Handle incoming messages from MainActivity
     */
    private class IncomingMessageHandler extends Handler { // Handler of incoming messages from clients.
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: " + msg.what);
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_SET_INT_VALUE:
                    incrementBy = msg.arg1;
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
