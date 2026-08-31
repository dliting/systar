package com.systar.monitor.drivers.iec104;

import com.systar.monitor.drivers.iec104.Iec104Service.Iec104Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openmuc.j60870.*;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class Iec104ServiceTest {

    private Iec104Service service;
    private Iec104Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        service = new Iec104Service();
        service.setHost("127.0.0.1");
        service.setPort(2404);
        conn = new Iec104Connection(service);
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("default port is 2404")
        void defaultPort() {
            assertThat(new Iec104Service().getPort()).isEqualTo(2404);
        }

        @Test
        @DisplayName("default timeout is 10000ms")
        void defaultTimeout() {
            assertThat(new Iec104Service().getTimeout()).isEqualTo(10000);
        }

        @Test
        @DisplayName("default commonAsduAddress is 1")
        void defaultCommonAsduAddress() {
            assertThat(new Iec104Service().getCommonAsduAddress()).isEqualTo(1);
        }

        @Test
        @DisplayName("getters and setters")
        void gettersAndSetters() {
            service.setHost("10.0.0.1");
            service.setPort(9999);
            service.setCommonAsduAddress(5);
            service.setOriginatorAddress(1);
            service.setTimeout(30000);
            assertThat(service.getHost()).isEqualTo("10.0.0.1");
            assertThat(service.getPort()).isEqualTo(9999);
            assertThat(service.getCommonAsduAddress()).isEqualTo(5);
            assertThat(service.getOriginatorAddress()).isEqualTo(1);
            assertThat(service.getTimeout()).isEqualTo(30000);
        }
    }

    @Nested
    @DisplayName("ASDU decoding")
    class AsduDecoding {

        @Test
        @DisplayName("YC telemetry (M_ME_NC_1) → cache updated with float value")
        void ycTelemetryFloatValue() {
            ASdu asdu = new ASdu(TypeId.M_ME_NC_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(100, new InformationElement[][]{
                                    {new IeShortFloat(42.5f)}
                            })
                    });

            conn.decodeAndCache(asdu);
            assertThat(conn.read(100)).isEqualTo(42.5f);
        }

        @Test
        @DisplayName("YX signal (M_SP_NA_1) → cache updated with boolean value")
        void yxSignalBooleanValue() {
            ASdu asdu = new ASdu(TypeId.M_SP_NA_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(200, new InformationElement[][]{
                                    {new IeSinglePointWithQuality(true, false, false, false, false)}
                            })
                    });

            conn.decodeAndCache(asdu);
            assertThat(conn.read(200)).isEqualTo(true);
        }

        @Test
        @DisplayName("YX signal (M_SP_NA_1) false → cache updated with false")
        void yxSignalFalse() {
            ASdu asdu = new ASdu(TypeId.M_SP_NA_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(300, new InformationElement[][]{
                                    {new IeSinglePointWithQuality(false, false, false, false, false)}
                            })
                    });

            conn.decodeAndCache(asdu);
            assertThat(conn.read(300)).isEqualTo(false);
        }

        @Test
        @DisplayName("multiple YC values in single ASDU")
        void multipleYcValues() {
            ASdu asdu = new ASdu(TypeId.M_ME_NC_1, true,
                    CauseOfTransmission.PERIODIC, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(10, new InformationElement[][]{{new IeShortFloat(1.0f)}}),
                            new InformationObject(20, new InformationElement[][]{{new IeShortFloat(2.0f)}}),
                            new InformationObject(30, new InformationElement[][]{{new IeShortFloat(3.0f)}})
                    });

            conn.decodeAndCache(asdu);
            assertThat(conn.read(10)).isEqualTo(1.0f);
            assertThat(conn.read(20)).isEqualTo(2.0f);
            assertThat(conn.read(30)).isEqualTo(3.0f);
        }

        @Test
        @DisplayName("overwrites existing cache entry")
        void overwritesExistingEntry() {
            ASdu first = new ASdu(TypeId.M_ME_NC_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(100, new InformationElement[][]{{new IeShortFloat(10.0f)}})
                    });
            ASdu second = new ASdu(TypeId.M_ME_NC_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(100, new InformationElement[][]{{new IeShortFloat(20.0f)}})
                    });

            conn.decodeAndCache(first);
            conn.decodeAndCache(second);
            assertThat(conn.read(100)).isEqualTo(20.0f);
        }

        @Test
        @DisplayName("empty elements array does not throw")
        void emptyElements() {
            ASdu asdu = new ASdu(TypeId.M_ME_NC_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(100, new InformationElement[][]{{}})
                    });

            assertThatCode(() -> conn.decodeAndCache(asdu)).doesNotThrowAnyException();
            assertThat(conn.read(100)).isNull();
        }

        @Test
        @DisplayName("null read for unknown address")
        void nullForUnknownAddress() {
            assertThat(conn.read(99999)).isNull();
        }

        @Test
        @DisplayName("M_ME_TF_1 also decoded as YC telemetry")
        void mMeTf1DecodedAsYc() {
            ASdu asdu = new ASdu(TypeId.M_ME_TF_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(42, new InformationElement[][]{{new IeShortFloat(99.9f)}})
                    });

            conn.decodeAndCache(asdu);
            assertThat(conn.read(42)).isEqualTo(99.9f);
        }

        @Test
        @DisplayName("M_SP_TB_1 also decoded as YX signal")
        void mSpTb1DecodedAsYx() {
            ASdu asdu = new ASdu(TypeId.M_SP_TB_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(55, new InformationElement[][]{
                                    {new IeSinglePointWithQuality(true, false, false, false, false)}
                            })
                    });

            conn.decodeAndCache(asdu);
            assertThat(conn.read(55)).isEqualTo(true);
        }

        @Test
        @DisplayName("cacheSize reflects number of unique addresses")
        void cacheSize() {
            ASdu asdu = new ASdu(TypeId.M_ME_NC_1, true,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(1, new InformationElement[][]{{new IeShortFloat(1f)}}),
                            new InformationObject(2, new InformationElement[][]{{new IeShortFloat(2f)}})
                    });
            conn.decodeAndCache(asdu);
            assertThat(conn.cacheSize()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("type classification")
    class TypeClassification {

        @Test
        @DisplayName("M_ME_NC_1 is YC telemetry")
        void mMeNc1IsYc() {
            assertThat(Iec104Connection.isYcTelemetry(TypeId.M_ME_NC_1)).isTrue();
        }

        @Test
        @DisplayName("M_ME_TF_1 is YC telemetry")
        void mMeTf1IsYc() {
            assertThat(Iec104Connection.isYcTelemetry(TypeId.M_ME_TF_1)).isTrue();
        }

        @Test
        @DisplayName("M_SP_NA_1 is NOT YC telemetry")
        void mSpNa1IsNotYc() {
            assertThat(Iec104Connection.isYcTelemetry(TypeId.M_SP_NA_1)).isFalse();
        }

        @Test
        @DisplayName("M_SP_NA_1 is YX signal")
        void mSpNa1IsYx() {
            assertThat(Iec104Connection.isYxSignal(TypeId.M_SP_NA_1)).isTrue();
        }

        @Test
        @DisplayName("M_SP_TB_1 is YX signal")
        void mSpTb1IsYx() {
            assertThat(Iec104Connection.isYxSignal(TypeId.M_SP_TB_1)).isTrue();
        }

        @Test
        @DisplayName("M_DP_NA_1 is YX signal (double point)")
        void mDpNa1IsYx() {
            assertThat(Iec104Connection.isYxSignal(TypeId.M_DP_NA_1)).isTrue();
        }

        @Test
        @DisplayName("M_ME_NA_1 (normalized) is YC telemetry")
        void mMeNa1IsYc() {
            assertThat(Iec104Connection.isYcTelemetry(TypeId.M_ME_NA_1)).isTrue();
        }
    }

    @Nested
    @DisplayName("connection lifecycle")
    class ConnectionLifecycle {

        @Test
        @DisplayName("isConnected returns false before open")
        void notConnectedBeforeOpen() {
            Iec104Connection fresh = new Iec104Connection(service);
            assertThat(fresh.isConnected()).isFalse();
        }

        @Test
        @DisplayName("close clears cache and nulls connection")
        void closeClearsCache() {
            ASdu asdu = new ASdu(TypeId.M_ME_NC_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(100, new InformationElement[][]{{new IeShortFloat(42.5f)}})
                    });
            conn.decodeAndCache(asdu);
            assertThat(conn.cacheSize()).isGreaterThan(0);

            conn.close();
            assertThat(conn.isConnected()).isFalse();
            assertThat(conn.cacheSize()).isZero();
        }

        @Test
        @DisplayName("close is idempotent")
        void closeIdempotent() {
            conn.close();
            assertThatCode(() -> conn.close()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("read returns null after close")
        void readNullAfterClose() {
            ASdu asdu = new ASdu(TypeId.M_ME_NC_1, false,
                    CauseOfTransmission.SPONTANEOUS, false, false, 0, 1,
                    new InformationObject[]{
                            new InformationObject(100, new InformationElement[][]{{new IeShortFloat(42.5f)}})
                    });
            conn.decodeAndCache(asdu);
            conn.close();
            assertThat(conn.read(100)).isNull();
        }
    }
}
