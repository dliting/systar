package com.systar.monitor.drivers.mqtt;

import com.systar.monitor.asset.PassiveService;
import com.systar.monitor.result.MonitorResult;

import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Passive service for MQTT-based data ingestion.
 * <p>
 * Connects to an MQTT broker, subscribes to topics registered by
 * {@link MqttProbe} instances, and routes incoming messages to the
 * matching probe(s) for value extraction and dispatch.
 */
public class MqttService extends PassiveService {

    private static final Logger LOG = LoggerFactory.getLogger(MqttService.class);

    private static final int DEFAULT_QOS = 1;
    private static final int DEFAULT_KEEPALIVE_SECS = 60;

    private String brokerUrl;
    private String clientId;
    private String username;
    private String password;
    private int qos = DEFAULT_QOS;
    private int keepAliveIntervalSecs = DEFAULT_KEEPALIVE_SECS;

    /** topic → probes registered for that topic. */
    private final Map<String, List<MqttProbe>> topicProbes = new ConcurrentHashMap<>();

    private MqttClient client;
    private volatile boolean running;

    public MqttService() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        if (brokerUrl == null || brokerUrl.isBlank()) {
            LOG.warn("MqttService brokerUrl not configured — service will not start");
            return;
        }
        running = true;

        String effectiveClientId = (clientId != null && !clientId.isBlank())
                ? clientId : "systar-iot-" + UUID.randomUUID().toString().substring(0, 8);

        client = new MqttClient(brokerUrl, effectiveClientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(keepAliveIntervalSecs);
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            options.setPassword(password.toCharArray());
        }

        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                LOG.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                onMessage(topic, message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // No-op for subscriber
            }
        });

        client.connect(options);
        LOG.info("MQTT connected to {}", brokerUrl);

        // Subscribe to all topics registered by probes
        for (String topic : topicProbes.keySet()) {
            client.subscribe(topic, qos);
            LOG.info("MQTT subscribed to topic: {} (qos={})", topic, qos);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (client != null) {
            try {
                client.disconnect();
            } catch (Exception e) {
                LOG.warn("Error disconnecting MQTT client", e);
            }
            try {
                client.close();
            } catch (Exception e) {
                LOG.warn("Error closing MQTT client", e);
            }
            client = null;
        }
        LOG.info("MqttService stopped");
    }

    // ======================== probe registration ========================

    /**
     * Registers a probe for the given MQTT topic.
     * <p>
     * Called from {@link MqttProbe#init(com.systar.monitor.asset.type.ProbeType, int, String)}.
     * If the service is already running, the topic is subscribed immediately.
     */
    void registerProbe(String topic, MqttProbe probe) {
        topicProbes.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(probe);
        LOG.debug("Registered probe {} for MQTT topic: {}", probe.getId(), topic);
    }

    // ======================== message routing ========================

    private void onMessage(String topic, MqttMessage message) {
        List<MqttProbe> probes = topicProbes.get(topic);
        if (probes == null || probes.isEmpty()) {
            LOG.debug("No probes registered for MQTT topic: {}", topic);
            return;
        }

        String payload = new String(message.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
        LOG.debug("MQTT message on topic {}: {}", topic, payload);

        var dispatcher = getResultDispatcher();
        if (dispatcher == null) {
            LOG.warn("No ResultDispatcher set on MqttService; message on topic {} dropped", topic);
            return;
        }

        for (MqttProbe probe : probes) {
            try {
                probe.onMessage(payload);
                Object value = probe.getCachedValue();
                dispatcher.dispatch(new MonitorResult(probe, value));
            } catch (Exception e) {
                LOG.warn("Failed to process MQTT message for probe {} on topic {}",
                        probe.getId(), topic, e);
            }
        }
    }

    // ======================== getters / setters ========================

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }

    public int getKeepAliveIntervalSecs() { return keepAliveIntervalSecs; }
    public void setKeepAliveIntervalSecs(int keepAliveIntervalSecs) { this.keepAliveIntervalSecs = keepAliveIntervalSecs; }
}
