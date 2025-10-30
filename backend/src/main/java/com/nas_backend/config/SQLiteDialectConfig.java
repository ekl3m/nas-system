package com.nas_backend.config;

import org.hibernate.community.dialect.SQLiteDialect;
import org.springframework.context.annotation.Configuration;

// This file is necessary to register SQLite dialect in hibernate
@Configuration
public class SQLiteDialectConfig extends SQLiteDialect {
    public SQLiteDialectConfig() {
        super();
    }
}
