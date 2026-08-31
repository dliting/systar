package com.systar.simulator.app.protocol;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds a Modbus register bank for a single device and processes PDU requests.
 * <p>
 * Four data areas are maintained, each 65536 elements wide:
 * <ul>
 *   <li>Holding registers (FC 03 / FC 06)</li>
 *   <li>Input registers (FC 04)</li>
 *   <li>Coils (FC 01 / FC 05)</li>
 *   <li>Discrete inputs (FC 02)</li>
 * </ul>
 * All operations are synchronized on the register bank instance so that
 * {@link #updateValue} and request processing are mutually exclusive.
 *
 * @see ModbusTcpProtocolServer
 */
public class ModbusSlaveHandler {

    private static final Logger LOGGER = Logger.getLogger(ModbusSlaveHandler.class.getName());

    // ======================== Function codes ========================

    private static final int FC_READ_COILS               = 1;
    private static final int FC_READ_DISCRETE_INPUTS     = 2;
    private static final int FC_READ_HOLDING_REGISTERS   = 3;
    private static final int FC_READ_INPUT_REGISTERS    = 4;
    private static final int FC_WRITE_SINGLE_COIL        = 5;
    private static final int FC_WRITE_SINGLE_REGISTER     = 6;

    // Exception codes (from ModbusConnection.describeException)
    private static final int EX_ILLEGAL_FUNCTION        = 1;
    private static final int EX_ILLEGAL_DATA_ADDRESS     = 2;
    private static final int EX_ILLEGAL_DATA_VALUE       = 3;
    private static final int EX_SERVER_DEVICE_FAILURE     = 4;

    // Coil constants (matching ModbusConnection)
    private static final int COIL_ON  = 0xFF00;
    private static final int COIL_OFF = 0x0000;

    // ======================== Register banks ========================

    private final int[]     holdingRegisters  = new int[65536];
    private final int[]     inputRegisters   = new int[65536];
    private final boolean[] coils            = new boolean[65536];
    private final boolean[] discreteInputs   = new boolean[65536];

    private final String deviceId;

    /**
     * Create a handler for the given device.
     *
     * @param deviceId logical device identifier (for logging)
     */
    public ModbusSlaveHandler(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    // ======================== Public API (called by ProtocolServer) ========================

    /**
     * Write a value into the register bank according to the register type
     * and data type conventions that match {@code ModbusValueReader}.
     * <p>
     * Encoding (must be exactly the reverse of ModbusValueReader decoding):
     * <ul>
     *   <li>float:  big-endian IEEE 754 across two registers</li>
     *   <li>int:    single register, unsigned</li>
     *   <li>short:  single register, sign-extended on read</li>
     *   <li>long:   big-endian across two registers</li>
     *   <li>bool:   single coil</li>
     * </ul>
     *
     * @param registerType "holding", "input", "coil", or "discrete"
     * @param offset      0-based register / coil address
     * @param dataType    "float", "int", "short", "long", or "bool"
     * @param value       the value to store
     */
    public synchronized void updateValue(String registerType, int offset, String dataType, Object value) {
        switch (registerType) {
            case "holding" -> writeHolding(offset, dataType, value);
            case "input"   -> writeInput(offset, dataType, value);
            case "coil"    -> writeCoil(offset, value);
            case "discrete" -> writeDiscrete(offset, value);
            default -> LOGGER.warning(() -> "Unknown register type '" + registerType
                    + "' for device " + deviceId + " at offset " + offset);
        }
    }

    // ======================== PDU processing ========================

    /**
     * Process a Modbus PDU (without MBAP header) and return the response PDU.
     * <p>
     * Called with the {@link #holdingRegisters}/{@link #inputRegisters}/{@link #coils}/{@link #discreteInputs}
     * lock already held by the caller if needed (this method itself synchronizes).
     *
     * @param pdu request PDU (starting with function code)
     * @return response PDU
     */
    public synchronized byte[] processPdu(byte[] pdu) {
        if (pdu == null || pdu.length < 1) {
            return exceptionResponse(FC_READ_HOLDING_REGISTERS, EX_ILLEGAL_DATA_VALUE);
        }

        int fc = pdu[0] & 0xFF;
        try {
            return switch (fc) {
                case FC_READ_COILS             -> handleReadCoils(pdu);
                case FC_READ_DISCRETE_INPUTS   -> handleReadDiscreteInputs(pdu);
                case FC_READ_HOLDING_REGISTERS -> handleReadHoldingRegisters(pdu);
                case FC_READ_INPUT_REGISTERS    -> handleReadInputRegisters(pdu);
                case FC_WRITE_SINGLE_COIL       -> handleWriteSingleCoil(pdu);
                case FC_WRITE_SINGLE_REGISTER   -> handleWriteSingleRegister(pdu);
                default -> exceptionResponse(fc, EX_ILLEGAL_FUNCTION);
            };
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "PDU processing error for device " + deviceId
                    + ", FC=" + fc, e);
            return exceptionResponse(fc, EX_SERVER_DEVICE_FAILURE);
        }
    }

    // ======================== Read handlers ========================

    private byte[] handleReadCoils(byte[] pdu) {
        if (pdu.length < 5) {
            return exceptionResponse(FC_READ_COILS, EX_ILLEGAL_DATA_VALUE);
        }
        int offset = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int count  = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
        if (count < 1 || count > 2000 || offset + count > 65536) {
            return exceptionResponse(FC_READ_COILS, EX_ILLEGAL_DATA_ADDRESS);
        }

        int byteCount = (count + 7) / 8;
        byte[] response = new byte[2 + byteCount];
        response[0] = FC_READ_COILS;
        response[1] = (byte) byteCount;

        for (int i = 0; i < count; i++) {
            if (coils[offset + i]) {
                response[2 + (i / 8)] |= (1 << (i % 8));
            }
        }
        return response;
    }

    private byte[] handleReadDiscreteInputs(byte[] pdu) {
        if (pdu.length < 5) {
            return exceptionResponse(FC_READ_DISCRETE_INPUTS, EX_ILLEGAL_DATA_VALUE);
        }
        int offset = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int count  = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
        if (count < 1 || count > 2000 || offset + count > 65536) {
            return exceptionResponse(FC_READ_DISCRETE_INPUTS, EX_ILLEGAL_DATA_ADDRESS);
        }

        int byteCount = (count + 7) / 8;
        byte[] response = new byte[2 + byteCount];
        response[0] = FC_READ_DISCRETE_INPUTS;
        response[1] = (byte) byteCount;

        for (int i = 0; i < count; i++) {
            if (discreteInputs[offset + i]) {
                response[2 + (i / 8)] |= (1 << (i % 8));
            }
        }
        return response;
    }

    private byte[] handleReadHoldingRegisters(byte[] pdu) {
        if (pdu.length < 5) {
            return exceptionResponse(FC_READ_HOLDING_REGISTERS, EX_ILLEGAL_DATA_VALUE);
        }
        int offset = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int count  = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
        if (count < 1 || count > 125 || offset + count > 65536) {
            return exceptionResponse(FC_READ_HOLDING_REGISTERS, EX_ILLEGAL_DATA_ADDRESS);
        }

        byte[] response = new byte[2 + count * 2];
        response[0] = FC_READ_HOLDING_REGISTERS;
        response[1] = (byte) (count * 2);

        for (int i = 0; i < count; i++) {
            int reg = holdingRegisters[offset + i];
            response[2 + i * 2]     = (byte) ((reg >> 8) & 0xFF);
            response[2 + i * 2 + 1] = (byte) (reg & 0xFF);
        }
        return response;
    }

    private byte[] handleReadInputRegisters(byte[] pdu) {
        if (pdu.length < 5) {
            return exceptionResponse(FC_READ_INPUT_REGISTERS, EX_ILLEGAL_DATA_VALUE);
        }
        int offset = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int count  = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
        if (count < 1 || count > 125 || offset + count > 65536) {
            return exceptionResponse(FC_READ_INPUT_REGISTERS, EX_ILLEGAL_DATA_ADDRESS);
        }

        byte[] response = new byte[2 + count * 2];
        response[0] = FC_READ_INPUT_REGISTERS;
        response[1] = (byte) (count * 2);

        for (int i = 0; i < count; i++) {
            int reg = inputRegisters[offset + i];
            response[2 + i * 2]     = (byte) ((reg >> 8) & 0xFF);
            response[2 + i * 2 + 1] = (byte) (reg & 0xFF);
        }
        return response;
    }

    // ======================== Write handlers ========================

    private byte[] handleWriteSingleCoil(byte[] pdu) {
        if (pdu.length < 5) {
            return exceptionResponse(FC_WRITE_SINGLE_COIL, EX_ILLEGAL_DATA_VALUE);
        }
        int offset = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int rawVal = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
        if (offset >= 65536 || (rawVal != COIL_ON && rawVal != COIL_OFF)) {
            return exceptionResponse(FC_WRITE_SINGLE_COIL, EX_ILLEGAL_DATA_VALUE);
        }

        coils[offset] = (rawVal == COIL_ON);

        // Echo back the request (standard Modbus behavior)
        return Arrays.copyOf(pdu, 5);
    }

    private byte[] handleWriteSingleRegister(byte[] pdu) {
        if (pdu.length < 5) {
            return exceptionResponse(FC_WRITE_SINGLE_REGISTER, EX_ILLEGAL_DATA_VALUE);
        }
        int offset = ((pdu[1] & 0xFF) << 8) | (pdu[2] & 0xFF);
        int value  = ((pdu[3] & 0xFF) << 8) | (pdu[4] & 0xFF);
        if (offset >= 65536) {
            return exceptionResponse(FC_WRITE_SINGLE_REGISTER, EX_ILLEGAL_DATA_ADDRESS);
        }

        holdingRegisters[offset] = value;

        // Echo back the request (standard Modbus behavior)
        return Arrays.copyOf(pdu, 5);
    }

    // ======================== Value writing (from updateValue) ========================

    private void writeHolding(int offset, String dataType, Object value) {
        switch (dataType) {
            case "float" -> {
                int bits = Float.floatToIntBits(((Number) value).floatValue());
                holdingRegisters[offset]     = (bits >> 16) & 0xFFFF;
                holdingRegisters[offset + 1] = bits & 0xFFFF;
            }
            case "int" -> holdingRegisters[offset] = ((Number) value).intValue() & 0xFFFF;
            case "short" -> holdingRegisters[offset] = ((Number) value).shortValue() & 0xFFFF;
            case "long" -> {
                long longVal = ((Number) value).longValue();
                holdingRegisters[offset]     = (int) (longVal >> 16) & 0xFFFF;
                holdingRegisters[offset + 1] = (int) longVal & 0xFFFF;
            }
            default -> LOGGER.warning(() -> "Unsupported data type '" + dataType
                    + "' for holding register in device " + deviceId);
        }
    }

    private void writeInput(int offset, String dataType, Object value) {
        switch (dataType) {
            case "float" -> {
                int bits = Float.floatToIntBits(((Number) value).floatValue());
                inputRegisters[offset]     = (bits >> 16) & 0xFFFF;
                inputRegisters[offset + 1] = bits & 0xFFFF;
            }
            case "int" -> inputRegisters[offset] = ((Number) value).intValue() & 0xFFFF;
            case "short" -> inputRegisters[offset] = ((Number) value).shortValue() & 0xFFFF;
            case "long" -> {
                long longVal = ((Number) value).longValue();
                inputRegisters[offset]     = (int) (longVal >> 16) & 0xFFFF;
                inputRegisters[offset + 1] = (int) longVal & 0xFFFF;
            }
            default -> LOGGER.warning(() -> "Unsupported data type '" + dataType
                    + "' for input register in device " + deviceId);
        }
    }

    private void writeCoil(int offset, Object value) {
        if (offset >= 0 && offset < 65536) {
            coils[offset] = Boolean.TRUE.equals(value);
        }
    }

    private void writeDiscrete(int offset, Object value) {
        if (offset >= 0 && offset < 65536) {
            discreteInputs[offset] = Boolean.TRUE.equals(value);
        }
    }

    // ======================== Helpers ========================

    private static byte[] exceptionResponse(int fc, int exceptionCode) {
        return new byte[]{
            (byte) (fc | 0x80),
            (byte) exceptionCode
        };
    }
}
