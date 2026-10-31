package com.certpractice.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class PracticeSelectionPropertiesTest {

    @Test
    void bindsOverridesAndKeepsDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.practice.selection.unseen-weight", "0.6")
                .withProperty("app.practice.selection.hard-weight", "0.1")
                .withProperty("app.practice.selection.max-candidates", "5000");

        PracticeSelectionProperties properties = Binder.get(environment)
                .bind("app.practice.selection", PracticeSelectionProperties.class)
                .get();

        assertThat(properties.getUnseenWeight()).isEqualTo(0.6);
        assertThat(properties.getHardWeight()).isEqualTo(0.1);
        assertThat(properties.getMaxCandidates()).isEqualTo(5000);
        assertThat(properties.getWrongWeight()).isEqualTo(0.3);
        assertThat(properties.difficultyWeight("EASY")).isEqualTo(0.3);
        assertThat(properties.difficultyWeight("MEDIUM")).isEqualTo(0.5);
        assertThat(properties.difficultyWeight("HARD")).isEqualTo(0.1);
        assertThat(properties.difficultyWeight("UNKNOWN")).isEqualTo(0.5);
    }

    @Test
    void rejectsInvalidCapacity() {
        PracticeSelectionProperties properties = new PracticeSelectionProperties();
        properties.setMaxCandidates(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-candidates");
    }
}
