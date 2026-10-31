package com.certpractice.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuestionSelectionQuotaTest {

    @Test
    void splitsTenQuestionsByDefaultWeights() {
        assertThat(QuestionSelectionService.quotas(10, 0.5, 0.3, 0.2))
                .containsExactly(5, 3, 2);
    }

    @Test
    void assignsRemainderToLargestFraction() {
        assertThat(QuestionSelectionService.quotas(1, 0.5, 0.3, 0.2))
                .containsExactly(1, 0, 0);
        assertThat(QuestionSelectionService.quotas(2, 0.5, 0.3, 0.2))
                .containsExactly(1, 1, 0);
    }

    @Test
    void handlesASingleNonZeroBucket() {
        assertThat(QuestionSelectionService.quotas(7, 0, 1, 0))
                .containsExactly(0, 7, 0);
    }

    @Test
    void rejectsAllZeroWeights() {
        assertThatThrownBy(() -> QuestionSelectionService.quotas(10, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucket weights");
    }
}
