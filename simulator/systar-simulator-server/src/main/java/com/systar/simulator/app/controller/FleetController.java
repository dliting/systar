package com.systar.simulator.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systar.simulator.app.controller.dto.DataPointSummary;
import com.systar.simulator.app.controller.dto.DeviceSummary;
import com.systar.simulator.app.controller.dto.FaultRequest;
import com.systar.simulator.app.controller.dto.OverrideRequest;
import com.systar.simulator.fleet.FleetManager;
import com.systar.simulator.model.SimulatedDevice;

/**
 * REST controller for managing the simulated device fleet.
 * <p>
 * Provides endpoints for listing devices, controlling lifecycle,
 * viewing data points, applying overrides, and injecting faults.
 */
@RestController
@RequestMapping("/api/fleet")
public class FleetController {

    private final FleetManager fleetManager;

    public FleetController(FleetManager fleetManager) {
        this.fleetManager = fleetManager;
    }

    // ======================== Fleet-level operations ========================

    @GetMapping
    public List<DeviceSummary> listDevices() {
        return fleetManager.listDevices().stream()
                .map(d -> new DeviceSummary(d.getId(), d.getName(),
                        d.getProtocol().name(), d.getStatus().name()))
                .toList();
    }

    @PostMapping("/start")
    public ResponseEntity<?> startAll() {
        try {
            fleetManager.startAll();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopAll() {
        fleetManager.stopAll();
        return ResponseEntity.ok().build();
    }

    // ======================== Single-device operations ========================

    @GetMapping("/{deviceId}")
    public ResponseEntity<?> getDevice(@PathVariable String deviceId) {
        SimulatedDevice device = fleetManager.getDevice(deviceId);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(device);
    }

    @PostMapping("/{deviceId}/start")
    public ResponseEntity<?> startDevice(@PathVariable String deviceId) {
        try {
            fleetManager.startDevice(deviceId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/{deviceId}/stop")
    public ResponseEntity<?> stopDevice(@PathVariable String deviceId) {
        fleetManager.stopDevice(deviceId);
        return ResponseEntity.ok().build();
    }

    // ======================== Data point operations ========================

    @GetMapping("/{deviceId}/points")
    public ResponseEntity<?> listPoints(@PathVariable String deviceId) {
        SimulatedDevice device = fleetManager.getDevice(deviceId);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(device.getDataPoints().stream()
                .map(p -> new DataPointSummary(p.getId(), p.getName(),
                        p.getCurrentValue(), p.getOverride() != null))
                .toList());
    }

    @PutMapping("/{deviceId}/points/{pointId}")
    public ResponseEntity<?> overrideValue(@PathVariable String deviceId,
                                           @PathVariable String pointId,
                                           @RequestBody OverrideRequest request) {
        fleetManager.applyOverride(deviceId, pointId, request.getValue());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{deviceId}/points/{pointId}/override")
    public ResponseEntity<?> clearOverride(@PathVariable String deviceId,
                                           @PathVariable String pointId) {
        fleetManager.clearOverride(deviceId, pointId);
        return ResponseEntity.ok().build();
    }

    // ======================== Fault operations ========================

    @PostMapping("/{deviceId}/fault")
    public ResponseEntity<?> injectFault(@PathVariable String deviceId,
                                         @RequestBody FaultRequest request) {
        fleetManager.injectFault(deviceId, request.getType());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{deviceId}/fault")
    public ResponseEntity<?> clearFault(@PathVariable String deviceId) {
        fleetManager.clearFault(deviceId);
        return ResponseEntity.ok().build();
    }
}
