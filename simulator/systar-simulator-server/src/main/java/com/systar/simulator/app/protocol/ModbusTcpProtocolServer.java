package com.systar.simulator.app.protocol;

import com.systar.simulator.model.DataPointAddress;
import com.systar.simulator.model.ModbusAddress;
import com.systar.simulator.model.ModbusTcpEndpoint;
import com.systar.simulator.model.SimulatedDevice;
import com.systar.simulator.protocol.ProtocolServer;
import com.systar.simulator.protocol.ServerStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hand-rolled Modbus TCP slave server implementing {@link ProtocolServer}.
 * <p>
 * Accepts TCP connections and serves Modbus requests for registered devices.
 * Devices sharing the same port are multiplexed by Unit ID, with a single
 * {@link ServerSocket} per port.
 * <p>
 * MBAP frame format (matching {@code ModbusConnection}):
 * <pre>
 *   Transaction ID (2) | Protocol ID (2) | Length (2) | Unit ID (1) | PDU (n)
 * </pre>
 * Supported function codes: FC 01, 02, 03, 04, 05, 06.
 *
 * @see ModbusSlaveHandler
 */
public class ModbusTcpProtocolServer implements ProtocolServer {

    private static final Logger LOGGER = Logger.getLogger(ModbusTcpProtocolServer.class.getName());

    // ======================== MBAP constants ========================

    private static final int MBAP_HEADER_LENGTH  = 7;  // tid(2) + pid(2) + len(2) + uid(1)
    private static final int MODBUS_PROTOCOL_ID = 0;
    private static final int DEFAULT_BACKLOG     = 50;
    private static final int SO_TIMEOUT_MS       = 5000;

    // ======================== State ========================

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** deviceId -> handler */
    private final Map<String, ModbusSlaveHandler> handlers = new ConcurrentHashMap<>();

    /** deviceId -> port (to track which port each device uses) */
    private final Map<String, Integer> devicePorts = new ConcurrentHashMap<>();

    /** port -> (unitId -> deviceId) — for routing incoming requests, scoped per port */
    private final Map<Integer, Map<Integer, String>> portUnitIdToDeviceId = new ConcurrentHashMap<>();

    /** port -> ServerSocket (shared among devices on the same port) */
    private final Map<Integer, ServerSocket> serverSockets = new ConcurrentHashMap<>();

    /** port -> accept thread reference */
    private final Map<Integer, Thread>     acceptThreads  = new ConcurrentHashMap<>();

    /** port -> connected client sockets (for connection count) */
    private final Map<Integer, Set<Socket>> clientSockets  = new ConcurrentHashMap<>();

    // ======================== ProtocolServer ========================

    @Override
    public void start(SimulatedDevice device) throws Exception {
        ModbusTcpEndpoint endpoint = (ModbusTcpEndpoint) device.getEndpoint();
        int port      = endpoint.getPort();
        int unitId    = endpoint.getUnitId();
        String deviceId = device.getId();

        // Check for duplicate registration
        if (handlers.containsKey(deviceId)) {
            throw new IllegalStateException("Device '" + deviceId + "' is already registered");
        }

        // Check for unit ID conflict on the same port
        Map<Integer, String> unitIdMap = portUnitIdToDeviceId.computeIfAbsent(port, k -> new ConcurrentHashMap<>());
        String existing = unitIdMap.putIfAbsent(unitId, deviceId);
        if (existing != null && !existing.equals(deviceId)) {
            unitIdMap.remove(unitId, deviceId); // revert
            throw new IllegalStateException(
                    "Unit ID " + unitId + " on port " + port
                    + " is already assigned to device '" + existing
                    + "', cannot assign to '" + deviceId + "'");
        }

        // Create handler and register
        ModbusSlaveHandler handler = new ModbusSlaveHandler(deviceId);
        handlers.put(deviceId, handler);
        devicePorts.put(deviceId, port);

        // Start the ServerSocket for this port if not already running
        startServerSocket(port);

        LOGGER.info(() -> "Modbus slave started for device '" + deviceId
                + "' on port " + port + ", unitId=" + unitId);
    }

