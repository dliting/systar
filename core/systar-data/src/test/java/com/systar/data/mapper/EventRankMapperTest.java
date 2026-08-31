package com.systar.data.mapper;

import com.systar.data.entity.EventRankEntity;
import com.systar.data.test.DataTestApplication;
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

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class EventRankMapperTest {

    @Autowired
    private EventRankMapper mapper;

    @Test
    void insertAndFindById() {
        EventRankEntity entity = new EventRankEntity();
        entity.setId(1);
        entity.setName("warning");

        int rows = mapper.insert(entity);
        assertThat(rows).isEqualTo(1);

        EventRankEntity found = mapper.selectById(1);
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("warning");
    }

    @Test
    void update() {
        EventRankEntity entity = new EventRankEntity();
        entity.setId(2);
        entity.setName("info");
        mapper.insert(entity);

        entity.setName("critical");
        mapper.updateById(entity);

        EventRankEntity updated = mapper.selectById(2);
        assertThat(updated.getName()).isEqualTo("critical");
    }

    @Test
    void delete() {
        EventRankEntity entity = new EventRankEntity();
        entity.setId(3);
        entity.setName("temp");
        mapper.insert(entity);

        mapper.deleteById(3);

        assertThat(mapper.selectById(3)).isNull();
    }

    @Test
    void selectAll() {
        EventRankEntity e1 = new EventRankEntity();
        e1.setId(10);
        e1.setName("notice");
        mapper.insert(e1);

        EventRankEntity e2 = new EventRankEntity();
        e2.setId(11);
        e2.setName("urgent");
        mapper.insert(e2);

        List<EventRankEntity> list = mapper.selectList(null);
        assertThat(list).hasSizeGreaterThanOrEqualTo(2);
    }
}
