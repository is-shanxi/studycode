package com.certpractice.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.certpractice.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the index migration that backs search, practice history, dashboard
 * aggregates and selection candidate queries. The test asserts index presence
 * rather than planner choices because test datasets are intentionally small.
 */
class IndexTuningIntegrationTest extends IntegrationTestSupport {

    private static final List<String> EXPECTED_INDEXES = List.of(
            "ix_question_status_subject_updated",
            "ix_question_version_status_difficulty",
            "ix_tag_name_trgm",
            "ix_practice_session_user_mode_created",
            "ix_practice_session_started_at",
            "ix_question_type",
            "ix_session_item_final_result",
            "ix_user_answer_auto_match",
            "ix_self_assessment_result");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tunedIndexesExistAfterMigration() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);

        assertThat(indexes).containsAll(EXPECTED_INDEXES);
    }
}
