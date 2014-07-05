package com.payid;

import android.bluetooth.BluetoothGattCharacteristic;


public class SensorTagData {

    public static double extractGasSalePrice(BluetoothGattCharacteristic c) {
        double result = Double.parseDouble(new String(c.getValue()));

        return result;
    }

}
