package com.systar.ops.analysis.model;

import java.time.LocalDateTime;

public record AnomalyPoint(LocalDateTime timestamp, double actualValue,
                            double expectedValue, double deviation, String severity) {
}
