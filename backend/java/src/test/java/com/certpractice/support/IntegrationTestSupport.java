package com.certpractice.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that need a real, migrated PostgreSQL schema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    @DynamicPropertySource
    static void registerDatabase(final DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }
}
