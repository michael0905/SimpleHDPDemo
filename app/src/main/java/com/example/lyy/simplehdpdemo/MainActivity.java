package com.example.lyy.simplehdpdemo;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BluetoothHealthActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int HEALTH_PROFILE_SOURCE_DATA_TYPE = 0x1007;
    private Messenger mHealthService;
    private boolean mHealthServiceBound;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;
    private ArrayAdapter<String> mArrayAdapter;
    private ArrayList<BluetoothDevice> deviceList;
    private ListView mListview;

    private TextView mSys;
    private TextView mDia;
    private TextView mPul;
    private TextView mDate;

    private Handler mIncomingHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                // Application registration complete.
//                case HDPService.STATUS_HEALTH_APP_REG:
//                    mStatusMessage.setText(String.format(
//                            mRes.getString(R.string.status_reg), msg.arg1));
//                    break;
//                // Application unregistration complete.
//                case HDPService.STATUS_HEALTH_APP_UNREG:
//                    mStatusMessage.setText(String.format(
//                            mRes.getString(R.string.status_unreg), msg.arg1));
//                    break;
                // Reading data from HDP device.
                case HDPService.STATUS_READ_DATA:
                    Log.d(TAG, "reading data");
                    break;
                // Finish reading data from HDP device.
                case HDPService.STATUS_READ_DATA_DONE:
                    Log.d(TAG, "done read data");
                    break;
                // Channel creation complete. Some devices will automatically
                // establish
                // connection.
                case HDPService.STATUS_CREATE_CHANNEL:
                    Log.d(TAG, "create channel");
                    break;
                // Channel destroy complete. This happens when either the device
                // disconnects or
                // there is extended inactivity.
                case HDPService.STATUS_DESTROY_CHANNEL:
                    Log.d(TAG, "destroy channel");
                    break;
                case HDPService.RECEIVED_SYS:
                    int sys = msg.arg1;
                    Log.i(TAG, "msg.arg1 @ sys is " + sys);
                    mSys.setText("" + sys);
                    break;
                case HDPService.RECEIVED_DIA:
                    int dia = msg.arg1;
                    mDia.setText("" + dia);
                    Log.i(TAG, "msg.arg1 @ dia is " + dia);
                    break;
                case HDPService.RECEIVED_PUL:
                    int pul = msg.arg1;
                    Log.i(TAG, "msg.arg1 @ pulse is " + pul);
                    mPul.setText("" + pul);
                    break;
                case HDPService.RECEIVED_DATE:
                    String date = (String) msg.obj;
                    mDate.setText(date);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    private final Messenger mMessenger = new Messenger(mIncomingHandler);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        mSys = (TextView) findViewById(R.id.Systolic);
        mDia = (TextView) findViewById(R.id.Diastolic);
        mPul = (TextView) findViewById(R.id.Pulse);
        mDate = (TextView) findViewById(R.id.Date);
        registerReceiver(mReceiver, initIntentFilter());
        fetchDevices();
        mListview = (ListView) findViewById(R.id.listView);
        mListview.setAdapter(mArrayAdapter);
//        mListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
//                final BluetoothDevice device = deviceList.get(i);
//                Toast.makeText(getApplication(), device.getName() + "\n" + device.getAddress(), Toast.LENGTH_SHORT).show();
//                mDevice = device;
//                connectChannel();
//            }
//        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If Bluetooth is not on, request that it be enabled.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            initialize();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHealthServiceBound)
            unbindService(mConnection);
        unregisterReceiver(mReceiver);
    }

    /**
     * Ensures user has turned on Bluetooth on the Android device.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    initialize();
                } else {
                    finish();
                    return;
                }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON) {
                    initialize();
                }
            }
        }
    };

    // Sets up communication with {@link BluetoothHDPService}.
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mHealthServiceBound = true;
            Message msg = Message.obtain(null,
                    HDPService.MSG_REG_CLIENT);
            msg.replyTo = mMessenger;
            mHealthService = new Messenger(service);
            try {
                mHealthService.send(msg);
                //register blood pressure data type
                sendMessage(HDPService.MSG_REG_HEALTH_APP,
                        HEALTH_PROFILE_SOURCE_DATA_TYPE);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to register client to service.");
                e.printStackTrace();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mHealthService = null;
            mHealthServiceBound = false;
        }
    };

    private void fetchDevices() {
        deviceList = new ArrayList<>();
        mArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_expandable_list_item_1);
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                deviceList.add(device);
            }
        }

    }

    private void connectChannel() {
        sendMessageWithDevice(HDPService.MSG_CONNECT_CHANNEL);
    }

    private void disconnectChannel() {
        sendMessageWithDevice(HDPService.MSG_DISCONNECT_CHANNEL);
    }

    private void initialize() {
        // Starts health service.
        Intent intent = new Intent(this, HDPService.class);
        //startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private IntentFilter initIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return filter;
    }

    // Sends a message to {@link BluetoothHDPService}.
    private void sendMessage(int what, int value) {
        if (mHealthService == null) {
            Log.d(TAG, "Health Service not connected.");
            return;
        }

        try {
            mHealthService.send(Message.obtain(null, what, value, 0));
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to reach service.");
            e.printStackTrace();
        }
    }

    // Sends an update message, along with an HDP BluetoothDevice object, to
    // {@link BluetoothHDPService}. The BluetoothDevice object is needed by the
    // channel creation
    // method.
    private void sendMessageWithDevice(int what) {
        if (mHealthService == null) {
            Log.d(TAG, "Health Service not connected.");
            return;
        }

        try {
            mHealthService.send(Message.obtain(null, what, mDevice));
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to reach service.");
            e.printStackTrace();
        }
    }

}
