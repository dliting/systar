package com.systar.server.repository;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetRepositoryTest {

    private AssetStore store;

    @BeforeEach
    void setUp() {
        store = new AssetStore();
    }

    @Nested
    @DisplayName("TypeResolver.matchesSourceType with inheritance")
    class MatchesSourceTypeWithInheritance {

        @Test
        @DisplayName("exact match without inheritance")
        void exactMatch() {
            ServiceType base = new ServiceType("ModbusTcpMaster");
            store.getServiceTypes().register(base);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThat(resolver.matchesSourceType("ModbusTcpMaster", base)).isTrue();
        }

        @Test
        @DisplayName("no match when names differ")
        void noMatch() {
            ServiceType base = new ServiceType("ModbusTcpMaster");
            ServiceType other = new ServiceType("SnmpService");
            store.getServiceTypes().register(base);
            store.getServiceTypes().register(other);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThat(resolver.matchesSourceType("ModbusTcpMaster", other)).isFalse();
        }

        @Test
        @DisplayName("matches via Super inheritance chain")
        void matchesViaInheritance() {
            ServiceType base = new ServiceType("ModbusTcpMaster");
            ServiceType child = new ServiceType("SimulatedModbus");
            child.setSuperType(base);
            store.getServiceTypes().register(base);
            store.getServiceTypes().register(child);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThat(resolver.matchesSourceType("ModbusTcpMaster", child)).isTrue();
        }

        @Test
        @DisplayName("matches via multi-level inheritance chain")
        void multiLevelInheritance() {
            ServiceType grandparent = new ServiceType("AbstractService");
            ServiceType parent = new ServiceType("TcpService");
            parent.setSuperType(grandparent);
            ServiceType child = new ServiceType("ModbusTcpMaster");
            child.setSuperType(parent);
            store.getServiceTypes().register(grandparent);
            store.getServiceTypes().register(parent);
            store.getServiceTypes().register(child);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThat(resolver.matchesSourceType("AbstractService", child)).isTrue();
            assertThat(resolver.matchesSourceType("TcpService", child)).isTrue();
            assertThat(resolver.matchesSourceType("ModbusTcpMaster", child)).isTrue();
        }

        @Test
        @DisplayName("does not match when expected is deeper than actual")
        void deeperExpectedNoMatch() {
            ServiceType grandparent = new ServiceType("AbstractService");
            ServiceType parent = new ServiceType("TcpService");
            parent.setSuperType(grandparent);
            store.getServiceTypes().register(grandparent);
            store.getServiceTypes().register(parent);

            var resolver = new AssetRepository.TypeResolver(store);
            // grandparent does not inherit from parent
            assertThat(resolver.matchesSourceType("TcpService", grandparent)).isFalse();
        }
    }

    @Nested
    @DisplayName("Abstract type enforcement")
    class AbstractTypeEnforcement {

        @Test
        @DisplayName("throws when resolving abstract Space type")
        void abstractSpaceTypeThrows() {
            SpaceType abstractType = new SpaceType("AbstractSpace");
            abstractType.setAbstractType(true);
            store.getSpaceTypes().register(abstractType);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThatThrownBy(() -> resolver.resolveSpaceType("AbstractSpace", 1, "test"))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("abstract type");
        }

        @Test
        @DisplayName("throws when resolving abstract Device type")
        void abstractDeviceTypeThrows() {
            DeviceType abstractType = new DeviceType("AbstractDevice");
            abstractType.setAbstractType(true);
            store.getDeviceTypes().register(abstractType);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThatThrownBy(() -> resolver.resolveDeviceType("AbstractDevice", 1, "test"))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("abstract type");
        }

        @Test
        @DisplayName("throws when resolving abstract Service type")
        void abstractServiceTypeThrows() {
            ServiceType abstractType = new ServiceType("AbstractService");
            abstractType.setAbstractType(true);
            store.getServiceTypes().register(abstractType);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThatThrownBy(() -> resolver.resolveServiceType("AbstractService", 1, "test"))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("abstract type");
        }

        @Test
        @DisplayName("throws when resolving abstract Probe type")
        void abstractProbeTypeThrows() {
            ProbeType abstractType = new ProbeType("AbstractProbe");
            abstractType.setAbstractType(true);
            store.getProbeTypes().register(abstractType);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThatThrownBy(() -> resolver.resolveProbeType("AbstractProbe", 1, "test"))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("abstract type");
        }

        @Test
        @DisplayName("throws when resolving abstract Control type")
        void abstractControlTypeThrows() {
            ControlType abstractType = new ControlType("AbstractControl");
            abstractType.setAbstractType(true);
            store.getControlTypes().register(abstractType);

            var resolver = new AssetRepository.TypeResolver(store);
            assertThatThrownBy(() -> resolver.resolveControlType("AbstractControl", 1, "test"))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("abstract type");
        }

        @Test
        @DisplayName("concrete type with same name as abstract type is allowed (if registered first)")
        void concreteTypeAllowed() {
            SpaceType concrete = new SpaceType("Building");
            store.getSpaceTypes().register(concrete);

            var resolver = new AssetRepository.TypeResolver(store);
            SpaceType resolved = resolver.resolveSpaceType("Building", 1, "bld");
            assertThat(resolved).isSameAs(concrete);
        }
    }

    @Nested
    @DisplayName("Missing type enforcement")
    class MissingTypeEnforcement {

        @Test
        @DisplayName("throws when type is not registered and typeName is not null/blank")
        void missingSpaceTypeThrows() {
            var resolver = new AssetRepository.TypeResolver(store);
            assertThatThrownBy(() -> resolver.resolveSpaceType("NonExistent", 1, "test"))
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("not registered");
        }

        @Test
        @DisplayName("returns fallback type when typeName is null")
        void nullTypeNameReturnsFallback() {
            var resolver = new AssetRepository.TypeResolver(store);
            SpaceType fallback = resolver.resolveSpaceType(null, 42, "fallback-space");
            assertThat(fallback).isNotNull();
            assertThat(fallback.getName()).isEqualTo("space-42");
        }

        @Test
        @DisplayName("returns fallback type when typeName is blank")
        void blankTypeNameReturnsFallback() {
            var resolver = new AssetRepository.TypeResolver(store);
            SpaceType fallback = resolver.resolveSpaceType("   ", 7, "blank-space");
            assertThat(fallback).isNotNull();
            assertThat(fallback.getName()).isEqualTo("space-7");
        }
    }
}
