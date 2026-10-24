package com.systar.server.plugin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.systar.monitor.asset.AssetException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PluginSeedExecutorTest {

    @TempDir Path root;
    private JdbcTemplate jdbc;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:seedtest;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE t_code_dict (id INT PRIMARY KEY, catalog_id INT, name VARCHAR(64), caption VARCHAR(64), parent VARCHAR(64))");
        jdbc.execute("CREATE TABLE t_service (id INT PRIMARY KEY, name VARCHAR(64), caption VARCHAR(64), mode INT, driver_class VARCHAR(255), max_connections INT, type_name VARCHAR(64))");
        logs = new ListAppender<>();
        ((Logger) LoggerFactory.getLogger(PluginSeedExecutor.class)).addAppender(logs);
        logs.start();
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(PluginSeedExecutor.class)).detachAppender(logs);
        jdbc.execute("DROP ALL OBJECTS");
    }

    private PluginSeedExecutor executor() throws IOException {
        DriverPluginLoader loader = new DriverPluginLoader(root.toString());
        loader.afterPropertiesSet();
        // sqlInitMode=always → gate open (dev form)
        return new PluginSeedExecutor(jdbc, loader, "always", false);
    }

    private Path pluginSeedFile(String plugin, String fileName, String content) throws IOException {
        Path dir = Files.createDirectories(root.resolve(plugin + "/seed"));
        Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
        return dir.resolve(fileName);
    }

    @Test
    @DisplayName("dictionary + service seeds insert once, idempotent on re-run")
    void insertsAndIdempotent() throws IOException {
        pluginSeedFile("demo", "01-dictionary.csv", "id,catalog_id,name,caption,parent\n50001,8,DEMO,Demo,\n");
        pluginSeedFile("demo", "02-service.csv", "id,name,caption,mode,driver_class,max_connections,type_name\n50001,demo_svc,Demo服务,0,com.example.DemoService,5,\n");

        executor().executeAll();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_code_dict", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM t_service WHERE id=50001", String.class)).isEqualTo("demo_svc");

        executor().executeAll(); // re-run: nothing duplicated
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_service", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Existing PK rows produce one aggregated WARN per file, not per row")
    void aggregatedWarnOnCollisions() throws IOException {
        pluginSeedFile("demo", "01-dictionary.csv",
                "id,catalog_id,name,caption,parent\n50001,8,A,A,\n50002,8,B,B,\n");
        executor().executeAll();
        logs.list.clear();
        executor().executeAll();
        assertThat(logs.list).filteredOn(e -> e.getLevel().toString().equals("WARN")).hasSize(1);
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("2 row(s) already present"));
    }

    @Test
    @DisplayName("BOM header tolerated")
    void bomTolerated() throws IOException {
        pluginSeedFile("demo", "01-dictionary.csv", "﻿id,catalog_id,name,caption,parent\n50001,8,BOM,\n");
        executor().executeAll();
        assertThat(jdbc.queryForObject("SELECT name FROM t_code_dict WHERE id=50001", String.class)).isEqualTo("BOM");
    }

    @Test
    @DisplayName("Unknown logical name fails startup naming file and allowed names (spec §7)")
    void unknownLogicalName() throws IOException {
        pluginSeedFile("demo", "01-unknown.csv", "id\n1\n");
        assertThatThrownBy(() -> executor().executeAll())
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("unknown logical name")
                .hasMessageContaining("dictionary");
    }

    @Test
    @DisplayName("Illegal column name fails startup (deploy error, not silently dropped)")
    void illegalColumnRejected() throws IOException {
        // 'id' present so the header passes; second column carries illegal chars
        pluginSeedFile("demo", "01-dictionary.csv", "id,bad col;DROP TABLE t_service\n1,2\n");
        assertThatThrownBy(() -> executor().executeAll())
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("illegal column");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_code_dict", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Header lacking 'id' fails startup naming the file (spec §7)")
    void missingIdHeader() throws IOException {
        pluginSeedFile("demo", "01-dictionary.csv", "catalog_id,name\n8,Demo\n");
        assertThatThrownBy(() -> executor().executeAll())
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("lacks 'id'");
    }

    @Test
    @DisplayName("Mid-file SQL failure names plugin, file and row (spec §7)")
    void sqlErrorNamesRow() throws IOException {
        // 100-char name exceeds the test table's VARCHAR(64) → H2 rejects at insert
        String longName = "x".repeat(100);
        pluginSeedFile("demo", "01-dictionary.csv",
                "id,catalog_id,name,caption,parent\n50001,8,ok,\n50002,8," + longName + ",\n");
        assertThatThrownBy(() -> executor().executeAll())
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("demo")
                .hasMessageContaining("01-dictionary.csv")
                .hasMessageContaining("row 3");   // 物理文件行号：表头=1，50001=2，50002=3（getRecordNumber 首条数据行=1，+1 对齐文件行）
    }

    @Test
    @DisplayName("Gate closed (prod, init-data=false) executes nothing")
    void gateClosed() throws IOException {
        pluginSeedFile("demo", "01-dictionary.csv", "id,catalog_id,name,caption,parent\n50001,8,G,\n");
        DriverPluginLoader loader = new DriverPluginLoader(root.toString());
        loader.afterPropertiesSet();
        new PluginSeedExecutor(jdbc, loader, "embedded", false).executeAll();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_code_dict", Integer.class)).isZero();
    }
}
