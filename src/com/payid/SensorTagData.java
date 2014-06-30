package com.payid;

import android.bluetooth.BluetoothGattCharacteristic;


public class SensorTagData {

    public static double extractGasSalePrice(BluetoothGattCharacteristic c) {
        int rawT = shortSignedAtOffset(c, 0);

        return -46.85 + 175.72/65536 *(double)rawT;
    }


    public static int[] extractCalibrationCoefficients(BluetoothGattCharacteristic c) {
        int[] coefficients = new int[8];

        coefficients[0] = shortUnsignedAtOffset(c, 0);
        coefficients[1] = shortUnsignedAtOffset(c, 2);
        coefficients[2] = shortUnsignedAtOffset(c, 4);
        coefficients[3] = shortUnsignedAtOffset(c, 6);
        coefficients[4] = shortSignedAtOffset(c, 8);
        coefficients[5] = shortSignedAtOffset(c, 10);
        coefficients[6] = shortSignedAtOffset(c, 12);
        coefficients[7] = shortSignedAtOffset(c, 14);

        return coefficients;
    }

    
    /**
     * Gyroscope, Magnetometer, Barometer, IR temperature
     * all store 16 bit two's complement values in the awkward format
     * LSB MSB, which cannot be directly parsed as getIntValue(FORMAT_SINT16, offset)
     * because the bytes are stored in the "wrong" direction.
     *
     * This function extracts these 16 bit two's complement values.
     * */
    private static Integer shortSignedAtOffset(BluetoothGattCharacteristic c, int offset) {
        Integer lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
        Integer upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, offset + 1); // Note: interpret MSB as signed.

        return (upperByte << 8) + lowerByte;
    }

    private static Integer shortUnsignedAtOffset(BluetoothGattCharacteristic c, int offset) {
        Integer lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
        Integer upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1); // Note: interpret MSB as unsigned.

        return (upperByte << 8) + lowerByte;
    }
}
