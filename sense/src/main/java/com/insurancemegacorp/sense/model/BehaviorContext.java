package com.insurancemegacorp.sense.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rich behavioral context output for downstream AI agents (Coach, Actuary).
 * This is the primary output format from the Sense component.
 */
public record BehaviorContext(
    @JsonProperty("context_id") String contextId,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("driver_id") String driverId,
    @JsonProperty("vehicle_id") String vehicleId,
    @JsonProperty("policy_id") String policyId,
    @JsonProperty("session_id") String sessionId,

    @JsonProperty("behaviors") List<DetectedBehavior> behaviors,
    @JsonProperty("trip_context") TripContext tripContext,
    @JsonProperty("risk_assessment") RiskAssessment riskAssessment,
    @JsonProperty("coaching_trigger") CoachingTrigger coachingTrigger,
    @JsonProperty("metadata") ProcessingMetadata metadata
) {

    /**
     * Create a new BehaviorContext with auto-generated ID and timestamp.
     */
    public static BehaviorContext create(
            String driverId,
            String vehicleId,
            String policyId,
            String sessionId,
            List<DetectedBehavior> behaviors,
            TripContext tripContext,
            RiskAssessment riskAssessment,
            CoachingTrigger coachingTrigger,
            ProcessingMetadata metadata) {
        return new BehaviorContext(
            UUID.randomUUID().toString(),
            Instant.now(),
            driverId,
            vehicleId,
            policyId,
            sessionId,
            behaviors,
            tripContext,
            riskAssessment,
            coachingTrigger,
            metadata
        );
    }

    /**
     * A single detected behavior with context.
     */
    public record DetectedBehavior(
        @JsonProperty("type") MicroBehavior type,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("severity") String severity,
        @JsonProperty("context") Map<String, Object> context,
        @JsonProperty("interpretation") String interpretation,
        @JsonProperty("interpretation_confidence") double interpretationConfidence
    ) {
        public static DetectedBehavior of(MicroBehavior type, double confidence, String severity) {
            return new DetectedBehavior(type, confidence, severity, Map.of(), null, 0.0);
        }

        public static DetectedBehavior withContext(
                MicroBehavior type,
                double confidence,
                String severity,
                Map<String, Object> context) {
            return new DetectedBehavior(type, confidence, severity, context, null, 0.0);
        }
    }

    /**
     * Trip-level context and statistics.
     */
    public record TripContext(
        @JsonProperty("trip_duration_minutes") int tripDurationMinutes,
        @JsonProperty("distance_miles") double distanceMiles,
        @JsonProperty("behavior_counts") Map<MicroBehavior, Integer> behaviorCounts
    ) {
        public static TripContext empty() {
            return new TripContext(0, 0.0, Map.of());
        }
    }

    /**
     * Risk assessment with scoring factors.
     */
    public record RiskAssessment(
        @JsonProperty("current_level") RiskLevel currentLevel,
        @JsonProperty("score") double score,
        @JsonProperty("trend") String trend,
        @JsonProperty("factors") List<RiskFactor> factors
    ) {
        public static RiskAssessment fromScore(double score) {
            return new RiskAssessment(
                RiskLevel.fromScore(score),
                score,
                "STABLE",
                List.of()
            );
        }

        public record RiskFactor(
            @JsonProperty("factor") String factor,
            @JsonProperty("weight") double weight,
            @JsonProperty("score") double score
        ) {}
    }

    /**
     * Coaching trigger information for the Coach Agent.
     */
    public record CoachingTrigger(
        @JsonProperty("should_trigger") boolean shouldTrigger,
        @JsonProperty("trigger_type") TriggerType triggerType,
        @JsonProperty("urgency") Urgency urgency,
        @JsonProperty("suggested_topic") String suggestedTopic,
        @JsonProperty("suggested_tone") String suggestedTone
    ) {
        public enum TriggerType {
            IMMEDIATE, END_OF_TRIP, MILESTONE, NONE
        }

        public enum Urgency {
            LOW, MEDIUM, HIGH, CRITICAL
        }

        public static CoachingTrigger none() {
            return new CoachingTrigger(false, TriggerType.NONE, Urgency.LOW, null, null);
        }

        public static CoachingTrigger immediate(String topic, Urgency urgency) {
            return new CoachingTrigger(true, TriggerType.IMMEDIATE, urgency, topic, "SUPPORTIVE");
        }
    }

    /**
     * Processing metadata for observability.
     */
    public record ProcessingMetadata(
        @JsonProperty("processing_time_ms") long processingTimeMs,
        @JsonProperty("window_events") int windowEvents,
        @JsonProperty("model_version") String modelVersion
    ) {
        public static ProcessingMetadata of(long processingTimeMs, int windowEvents) {
            return new ProcessingMetadata(processingTimeMs, windowEvents, "1.0.0");
        }
    }
}
