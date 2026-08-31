package com.systar.data.service.retention;

public record RetentionSummary(
        RetentionResult sampleFloat,
        RetentionResult sampleInt,
        RetentionResult sampleBool,
        RetentionResult sampleException,
        RetentionResult alarmMessage,
        RetentionResult alarmLog,
        RetentionResult linkageLog
) {
}
