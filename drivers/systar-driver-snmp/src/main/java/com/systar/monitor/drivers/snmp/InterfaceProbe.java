package com.systar.monitor.drivers.snmp;

import com.systar.monitor.asset.MonitorService;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.drivers.snmp.SnmpService.SnmpConnection;

import org.snmp4j.smi.Variable;

/**
 * Abstract base for SNMP network-interface monitoring probes.
 * <p>
 * Reads multiple ifMIB counter OIDs and performs a two-sample measurement
 * to compute derived values (speed, loss rate, up state).
 * Concrete subclasses extract a single derived value from the shared result cache.
 * <p>
 * Requires {@code interfaceIndex} to be set via {@link #setInterfaceIndex(int)}
 * (typically bound from the type's PropertyList).
 */
public abstract class InterfaceProbe extends Probe {

    // ======================== ifMIB OID prefixes (RFC 1213 / RFC 2863) ========================

    private static final String OID_UP_STATE         = "1.3.6.1.2.1.2.2.1.8";
    private static final String OID_IN_OCTETS         = "1.3.6.1.2.1.2.2.1.10";
    private static final String OID_IN_UCAST_PKTS     = "1.3.6.1.2.1.2.2.1.11";
    private static final String OID_IN_NUCAST_PKTS    = "1.3.6.1.2.1.2.2.1.12";
    private static final String OID_IN_DISCARDS       = "1.3.6.1.2.1.2.2.1.13";
    private static final String OID_IN_ERRORS         = "1.3.6.1.2.1.2.2.1.14";
    private static final String OID_OUT_OCTETS        = "1.3.6.1.2.1.2.2.1.16";
    private static final String OID_OUT_UCAST_PKTS    = "1.3.6.1.2.1.2.2.1.17";
    private static final String OID_OUT_NUCAST_PKTS   = "1.3.6.1.2.1.2.2.1.18";
    private static final String OID_OUT_DISCARDS      = "1.3.6.1.2.1.2.2.1.19";
    private static final String OID_OUT_ERRORS        = "1.3.6.1.2.1.2.2.1.20";
    private static final String OID_HC_IN_OCTETS      = "1.3.6.1.2.1.31.1.1.1.6";
    private static final String OID_HC_OUT_OCTETS     = "1.3.6.1.2.1.31.1.1.1.10";

    // RFC 1156 interface states
    private static final long IF_STATE_UP       = 1;
    private static final long IF_STATE_TESTING  = 3;

    private static final long DEFAULT_SAMPLE_SECS = 1;
    private static final int  FRACTION_SPEED      = 1000;
    private static final int  FRACTION_LOSS_RATE  = 100;

    // ======================== instance fields ========================

    private int interfaceIndex;
    private final ResultCache resultCache = new ResultCache();

    // ======================== setters (for bindProperties) ========================

    public void setInterfaceIndex(int interfaceIndex) {
        this.interfaceIndex = interfaceIndex;
    }

    public int getInterfaceIndex() {
        return interfaceIndex;
    }

    // ======================== OID builder ========================

    private String buildOid(String baseOid) {
        return baseOid + "." + interfaceIndex;
    }

    // ======================== cache check ========================

    /**
     * Performs detection only if the cached result is stale (i.e., a new
     * detect cycle has started since the last sampling).
     */
    protected void detectOnDemand() throws Exception {
        boolean cacheStale = getLastDetectTimeMs() > resultCache.lastUpdateTimeMs;
        if (cacheStale) {
            doDetect();
        }
    }

    // ======================== cache accessors (for subclasses) ========================

    protected Float getCachedRecvSpeed()  { return resultCache.recvSpeed; }
    protected Float getCachedSendSpeed()  { return resultCache.sendSpeed; }
    protected Float getCachedInLossRate() { return resultCache.inLossRate; }
    protected Float getCachedOutLossRate() { return resultCache.outLossRate; }
    protected Boolean getCachedUpState()  { return resultCache.upState; }

    // ======================== core detection ========================

    private void doDetect() throws Exception {
        SnmpService snmpService = requireSnmpService();
        SnmpConnection conn     = (SnmpConnection) snmpService.getConnection();
        try {
            long state = getLong(conn, OID_UP_STATE);
            boolean upState = (state == IF_STATE_UP) || (state == IF_STATE_TESTING);

            if (!upState) {
                resultCache.clear();
                resultCache.upState        = false;
                resultCache.lastUpdateTimeMs = System.currentTimeMillis();
                return;
            }

            // Check if 64-bit HC counters are available
            boolean useHcCounters = getVar(conn, OID_HC_IN_OCTETS) != null;
            String inOctetsOid  = useHcCounters ? OID_HC_IN_OCTETS  : OID_IN_OCTETS;
            String outOctetsOid = useHcCounters ? OID_HC_OUT_OCTETS : OID_OUT_OCTETS;

            long startTime = System.currentTimeMillis();

            // First sample
            SampleValues before = new SampleValues();
            retrieveValues(conn, before, inOctetsOid, outOctetsOid);

            Thread.sleep(DEFAULT_SAMPLE_SECS * 1000);
            long elapsedMs = Math.max(System.currentTimeMillis() - startTime, 1L);

            // Second sample
            SampleValues after = new SampleValues();
            retrieveValues(conn, after, inOctetsOid, outOctetsOid);

            computeResult(before, after, elapsedMs, upState);
        } finally {
            snmpService.releaseConnection(conn);
        }
    }

