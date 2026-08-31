package com.systar.data.repository;

import com.systar.data.entity.ScheduledTaskEntity;
import com.systar.data.mapper.ScheduledTaskMapper;
import com.systar.data.test.DataTestApplication;
import com.systar.monitor.control.ScheduledTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ScheduledTaskRepositoryImplTest {

    @Autowired
    private ScheduledTaskRepositoryImpl repository;

    @Autowired
    private ScheduledTaskMapper mapper;

    private ScheduledTask makeTask(String name, int controlId, String command, String cron) {
        ScheduledTask task = new ScheduledTask();
        task.setName(name);
        task.setControlId(controlId);
        task.setCommand(command);
        task.setCronExpression(cron);
        task.setEnabled(true);
        task.setDescription("test task");
        return task;
    }

    @Test
    void saveAndFindAll() {
        ScheduledTask task = makeTask("Relay daily on", 4001, "ON", "0 0 8 * * ?");
        repository.save(task);

        assertThat(task.getId()).isGreaterThan(0);

        List<ScheduledTask> all = repository.findAll();
        assertThat(all).anyMatch(t ->
                t.getName().equals("Relay daily on")
                && t.getControlId() == 4001
                && t.getCommand().equals("ON"));
    }

    @Test
    void update() {
        ScheduledTask task = makeTask("Relay update test", 4002, "ON", "0 0 9 * * ?");
        repository.save(task);

        task.setCommand("OFF");
        task.setEnabled(false);
        repository.update(task);

        ScheduledTaskEntity updated = mapper.selectById(task.getId());
        assertThat(updated.getCommand()).isEqualTo("OFF");
        assertThat(updated.getEnabled()).isFalse();
    }

    @Test
    void deleteById() {
        ScheduledTask task = makeTask("Relay delete test", 4003, "PING", "0 0 0 * * ?");
        repository.save(task);
        int id = task.getId();

        repository.deleteById(id);
        assertThat(mapper.selectById(id)).isNull();
    }

    @Test
    void updateRejectsNonPositiveId() {
        ScheduledTask task = makeTask("bad update", 4004, "ON", "0 0 0 * * ?");
        task.setId(0);
        assertThatThrownBy(() -> repository.update(task))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void deleteByIdRejectsNonPositiveId() {
        assertThatThrownBy(() -> repository.deleteById(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void findAllReturnsEmptyWhenNoData() {
        List<ScheduledTask> all = repository.findAll();
        assertThat(all).isNotNull();
    }

    @Test
    void findByIdReturnsTask() {
        ScheduledTask task = makeTask("FindById test", 4010, "ON", "0 0 8 * * ?");
        repository.save(task);
        int id = task.getId();

        ScheduledTask found = repository.findById(id);
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("FindById test");
        assertThat(found.getControlId()).isEqualTo(4010);
        assertThat(found.getCommand()).isEqualTo("ON");
        assertThat(found.isEnabled()).isTrue();
    }

    @Test
    void findByIdReturnsNullForMissing() {
        ScheduledTask found = repository.findById(999999);
        assertThat(found).isNull();
    }
}
