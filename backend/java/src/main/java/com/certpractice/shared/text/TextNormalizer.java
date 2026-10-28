package com.certpractice.shared.text;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic normalization rules shared by identity and fill-answer grading.
 */
public final class TextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    public static String normalizeUsername(final String value) {
        String normalized = Normalizer.normalize(Objects.requireNonNull(value, "value"), Normalizer.Form.NFKC);
        return asciiLower(normalized.trim());
    }

    public static String normalizeFillAnswer(final String value) {
        String normalized = Normalizer.normalize(Objects.requireNonNull(value, "value"), Normalizer.Form.NFKC);
        normalized = WHITESPACE.matcher(normalized.trim()).replaceAll(" ");
        return asciiLower(normalized);
    }

    private static String asciiLower(final String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            builder.append(character >= 'A' && character <= 'Z' ? (char) (character + 32) : character);
        }
        return builder.toString();
    }
}
