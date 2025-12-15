package com.insurancemegacorp.sense.model;

/**
 * Risk level classification for driver behavior assessment.
 */
public enum RiskLevel {

    /** Score < 0.2 - Exemplary driving */
    LOW(0.0, 0.2, "Low Risk", "Driver demonstrating safe behavior"),

    /** Score 0.2 - 0.4 - Minor concerns */
    MODERATE(0.2, 0.4, "Moderate Risk", "Some areas for improvement identified"),

    /** Score 0.4 - 0.6 - Notable concerns */
    ELEVATED(0.4, 0.6, "Elevated Risk", "Multiple safety concerns detected"),

    /** Score 0.6 - 0.8 - Significant concerns */
    HIGH(0.6, 0.8, "High Risk", "Significant safety issues requiring attention"),

    /** Score > 0.8 - Critical safety issue */
    CRITICAL(0.8, 1.0, "Critical Risk", "Immediate intervention recommended");

    private final double minScore;
    private final double maxScore;
    private final String displayName;
    private final String description;

    RiskLevel(double minScore, double maxScore, String displayName, String description) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.displayName = displayName;
        this.description = description;
    }

    public double getMinScore() {
        return minScore;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Determine risk level from a numeric score (0.0 - 1.0).
     */
    public static RiskLevel fromScore(double score) {
        if (score < 0.2) return LOW;
        if (score < 0.4) return MODERATE;
        if (score < 0.6) return ELEVATED;
        if (score < 0.8) return HIGH;
        return CRITICAL;
    }

    /**
     * Check if this risk level should trigger coaching.
     */
    public boolean shouldTriggerCoaching() {
        return this.ordinal() >= MODERATE.ordinal();
    }

    /**
     * Check if this risk level requires immediate attention.
     */
    public boolean requiresImmediateAttention() {
        return this == HIGH || this == CRITICAL;
    }
}
