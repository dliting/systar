package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.MonitorConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modbus TCP connection using raw socket and Modbus TCP protocol frames.
 * <p>
 * Implements the standard Modbus TCP ADU format:
 * <pre>
 *   Transaction ID (2) | Protocol ID (2) | Length (2) | Unit ID (1) | PDU (n)
 * </pre>
 * Supports function codes 01 (Read Coils), 02 (Read Discrete Inputs),
 * 03 (Read Holding Registers), 04 (Read Input Registers),
 * 05 (Write Single Coil), 06 (Write Single Register).
 */
public class ModbusConnection implements MonitorConnection {

    // ======================== Modbus constants ========================

    private static final int MODBUS_PROTOCOL_ID = 0;
    private static final int MBAP_HEADER_LENGTH = 7; // tid(2) + pid(2) + len(2) + uid(1)

    // Function codes
    private static final int FC_READ_COILS = 1;
    private static final int FC_READ_DISCRETE_INPUTS = 2;
    private static final int FC_READ_HOLDING_REGISTERS = 3;
    private static final int FC_READ_INPUT_REGISTERS = 4;
    private static final int FC_WRITE_SINGLE_COIL = 5;
    private static final int FC_WRITE_SINGLE_REGISTER = 6;

    // Exception bit in function code
    private static final int EXCEPTION_BIT = 0x80;

    // Write coil constants
    private static final int COIL_ON = 0xFF00;
    private static final int COIL_OFF = 0x0000;

    // ======================== fields ========================

    private final String host;
    private final int port;
    private final int unitId;
    private final int timeoutMs;

    private final AtomicInteger transactionId = new AtomicInteger(0);
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    // ======================== constructor ========================

    public ModbusConnection(String host, int port, int unitId, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.unitId = unitId;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 5000;
    }

    // ======================== MonitorConnection ========================

