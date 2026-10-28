package com.certpractice.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL 16 container per test JVM. Ryuk removes it when the JVM exits.
 */
public final class TestDatabase {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("cert_practice_test")
            .withUsername("cert_practice")
            .withPassword("test-password");

    static {
        POSTGRES.start();
    }

    private TestDatabase() {
    }

    public static PostgreSQLContainer<?> container() {
        return POSTGRES;
    }

    public static void register(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
