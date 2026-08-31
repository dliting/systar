package com.systar.ops.workorder;

public enum WorkOrderStatus {
    CREATED, ASSIGNED, PROCESSING, CLOSED, CANCELLED;

    public boolean canTransitionTo(WorkOrderStatus target) {
        return switch (this) {
            case CREATED -> target == ASSIGNED || target == CANCELLED;
            case ASSIGNED -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == CLOSED;
            case CLOSED, CANCELLED -> false;
        };
    }
}
