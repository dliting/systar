package com.systar.monitor.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the MonitorEngine port surface: the closed-source engine must provide
 * exactly these runtime methods, and the open server may only call these.
 * A drift here breaks one side's compile at the module boundary.
 */
class MonitorEngineContractTest {

    @Test
    @DisplayName("MonitorEngine declares exactly the runtime port methods")
    void portSurfaceIsExactlyTheRuntimeContract() {
        Set<String> actual = Arrays.stream(MonitorEngine.class.getDeclaredMethods())
                .map(MonitorEngineContractTest::signature)
                .collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrder(
                "loadAssets(AssetLoader)",
                "loadAlarmRules(List)",
                "reloadAlarmRules(List)",
                "loadLinkageRules(List,List,List)",
                "reloadLinkageRules(List,List,List)",
                "startUp()",
                "shutDown()",
                "addAsset(Asset)",
                "removeAsset(int)",
                "updateAsset(Asset)",
                "startMonitor(int)",
                "stopMonitor(int)",
                "detectOnce(int)",
                "detectImmediately(int)",
                "controlExecute(int,String)",
                "controlImmediately(int,String)",
                "receivePassiveData(String,Object)");
    }

    private static String signature(Method m) {
        String params = Arrays.stream(m.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
        return m.getName() + "(" + params + ")";
    }
}
