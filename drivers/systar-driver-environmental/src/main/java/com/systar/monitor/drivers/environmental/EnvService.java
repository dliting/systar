package com.systar.monitor.drivers.environmental;

import com.systar.monitor.asset.PassiveService;
import com.systar.monitor.result.MonitorResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Passive service for environmental sensor data reception via TCP.
 * <p>
 * Listens on a TCP port for fixed-format 25-byte sensor frames.
 * Each frame carries temperature, humidity, and a device ID.
 * Data is routed to registered probes by device ID and attribute.
 */
public class EnvService extends PassiveService {

    private static final Logger LOG = LoggerFactory.getLogger(EnvService.class);

    private static final int FRAME_LENGTH = 25;
    private static final int DEFAULT_PORT = 20502;
    private static final int MAX_CLIENT_THREADS = 16;

    private String connectionType = "tcp";
    private String host;
    private int port = DEFAULT_PORT;
    private String serialPort;
    private int baudRate = 9600;
    private int slaveId = 1;
    private int pollingInterval = 10;

    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private volatile boolean running;

    public EnvService() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        if (!"tcp".equalsIgnoreCase(connectionType)) {
            LOG.warn("Only TCP mode is currently supported, got: {}", connectionType);
        }
        running = true;
        clientExecutor = Executors.newFixedThreadPool(MAX_CLIENT_THREADS, r -> {
            Thread t = new Thread(r, "env-client");
            t.setDaemon(true);
            return t;
        });
        serverSocket = new ServerSocket(port);
        LOG.info("EnvService listening on port {}", port);

        Thread acceptThread = new Thread(this::acceptLoop, "env-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.warn("Error closing server socket", e);
        }
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
        }
        LOG.info("EnvService stopped");
    }

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                clientExecutor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running) {
                    LOG.warn("Accept error", e);
                }
            }
        }
    }

    private void handleClient(Socket client) {
        byte[] buffer = new byte[FRAME_LENGTH];
        try (client; InputStream in = client.getInputStream()) {
            while (running) {
                int totalRead = 0;
                while (totalRead < FRAME_LENGTH) {
                    int n = in.read(buffer, totalRead, FRAME_LENGTH - totalRead);
                    if (n < 0) return; // EOF
                    totalRead += n;
                }
                processFrame(buffer);
            }
        } catch (IOException e) {
            if (running) {
                LOG.debug("Client connection error: {}", e.getMessage());
            }
        }
    }

    private void processFrame(byte[] frame) {
        EnvFrameDecoder.ParsedData data = EnvFrameDecoder.decode(frame);
        if (data != null) {
            LOG.debug("Decoded env frame: device={}, temp={}, humidity={}",
                    data.getDeviceId(), data.getTemperature(), data.getHumidity());
            dispatchSensorData(
                    data.getDeviceId(), data.getTemperature(), data.getHumidity());
        } else {
            LOG.warn("Received invalid frame ({} bytes)", frame.length);
        }
    }

    // ======================== data routing ========================

    private void dispatchSensorData(String deviceId, float temperature, float humidity) {
        dispatchAttribute(deviceId, "temperature", temperature);
        dispatchAttribute(deviceId, "humidity", humidity);
    }

    private void dispatchAttribute(String deviceId, String attribute, float value) {
        var dispatcher = getResultDispatcher();
        if (dispatcher == null) return;

        // Primary: lookup by attribute name (matches EnvProbe.makeRegisterKey())
        var monitor = getMonitor(attribute);
        if (monitor != null) {
            dispatcher.dispatch(new MonitorResult(monitor, value));
        }
        // Fallback: lookup by deviceId:attribute compound key (multi-device setups)
        var compoundMonitor = getMonitor(deviceId + ":" + attribute);
        if (compoundMonitor != null && compoundMonitor != monitor) {
            dispatcher.dispatch(new MonitorResult(compoundMonitor, value));
        }
    }

    // ======================== getters / setters ========================

    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getSerialPort() { return serialPort; }
    public void setSerialPort(String serialPort) { this.serialPort = serialPort; }

    public int getBaudRate() { return baudRate; }
    public void setBaudRate(int baudRate) { this.baudRate = baudRate; }

    public int getSlaveId() { return slaveId; }
    public void setSlaveId(int slaveId) { this.slaveId = slaveId; }

    public int getPollingInterval() { return pollingInterval; }
    public void setPollingInterval(int pollingInterval) { this.pollingInterval = pollingInterval; }
}
