package com.systar.common.database;

public interface DatabaseDialect {

    String getDatabaseType();

    String getSchemaLocation();

    String getDataLocation();

    String getUpsertAttributeSql();
}
