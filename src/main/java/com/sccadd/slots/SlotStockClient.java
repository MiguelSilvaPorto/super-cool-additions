package com.sccadd.slots;

/** The last stock list the server sent (client side). Plain data, so it is safe to reference anywhere. */
public final class SlotStockClient {
    private static volatile String text = "";
    private static volatile int version;

    private SlotStockClient() {
    }

    public static void set(String value) {
        text = value == null ? "" : value;
        version++;
    }

    public static String text() {
        return text;
    }

    public static int version() {
        return version;
    }
}
