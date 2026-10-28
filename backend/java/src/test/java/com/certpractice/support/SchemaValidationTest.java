package com.certpractice.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

class SchemaValidationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionRepository<?> sessionRepository;

    @Test
    void appliesMigrationsSeedsAndJpaValidation() {
        Integer users = jdbcTemplate.queryForObject(
                "select count(*) from app_user",
                Integer.class);
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'",
                Integer.class);

        assertThat(users).isGreaterThanOrEqualTo(2);
        assertThat(tables).isGreaterThanOrEqualTo(20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistsSessionsThroughTheSpringSessionJdbcTables() {
        SessionRepository<Session> repository = (SessionRepository<Session>) sessionRepository;
        Session session = repository.createSession();
        session.setAttribute("probe", "value");
        repository.save(session);

        Session loaded = repository.findById(session.getId());
        assertThat(loaded).isNotNull();
        String probe = loaded.getAttribute("probe");
        assertThat(probe).isEqualTo("value");

        repository.deleteById(session.getId());
    }
}
