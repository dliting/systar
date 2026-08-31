package com.systar.monitor.drivers.tcpip;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.MonitorResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class TcpIpProbeTest {

    private TcpIpProbe probe;
    private TcpIpService service;

    @BeforeEach
    void setUp() {
        probe = new TcpIpProbe();
        service = new TcpIpService();
        service.setTimeout(2000);
    }

    private void initProbeWithSource(String source) {
        ProbeType type = new ProbeType("tcpip-probe");
        type.setSource(source);
        probe.init(type, 1, "tcpip-probe");
    }

    // ======================== source parsing ========================

    @Nested
    @DisplayName("parseSource - valid and invalid inputs")
    class ParseSource {

        @Test
        @DisplayName("parses register:40001:int format")
        void parsesRegisterFormat() {
            initProbeWithSource("register:40001:int");
            assertThat(probe.getRegisterType()).isEqualTo("register");
            assertThat(probe.getAddress()).isEqualTo(40001);
            assertThat(probe.getDataType()).isEqualTo("int");
        }

        @Test
        @DisplayName("parses register:30001:float format")
        void parsesFloatFormat() {
            initProbeWithSource("register:30001:float");
            assertThat(probe.getRegisterType()).isEqualTo("register");
            assertThat(probe.getAddress()).isEqualTo(30001);
            assertThat(probe.getDataType()).isEqualTo("float");
        }

        @Test
        @DisplayName("trims whitespace")
        void trimsWhitespace() {
            initProbeWithSource(" register : 40001 : int ");
            assertThat(probe.getRegisterType()).isEqualTo("register");
            assertThat(probe.getAddress()).isEqualTo(40001);
            assertThat(probe.getDataType()).isEqualTo("int");
        }

        @Test
        @DisplayName("insufficient parts throws IllegalArgumentException")
        void insufficientParts() {
            assertThatThrownBy(() -> initProbeWithSource("register:40001"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TCP/IP source format");
        }

        @Test
        @DisplayName("non-numeric address throws NumberFormatException")
        void nonNumericAddress() {
            assertThatThrownBy(() -> initProbeWithSource("register:abc:int"))
                    .isInstanceOf(NumberFormatException.class);
        }
    }

    // ======================== connection lifecycle ========================

    @Nested
    @DisplayName("TcpIpConnection - socket lifecycle")
    class ConnectionLifecycle {

        private ServerSocket serverSocket;
        private int port;

        @BeforeEach
        void startServer() throws Exception {
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            service.setHost("127.0.0.1");
            service.setPort(port);
        }

        @AfterEach
        void stopServer() throws Exception {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        }

        @Test
        @DisplayName("connection opens and reports connected")
        void openAndConnected() throws Exception {
            TcpIpService.TcpIpConnection conn = (TcpIpService.TcpIpConnection) service.createConnection();

            // Accept the connection on server side
            Thread acceptor = new Thread(() -> {
                try { serverSocket.accept(); } catch (Exception ignored) {}
            });
            acceptor.start();

            conn.open();
            assertThat(conn.isConnected()).isTrue();
            conn.close();
            assertThat(conn.isConnected()).isFalse();
        }

        @Test
        @DisplayName("sendAndReceive exchanges bytes with server")
        void sendAndReceive() throws Exception {
            TcpIpService.TcpIpConnection conn = (TcpIpService.TcpIpConnection) service.createConnection();

            // Server echoes back the request
            Thread server = new Thread(() -> {
                try {
                    Socket client = serverSocket.accept();
                    byte[] buf = new byte[256];
                    int n = client.getInputStream().read(buf);
                    if (n > 0) {
                        client.getOutputStream().write(buf, 0, n);
                        client.getOutputStream().flush();
                    }
                    client.close();
                } catch (Exception ignored) {}
            });
            server.start();

            conn.open();
            byte[] request = {0x01, 0x03, 0x00, 0x00, 0x00, 0x0A};
            byte[] response = conn.sendAndReceive(request);

            assertThat(response).containsExactly(request);
            conn.close();
        }
    }

    // ======================== detect ========================

    @Nested
    @DisplayName("detect - with real server")
    class DetectWithServer {

        private ServerSocket serverSocket;

        @BeforeEach
        void startServer() throws Exception {
            serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            service.setHost("127.0.0.1");
            service.setPort(port);
        }

        @AfterEach
        void stopServer() throws Exception {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        }

        @Test
        @DisplayName("detect connects to server and returns true for connectivity check")
        void detectConnectivityCheck() throws Exception {
            initProbeWithSource("connectivity:0:bool");

            // Accept connection on server side
            Thread acceptor = new Thread(() -> {
                try { serverSocket.accept(); } catch (Exception ignored) {}
            });
            acceptor.start();

            service.addMonitor(probe);
            probe.setSource(service);

            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            assertThat(result.getValue()).isEqualTo(true);
            assertThat(result.getSampleTime()).isNotNull();
        }

        @Test
        @DisplayName("detect sets error when connection fails")
        void detectConnectionFailure() throws Exception {
            serverSocket.close(); // Close server so connection fails
            initProbeWithSource("connectivity:0:bool");

            service.addMonitor(probe);
            probe.setSource(service);

            MonitorResult result = new MonitorResult(probe);
            probe.detect(result);

            assertThat(result.getError()).isNotNull();
        }
    }
}
