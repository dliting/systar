package com.systar.server.config;

import com.systar.common.database.DatabaseDialect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "systar.database.type", havingValue = "mysql", matchIfMissing = true)
public class MySQLDialect implements DatabaseDialect {

    @Override
    public String getDatabaseType() { return "mysql"; }

    @Override
    public String getSchemaLocation() { return "file:sql/mysql/ddl/01-core.sql,file:sql/mysql/ddl/02-ops.sql,file:sql/mysql/ddl/03-system.sql"; }

    @Override
    public String getDataLocation() { return "file:sql/mysql/data/01-init.sql"; }

    @Override
    public String getUpsertAttributeSql() {
        return "INSERT INTO t_device_attribute (device_id, attr_key, attr_value, attr_type, created_at, updated_at) "
             + "VALUES (?, ?, ?, ?, NOW(), NOW()) "
             + "ON DUPLICATE KEY UPDATE attr_value = VALUES(attr_value), attr_type = VALUES(attr_type), updated_at = NOW()";
    }
}
