package com.systar.ops.test;

import com.systar.common.config.SystemConfigManager;
import com.systar.common.database.DatabaseDialect;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.systar")
@MapperScan({"com.systar.data.mapper", "com.systar.ops.**.mapper"})
@EnableCaching
public class OpsTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpsTestApplication.class, args);
    }

    @Bean
    SystemConfigManager systemConfigManager() {
        SystemConfigManager manager = new SystemConfigManager();
        manager.loadConfigs(java.util.Map.of(
                "ops.workorder.alarm_trigger_levels", "2,3,4",
                "ops.workorder.sla_hours_urgent", "4",
                "ops.workorder.sla_hours_high", "24",
                "ops.workorder.sla_hours_medium", "72",
                "ops.workorder.sla_hours_low", "168"));
        return manager;
    }

    @Bean
    DatabaseDialect databaseDialect() {
        return new DatabaseDialect() {
            @Override
            public String getDatabaseType() {
                return "h2";
            }

            @Override
            public String getSchemaLocation() {
                return "file:sql/h2/ddl/01-core.sql,file:sql/h2/ddl/02-ops.sql,file:sql/h2/ddl/03-system.sql";
            }

            @Override
            public String getDataLocation() {
                return "file:sql/h2/data/01-init.sql";
            }

            @Override
            public String getUpsertAttributeSql() {
                return "MERGE INTO t_device_attribute (device_id, attr_key, attr_value, attr_type, created_at, updated_at) "
                        + "KEY (device_id, attr_key) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())";
            }
        };
    }
}
