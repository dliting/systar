package com.systar.server.plugin;

import com.systar.monitor.asset.AssetException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Executes plugin seed CSVs (insert-if-absent by primary key).
 * <p>
 * Conventions (spec §3): file name {@code NN-<logical>.csv}; the logical name
 * maps through a fixed whitelist to a table; the header row lists column
 * names (must include {@code id}); an empty field inserts NULL. Rows whose id
 * already exists are kept (dev re-seeds every start; prod re-runs on every
 * {@code init-data=true} restart) — counted into one aggregated WARN per file,
 * per-row at DEBUG. An unknown logical name, an illegal column name, a header
 * lacking {@code id}, or a mid-file SQL error aborts startup naming the plugin,
 * file, and row (spec §7 — a malformed seed is a deploy error, never silently
 * dropped).
 * <p>
 * Execution gate: plugin seeds run exactly when the main seed mechanism runs —
 * dev via {@code spring.sql.init.mode=always}, prod via
 * {@code systar.database.init-data=true}.
 */
@Service
public class PluginSeedExecutor {

    private static final Logger log = LoggerFactory.getLogger(PluginSeedExecutor.class);

    /** Whitelist: seed logical name → target table. */
    static final Map<String, String> TABLE_BY_LOGICAL_NAME = Map.of(
            "dictionary", "t_code_dict",
            "service", "t_service",
            "asset", "t_asset",
            "asset_attribute", "t_asset_attribute",
            "probe", "t_probe",
            "control", "t_control");

    private static final Pattern LEGAL_COLUMN = Pattern.compile("[a-z][a-z0-9_]*");

    private final JdbcTemplate jdbc;
    private final DriverPluginLoader pluginLoader;
    private final String sqlInitMode;
    private final boolean initData;

    public PluginSeedExecutor(JdbcTemplate jdbc,
                              DriverPluginLoader pluginLoader,
                              @Value("${spring.sql.init.mode:embedded}") String sqlInitMode,
                              @Value("${systar.database.init-data:false}") boolean initData) {
        this.jdbc = jdbc;
        this.pluginLoader = pluginLoader;
        this.sqlInitMode = sqlInitMode;
        this.initData = initData;
    }

    public void executeAll() {
        if (!("always".equalsIgnoreCase(sqlInitMode) || initData)) {
            log.info("Plugin seed execution skipped: main seed mechanism inactive "
                    + "(spring.sql.init.mode={}, systar.database.init-data=false).", sqlInitMode);
            return;
        }
        for (DriverPlugin plugin : pluginLoader.plugins()) {
            for (Path csv : plugin.seedFiles()) {
                executeFile(plugin.name(), csv);
            }
        }
    }

    void executeFile(String pluginName, Path csv) {
        String fileName = csv.getFileName().toString();
        String logical = logicalName(fileName);
        String table = logical == null ? null : TABLE_BY_LOGICAL_NAME.get(logical);
        if (table == null) {
            throw new AssetException("Plugin '%s': seed file '%s' has unknown logical name '%s'; "
                    + "allowed: %s.", pluginName, fileName, logical, TABLE_BY_LOGICAL_NAME.keySet());
        }
        try (Reader fr = Files.newBufferedReader(csv, StandardCharsets.UTF_8);
             PushbackReader reader = new PushbackReader(fr, 1)) {
            int first = reader.read();  // spec §7: tolerate a leading UTF-8 BOM
            if (first != '﻿' && first != -1) reader.unread(first);
            CSVParser parser = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setHeader().setSkipHeaderRecord(true).setTrim(true).build()
                    .parse(reader);
            List<String> columns = new ArrayList<>(parser.getHeaderNames());
            if (!columns.contains("id")) {
                throw new AssetException("Plugin '%s': seed file '%s' header lacks 'id'.",
                        pluginName, fileName);
            }
            for (String column : columns) {
                if (!LEGAL_COLUMN.matcher(column).matches()) {
                    throw new AssetException("Plugin '%s': seed file '%s' has illegal column '%s'.",
                            pluginName, fileName, column);
                }
            }
            String insert = "INSERT INTO " + table + " (" + String.join(",", columns) + ") VALUES ("
                    + String.join(",", columns.stream().map(c -> "?").toList()) + ")";
            int inserted = 0, kept = 0;
            for (CSVRecord record : parser) {
                Object id = value(record, "id");
                if (id == null) {
                    throw new AssetException("Plugin '%s': seed file '%s': row %d lacks id.",
                            pluginName, fileName, record.getRecordNumber() + 1);
                }
                Long existing = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE id=?", Long.class, id);
                if (existing != null && existing > 0) {
                    kept++;
                    log.debug("Plugin '{}': seed row id={} in {} already present; kept.", pluginName, id, table);
                    continue;
                }
                Object[] values = columns.stream().map(c -> value(record, c)).toArray();
                try {
                    jdbc.update(insert, values);
                } catch (Exception e) {
                    // spec §7: FK/SQL failure names plugin, file AND row
                    throw new AssetException(e, "Plugin '%s': seed file '%s': row %d insert failed: %s",
                            pluginName, fileName, record.getRecordNumber() + 1, e.getMessage());
                }
                inserted++;
            }
            if (kept > 0) {
                log.warn("Plugin '{}': seed file '{}': {} row(s) already present (kept), {} inserted.",
                        pluginName, fileName, kept, inserted);
            } else {
                log.info("Plugin '{}': seed file '{}': {} row(s) inserted.", pluginName, fileName, inserted);
            }
        } catch (AssetException e) {
            throw e;
        } catch (Exception e) {
            throw new AssetException(e, "Plugin '%s': seed file '%s' failed: %s",
                    pluginName, fileName, e.getMessage());
        }
    }

    /** {@code NN-<logical>.csv} → logical name; null when the name doesn't match. */
    private static String logicalName(String fileName) {
        String base = fileName.substring(0, fileName.length() - 4); // strip .csv
        int dash = base.indexOf('-');
        if (dash <= 0) return null;
        String prefix = base.substring(0, dash);
        if (!prefix.chars().allMatch(Character::isDigit)) return null;
        return base.substring(dash + 1);
    }

    /** Empty CSV field → NULL; otherwise the raw string (JDBC converts numerics). */
    private static Object value(CSVRecord record, String column) {
        // isSet (not isMapped): a row shorter than the header has no value for the
        // missing trailing columns — treat them as NULL, same as an empty field
        if (!record.isSet(column)) return null;
        String v = record.get(column);
        return (v == null || v.isEmpty()) ? null : v;
    }
}