    @Override
    public void stop(String deviceId) {
        ModbusSlaveHandler handler = handlers.remove(deviceId);
        if (handler == null) {
            LOGGER.warning(() -> "Device '" + deviceId + "' not registered, ignoring stop");
            return;
        }

        // Remove port association
        Integer port = devicePorts.remove(deviceId);
        if (port != null) {
            Map<Integer, String> unitIdMap = portUnitIdToDeviceId.get(port);
            if (unitIdMap != null) {
                unitIdMap.entrySet().removeIf(e -> e.getValue().equals(deviceId));
                // Clean up empty unit ID map
                if (unitIdMap.isEmpty()) {
                    portUnitIdToDeviceId.remove(port);
                }
            }
        }

        // If no more handlers on this port, close the server socket
        if (port != null && !hasDevicesOnPort(port)) {
            closeServerSocket(port);
        }

        LOGGER.info(() -> "Modbus slave stopped for device '" + deviceId + "'");
    }

    @Override
    public void updateValue(String deviceId, DataPointAddress address, Object value) {
        ModbusSlaveHandler handler = handlers.get(deviceId);
        if (handler == null) {
            LOGGER.warning(() -> "updateValue: device '" + deviceId + "' not registered");
            return;
        }
        if (!(address instanceof ModbusAddress modbusAddr)) {
            LOGGER.warning(() -> "updateValue: address is not a ModbusAddress for device '"
                    + deviceId + "'");
            return;
        }
        handler.updateValue(
                modbusAddr.getRegisterType(),
                modbusAddr.getOffset(),
                modbusAddr.getDataType(),
                value
        );
    }

    @Override
    public ServerStatus getStatus() {
        int deviceCount     = handlers.size();
        int connectionCount = 0;
        for (Set<Socket> sockets : clientSockets.values()) {
            connectionCount += sockets.size();
        }
        return new ServerStatus(running.get(), deviceCount, connectionCount);
    }

    @Override
    public void close() {
        running.set(false);

        // Close all server sockets
        for (Integer port : new HashSet<>(serverSockets.keySet())) {
            closeServerSocket(port);
        }

        handlers.clear();
        devicePorts.clear();
        portUnitIdToDeviceId.clear();
    }

    // ======================== Port/device helpers ========================

