package com.systar.monitor.drivers.websocket;

import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.IMonitorResult;

/**
 * WebSocket probe that receives data pushed from external WebSocket sources.
 * <p>
 * Passive probe: data is routed from incoming WebSocket messages by the
 * WsService using the messageKey as the routing key.
 */
public class WsProbe extends Probe {

    private String messageKey;

    public WsProbe() {
    }

    @Override
    public void init(ProbeType type, int id, String name) {
        super.init(type, id, name);
        if (type != null && type.getSource() != null) {
            parseSource(type.getSource());
        }
    }

    // ======================== source parsing ========================

    private void parseSource(String source) {
        if (source == null || source.isBlank()) {
            this.messageKey = null;
            return;
        }
        this.messageKey = source.trim();
    }

    // ======================== detection (passive) ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        result.setValue(getValue());
        result.setSampleTime(System.currentTimeMillis());
    }

    // ======================== IPassiveMonitor ========================

    @Override
    public String makeRegisterKey() {
        return messageKey;
    }

    // ======================== getters / setters ========================

    public String getMessageKey() { return messageKey; }
    public void setMessageKey(String messageKey) { this.messageKey = messageKey; }
}
