package com.systar.ops.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

    private static final String CREATE_STATS_TABLE_SQL = """
        CREATE TABLE IF NOT EXISTS t_monitor_stats (
            id              BIGINT          NOT NULL AUTO_INCREMENT,
            monitor         INT             NOT NULL,
            bucket_start    DATETIME        NOT NULL,
            bucket_end      DATETIME        NOT NULL,
            granularity     TINYINT         NOT NULL,
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
        return jdbc;
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
