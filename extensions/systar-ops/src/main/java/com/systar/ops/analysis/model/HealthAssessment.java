package com.systar.ops.analysis.model;

import java.util.List;

public record HealthAssessment(int deviceId, String deviceName,
                                double healthScore, String level,
                                List<String> riskFactors) {
}
