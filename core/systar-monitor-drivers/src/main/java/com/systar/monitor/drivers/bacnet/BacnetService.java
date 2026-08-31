package com.systar.monitor.drivers.bacnet;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.ServiceFuture;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.Encodable;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyRequest;
import com.systar.monitor.asset.ActiveService;
import com.systar.monitor.asset.MonitorConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Active service for BACnet/IP device communication using BACnet4J.
 * <p>
 * Each connection wraps a BACnet4J {@link LocalDevice} bound to a local
 * UDP port. ReadProperty requests are sent to the configured remote device.
 */
public class BacnetService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(BacnetService.class);

    static final int DEFAULT_BACNET_PORT = 47808;
    static final int DEFAULT_DEVICE_ID = 100;
    static final int DEFAULT_TIMEOUT_MS = 5000;
    static final int LOCAL_DEVICE_ID = 1;
    /** BACnet write priority (1=highest, 16=lowest/relinquishable). 16 = manual operator default. */
    static final UnsignedInteger DEFAULT_WRITE_PRIORITY = new UnsignedInteger(16);

    private int deviceId = DEFAULT_DEVICE_ID;
    private String remoteHost;
    private int remotePort = DEFAULT_BACNET_PORT;
    private int timeout = DEFAULT_TIMEOUT_MS;

    public BacnetService() {
    }

    @Override
    public void start() throws Exception {
        LOG.info("BacnetService started for {}:{}", remoteHost, remotePort);
    }

    @Override
    public void stop() {
        LOG.info("BacnetService stopped");
    }

    @Override
    public MonitorConnection createConnection() throws Exception {
        return new BacnetConnection(this);
    }

    public int getDeviceId() { return deviceId; }
    public void setDeviceId(int deviceId) { this.deviceId = deviceId; }

    public String getRemoteHost() { return remoteHost; }
    public void setRemoteHost(String remoteHost) { this.remoteHost = remoteHost; }

    public int getRemotePort() { return remotePort; }
    public void setRemotePort(int remotePort) { this.remotePort = remotePort; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    // ======================== inner connection class ========================

    public static class BacnetConnection implements MonitorConnection {

        private static final Logger CONN_LOG = LoggerFactory.getLogger(BacnetConnection.class);

        private final BacnetService service;
        private LocalDevice localDevice;
        private Transport transport;

        public BacnetConnection(BacnetService service) {
            this.service = service;
        }

        @Override
        public void open() throws Exception {
            IpNetwork network = new IpNetworkBuilder()
                    .withLocalBindAddress("0.0.0.0")
                    .withPort(0) // any available port
                    .withBroadcast(service.getRemoteHost(), service.getRemotePort())
                    .withReuseAddress(true)
                    .build();

            transport = new DefaultTransport(network);
            transport.setTimeout(service.getTimeout());

            localDevice = new LocalDevice(LOCAL_DEVICE_ID, transport);
            localDevice.initialize();

            CONN_LOG.info("BACnet local device initialized, target: {}:{} (deviceId={})",
                    service.getRemoteHost(), service.getRemotePort(), service.getDeviceId());
        }

        @Override
        public boolean isConnected() {
            return localDevice != null && localDevice.isInitialized();
        }

        @Override
        public void close() {
            if (localDevice != null) {
                localDevice.terminate();
                localDevice = null;
            }
            transport = null;
        }

        /**
         * Reads a BACnet object property from the remote device.
         *
         * @param objectType BACnet object type identifier
         * @param instance   object instance number
         * @param propertyId property identifier
         * @return the property value, or null if reading fails
         */
        public Object read(int objectType, int instance, int propertyId) throws Exception {
            if (localDevice == null) {
                throw new IllegalStateException("BACnet connection not open");
            }

            ObjectType objType = mapObjectType(objectType);
            PropertyIdentifier pid = mapPropertyIdentifier(propertyId);
            if (objType == null || pid == null) {
                throw new IllegalArgumentException(
                        "Unknown BACnet type/property: " + objectType + "/" + propertyId);
            }

            ObjectIdentifier oid = new ObjectIdentifier(objType, instance);

            // Try cached value first
            Encodable cached = localDevice.getCachedRemoteProperty(
                    service.getDeviceId(), oid, pid);
            if (cached != null) {
                return encodableToJava(cached);
            }

            // Send ReadProperty request — let BACnetException propagate so the
            // Control layer can record/log it as a detect failure instead of
            // silently returning null (which looked like "device returned nothing").
            ReadPropertyRequest request = new ReadPropertyRequest(oid, pid);
            RemoteDevice remoteDevice = findOrDiscoverRemoteDevice();
            ServiceFuture future = localDevice.send(remoteDevice, request);
            ReadPropertyAck ack = future.get();
            Encodable value = ack.getValue();
            if (value != null) {
                localDevice.setCachedRemoteProperty(
                        service.getDeviceId(), oid, pid, value);
            }
            return encodableToJava(value);
        }

        /**
         * Writes a BACnet object property to the remote device.
         *
         * @param objectType BACnet object type identifier
         * @param instance   object instance number
         * @param propertyId property identifier
         * @param value      the value to write (Encodable)
         */
        public void write(int objectType, int instance, int propertyId, Encodable value) throws Exception {
            if (localDevice == null) {
                throw new IllegalStateException("BACnet connection not open");
            }

            ObjectType objType = mapObjectType(objectType);
            PropertyIdentifier pid = mapPropertyIdentifier(propertyId);
            if (objType == null || pid == null) {
                throw new IllegalArgumentException(
                        "Unknown BACnet type/property: " + objectType + "/" + propertyId);
            }

            ObjectIdentifier oid = new ObjectIdentifier(objType, instance);
            WritePropertyRequest request = new WritePropertyRequest(
                    oid, pid, null, value, DEFAULT_WRITE_PRIORITY);
            try {
                RemoteDevice remoteDevice = findOrDiscoverRemoteDevice();
                localDevice.send(remoteDevice, request).get();
                CONN_LOG.info("BACnet WriteProperty succeeded for {}:{}:{}", objType, instance, pid);
            } catch (BACnetException e) {
                CONN_LOG.warn("BACnet WriteProperty failed for {}:{}:{}: {}",
                        objType, instance, pid, e.getMessage());
                throw e;
            }
        }

        private RemoteDevice findOrDiscoverRemoteDevice() throws BACnetException {
            RemoteDevice remote = localDevice.getCachedRemoteDevice(service.getDeviceId());
            if (remote != null) {
                return remote;
            }
            CONN_LOG.debug("Discovering remote BACnet device {}", service.getDeviceId());
            return localDevice.getRemoteDeviceBlocking(service.getDeviceId(),
                    service.getTimeout());
        }

        static ObjectType mapObjectType(int objectType) {
            return switch (objectType) {
                case 0 -> ObjectType.analogInput;
                case 1 -> ObjectType.analogOutput;
                case 2 -> ObjectType.analogValue;
                case 3 -> ObjectType.binaryInput;
                case 4 -> ObjectType.binaryOutput;
                case 5 -> ObjectType.binaryValue;
                case 13 -> ObjectType.multiStateInput;
                case 14 -> ObjectType.multiStateOutput;
                case 19 -> ObjectType.multiStateValue;
                default -> null;
            };
        }

        static PropertyIdentifier mapPropertyIdentifier(int propertyId) {
            return switch (propertyId) {
                case 85 -> PropertyIdentifier.presentValue;
                case 28 -> PropertyIdentifier.description;
                case 117 -> PropertyIdentifier.units;
                case 77 -> PropertyIdentifier.objectName;
                case 111 -> PropertyIdentifier.statusFlags;
                default -> null;
            };
        }

        static Object encodableToJava(Encodable encodable) {
            if (encodable == null) return null;
            // BACnet4J Encodable subclasses implement toString() meaningfully
            String str = encodable.toString();
            // Try as number
            try {
                if (str.contains(".")) return Float.parseFloat(str);
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                // Return string representation
            }
            return str;
        }
    }
}
