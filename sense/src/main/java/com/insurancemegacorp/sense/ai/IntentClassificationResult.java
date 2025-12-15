package com.insurancemegacorp.sense.ai;

import com.insurancemegacorp.sense.model.MicroBehavior;
import java.time.Instant;
import java.util.Map;

/**
 * Result of LLM-powered intent classification for a driving behavior.
 */
public record IntentClassificationResult(
    /** The classified intent */
    DrivingIntent intent,

    /** Confidence score from 0.0 to 1.0 */
    double confidence,

    /** LLM-generated explanation for the classification */
    String explanation,

    /** The original behavior that was classified */
    MicroBehavior originalBehavior,

    /** Key factors that influenced the classification */
    Map<String, Object> contributingFactors,

    /** Whether this was classified by AI or fell back to rules */
    boolean aiClassified,

    /** Processing time in milliseconds */
    long processingTimeMs,

    /** Timestamp of classification */
    Instant classifiedAt
) {
    /**
     * Create a result from rule-based fallback (no AI used).
     */
    public static IntentClassificationResult fromRuleFallback(
            MicroBehavior behavior,
            DrivingIntent defaultIntent) {
        return new IntentClassificationResult(
            defaultIntent,
            0.5, // Medium confidence for rule-based
            "Classified by rule-based fallback (AI not invoked)",
            behavior,
            Map.of(),
            false,
            0,
            Instant.now()
        );
    }

    /**
     * Create a result when AI classification fails.
     */
    public static IntentClassificationResult fromError(
            MicroBehavior behavior,
            String errorMessage) {
        return new IntentClassificationResult(
            DrivingIntent.UNKNOWN,
            0.0,
            "Classification failed: " + errorMessage,
            behavior,
            Map.of("error", errorMessage),
            false,
            0,
            Instant.now()
        );
    }

    /**
     * Whether the classification is confident enough to use.
     */
    public boolean isHighConfidence() {
        return confidence >= 0.7;
    }

    /**
     * Whether this should adjust the risk score downward.
     */
    public boolean shouldReduceRisk() {
        return intent.isDefensive() && isHighConfidence();
    }

    /**
     * Whether this should adjust the risk score upward.
     */
    public boolean shouldIncreaseRisk() {
        return intent.isRisky() && isHighConfidence();
    }
}
