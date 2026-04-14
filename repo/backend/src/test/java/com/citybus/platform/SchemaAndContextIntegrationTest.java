package com.citybus.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SchemaAndContextIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithFlywayManagedSchema() {
        assertNotNull(jdbcTemplate);
    }

    @Test
    void routeStopsTableExistsInTestSchema() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where upper(table_name) = 'ROUTE_STOPS'",
                Integer.class
        );
        assertNotNull(count);
        assertTrue(count > 0);
    }
}
