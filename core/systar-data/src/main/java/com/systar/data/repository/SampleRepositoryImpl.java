package com.systar.data.repository;

import com.systar.data.entity.SampleBooleanEntity;
import com.systar.data.entity.SampleExceptionEntity;
import com.systar.data.entity.SampleFloatEntity;
import com.systar.data.entity.SampleIntEntity;
import com.systar.data.mapper.SampleBooleanMapper;
import com.systar.data.mapper.SampleExceptionMapper;
import com.systar.data.mapper.SampleFloatMapper;
import com.systar.data.mapper.SampleIntMapper;
import com.systar.monitor.result.SampleRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Repository implementation that persists typed sample data
 * to the appropriate database table via MyBatis-Plus mappers.
 */
@Component
public class SampleRepositoryImpl implements SampleRepository {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final SampleFloatMapper floatMapper;
    private final SampleIntMapper intMapper;
    private final SampleBooleanMapper boolMapper;
    private final SampleExceptionMapper exceptionMapper;

    public SampleRepositoryImpl(SampleFloatMapper floatMapper,
                                SampleIntMapper intMapper,
                                SampleBooleanMapper boolMapper,
                                SampleExceptionMapper exceptionMapper) {
        this.floatMapper = floatMapper;
        this.intMapper = intMapper;
        this.boolMapper = boolMapper;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public void saveFloat(int monitorId, float value, long sampleTimeMs) {
        SampleFloatEntity entity = new SampleFloatEntity();
        entity.setMonitorId(monitorId);
        entity.setValue(value);
        entity.setSampleTime(toLocalDateTime(sampleTimeMs));
        floatMapper.insert(entity);
    }

    @Override
    public void saveInt(int monitorId, int value, long sampleTimeMs) {
        SampleIntEntity entity = new SampleIntEntity();
        entity.setMonitorId(monitorId);
        entity.setValue(value);
        entity.setSampleTime(toLocalDateTime(sampleTimeMs));
        intMapper.insert(entity);
    }

    @Override
    public void saveBoolean(int monitorId, boolean value, long sampleTimeMs) {
        SampleBooleanEntity entity = new SampleBooleanEntity();
        entity.setMonitorId(monitorId);
        entity.setValue(value);
        entity.setSampleTime(toLocalDateTime(sampleTimeMs));
        boolMapper.insert(entity);
    }

    @Override
    public void saveException(int monitorId, String error, long sampleTimeMs) {
        SampleExceptionEntity entity = new SampleExceptionEntity();
        entity.setMonitorId(monitorId);
        entity.setError(error);
        entity.setSampleTime(toLocalDateTime(sampleTimeMs));
        exceptionMapper.insert(entity);
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(SYSTEM_ZONE).toLocalDateTime();
    }
}
