package com.insurancemegacorp.sense.ai;

/**
 * Classified driving intent from LLM analysis.
 * Distinguishes between defensive/evasive actions and aggressive/risky behavior.
 */
public enum DrivingIntent {

    /**
     * Defensive maneuver to avoid collision or hazard.
     * Examples: emergency braking for obstacle, swerving to avoid debris
     */
    EVASIVE("Evasive/Defensive", "Driver reacting to external hazard"),

    /**
     * Aggressive driving behavior indicating poor judgment.
     * Examples: tailgating stop, road rage braking, aggressive lane change
     */
    AGGRESSIVE("Aggressive", "Risky driving behavior"),

    /**
     * Normal driving pattern, neither defensive nor aggressive.
     * Examples: normal stop at light, routine lane change
     */
    NORMAL("Normal", "Standard driving behavior"),

    /**
     * Pattern suggests distraction or impairment.
     * Examples: erratic speed changes, drifting without correction
     */
    DISTRACTED("Distracted", "Attention or focus issue"),

    /**
     * Unable to determine intent with confidence.
     */
    UNKNOWN("Unknown", "Insufficient context for classification");

    private final String displayName;
    private final String description;

    DrivingIntent(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Whether this intent should reduce the risk score (driver was being careful).
     */
    public boolean isDefensive() {
        return this == EVASIVE;
    }

    /**
     * Whether this intent should increase the risk score.
     */
    public boolean isRisky() {
        return this == AGGRESSIVE || this == DISTRACTED;
    }
}
