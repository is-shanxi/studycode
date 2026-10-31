package com.certpractice.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void normalizesUsernamesWithNfkcTrimAndAsciiLowercase() {
        assertThat(TextNormalizer.normalizeUsername("  Ａdmin  ")).isEqualTo("admin");
        assertThat(TextNormalizer.normalizeUsername("张三")).isEqualTo("张三");
        assertThat(TextNormalizer.normalizeUsername("ÄDMIN")).isEqualTo("Ädmin");
    }

    @Test
    void normalizesFillAnswersWithWhitespaceCollapse() {
        assertThat(TextNormalizer.normalizeFillAnswer("  ＳＹＮ   同步  ")).isEqualTo("syn 同步");
        assertThat(TextNormalizer.normalizeFillAnswer("A\t B\nC")).isEqualTo("a b c");
    }
}
