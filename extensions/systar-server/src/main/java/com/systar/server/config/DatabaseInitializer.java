package com.systar.server.config;

import com.systar.common.database.DatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * Seeds initial data on first deployment.
 * <p>
 * Controlled by {@code systar.database.init-data}. Defaults to {@code false}
 * to avoid overwriting runtime configuration on every startup.
 * Set to {@code true} for initial deployment or database reset.
 */
@Component("databaseInitializer")
@ConditionalOnProperty(name = "systar.database.init-data", havingValue = "true")
public class DatabaseInitializer implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;
    private final DatabaseDialect dialect;

    public DatabaseInitializer(DataSource dataSource, ResourceLoader resourceLoader,
                               DatabaseDialect dialect) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.dialect = dialect;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        LOG.info("Seeding database with {} dialect...", dialect.getDatabaseType());
        executeScript(dialect.getDataLocation());
        LOG.info("Database seed data complete.");
    }

    private void executeScript(String location) throws Exception {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            LOG.info("Seed script not found at {}, skipping.", location);
            return;
        }
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new EncodedResource(resource, StandardCharsets.UTF_8));
            LOG.info("Executed seed script: {}", location);
        }
    }
}
