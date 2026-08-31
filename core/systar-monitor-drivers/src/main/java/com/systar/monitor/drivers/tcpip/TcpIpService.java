package com.systar.monitor.drivers.tcpip;

import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Active service for generic TCP/IP socket communication.
 * <p>
 * Configuration parameters:
 * <ul>
 *   <li>{@code host} -- target device IP address</li>
 *   <li>{@code port} -- target device TCP port</li>
 *   <li>{@code timeout} -- socket read timeout in ms (default: 5000)</li>
 *   <li>{@code frameType} -- custom protocol frame format identifier (default: "raw")</li>
 *   <li>{@code byteOrder} -- byte order: "big-endian" or "little-endian" (default: "big-endian")</li>
 * </ul>
 */
public class TcpIpService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(TcpIpService.class);

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_SIZE = 4096;

    private String host;
    private int port;
    private int timeout = DEFAULT_TIMEOUT_MS;
    private String frameType = "raw";
    private String byteOrder = "big-endian";

    public TcpIpService() {
    }

    // ======================== lifecycle ========================

    @Override
    public void start() throws Exception {
        LOG.info("TcpIpService started for {}:{}", host, port);
    }

    @Override
    public void stop() {
        LOG.info("TcpIpService stopped");
    }

    // ======================== connection factory ========================

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new TcpIpConnection(this);
    }

    int getMaxResponseSize() {
        return MAX_RESPONSE_SIZE;
    }

    // ======================== getters / setters ========================

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getFrameType() {
        return frameType;
    }

    public void setFrameType(String frameType) {
        this.frameType = frameType;
    }

    public String getByteOrder() {
        return byteOrder;
    }

    public void setByteOrder(String byteOrder) {
        this.byteOrder = byteOrder;
    }

    // ======================== inner connection class ========================

    /**
     * TCP socket connection for custom protocol communication.
     */
    public static class TcpIpConnection implements MonitorConnection {

        private static final Logger CONN_LOG = LoggerFactory.getLogger(TcpIpConnection.class);

        private final TcpIpService service;
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public TcpIpConnection(TcpIpService service) {
            this.service = service;
        }

        @Override
        public void open() throws Exception {
            socket = new Socket();
            socket.connect(new InetSocketAddress(service.getHost(), service.getPort()), service.getTimeout());
            socket.setSoTimeout(service.getTimeout());
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            CONN_LOG.debug("Connected to {}:{}", service.getHost(), service.getPort());
        }

        @Override
        public boolean isConnected() {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }

        @Override
        public void close() {
            try {
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                CONN_LOG.warn("Error closing TCP input stream", e);
            }
            try {
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                CONN_LOG.warn("Error closing TCP output stream", e);
            }
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                CONN_LOG.warn("Error closing TCP socket", e);
            }
            socket = null;
            inputStream = null;
            outputStream = null;
        }

        /**
         * Sends a request frame and reads the response.
         *
         * @param request the request bytes to send
         * @return the response bytes (up to max response size)
         * @throws IOException if I/O fails
         */
        public byte[] sendAndReceive(byte[] request) throws IOException {
            outputStream.write(request);
            outputStream.flush();
            byte[] buffer = new byte[service.getMaxResponseSize()];
            int n = inputStream.read(buffer);
            if (n <= 0) {
                throw new IOException("No response received from " + service.getHost() + ":" + service.getPort());
            }
            byte[] response = new byte[n];
            System.arraycopy(buffer, 0, response, 0, n);
            return response;
        }
    }
}
