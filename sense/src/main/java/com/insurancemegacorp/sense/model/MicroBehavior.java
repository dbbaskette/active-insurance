package com.insurancemegacorp.sense.model;

/**
 * Enumeration of detectable micro-behaviors from telemetry analysis.
 */
public enum MicroBehavior {

    /** Sudden deceleration exceeding threshold */
    HARSH_BRAKING("Harsh Braking", "Sudden deceleration detected"),

    /** Aggressive acceleration exceeding threshold */
    HARSH_ACCELERATION("Harsh Acceleration", "Aggressive acceleration detected"),

    /** High lateral g-force during turns */
    AGGRESSIVE_CORNERING("Aggressive Cornering", "High-speed or sharp cornering detected"),

    /** Exceeding posted speed limit */
    SPEEDING("Speeding", "Vehicle exceeding speed limit"),

    /** Lane drift patterns indicating distraction */
    DISTRACTED_DRIFTING("Distracted Drifting", "Erratic lane position suggesting distraction"),

    /** Unsafe following distance */
    TAILGATING("Tailgating", "Insufficient following distance detected"),

    /** Exemplary driving behavior */
    SMOOTH_DRIVING("Smooth Driving", "Consistent, safe driving pattern"),

    /** Inconsistent behavior patterns */
    ERRATIC_PATTERN("Erratic Pattern", "Inconsistent driving behavior detected"),

    /** Emergency maneuver to avoid collision */
    COLLISION_AVOIDANCE("Collision Avoidance", "Emergency evasive maneuver detected"),

    /** High g-force event indicating possible accident */
    POTENTIAL_ACCIDENT("Potential Accident", "High g-force event suggesting collision");

    private final String displayName;
    private final String description;

    MicroBehavior(String displayName, String description) {
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
     * Check if this behavior type is considered a safety concern.
     */
    public boolean isSafetyConcern() {
        return this != SMOOTH_DRIVING;
    }

    /**
     * Check if this behavior should trigger immediate coaching.
     */
    public boolean requiresImmediateCoaching() {
        return switch (this) {
            case HARSH_BRAKING, AGGRESSIVE_CORNERING, SPEEDING,
                 DISTRACTED_DRIFTING, TAILGATING, ERRATIC_PATTERN -> true;
            default -> false;
        };
    }

    /**
     * Check if this behavior should be recorded to Greenplum for ML.
     */
    public boolean shouldRecordToDatabase() {
        return switch (this) {
            case POTENTIAL_ACCIDENT, COLLISION_AVOIDANCE,
                 HARSH_BRAKING, AGGRESSIVE_CORNERING -> true;
            default -> false;
        };
    }
}
