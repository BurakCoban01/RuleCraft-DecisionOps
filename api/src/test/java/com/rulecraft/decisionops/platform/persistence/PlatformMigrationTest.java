package com.rulecraft.decisionops.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class PlatformMigrationTest {

    @Autowired
    JdbcClient jdbcClient;

    @Test
    void flywayCreatesFoundationSentinel() {
        var schemaVersion = jdbcClient.sql("select schema_version from platform_installation where singleton_key = 'primary'")
                .query(Integer.class)
                .single();
        assertThat(schemaVersion).isEqualTo(1);
    }
}
