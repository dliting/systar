package com.systar.monitor.drivers.snmp;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.drivers.snmp.SnmpService.SnmpConnection;
import com.systar.monitor.result.MonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.Variable;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class InterfaceProbeTest {

    private static final int IF_INDEX = 1;

    // OID suffixes used in mock setup
    private static final String SUFFIX_UP_STATE      = "1.3.6.1.2.1.2.2.1.8."  + IF_INDEX;
    private static final String SUFFIX_HC_IN_OCTETS   = "1.3.6.1.2.1.31.1.1.1.6."  + IF_INDEX;
    private static final String SUFFIX_IN_OCTETS      = "1.3.6.1.2.1.2.2.1.10." + IF_INDEX;
    private static final String SUFFIX_IN_UCAST       = "1.3.6.1.2.1.2.2.1.11." + IF_INDEX;
    private static final String SUFFIX_IN_NUCAST      = "1.3.6.1.2.1.2.2.1.12." + IF_INDEX;
    private static final String SUFFIX_IN_DISCARDS    = "1.3.6.1.2.1.2.2.1.13." + IF_INDEX;
    private static final String SUFFIX_IN_ERRORS      = "1.3.6.1.2.1.2.2.1.14." + IF_INDEX;
    private static final String SUFFIX_OUT_OCTETS     = "1.3.6.1.2.1.2.2.1.16." + IF_INDEX;
    private static final String SUFFIX_OUT_UCAST      = "1.3.6.1.2.1.2.2.1.17." + IF_INDEX;
    private static final String SUFFIX_OUT_NUCAST     = "1.3.6.1.2.1.2.2.1.18." + IF_INDEX;
    private static final String SUFFIX_OUT_DISCARDS   = "1.3.6.1.2.1.2.2.1.19." + IF_INDEX;
    private static final String SUFFIX_OUT_ERRORS     = "1.3.6.1.2.1.2.2.1.20." + IF_INDEX;

    private SnmpService snmpService;
    private SnmpConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        snmpService = mock(SnmpService.class);
        connection  = mock(SnmpConnection.class);
        when(snmpService.getConnection()).thenReturn(connection);
        when(snmpService.getMode()).thenReturn(com.systar.monitor.asset.MonitorMode.ACTIVE);
    }

    // ======================== helpers ========================

    private InterfaceRecvSpeed createRecvSpeedProbe() {
        InterfaceRecvSpeed probe = new InterfaceRecvSpeed();
        ProbeType type = new ProbeType("SnmpInterfaceRecvSpeed");
        probe.init(type, 1, "recv-speed");
        probe.setInterfaceIndex(IF_INDEX);
        probe.setSource(snmpService);
        return probe;
    }

    private InterfaceSendSpeed createSendSpeedProbe() {
        InterfaceSendSpeed probe = new InterfaceSendSpeed();
        ProbeType type = new ProbeType("SnmpInterfaceSendSpeed");
        probe.init(type, 2, "send-speed");
        probe.setInterfaceIndex(IF_INDEX);
        probe.setSource(snmpService);
        return probe;
    }

    private InterfaceUpState createUpStateProbe() {
        InterfaceUpState probe = new InterfaceUpState();
        ProbeType type = new ProbeType("SnmpInterfaceUpState");
        probe.init(type, 3, "up-state");
        probe.setInterfaceIndex(IF_INDEX);
        probe.setSource(snmpService);
        return probe;
    }

    private InterfaceInLossRate createInLossRateProbe() {
        InterfaceInLossRate probe = new InterfaceInLossRate();
        ProbeType type = new ProbeType("SnmpInterfaceInLossRate");
        probe.init(type, 4, "in-loss");
        probe.setInterfaceIndex(IF_INDEX);
        probe.setSource(snmpService);
        return probe;
    }

    /**
     * Sets up a simple mock where all counters return the same value.
     * Interface is UP, HC counters not available.
     */
    private void setUpConstantCounters(long counterValue) throws Exception {
        when(connection.getVar(anyString())).thenAnswer(inv -> {
            String oid = inv.getArgument(0);
            if (oid.equals(SUFFIX_UP_STATE))    return new Integer32(1);
            if (oid.equals(SUFFIX_HC_IN_OCTETS)) return null;
            return counterVar(counterValue);
        });
    }

    /**
     * Sets up a two-sample mock using answer-based call counting.
     * First 10 counter calls return {@code before} values,
     * next 10 counter calls return {@code after} values.
     * UpState and HC check calls are not counted.
     */
    private void setUpTwoSampleCounters(long beforeValue, long afterValue) throws Exception {
        final int[] counterCalls = {0};
        when(connection.getVar(anyString())).thenAnswer(inv -> {
            String oid = inv.getArgument(0);
            if (oid.equals(SUFFIX_UP_STATE))    return new Integer32(1);
            if (oid.equals(SUFFIX_HC_IN_OCTETS)) return null;
            counterCalls[0]++;
            return counterVar(counterCalls[0] <= 10 ? beforeValue : afterValue);
        });
    }

    private static Variable counterVar(long value) {
        return new Counter32((int) value);
    }

    // ======================== service validation ========================

    @Nested
    @DisplayName("service validation")
    class ServiceValidation {

        @Test
        @DisplayName("throws when not attached to SnmpService")
        void wrongService() {
            InterfaceRecvSpeed probe = new InterfaceRecvSpeed();
            ProbeType type = new ProbeType("recv");
            probe.init(type, 1, "recv");
            probe.setInterfaceIndex(IF_INDEX);

            assertThatThrownBy(() -> probe.detect(new MonitorResult(probe)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SnmpService");
        }
    }

    // ======================== interface down ========================

    @Nested
    @DisplayName("interface down state")
    class InterfaceDown {

        @Test
        @DisplayName("UpState returns false when interface is DOWN")
        void upStateReturnsFalse() throws Exception {
            when(connection.getVar(SUFFIX_UP_STATE)).thenReturn(new Integer32(2));

            InterfaceUpState probe = createUpStateProbe();
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            assertThat(result.getValue()).isEqualTo(false);
        }

        @Test
        @DisplayName("RecvSpeed sets error when interface is DOWN")
        void recvSpeedSetsErrorWhenDown() throws Exception {
            when(connection.getVar(SUFFIX_UP_STATE)).thenReturn(new Integer32(2));

            InterfaceRecvSpeed probe = createRecvSpeedProbe();
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            assertThat(result.getValue()).isNull();
            assertThat(result.getError()).isNotNull();
        }
    }

    // ======================== interface up ========================

    @Nested
    @DisplayName("interface up state")
    class InterfaceUp {

        @Test
        @DisplayName("UpState returns true when interface is UP (state=1)")
        void upStateReturnsTrue() throws Exception {
            setUpConstantCounters(1000L);

            InterfaceUpState probe = createUpStateProbe();
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            assertThat(result.getValue()).isEqualTo(true);
        }

        @Test
        @DisplayName("UpState returns true when interface is TESTING (state=3)")
        void upStateReturnsTrueForTesting() throws Exception {
            when(connection.getVar(anyString())).thenAnswer(inv -> {
                String oid = inv.getArgument(0);
                if (oid.equals(SUFFIX_UP_STATE))    return new Integer32(3);
                if (oid.equals(SUFFIX_HC_IN_OCTETS)) return null;
                return counterVar(1000L);
            });

            InterfaceUpState probe = createUpStateProbe();
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            assertThat(result.getValue()).isEqualTo(true);
        }
    }

    // ======================== speed calculation ========================

    @Nested
    @DisplayName("speed calculation")
    class SpeedCalculation {

        @Test
        @DisplayName("calculates receive speed from octet delta")
        void recvSpeedFromOctetDelta() throws Exception {
            // before=1000 octets, after=2000 octets
            // delta=1000, elapsed≈1000ms → speed = 1000/1000*8 = 8 Kbps
            setUpTwoSampleCounters(1000L, 2000L);

            InterfaceRecvSpeed probe = createRecvSpeedProbe();
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            Float speed = (Float) result.getValue();
            assertThat(speed).isNotNull();
            assertThat(speed).isCloseTo(8.0f, within(0.5f));
        }

        @Test
        @DisplayName("calculates send speed from octet delta")
        void sendSpeedFromOctetDelta() throws Exception {
            setUpTwoSampleCounters(500L, 1500L);

            InterfaceSendSpeed probe = createSendSpeedProbe();
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            Float speed = (Float) result.getValue();
            assertThat(speed).isNotNull();
            assertThat(speed).isCloseTo(8.0f, within(0.5f));
        }

        @Test
        @DisplayName("returns zero speed when counters are unchanged")
        void zeroSpeedWhenNoTraffic() throws Exception {
            setUpConstantCounters(1000L);

            InterfaceRecvSpeed probe = createRecvSpeedProbe();
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            Float speed = (Float) result.getValue();
            assertThat(speed).isNotNull();
            assertThat(speed).isEqualTo(0.0f);
        }
    }

    // ======================== loss rate ========================

    @Nested
    @DisplayName("loss rate calculation")
    class LossRateCalculation {

        @Test
        @DisplayName("calculates input loss rate from discard delta")
        void inLossRateFromDiscards() throws Exception {
            // All counters 0→100: inUCastPkts=100, inNUCastPkts=100
            // deltaPkts = 200, deltaDiscards = 100 → lossRate = 0.5
            setUpTwoSampleCounters(0L, 100L);

            InterfaceInLossRate probe = createInLossRateProbe();
            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            Float lossRate = (Float) result.getValue();
            assertThat(lossRate).isNotNull();
            assertThat(lossRate).isCloseTo(0.5f, within(0.05f));
        }
    }

    // ======================== cache behavior ========================

    @Nested
    @DisplayName("cache behavior")
    class CacheBehavior {

        @Test
        @DisplayName("second detect reuses cached result without extra SNMP calls")
        void secondDetectUsesCache() throws Exception {
            setUpConstantCounters(1000L);

            InterfaceUpState probe = createUpStateProbe();

            MonitorResult result1 = new MonitorResult(probe);
            probe.detect(result1);
            assertThat(result1.getValue()).isEqualTo(true);

            // Count calls after first detect
            int callsAfterFirst = mockingDetails(connection).getInvocations().size();

            MonitorResult result2 = new MonitorResult(probe);
            probe.detect(result2);
            assertThat(result2.getValue()).isEqualTo(true);

            int callsAfterSecond = mockingDetails(connection).getInvocations().size();
            // Second detect should not have made additional getVar calls
            assertThat(callsAfterSecond).isEqualTo(callsAfterFirst);
        }
    }
}
