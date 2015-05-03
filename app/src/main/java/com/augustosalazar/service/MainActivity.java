package com.augustosalazar.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;


public class MainActivity extends ActionBarActivity implements  ServiceConnection{

    private static final String TAG = MainActivity.class.getSimpleName();
    Intent mIntent;
    TextView textLat;
    TextView textLong;
    TextView textStatus;
    boolean mIsBound = false;
    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler());
    private ServiceConnection mConnection = (ServiceConnection) this;
    private Messenger mServiceMessenger = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");
        textLat = (TextView) findViewById(R.id.textViewLat);
        textLong = (TextView) findViewById(R.id.textViewLong);
        textStatus = (TextView) findViewById(R.id.textViewStatus);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void StartProcess(View view) {
        Intent i=new Intent(this, GpsService.class);
        startService(i);
    }

    private void doBindService() {
        bindService(new Intent(this, GpsService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        textStatus.setText("Binding.");
    }

    public void StopProcess(View view) {

        stopService(new Intent(this, GpsService.class));
        doUnbindService();

    }

    /**
     * Un-bind this Activity to MyService
     */
    private void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mServiceMessenger != null) {
                try {
                    Message msg = Message.obtain(null, GpsService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mServiceMessenger.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            textStatus.setText("Unbinding.");
        }
    }

    public void doTheBind(View view) {
        doBindService();
    }

    public void doTheUnbind(View view) {
        doUnbindService();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mServiceMessenger = new Messenger(service);
        textStatus.setText("Attached.");
        try {
            Message msg = Message.obtain(null, GpsService.MSG_REGISTER_CLIENT);
            msg.replyTo = mMessenger;
            mServiceMessenger.send(msg);
        }
        catch (RemoteException e) {
            // In this case the service has crashed before we could even do anything with it
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mServiceMessenger = null;
        textStatus.setText("Disconnected.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            doUnbindService();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to unbind from the service", t);
        }
    }

    /**
     * Handle incoming messages from MyService
     */
    private class IncomingMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
             Log.d("IncomingMessageHandler","IncomingHandler:handleMessage");
            switch (msg.what) {
                case GpsService.MSG_SET_INT_VALUE:
                    textLat.setText("Int Message: " + msg.arg1);
                    break;
                case GpsService.MSG_SET_STRING_VALUE:
                    String str1 = msg.getData().getString("lat");
                    textLat.setText(str1);
                    String str2 = msg.getData().getString("long");
                    textLong.setText(str2);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
