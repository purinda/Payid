package com.payid;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {
	
	// Debug tag
    private static final String TAG             = "Payid";
    private static final String DEVICE_NAME     = "Pump";

    // Payid UUID
    private static final UUID GAS_SALE_SERVICE  = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID GAS_SALE_CHAR     = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID CONFIG_DESCRIPTOR = UUID.fromString("00002901-0000-1000-8000-00805f9b34fb");
    
    private BluetoothAdapter mBluetoothAdapter;
    private SparseArray<BluetoothDevice> mDevices;
    private BluetoothGatt mConnectedGatt;
    
    private TextView mTag;
    private ProgressDialog mProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main_activity);
        setProgressBarIndeterminate(true);

        // We are going to display the results in some text fields
        mTag = (TextView) findViewById(R.id.label_tagname);

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        mDevices = new SparseArray<BluetoothDevice>();

        // A progress dialog will be needed while the connection process is
        // taking place
        mProgress = new ProgressDialog(this);
        mProgress.setIndeterminate(true);
        mProgress.setCancelable(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // We need to enforce that Bluetooth is first enabled, and take the
        // user to settings to enable it if they have not done so.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            //Bluetooth is disabled
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }

        
        // Check for Bluetooth LE Support.  In production, our manifest entry will keep this
        // from installing on these devices, but this will allow test devices or other
        // sideloads to report whether or not the feature exists.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        clearDisplayValues();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        //Make sure dialog is hidden
        mProgress.dismiss();
        
        //Cancel any scans in progress
        mHandler.removeCallbacks(mStopRunnable);
        mHandler.removeCallbacks(mStartRunnable);
        mBluetoothAdapter.stopLeScan(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        //Disconnect from any active tag connection
        if (mConnectedGatt != null) {
            mConnectedGatt.disconnect();
            mConnectedGatt = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
        // Add the "scan" option to the menu
        getMenuInflater().inflate(R.menu.main, menu);
        
        // Add any device elements we've discovered to the overflow menu
        for (int i=0; i < mDevices.size(); i++) {
            BluetoothDevice device = mDevices.valueAt(i);
            menu.add(0, mDevices.keyAt(i), 0, device.getName().substring(0, device.getName().length()));
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
            	Log.d(TAG, "Refresh menu clicked");
            	
                mDevices.clear();                
                startScan();
                return true;
                
            default:
            	Log.d(TAG, "Device item clicked.");
            	
                //Obtain the discovered device to connect with
                BluetoothDevice device = mDevices.get(item.getItemId());
                Log.i(TAG, "Connecting to " + device.getName());
                
                // Make a connection with the device using the special LE-specific
                // connectGatt() method, passing in a callback for GATT events
                mConnectedGatt = device.connectGatt(this, false, mGattCallback);
                
                // Display progress UI
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Connecting to " + device.getName() + "..."));
                return super.onOptionsItemSelected(item);
        }
    }

    private void clearDisplayValues() {
        mTag.setText("-------");
    }


    private Runnable mStopRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };
    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            startScan();
        }
    };

    private void startScan() {
        mBluetoothAdapter.startLeScan(this);
        setProgressBarIndeterminateVisibility(true);

        mHandler.postDelayed(mStopRunnable, 2500);
    }

    private void stopScan() {
        mBluetoothAdapter.stopLeScan(this);
        setProgressBarIndeterminateVisibility(false);
    }

    //
    // BluetoothAdapter.LeScanCallback 
    //
    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.i(TAG, "New LE Device: " + device.getName() + " @ " + rssi);
        
        // Get items within a 0.5m radius (~<-55dBm)       
        if (rssi > -60) {
        	return;
        }
        
        // We are looking for PayidTag devices only, so validate the name
        // that each device reports before adding it to our collection
        if (device.getName().startsWith(DEVICE_NAME)) {
            mDevices.put(device.hashCode(), device);
            
            //Update the overflow menu
            invalidateOptionsMenu();
        }
    }


    // In this callback, we've created a bit of a state machine to enforce that only
    // one characteristic be read or written at a time until all of our sensors
    // are enabled and we are registered to get notifications.
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        // State Machine Tracking
        private int mState = 0;
        
        private void reset() { 
        	mState = 0; 
        }
        
        private void advance() {
        	mState++;
        }


        // Send an enable command to each sensor by writing a configuration
        // characteristic.  This is specific to the SensorTag to keep power
        // low by disabling sensors you aren't using.
        private void enableNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Enabling GAS service");
                    characteristic = gatt.getService(GAS_SALE_SERVICE)
                            .getCharacteristic(GAS_SALE_CHAR);
                    characteristic.setValue(new byte[] {0x41});
                    break;
                    
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            gatt.writeCharacteristic(characteristic);
        }

        // Read the data characteristic's value for each sensor explicitly
        private void readNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Reading GAS service");
                    characteristic = gatt.getService(GAS_SALE_SERVICE)
                            .getCharacteristic(GAS_SALE_CHAR);
                    break;
                    
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            gatt.readCharacteristic(characteristic);
            
            // Enable notifications
            this.setNotifyNextSensor(gatt);
        }

        // Enable notification of changes on the data characteristic for each sensor
        // by writing the ENABLE_NOTIFICATION_VALUE flag to that characteristic's
        // configuration descriptor.
        private void setNotifyNextSensor(BluetoothGatt gatt) {
            BluetoothGattCharacteristic characteristic;
            switch (mState) {
                case 0:
                    Log.d(TAG, "Set notify GAS service");
                    characteristic = gatt.getService(GAS_SALE_SERVICE)
                            .getCharacteristic(GAS_SALE_CHAR);
                    break;
                    
                default:
                    mHandler.sendEmptyMessage(MSG_DISMISS);
                    Log.i(TAG, "All Sensors Enabled");
                    return;
            }

            //Enable local notifications
            gatt.setCharacteristicNotification(characteristic, true);
            
            //Enabled remote notifications
            BluetoothGattDescriptor desc = characteristic.getDescriptor(CONFIG_DESCRIPTOR);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(desc);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "Connection State Change: " + status + " -> "+connectionState(newState));
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                
                // Once successfully connected, we must next discover all the services on the
                // device before we can read and write their characteristics.
                gatt.discoverServices();
                mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Discovering Services..."));
            } else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {

                // If at any point we disconnect, send a message to clear the weather values
                // out of the UI
                mHandler.sendEmptyMessage(MSG_CLEAR);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {

                // If there is a failure at any stage, simply disconnect
                gatt.disconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services Discovered: " + status);
            mHandler.sendMessage(Message.obtain(null, MSG_PROGRESS, "Enabling Sensors..."));

            // With services discovered, we are going to reset our state machine and start
            // working through the sensors we need to enable
            reset();
            enableNextSensor(gatt);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        	
            // For each read, pass the data up to the UI thread to update the display
            if (GAS_SALE_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_GAS, characteristic));
            }

            // After reading the initial value, next we enable notifications
            setNotifyNextSensor(gatt);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            // After writing the enable flag, next we read the initial value
            readNextSensor(gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            // After notifications are enabled, all updates from the device on characteristic
            // value changes will be posted here.  Similar to read, we hand these up to the
            // UI thread to update the display.
            if (GAS_SALE_CHAR.equals(characteristic.getUuid())) {
                mHandler.sendMessage(Message.obtain(null, MSG_GAS, characteristic));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        	
            //Once notifications are enabled, we move to the next sensor and start over with enable
            advance();
            enableNextSensor(gatt);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "Remote RSSI: " + rssi);
        }

        private String connectionState(int status) {
            switch (status) {
                case BluetoothProfile.STATE_CONNECTED:
                    return "Connected";
                case BluetoothProfile.STATE_DISCONNECTED:
                    return "Disconnected";
                case BluetoothProfile.STATE_CONNECTING:
                    return "Connecting";
                case BluetoothProfile.STATE_DISCONNECTING:
                    return "Disconnecting";
                default:
                    return String.valueOf(status);
            }
        }
    };

    // We have a Handler to process event results on the main thread
    private static final int MSG_GAS      = 101;
    private static final int MSG_PROGRESS = 201;
    private static final int MSG_DISMISS  = 202;
    private static final int MSG_CLEAR    = 301;
    
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothGattCharacteristic characteristic;
            switch (msg.what) {
                case MSG_GAS:
                    characteristic = (BluetoothGattCharacteristic) msg.obj;
                    if (characteristic.getValue() == null) {
                        Log.w(TAG, "Error obtaining GAS price value");
                        return;
                    }
                    updateGasCost(characteristic);
                    break;
                    
                case MSG_PROGRESS:
                    mProgress.setMessage((String) msg.obj);
                    if (!mProgress.isShowing()) {
                        mProgress.show();
                    }
                    break;
                    
                case MSG_DISMISS:
                    mProgress.hide();
                    break;
                    
                case MSG_CLEAR:
                    clearDisplayValues();
                    break;
            }
        }
    };

    // Methods to extract sensor data and update the UIs
    private void updateGasCost(BluetoothGattCharacteristic characteristic) {
        double gasPrice = SensorTagData.extractGasSalePrice(characteristic);

        mTag.setText(String.format("AUD $%.2f", gasPrice));
    }

}
