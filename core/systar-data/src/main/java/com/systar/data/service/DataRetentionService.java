package com.systar.data.service;

import com.systar.data.service.retention.RetentionSummary;

public interface DataRetentionService {

    RetentionSummary executeAll();
}