    @Override
    public void open() throws Exception {
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
        }
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        in = null;
        out = null;
        socket = null;
    }

    // ======================== read operations ========================

    /**
     * Reads holding registers (FC 03).
     *
     * @param offset starting register address (0-based)
     * @param length number of registers to read
     * @return register values as int array
     */
    public int[] readHoldingRegisters(int offset, int length) throws Exception {
        byte[] pdu = encodeReadRequest(FC_READ_HOLDING_REGISTERS, offset, length);
        byte[] response = sendAndReceive(pdu);
        return extractRegisters(response, length);
    }

    /**
     * Reads input registers (FC 04).
     *
     * @param offset starting register address (0-based)
     * @param length number of registers to read
     * @return register values as int array
     */
    public int[] readInputRegisters(int offset, int length) throws Exception {
        byte[] pdu = encodeReadRequest(FC_READ_INPUT_REGISTERS, offset, length);
        byte[] response = sendAndReceive(pdu);
        return extractRegisters(response, length);
    }

    /**
     * Reads coils (FC 01).
     *
     * @param offset starting coil address (0-based)
     * @param length number of coils to read
     * @return boolean array of coil states
     */
    public boolean[] readCoils(int offset, int length) throws Exception {
        byte[] pdu = encodeReadRequest(FC_READ_COILS, offset, length);
        byte[] response = sendAndReceive(pdu);
        return extractCoils(response, length);
    }

    /**
     * Reads discrete inputs (FC 02).
     *
     * @param offset starting input address (0-based)
     * @param length number of inputs to read
     * @return boolean array of input states
     */
    public boolean[] readDiscreteInputs(int offset, int length) throws Exception {
        byte[] pdu = encodeReadRequest(FC_READ_DISCRETE_INPUTS, offset, length);
        byte[] response = sendAndReceive(pdu);
        return extractCoils(response, length);
    }

    // ======================== write operations ========================

    /**
     * Writes a single register (FC 06).
     *
     * @param offset register address (0-based)
     * @param value  the value to write (0-65535)
     */
    public void writeSingleRegister(int offset, int value) throws Exception {
        byte[] pdu = new byte[5];
        pdu[0] = FC_WRITE_SINGLE_REGISTER;
        pdu[1] = (byte) ((offset >> 8) & 0xFF);
        pdu[2] = (byte) (offset & 0xFF);
        pdu[3] = (byte) ((value >> 8) & 0xFF);
        pdu[4] = (byte) (value & 0xFF);

        byte[] response = sendAndReceive(pdu);
        // Echo-back validation: the response echoes the address and value
        int respAddr = ((response[1] & 0xFF) << 8) | (response[2] & 0xFF);
        int respVal = ((response[3] & 0xFF) << 8) | (response[4] & 0xFF);
        if (respAddr != offset || respVal != value) {
            throw new IOException("Modbus write register echo mismatch: wrote " + offset + "=" + value
                    + ", echoed " + respAddr + "=" + respVal);
        }
    }

    /**
     * Writes a single coil (FC 05).
     *
     * @param offset coil address (0-based)
     * @param value  true for ON, false for OFF
     */
    public void writeCoil(int offset, boolean value) throws Exception {
        byte[] pdu = new byte[5];
        pdu[0] = FC_WRITE_SINGLE_COIL;
        pdu[1] = (byte) ((offset >> 8) & 0xFF);
        pdu[2] = (byte) (offset & 0xFF);
        int coilValue = value ? COIL_ON : COIL_OFF;
        pdu[3] = (byte) ((coilValue >> 8) & 0xFF);
        pdu[4] = (byte) (coilValue & 0xFF);

        byte[] response = sendAndReceive(pdu);
        int respAddr = ((response[1] & 0xFF) << 8) | (response[2] & 0xFF);
        int respVal = ((response[3] & 0xFF) << 8) | (response[4] & 0xFF);
        if (respAddr != offset || respVal != coilValue) {
            throw new IOException("Modbus write coil echo mismatch");
        }
    }

    // ======================== frame encoding ========================

    private byte[] encodeReadRequest(int functionCode, int offset, int length) {
        return new byte[]{
                (byte) functionCode,
                (byte) ((offset >> 8) & 0xFF),
                (byte) (offset & 0xFF),
                (byte) ((length >> 8) & 0xFF),
                (byte) (length & 0xFF)
        };
    }

    /**
     * Sends a Modbus TCP ADU and returns the PDU from the response.
     */
    private synchronized byte[] sendAndReceive(byte[] pdu) throws Exception {
        if (!isConnected()) {
            throw new IOException("Modbus connection is not open: " + host + ":" + port);
        }

        int tid = transactionId.incrementAndGet() & 0xFFFF;
        int pduLength = pdu.length;

        // Build MBAP header + PDU
        ByteBuffer frame = ByteBuffer.allocate(MBAP_HEADER_LENGTH + pduLength);
        frame.putShort((short) tid);                           // Transaction ID
        frame.putShort((short) MODBUS_PROTOCOL_ID);            // Protocol ID (0 = Modbus)
        frame.putShort((short) (pduLength + 1));               // Length (unitId + PDU)
        frame.put((byte) unitId);                               // Unit ID
        frame.put(pdu);                                         // PDU

        out.write(frame.array());
        out.flush();

        // Read MBAP header
        byte[] header = readExact(MBAP_HEADER_LENGTH);
        ByteBuffer headerBuf = ByteBuffer.wrap(header);

        int respTid = headerBuf.getShort() & 0xFFFF;
        int respPid = headerBuf.getShort() & 0xFFFF;
        int respLen = headerBuf.getShort() & 0xFFFF;
        int respUid = headerBuf.get() & 0xFF;

        // Validate header
        if (respTid != tid) {
            throw new IOException("Modbus transaction ID mismatch: expected " + tid + ", got " + respTid);
        }
        if (respPid != MODBUS_PROTOCOL_ID) {
            throw new IOException("Modbus protocol ID mismatch: " + respPid);
        }

        // Read remaining PDU bytes (respLen includes unitId, so PDU = respLen - 1)
        int respPduLength = respLen - 1;
        if (respPduLength <= 0) {
            throw new IOException("Modbus response PDU length invalid: " + respPduLength);
        }

        byte[] respPdu = readExact(respPduLength);

        // Check for exception response
        int fc = respPdu[0] & 0xFF;
        if ((fc & EXCEPTION_BIT) != 0) {
            int exceptionCode = respPdu.length > 1 ? respPdu[1] & 0xFF : 0;
            throw new IOException("Modbus exception: FC=" + (fc & ~EXCEPTION_BIT)
                    + ", exception code=" + exceptionCode
                    + " (" + describeException(exceptionCode) + ")");
        }

        return respPdu;
    }

    // ======================== frame decoding ========================

    private int[] extractRegisters(byte[] pdu, int count) {
        // PDU: FC(1) + byteCount(1) + registerData(count*2)
        int[] registers = new int[count];
        for (int i = 0; i < count; i++) {
            int hi = pdu[2 + i * 2] & 0xFF;
            int lo = pdu[3 + i * 2] & 0xFF;
            registers[i] = (hi << 8) | lo;
        }
        return registers;
    }

    private boolean[] extractCoils(byte[] pdu, int count) {
        // PDU: FC(1) + byteCount(1) + coilData(...)
        boolean[] coils = new boolean[count];
        for (int i = 0; i < count; i++) {
            int byteIndex = 2 + (i / 8);
            int bitIndex = i % 8;
            coils[i] = (pdu[byteIndex] & (1 << bitIndex)) != 0;
        }
        return coils;
    }

    // ======================== I/O helpers ========================

    private byte[] readExact(int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read < 0) {
                throw new IOException("Modbus connection closed unexpectedly while reading");
            }
            totalRead += read;
        }
        return buffer;
    }

    // ======================== exception descriptions ========================

    private static String describeException(int code) {
        return switch (code) {
            case 1 -> "Illegal Function";
            case 2 -> "Illegal Data Address";
            case 3 -> "Illegal Data Value";
            case 4 -> "Server Device Failure";
            case 5 -> "Acknowledge";
            case 6 -> "Server Device Busy";
            case 7 -> "Negative Acknowledge";
            case 8 -> "Memory Parity Error";
            case 10 -> "Gateway Path Unavailable";
            case 11 -> "Gateway Target Device Failed to Respond";
            default -> "Unknown (" + code + ")";
        };
    }

    @Override
    public String toString() {
        return "ModbusConnection[" + host + ":" + port + ", unitId=" + unitId + "]";
    }
}
