package com.systar.ops.analysis.model;

import java.util.List;

public record TrendPrediction(int monitorId, String granularity,
                               List<DataPoint> historical, List<DataPoint> predicted,
                               double confidence) {
}
