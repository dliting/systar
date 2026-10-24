package com.systar.monitor.drivers.environmental;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class EnvServiceTest {

    @Nested
    @DisplayName("frame decoding")
    class FrameDecoding {

        @Test
        @DisplayName("decodes valid 25-byte frame with temperature")
        void decodesTemperatureFrame() {
            byte[] frame = buildFrame((byte) 0x01, 25.5f);
            EnvFrameDecoder.ParsedData data = EnvFrameDecoder.decode(frame);

            assertThat(data.getDeviceId()).isEqualTo("01");
            assertThat(data.getTemperature()).isCloseTo(25.5f, within(0.1f));
        }

        @Test
        @DisplayName("frame too short returns null")
        void frameTooShort() {
            byte[] frame = new byte[10];
            EnvFrameDecoder.ParsedData data = EnvFrameDecoder.decode(frame);
            assertThat(data).isNull();
        }
    }

    /**
     * Builds a simulated 25-byte environmental sensor frame.
     * Layout: [header(1)][deviceId(1)][temp(4)][humidity(4)][padding(15)]
     */
    private byte[] buildFrame(byte deviceId, float temperature) {
        byte[] frame = new byte[25];
        frame[0] = 0x7E; // header
        frame[1] = deviceId;
        int tempBits = Float.floatToIntBits(temperature);
        frame[2] = (byte) ((tempBits >> 24) & 0xFF);
        frame[3] = (byte) ((tempBits >> 16) & 0xFF);
        frame[4] = (byte) ((tempBits >> 8) & 0xFF);
        frame[5] = (byte) (tempBits & 0xFF);
        return frame;
    }
}