    private boolean hasDevicesOnPort(int port) {
        for (Map.Entry<String, Integer> entry : devicePorts.entrySet()) {
            if (entry.getValue().equals(port)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Look up the device ID for a given port and unit ID from an incoming request.
     */
    private String resolveDeviceId(int port, int unitId) {
        Map<Integer, String> unitIdMap = portUnitIdToDeviceId.get(port);
        if (unitIdMap == null) {
            return null;
        }
        return unitIdMap.get(unitId);
    }

    // ======================== ServerSocket lifecycle ========================

    private void startServerSocket(int port) throws IOException {
        serverSockets.computeIfAbsent(port, p -> {
            try {
                ServerSocket ss = new ServerSocket(p, DEFAULT_BACKLOG);
                ss.setSoTimeout(SO_TIMEOUT_MS);
                running.set(true);

                Thread acceptThread = new Thread(
                        new AcceptLoop(p, ss),
                        "modbus-accept-" + p
                );
                acceptThread.setDaemon(true);
                acceptThread.start();
                acceptThreads.put(p, acceptThread);

                LOGGER.info(() -> "Modbus TCP server listening on port " + p);
                return ss;
            } catch (IOException e) {
                throw new RuntimeException("Failed to create ServerSocket on port " + p, e);
            }
        });
    }

    private void closeServerSocket(int port) {
        Thread thread = acceptThreads.remove(port);
        if (thread != null) {
            thread.interrupt();
        }

        ServerSocket ss = serverSockets.remove(port);
        if (ss != null && !ss.isClosed()) {
            try {
                ss.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error closing ServerSocket on port " + port, e);
            }
        }

        // Close connected client sockets
        Set<Socket> sockets = clientSockets.remove(port);
        if (sockets != null) {
            for (Socket s : sockets) {
                try {
                    s.close();
                } catch (IOException ignored) {
                }
            }
        }

        LOGGER.info(() -> "Modbus TCP server stopped on port " + port);
    }

    // ======================== Connection handling ========================

    /**
     * Accept loop that runs in a daemon thread, one per port.
     */
    private class AcceptLoop implements Runnable {

        private final int          port;
        private final ServerSocket serverSocket;

        AcceptLoop(int port, ServerSocket serverSocket) {
            this.port         = port;
            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            while (running.get() && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    client.setTcpNoDelay(true);
                    client.setSoTimeout(SO_TIMEOUT_MS);

                    registerClient(port, client);

                    Thread clientThread = new Thread(
                            new ClientHandler(port, client),
                            "modbus-client-" + port + "-" + client.getRemoteSocketAddress()
                    );
                    clientThread.setDaemon(true);
                    clientThread.start();
                } catch (IOException e) {
                    if (running.get() && !serverSocket.isClosed()) {
                        LOGGER.log(Level.WARNING,
                                "Accept error on port " + port, e);
                    }
                }
            }
        }
    }

    /**
     * Handles a single connected Modbus TCP client.
     * Reads MBAP frames, dispatches to the appropriate handler by Unit ID,
     * and writes back the response.
     */
    private class ClientHandler implements Runnable {

        private final int    port;
        private final Socket socket;

        ClientHandler(int port, Socket socket) {
            this.port   = port;
            this.socket = socket;
        }

        @Override
        public void run() {
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                while (running.get() && !socket.isClosed()) {
                    // Read MBAP header
                    byte[] header = readExact(in, MBAP_HEADER_LENGTH);
                    ByteBuffer headerBuf = ByteBuffer.wrap(header);

                    int tid    = headerBuf.getShort() & 0xFFFF;
                    int pid    = headerBuf.getShort() & 0xFFFF;
                    int length = headerBuf.getShort() & 0xFFFF;
                    int uid    = headerBuf.get() & 0xFF;

                    // Validate protocol ID
                    if (pid != MODBUS_PROTOCOL_ID) {
                        LOGGER.warning(() -> "Invalid protocol ID " + pid
                                + " from " + socket.getRemoteSocketAddress());
                        continue;
                    }

                    // Read PDU (length includes unitId byte, so PDU = length - 1)
                    int pduLength = length - 1;
                    if (pduLength <= 0) {
                        LOGGER.warning(() -> "Invalid MBAP length " + length
                                + " from " + socket.getRemoteSocketAddress());
                        continue;
                    }
                    byte[] pdu = readExact(in, pduLength);

                    // Route to handler by port + unit ID
                    String deviceId = resolveDeviceId(port, uid);
                    if (deviceId == null) {
                        // No device registered for this unit ID; send exception response
                        int fc = pdu[0] & 0xFF;
                        byte[] exPdu = new byte[]{(byte) (fc | 0x80), (byte) 2}; // Illegal Data Address
                        sendResponse(out, tid, uid, exPdu);
                        continue;
                    }

                    ModbusSlaveHandler handler = handlers.get(deviceId);
                    if (handler == null) {
                        int fc = pdu[0] & 0xFF;
                        byte[] exPdu = new byte[]{(byte) (fc | 0x80), (byte) 4}; // Server Device Failure
                        sendResponse(out, tid, uid, exPdu);
                        continue;
                    }

                    // Process PDU
                    byte[] responsePdu = handler.processPdu(pdu);
                    sendResponse(out, tid, uid, responsePdu);

                }
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.log(Level.FINE, "Client disconnected: "
                            + socket.getRemoteSocketAddress(), e);
                }
            } finally {
                unregisterClient(port, socket);
                closeQuietly(socket);
            }
        }
    }

    // ======================== I/O helpers ========================

    private static void sendResponse(OutputStream out, int tid, int uid, byte[] pdu) throws IOException {
        ByteBuffer frame = ByteBuffer.allocate(MBAP_HEADER_LENGTH + pdu.length);
        frame.putShort((short) tid);
        frame.putShort((short) MODBUS_PROTOCOL_ID);
        frame.putShort((short) (pdu.length + 1)); // length = uid(1) + PDU
        frame.put((byte) uid);
        frame.put(pdu);
        out.write(frame.array());
        out.flush();
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read < 0) {
                throw new IOException("Connection closed while reading "
                        + (length - totalRead) + " more bytes");
            }
            totalRead += read;
        }
        return buffer;
    }

    private void registerClient(int port, Socket socket) {
        clientSockets.computeIfAbsent(port, k -> ConcurrentHashMap.newKeySet()).add(socket);
    }

    private void unregisterClient(int port, Socket socket) {
        Set<Socket> sockets = clientSockets.get(port);
        if (sockets != null) {
            sockets.remove(socket);
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
