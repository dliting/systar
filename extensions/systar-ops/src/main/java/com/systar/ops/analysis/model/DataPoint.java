package com.systar.ops.analysis.model;

import java.time.LocalDateTime;

public record DataPoint(LocalDateTime timestamp, double value) {
}
