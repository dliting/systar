package com.systar.monitor.drivers.environmental;

/**
 * Decodes a 25-byte fixed-format environmental sensor data frame.
 * <p>
 * Frame layout (25 bytes):
 * <pre>
 *   [0]      header (0x7E)
 *   [1]      device id (1 byte, displayed as 2-char hex)
 *   [2-5]    temperature (IEEE 754 float, big-endian)
 *   [6-9]    humidity (IEEE 754 float, big-endian)
 *   [10-24]  reserved / padding
 * </pre>
 */
public final class EnvFrameDecoder {

    private static final int FRAME_LENGTH = 25;
    private static final byte FRAME_HEADER = (byte) 0x7E;

    private EnvFrameDecoder() {
    }

    /**
     * Parsed environmental sensor data from a single frame.
     */
    public static class ParsedData {
        private final String deviceId;
        private final float temperature;
        private final float humidity;

        ParsedData(String deviceId, float temperature, float humidity) {
            this.deviceId = deviceId;
            this.temperature = temperature;
            this.humidity = humidity;
        }

        public String getDeviceId() { return deviceId; }
        public float getTemperature() { return temperature; }
        public float getHumidity() { return humidity; }
    }

    /**
     * Decodes a 25-byte frame. Returns null if the frame is invalid.
     */
    public static ParsedData decode(byte[] frame) {
        if (frame == null || frame.length < FRAME_LENGTH) {
            return null;
        }
        if (frame[0] != FRAME_HEADER) {
            return null;
        }

        String deviceId = String.format("%02X", frame[1] & 0xFF);
        float temperature = readFloat(frame, 2);
        float humidity = readFloat(frame, 6);

        return new ParsedData(deviceId, temperature, humidity);
    }

    private static float readFloat(byte[] buf, int offset) {
        int bits = ((buf[offset] & 0xFF) << 24)
                | ((buf[offset + 1] & 0xFF) << 16)
                | ((buf[offset + 2] & 0xFF) << 8)
                | (buf[offset + 3] & 0xFF);
        return Float.intBitsToFloat(bits);
    }
}
