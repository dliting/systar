package com.systar.ops.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Database configuration for the statistics pipeline.
 * <p>
 * Two JdbcTemplate beans are defined with explicit qualifiers:
 * <ul>
 *   <li>{@code mainJdbcTemplate} (@Primary) — for the main CRUD database</li>
 *   <li>{@code statsJdbcTemplate} — for the append-only statistics database</li>
 * </ul>
 * Callers MUST use {@code @Qualifier} to specify which database they need.
 */
@Configuration
public class StatisticsDataConfig {

    private static final Logger log = LoggerFactory.getLogger(StatisticsDataConfig.class);

    private static final String CREATE_STATS_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS t_monitor_stats (
            id              BIGINT          NOT NULL AUTO_INCREMENT,
            monitor         INT             NOT NULL,
            bucket_start    DATETIME        NOT NULL,
            bucket_end      DATETIME        NOT NULL,
            granularity     TINYINT         NOT NULL,
            open_val        FLOAT           NULL,
            close_val       FLOAT           NULL,
            avg_val         FLOAT           NULL,
            max_val         FLOAT           NULL,
            min_val         FLOAT           NULL,
            sample_count    INT             NOT NULL DEFAULT 0,
            updated_at      DATETIME        NOT NULL,
            PRIMARY KEY (id),
            CONSTRAINT uk_monitor_gran_start UNIQUE (monitor, granularity, bucket_start),
            INDEX i_gran_start (granularity, bucket_start)
        )
        """;

    @Value("${systar.stats-datasource.url}")
    private String statsUrl;

    @Value("${systar.stats-datasource.username}")
    private String statsUsername;

    @Value("${systar.stats-datasource.password}")
    private String statsPassword;

    @Value("${systar.stats-datasource.driver-class-name}")
    private String statsDriverClassName;

    @Bean
    @Primary
    public JdbcTemplate mainJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcTemplate statsJdbcTemplate() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(statsUrl);
        ds.setUsername(statsUsername);
        ds.setPassword(statsPassword);
        ds.setDriverClassName(statsDriverClassName);
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute(CREATE_STATS_TABLE_SQL);
        ensureOpenCloseColumns(jdbc);
        return jdbc;
    }

    /**
     * B1 guarded ALTER: existing deployments keep their rows and gain the two
     * nullable columns; fresh databases already have them from the CREATE.
     * Probing with a zero-row SELECT (instead of INFORMATION_SCHEMA) avoids the
     * H2-uppercase vs MySQL-lowercase identifier metadata divergence.
     */
    static void ensureOpenCloseColumns(JdbcTemplate jdbc) {
        addColumnIfAbsent(jdbc, "open_val");
        addColumnIfAbsent(jdbc, "close_val");
    }

    private static void addColumnIfAbsent(JdbcTemplate jdbc, String column) {
        try {
            jdbc.query("SELECT " + column + " FROM t_monitor_stats WHERE 1 = 0", rs -> { });
        } catch (DataAccessException missing) {
            try {
                jdbc.execute("ALTER TABLE t_monitor_stats ADD COLUMN " + column + " FLOAT NULL");
            } catch (DataAccessException e) {
                log.warn("Cannot add {} to t_monitor_stats ({}); trend open/close degrade to NULL",
                        column, e.getMessage());
            }
        }
    }

    @Bean("statsCascadeExecutor")
    public Executor statsCascadeExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(1);
        exec.setQueueCapacity(10);
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        exec.setThreadNamePrefix("stats-cascade-");
        exec.initialize();
        return exec;
    }
}
