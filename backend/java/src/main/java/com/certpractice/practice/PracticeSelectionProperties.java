package com.certpractice.practice;

import java.util.Map;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable question-selection weights. Defaults match the confirmed
 * 50/30/20 bucket mix and the 30/50/20 difficulty target; deployments can
 * override them without touching code.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.practice.selection")
public class PracticeSelectionProperties {

    private double unseenWeight = 0.5;
    private double wrongWeight = 0.3;
    private double normalWeight = 0.2;
    private double easyWeight = 0.3;
    private double mediumWeight = 0.5;
    private double hardWeight = 0.2;
    private int maxCandidates = 20_000;

    @PostConstruct
    public void validate() {
        requireNonNegative(unseenWeight, "unseen-weight");
        requireNonNegative(wrongWeight, "wrong-weight");
        requireNonNegative(normalWeight, "normal-weight");
        requireNonNegative(easyWeight, "easy-weight");
        requireNonNegative(mediumWeight, "medium-weight");
        requireNonNegative(hardWeight, "hard-weight");
        if (unseenWeight + wrongWeight + normalWeight <= 0) {
            throw new IllegalStateException(
                    "app.practice.selection bucket weights must sum to a positive value");
        }
        if (easyWeight + mediumWeight + hardWeight <= 0) {
            throw new IllegalStateException(
                    "app.practice.selection difficulty weights must sum to a positive value");
        }
        if (maxCandidates < 1) {
            throw new IllegalStateException(
                    "app.practice.selection.max-candidates must be greater than 0");
        }
    }

    public Map<String, Double> difficultyWeights() {
        return Map.of("EASY", easyWeight, "MEDIUM", mediumWeight, "HARD", hardWeight);
    }

    public double difficultyWeight(final String difficulty) {
        return switch (difficulty) {
            case "EASY" -> easyWeight;
            case "HARD" -> hardWeight;
            default -> mediumWeight;
        };
    }

    private static void requireNonNegative(final double value, final String name) {
        if (value < 0) {
            throw new IllegalStateException(
                    "app.practice.selection." + name + " must be greater than or equal to 0");
        }
    }
}
