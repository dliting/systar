package com.systar.monitor.asset;

import com.systar.monitor.asset.type.AssetTypeProperty;
import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.asset.type.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
class AssetBindPropertiesTest {

    /** Concrete Control subclass with typed setters for testing. */
    static class StubControl extends Control {
        private int    address;
        private String nodeId;
        private float  threshold;
        private boolean enabled;

        @Override public AssetKind getKind()      { return AssetKind.CONTROL; }
        @Override public boolean   isCompound()    { return false; }
        @Override public boolean   isMonitor()     { return true; }
        @Override public <R> R accept(AssetVisitor<R> v) { return v.visit(this); }
        @Override public void execute(String cmd)  { /* no-op */ }

        public int     getAddress()                { return address; }
        public void    setAddress(int address)      { this.address = address; }
        public String  getNodeId()                  { return nodeId; }
        public void    setNodeId(String nodeId)     { this.nodeId = nodeId; }
        public float   getThreshold()               { return threshold; }
        public void    setThreshold(float threshold) { this.threshold = threshold; }
        public boolean isEnabled()                   { return enabled; }
        public void    setEnabled(boolean enabled)   { this.enabled = enabled; }
    }

    static class StubService extends Control {
        private String remoteHost;
        private int    port;

        @Override public void execute(String cmd) { /* no-op */ }

        public String getRemoteHost()           { return remoteHost; }
        public void   setRemoteHost(String h)   { this.remoteHost = h; }
        public int    getPort()                 { return port; }
        public void   setPort(int p)            { this.port = p; }
    }

    private StubControl control;
    private ControlType  controlType;

    @BeforeEach
    void setUp() {
        control    = new StubControl();
        controlType = new ControlType("StubControl");
        control.init(controlType, 1, "test");
    }

    // ======================== INT binding ========================

    @Test
    @DisplayName("Binds INT property from string metadata to int setter")
    void bindsIntFromString() {
        controlType.addProperty(new AssetTypeProperty("Address", DataType.INT, "0", "addr"));
        control.setMetadata("Address", "1001");

        control.bindProperties();

        assertThat(control.getAddress()).isEqualTo(1001);
    }

    @Test
    @DisplayName("Binds INT property from Number metadata")
    void bindsIntFromNumber() {
        controlType.addProperty(new AssetTypeProperty("Address", DataType.INT, "0", "addr"));
        control.setMetadata("Address", 42);

        control.bindProperties();

        assertThat(control.getAddress()).isEqualTo(42);
    }

    // ======================== STRING binding ========================

    @Test
    @DisplayName("Binds STRING property to String setter")
    void bindsString() {
        controlType.addProperty(new AssetTypeProperty("NodeId", DataType.STRING, "", "node"));
        control.setMetadata("NodeId", "ns=2;s=Test");

        control.bindProperties();

        assertThat(control.getNodeId()).isEqualTo("ns=2;s=Test");
    }

    // ======================== FLOAT binding ========================

    @Test
    @DisplayName("Binds FLOAT property from string metadata")
    void bindsFloat() {
        controlType.addProperty(new AssetTypeProperty("Threshold", DataType.FLOAT, "0", "thr"));
        control.setMetadata("Threshold", "3.14");

        control.bindProperties();

        assertThat(control.getThreshold()).isCloseTo(3.14f, within(0.001f));
    }

    // ======================== BOOLEAN binding ========================

    @Test
    @DisplayName("Binds BOOLEAN property from string metadata")
    void bindsBoolean() {
        controlType.addProperty(new AssetTypeProperty("Enabled", DataType.BOOLEAN, "false", "flag"));
        control.setMetadata("Enabled", "true");

        control.bindProperties();

        assertThat(control.isEnabled()).isTrue();
    }

    // ======================== edge cases ========================

    @Test
    @DisplayName("Null metadata value skips property")
    void nullMetadataSkips() {
        controlType.addProperty(new AssetTypeProperty("Address", DataType.INT, "0", "addr"));

        control.bindProperties();

        assertThat(control.getAddress()).isEqualTo(0);
    }

