package com.certpractice.community;

import java.util.List;
import java.util.regex.Pattern;

import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Component;

/**
 * Minimal content filtering for user-generated discussions. External links are
 * replaced and a small sensitive-word list is masked before persistence. This
 * is not a replacement for administrator moderation.
 */
@Component
public class ContentFilter {

    private static final Pattern LINK_PATTERN =
            Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final List<String> SENSITIVE_WORDS = List.of("广告", "代考", "加微信");
    private static final String LINK_PLACEHOLDER = "[链接已屏蔽]";
    private static final String MASK = "***";

    public String sanitize(final String text) {
        if (text == null) {
            return null;
        }
        String filtered = LINK_PATTERN.matcher(text).replaceAll(LINK_PLACEHOLDER);
        for (String word : SENSITIVE_WORDS) {
            filtered = filtered.replace(word, MASK);
        }
        return filtered.trim();
    }

    public String sanitizeRequired(final String text, final String fieldName) {
        String filtered = sanitize(text);
        Guard.require(filtered != null && !filtered.isBlank(),
                ErrorCode.VALIDATION_FAILED, fieldName + " is required");
        return filtered;
    }
}