    // ======================== SNMP helpers ========================

    private SnmpService requireSnmpService() {
        MonitorService svc = getSource();
        if (!(svc instanceof SnmpService snmpService)) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + " must belong to a SnmpService");
        }
        return snmpService;
    }

    private Variable getVar(SnmpConnection conn, String baseOid) throws Exception {
        return conn.getVar(buildOid(baseOid));
    }

    private long getLong(SnmpConnection conn, String baseOid) throws Exception {
        Variable v = getVar(conn, baseOid);
        return v != null ? v.toLong() : 0L;
    }

    // ======================== data retrieval ========================

    private void retrieveValues(SnmpConnection conn, SampleValues values,
                                String inOctetsOid, String outOctetsOid) throws Exception {
        values.inOctets     = getLong(conn, inOctetsOid);
        values.outOctets    = getLong(conn, outOctetsOid);
        values.inDiscards   = getLong(conn, OID_IN_DISCARDS);
        values.outDiscards  = getLong(conn, OID_OUT_DISCARDS);
        values.inErrors     = getLong(conn, OID_IN_ERRORS);
        values.outErrors    = getLong(conn, OID_OUT_ERRORS);
        values.inUCastPkts  = getLong(conn, OID_IN_UCAST_PKTS);
        values.inNUCastPkts = getLong(conn, OID_IN_NUCAST_PKTS);
        values.outUCastPkts = getLong(conn, OID_OUT_UCAST_PKTS);
        values.outNUCastPkts = getLong(conn, OID_OUT_NUCAST_PKTS);

        values.inPkts  = values.inUCastPkts  + values.inNUCastPkts;
        values.outPkts = values.outUCastPkts + values.outNUCastPkts;

        values.inLoss  = values.inDiscards;
        values.outLoss = values.outDiscards;
        if (values.outErrors > values.outDiscards) {
            // Device does not support discards monitoring; fall back to errors
            values.inLoss  = values.inErrors;
            values.outLoss = values.outErrors;
        }
    }

    // ======================== result computation ========================

    private void computeResult(SampleValues before, SampleValues after,
                               long elapsedMs, boolean upState) {
        long deltaInPkts  = after.inPkts  - before.inPkts;
        long deltaOutPkts = after.outPkts - before.outPkts;

        float inLossRate = 0f;
        if (deltaInPkts != 0) {
            inLossRate = (float) (after.inLoss - before.inLoss) / deltaInPkts;
        }
        float outLossRate = 0f;
        if (deltaOutPkts != 0) {
            outLossRate = (float) (after.outLoss - before.outLoss) / deltaOutPkts;
        }

        // Speed in Kbps (octets * 8 / ms = Kbps)
        float recvSpeed = (float) Math.abs(after.inOctets - before.inOctets)
                / elapsedMs * 8;
        float sendSpeed = (float) Math.abs(after.outOctets - before.outOctets)
                / elapsedMs * 8;

        resultCache.recvSpeed   = roundToFraction(recvSpeed, FRACTION_SPEED);
        resultCache.sendSpeed   = roundToFraction(sendSpeed, FRACTION_SPEED);
        resultCache.inLossRate  = roundToFraction(inLossRate, FRACTION_LOSS_RATE);
        resultCache.outLossRate = roundToFraction(outLossRate, FRACTION_LOSS_RATE);
        resultCache.upState     = upState;
        resultCache.lastUpdateTimeMs = System.currentTimeMillis();
    }

    private static float roundToFraction(double value, int fraction) {
        return (float) ((long) (value * fraction)) / fraction;
    }

    // ======================== inner classes ========================

    private static class SampleValues {
        long inOctets;
        long outOctets;
        long inDiscards;
        long outDiscards;
        long inErrors;
        long outErrors;
        long inUCastPkts;
        long inNUCastPkts;
        long outUCastPkts;
        long outNUCastPkts;
        long inPkts;
        long outPkts;
        long inLoss;
        long outLoss;
    }

    private static class ResultCache {
        Float  recvSpeed;
        Float  sendSpeed;
        Float  inLossRate;
        Float  outLossRate;
        Boolean upState;
        long   lastUpdateTimeMs = -1;

        void clear() {
            recvSpeed   = null;
            sendSpeed   = null;
            inLossRate  = null;
            outLossRate = null;
            upState     = null;
        }
    }
}
