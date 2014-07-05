package com.payid;

import java.util.HashMap;
 
/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class BLEAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String PAYMENT_SERVICE              = "0000fff0-0000-1000-8000-00805f9b34fb";
    public static String PAYMENT_SERVICE_READ_CHAR    = "0000fff1-0000-1000-8000-00805f9b34fb";
    public static String PAYMENT_SERVICE_WRITE_CHAR   = "0000fff2-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put(PAYMENT_SERVICE, "Payment Service");

        // Sample Characteristics.
        attributes.put(PAYMENT_SERVICE_READ_CHAR, "Read payment information");
        attributes.put(PAYMENT_SERVICE_WRITE_CHAR, "Modify payment information");
    }
 
    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}