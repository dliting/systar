package com.systar.monitor.asset;

/**
 * Interface for passive monitors that register with a routing key.
 * <p>
 * The key is used by {@link PassiveService} to route incoming data
 * to the correct monitor instance.
 */
public interface IPassiveMonitor {

    /**
     * Returns a unique routing key for this passive monitor.
     *
     * @return the register key, must not be null
     */
    String makeRegisterKey();
}
