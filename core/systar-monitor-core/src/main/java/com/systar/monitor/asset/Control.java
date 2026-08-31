package com.systar.monitor.asset;

import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.result.IMonitorResult;

/**
 * A control monitor that can both read state and execute commands.
 * <p>
 * Controls represent actuators or writable registers. In addition to
 * the detection capability inherited from {@link Monitor}, controls
 * expose an {@link #execute(String)} method for sending commands.
 */
public abstract class Control extends Monitor<ControlType> {

    public Control() {
    }

    // ======================== Asset ========================

    @Override
    public AssetKind getKind() {
        return AssetKind.CONTROL;
    }

    @Override
    public <R> R accept(AssetVisitor<R> visitor) {
        return visitor.visit(this);
    }

    // ======================== control command ========================

    /**
     * Executes a control command.
     * <p>
     * Subclasses (driver layer) must implement the concrete mechanism
     * for writing a command to the device or actuator.
     *
     * @param command the command to execute
     * @throws Exception if execution fails
     */
    public abstract void execute(String command) throws Exception;

    // ======================== detection ========================

    /**
     * Detects the current control state (e.g., on/off, position).
     * <p>
     * Subclasses should override this with driver-specific logic.
     */
    @Override
    public void detect(IMonitorResult result) throws Exception {
        // Default no-op; driver layer should override.
    }
}
