package com.systar.data.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ScheduledTaskEntityTest {

    @Test
    void fieldsRoundTrip() {
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        entity.setId(7);
        entity.setName("Nightly relay off");
        entity.setControlId(3001);
        entity.setCommand("OFF");
        entity.setCronExpression("0 0 22 * * ?");
        entity.setEnabled(true);
        entity.setDescription("Turn off relay every night at 22:00");

        assertThat(entity.getId()).isEqualTo(7);
        assertThat(entity.getName()).isEqualTo("Nightly relay off");
        assertThat(entity.getControlId()).isEqualTo(3001);
        assertThat(entity.getCommand()).isEqualTo("OFF");
        assertThat(entity.getCronExpression()).isEqualTo("0 0 22 * * ?");
        assertThat(entity.getEnabled()).isTrue();
        assertThat(entity.getDescription()).isEqualTo("Turn off relay every night at 22:00");
    }

    @Test
    void defaultsAreNull() {
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getEnabled()).isNull();
        assertThat(entity.getDescription()).isNull();
    }
}
