package com.systar.simulator.fleet;

import com.systar.simulator.generator.CorrelatedGenerator;
import com.systar.simulator.generator.FixedGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.SimulatedDevice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DependencyResolverTest {

    private static final int TIMEOUT_SECONDS = 180;

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void independentDataPointsMaintainOriginalOrder() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("ind-test");

        DataPoint dp1 = new DataPoint();
        dp1.setId("temp");
        dp1.setGenerator(new FixedGenerator(20.0));

        DataPoint dp2 = new DataPoint();
        dp2.setId("humidity");
        dp2.setGenerator(new FixedGenerator(50.0));

        DataPoint dp3 = new DataPoint();
        dp3.setId("pressure");
        dp3.setGenerator(new FixedGenerator(1013.25));

        device.setDataPoints(List.of(dp1, dp2, dp3));

        DependencyResolver resolver = new DependencyResolver();
        List<DataPoint> sorted      = resolver.resolveOrder(device);

        assertEquals(3, sorted.size());
        assertEquals("temp", sorted.get(0).getId());
        assertEquals("humidity", sorted.get(1).getId());
        assertEquals("pressure", sorted.get(2).getId());
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void dependentDataPointComesAfterDependency() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("dep-test");

        DataPoint dp1 = new DataPoint();
        dp1.setId("temp");
        dp1.setGenerator(new FixedGenerator(20.0));

        DataPoint dp2 = new DataPoint();
        dp2.setId("return-temp");
        CorrelatedGenerator cg = new CorrelatedGenerator();
        cg.setExpression("temp + 2.0");
        cg.setReferences(Map.of("temp", "temp"));
        dp2.setGenerator(cg);

        // dp2 declared before dp1, but dp2 depends on dp1
        device.setDataPoints(List.of(dp2, dp1));

        DependencyResolver resolver = new DependencyResolver();
        List<DataPoint> sorted      = resolver.resolveOrder(device);

        assertEquals(2, sorted.size());
        int tempIdx        = indexOf(sorted, "temp");
        int returnTempIdx  = indexOf(sorted, "return-temp");
        assertTrue(tempIdx < returnTempIdx,
                "Dependency 'temp' (index " + tempIdx + ") should come before 'return-temp' (index " + returnTempIdx + ")");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void circularDependencyThrowsIllegalStateException() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("circular-test");

        DataPoint dp1 = new DataPoint();
        dp1.setId("a");
        CorrelatedGenerator cg1 = new CorrelatedGenerator();
        cg1.setExpression("b");
        cg1.setReferences(Map.of("b", "b"));
        dp1.setGenerator(cg1);

        DataPoint dp2 = new DataPoint();
        dp2.setId("b");
        CorrelatedGenerator cg2 = new CorrelatedGenerator();
        cg2.setExpression("a");
        cg2.setReferences(Map.of("a", "a"));
        dp2.setGenerator(cg2);

        device.setDataPoints(List.of(dp1, dp2));

        DependencyResolver resolver = new DependencyResolver();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolveOrder(device));
        assertTrue(ex.getMessage().contains("Circular dependency"));
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void multipleDependenciesResolvedCorrectly() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("multi-dep-test");

        // Independent base points
        DataPoint temp     = new DataPoint(); temp.setId("temp");     temp.setGenerator(new FixedGenerator(20.0));
        DataPoint humidity = new DataPoint(); humidity.setId("humidity"); humidity.setGenerator(new FixedGenerator(50.0));

        // Depends on temp
        DataPoint returnTemp = new DataPoint(); returnTemp.setId("return-temp");
        CorrelatedGenerator cgRT = new CorrelatedGenerator();
        cgRT.setExpression("temp + 2.0");
        cgRT.setReferences(Map.of("temp", "temp"));
        returnTemp.setGenerator(cgRT);

        // Depends on both temp and humidity
        DataPoint comfort = new DataPoint(); comfort.setId("comfort");
        CorrelatedGenerator cgC = new CorrelatedGenerator();
        cgC.setExpression("temp - humidity * 0.1");
        cgC.setReferences(Map.of("temp", "temp", "humidity", "humidity"));
        comfort.setGenerator(cgC);

        // Deliberately shuffled order
        device.setDataPoints(List.of(comfort, temp, returnTemp, humidity));

        DependencyResolver resolver = new DependencyResolver();
        List<DataPoint> sorted      = resolver.resolveOrder(device);

        assertEquals(4, sorted.size());

        int tempIdx       = indexOf(sorted, "temp");
        int humIdx        = indexOf(sorted, "humidity");
        int rtIdx         = indexOf(sorted, "return-temp");
        int comfortIdx    = indexOf(sorted, "comfort");

        // return-temp depends on temp
        assertTrue(tempIdx < rtIdx,
                "'temp' must precede 'return-temp'");
        // comfort depends on both temp and humidity
        assertTrue(tempIdx < comfortIdx,
                "'temp' must precede 'comfort'");
        assertTrue(humIdx < comfortIdx,
                "'humidity' must precede 'comfort'");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void emptyDeviceReturnsEmptyList() {
        SimulatedDevice device = new SimulatedDevice();
        device.setId("empty-test");

        DependencyResolver resolver = new DependencyResolver();
        List<DataPoint> sorted      = resolver.resolveOrder(device);

        assertNotNull(sorted);
        assertTrue(sorted.isEmpty());
    }

    private static int indexOf(List<DataPoint> points, String id) {
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).getId().equals(id)) {
                return i;
            }
        }
        fail("DataPoint with id '" + id + "' not found in list");
        return -1; // unreachable
    }
}