    @Test
    @DisplayName("Missing setter does not throw")
    void missingSetterDoesNotThrow() {
        controlType.addProperty(new AssetTypeProperty("Nonexistent", DataType.STRING, "", "none"));
        control.setMetadata("Nonexistent", "value");

        assertThatCode(() -> control.bindProperties()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("No properties on type does not throw")
    void noPropertiesDoesNotThrow() {
        assertThatCode(() -> control.bindProperties()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Case-insensitive setter matching handles Remotehost→setRemoteHost")
    void caseInsensitiveMatch() {
        StubService svc    = new StubService();
        ControlType svcType = new ControlType("StubService");
        svcType.addProperty(new AssetTypeProperty("Remotehost", DataType.STRING, "", "host"));
        svc.init(svcType, 2, "svc");
        svc.setMetadata("Remotehost", "192.168.1.1");

        svc.bindProperties();

        assertThat(svc.getRemoteHost()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("Instance attribute overrides type default")
    void instanceOverridesDefault() {
        controlType.addProperty(new AssetTypeProperty("Address", DataType.INT, "10", "addr"));
        control.setMetadata("Address", "999");

        control.bindProperties();

        assertThat(control.getAddress()).isEqualTo(999);
    }

    // ======================== additional coverage ========================

    @Test
    @DisplayName("Binds FLOAT property from Number metadata (not just string)")
    void bindsFloatFromNumber() {
        controlType.addProperty(new AssetTypeProperty("Threshold", DataType.FLOAT, "0", "thr"));
        control.setMetadata("Threshold", 2.5d);

        control.bindProperties();

        assertThat(control.getThreshold()).isCloseTo(2.5f, within(0.001f));
    }

    /** Subclass with two overloaded setters for the same logical property. */
    static class OverloadedControl extends Control {
        private int    intAddress;
        private String strAddress;

        @Override public void execute(String cmd) { /* no-op */ }

        public int    getIntAddress() { return intAddress; }
        public String getStrAddress() { return strAddress; }

        public void setAddress(int addr)    { this.intAddress = addr; }
        public void setAddress(String addr) { this.strAddress = addr; }
    }

    @Test
    @DisplayName("Overloaded setter — picks the overload matching property DataType (INT→int)")
    void overloadDisambiguatesByDataType() {
        OverloadedControl c = new OverloadedControl();
        ControlType type    = new ControlType("Overloaded");
        type.addProperty(new AssetTypeProperty("Address", DataType.INT, "0", "addr"));
        c.init(type, 3, "ov");
        c.setMetadata("Address", "77");

        c.bindProperties();

        assertThat(c.getIntAddress()).isEqualTo(77);
        assertThat(c.getStrAddress()).isNull();
    }

    @Test
    @DisplayName("Overloaded setter — picks String overload when property DataType=STRING")
    void overloadPicksStringWhenDataTypeString() {
        OverloadedControl c = new OverloadedControl();
        ControlType type    = new ControlType("Overloaded");
        type.addProperty(new AssetTypeProperty("Address", DataType.STRING, "", "addr"));
        c.init(type, 4, "ov2");
        c.setMetadata("Address", "named-addr");

        c.bindProperties();

        assertThat(c.getStrAddress()).isEqualTo("named-addr");
        assertThat(c.getIntAddress()).isZero();
    }

    @Test
    @DisplayName("Mismatched property DataType vs setter param — converts to setter's actual type")
    void convertsToSetterParamTypeWhenPropertyDataTypeDiffers() {
        // Real-world regression: ModbusServices.xml declared UnitId as STRING but
        // ModbusService.setUnitId takes int. Binding must succeed by converting
        // "1" → int, not failing with "argument type mismatch".
        controlType.addProperty(new AssetTypeProperty("Address", DataType.STRING, "0", "addr"));
        control.setMetadata("Address", "42");

        control.bindProperties();

        assertThat(control.getAddress()).isEqualTo(42);
    }
}
