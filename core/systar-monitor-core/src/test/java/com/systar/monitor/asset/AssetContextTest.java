package com.systar.monitor.asset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetContextTest {

    private AssetContext context;

    @BeforeEach
    void setUp() {
        context = new AssetContext();
    }

    @Test
    @DisplayName("addStateListener registers a listener")
    void addListener() {
        AssetStateListener listener = mock(AssetStateListener.class);
        context.addStateListener(listener);
        context.notifyStateChange(null, AssetState.NORMAL, AssetState.WARNING);

        verify(listener).onStateChanged(null, AssetState.NORMAL, AssetState.WARNING);
    }

    @Test
    @DisplayName("addStateListener rejects null")
    void addNullListener() {
        context.addStateListener(null);
        // Should not throw, and no listeners registered
        context.notifyStateChange(null, AssetState.NORMAL, AssetState.WARNING);
        // No exception thrown
    }

    @Test
    @DisplayName("addStateListener rejects duplicate registration")
    void duplicateListener() {
        AssetStateListener listener = mock(AssetStateListener.class);
        context.addStateListener(listener);
        context.addStateListener(listener); // duplicate

        context.notifyStateChange(null, AssetState.NORMAL, AssetState.WARNING);
        // Should only be called once
        verify(listener, times(1)).onStateChanged(null, AssetState.NORMAL, AssetState.WARNING);
    }

    @Test
    @DisplayName("removeStateListener removes a previously registered listener")
    void removeListener() {
        AssetStateListener listener = mock(AssetStateListener.class);
        context.addStateListener(listener);
        context.removeStateListener(listener);

        context.notifyStateChange(null, AssetState.NORMAL, AssetState.WARNING);
        verifyNoInteractions(listener);
    }

    @Test
    @DisplayName("notifyStateChange notifies all registered listeners")
    void notifiesAllListeners() {
        AssetStateListener listener1 = mock(AssetStateListener.class);
        AssetStateListener listener2 = mock(AssetStateListener.class);
        context.addStateListener(listener1);
        context.addStateListener(listener2);

        context.notifyStateChange(null, AssetState.NORMAL, AssetState.ERROR);

        verify(listener1).onStateChanged(null, AssetState.NORMAL, AssetState.ERROR);
        verify(listener2).onStateChanged(null, AssetState.NORMAL, AssetState.ERROR);
    }
}
