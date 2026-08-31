package com.systar.server.config;

import com.systar.common.database.DatabaseDialect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "systar.database.type", havingValue = "h2")
public class H2Dialect implements DatabaseDialect {

    @Override
    public String getDatabaseType() { return "h2"; }

    @Override
    public String getSchemaLocation() { return "file:sql/h2/ddl/01-core.sql,file:sql/h2/ddl/02-ops.sql,file:sql/h2/ddl/03-system.sql"; }

    @Override
    public String getDataLocation() { return "file:sql/h2/data/01-init.sql"; }

    @Override
    public String getUpsertAttributeSql() {
        return "MERGE INTO t_device_attribute (device_id, attr_key, attr_value, attr_type, created_at, updated_at) "
             + "KEY (device_id, attr_key) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())";
    }
}
