package com.insurancemegacorp.sense.ai;

import com.insurancemegacorp.sense.model.MicroBehavior;
import com.insurancemegacorp.sense.model.TelemetryEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * LLM-powered intent classifier for ambiguous driving behaviors.
 *
 * <p>This service is only invoked for high-severity or ambiguous events
 * where rule-based detection cannot determine the driver's intent.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Was harsh braking evasive (avoiding obstacle) or aggressive (tailgating)?</li>
 *   <li>Was the cornering maneuver an emergency swerve or reckless driving?</li>
 *   <li>Is the erratic pattern distraction or road conditions?</li>
 * </ul>
 */
@Service
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private final ChatClient chatClient;
    private final boolean enabled;
    private final double gForceThreshold;
    private final double speedExcessThreshold;
    private final double lateralGThreshold;

    // Metrics
    private final Counter classificationsRequested;
    private final Counter classificationsSucceeded;
    private final Counter classificationsFailed;
    private final Counter classificationsSkipped;
    private final Timer classificationTimer;

    private static final String CLASSIFICATION_PROMPT = """
        You are an expert driving behavior analyst for an insurance telematics system.
        Analyze the following vehicle telemetry data and classify the driver's INTENT.

        ## Telemetry Data
        - Event Type: %s
        - G-Force: %.2f g
        - Speed: %.1f mph (limit: %.1f mph)
        - Lateral Acceleration: %.2f g
        - Location: %s
        - Time: %s

        ## Context
        %s

        ## Classification Task
        Classify the driver's intent as one of:
        1. EVASIVE - Defensive maneuver to avoid collision or hazard
        2. AGGRESSIVE - Risky driving behavior, poor judgment
        3. NORMAL - Standard driving, neither defensive nor aggressive
        4. DISTRACTED - Attention or focus issue
        5. UNKNOWN - Cannot determine with available data

        ## Response Format
        Respond with ONLY a JSON object (no markdown, no explanation outside JSON):
        {
          "intent": "EVASIVE|AGGRESSIVE|NORMAL|DISTRACTED|UNKNOWN",
          "confidence": 0.0-1.0,
          "explanation": "Brief explanation of classification",
          "factors": ["factor1", "factor2"]
        }
        """;

    public IntentClassifier(
            ChatClient.Builder chatClientBuilder,
            MeterRegistry meterRegistry,
            @Value("${sense.ai.intent-classification.enabled:true}") boolean enabled,
            @Value("${sense.ai.intent-classification.g-force-threshold:1.5}") double gForceThreshold,
            @Value("${sense.ai.intent-classification.speed-excess-threshold:25}") double speedExcessThreshold,
            @Value("${sense.ai.intent-classification.lateral-g-threshold:0.6}") double lateralGThreshold) {

        this.chatClient = chatClientBuilder.build();
        this.enabled = enabled;
        this.gForceThreshold = gForceThreshold;
        this.speedExcessThreshold = speedExcessThreshold;
        this.lateralGThreshold = lateralGThreshold;

        // Initialize metrics
        this.classificationsRequested = Counter.builder("sense_ai_classifications_requested_total")
                .description("Total AI classification requests")
                .register(meterRegistry);

        this.classificationsSucceeded = Counter.builder("sense_ai_classifications_succeeded_total")
                .description("Successful AI classifications")
                .register(meterRegistry);

        this.classificationsFailed = Counter.builder("sense_ai_classifications_failed_total")
                .description("Failed AI classifications")
                .register(meterRegistry);

        this.classificationsSkipped = Counter.builder("sense_ai_classifications_skipped_total")
                .description("Skipped AI classifications (below threshold)")
                .register(meterRegistry);

        this.classificationTimer = Timer.builder("sense_ai_classification_duration_seconds")
                .description("Time to classify intent via AI")
                .register(meterRegistry);

        log.info("IntentClassifier initialized: enabled={}, gForceThreshold={}, speedExcessThreshold={}, lateralGThreshold={}",
                enabled, gForceThreshold, speedExcessThreshold, lateralGThreshold);
    }

    /**
     * Classify the intent behind a detected behavior.
     *
     * <p>Only invokes LLM for events that meet the ambiguity threshold.
     * Returns rule-based fallback for simple cases.
     *
     * @param event The telemetry event
     * @param behavior The detected behavior
     * @return Classification result with intent and confidence
     */
    public IntentClassificationResult classify(TelemetryEvent event, MicroBehavior behavior) {
        // Check if AI classification is enabled
        if (!enabled) {
            log.debug("AI classification disabled, using rule fallback");
            classificationsSkipped.increment();
            return IntentClassificationResult.fromRuleFallback(behavior, getDefaultIntent(behavior));
        }

        // Check if this event warrants AI classification
        if (!shouldClassifyWithAI(event, behavior)) {
            log.debug("Event below AI threshold, using rule fallback for {}", behavior);
            classificationsSkipped.increment();
            return IntentClassificationResult.fromRuleFallback(behavior, getDefaultIntent(behavior));
        }

        classificationsRequested.increment();

        return classificationTimer.record(() -> {
            long startTime = System.currentTimeMillis();
            try {
                String prompt = buildPrompt(event, behavior);

                log.debug("Sending intent classification request for behavior: {}", behavior);

                String response = chatClient.prompt()
                        .user(prompt)
                        .call()
                        .content();

                IntentClassificationResult result = parseResponse(response, behavior, startTime);

                classificationsSucceeded.increment();
                log.info("AI classified {} as {} with confidence {}",
                        behavior, result.intent(), String.format("%.2f", result.confidence()));

                return result;

            } catch (Exception e) {
                log.error("AI classification failed for {}: {}", behavior, e.getMessage(), e);
                classificationsFailed.increment();
                return IntentClassificationResult.fromError(behavior, e.getMessage());
            }
        });
    }

    /**
     * Determine if an event should trigger AI classification.
     * Only high-severity or ambiguous events warrant the latency/cost of LLM.
     */
    private boolean shouldClassifyWithAI(TelemetryEvent event, MicroBehavior behavior) {
        // Always classify potential accidents
        if (behavior == MicroBehavior.POTENTIAL_ACCIDENT ||
            behavior == MicroBehavior.COLLISION_AVOIDANCE) {
            return true;
        }

        // Classify harsh braking above threshold
        if (behavior == MicroBehavior.HARSH_BRAKING &&
            event.gForce() != null &&
            event.gForce() > gForceThreshold) {
            return true;
        }

        // Classify aggressive cornering (only high-severity)
        if (behavior == MicroBehavior.AGGRESSIVE_CORNERING &&
            event.accelerometerY() != null &&
            Math.abs(event.accelerometerY()) > lateralGThreshold) {
            return true;
        }

        // Classify significant speeding
        if (behavior == MicroBehavior.SPEEDING &&
            event.speedExcess() > speedExcessThreshold) {
            return true;
        }

        return false;
    }

    /**
     * Build the LLM prompt with telemetry context.
     */
    private String buildPrompt(TelemetryEvent event, MicroBehavior behavior) {
        String context = buildContextDescription(event, behavior);

        return String.format(CLASSIFICATION_PROMPT,
                behavior.getDisplayName(),
                event.gForce() != null ? event.gForce() : 0.0,
                event.speedMph() != null ? event.speedMph() : 0.0,
                event.speedLimitMph() != null ? event.speedLimitMph() : 0.0,
                event.accelerometerY() != null ? event.accelerometerY() : 0.0,
                event.currentStreet() != null ? event.currentStreet() : "Unknown",
                event.eventTime().toString(),
                context
        );
    }

    /**
     * Build additional context for the LLM based on sensor data.
     */
    private String buildContextDescription(TelemetryEvent event, MicroBehavior behavior) {
        StringBuilder context = new StringBuilder();

        // Speed context
        if (event.speedMph() != null && event.speedLimitMph() != null) {
            double excess = event.speedMph() - event.speedLimitMph();
            if (excess > 0) {
                context.append(String.format("- Vehicle was %.0f mph over speed limit\n", excess));
            } else {
                context.append("- Vehicle was at or below speed limit\n");
            }
        }

        // Gyroscope data (rotation)
        if (event.gyroscopeZ() != null && Math.abs(event.gyroscopeZ()) > 10) {
            context.append(String.format("- Significant yaw rotation detected: %.1f deg/s\n",
                    event.gyroscopeZ()));
        }

        // Device state
        if (Boolean.TRUE.equals(event.deviceScreenOn())) {
            context.append("- Driver's phone screen was ON during event\n");
        }

        // Battery/signal (might indicate device issues)
        if (event.deviceBatteryLevel() != null && event.deviceBatteryLevel() < 0.1) {
            context.append("- Device battery critically low (may affect data quality)\n");
        }

        return context.toString();
    }

    /**
     * Parse the LLM response into a classification result.
     */
    private IntentClassificationResult parseResponse(
            String response,
            MicroBehavior behavior,
            long startTime) {

        try {
            // Clean up response (remove markdown if present)
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\n?", "").replaceAll("```\\n?", "").trim();
            }

            // Parse JSON manually to avoid adding more dependencies
            DrivingIntent intent = parseIntent(json);
            double confidence = parseConfidence(json);
            String explanation = parseExplanation(json);
            Map<String, Object> factors = parseFactors(json);

            return new IntentClassificationResult(
                intent,
                confidence,
                explanation,
                behavior,
                factors,
                true,
                System.currentTimeMillis() - startTime,
                Instant.now()
            );

        } catch (Exception e) {
            log.warn("Failed to parse AI response: {}", e.getMessage());
            return IntentClassificationResult.fromError(behavior,
                    "Failed to parse response: " + e.getMessage());
        }
    }

    private DrivingIntent parseIntent(String json) {
        for (DrivingIntent intent : DrivingIntent.values()) {
            if (json.contains("\"" + intent.name() + "\"")) {
                return intent;
            }
        }
        return DrivingIntent.UNKNOWN;
    }

    private double parseConfidence(String json) {
        try {
            int idx = json.indexOf("\"confidence\"");
            if (idx >= 0) {
                int colonIdx = json.indexOf(":", idx);
                int commaIdx = json.indexOf(",", colonIdx);
                int braceIdx = json.indexOf("}", colonIdx);
                int endIdx = Math.min(
                    commaIdx > 0 ? commaIdx : Integer.MAX_VALUE,
                    braceIdx > 0 ? braceIdx : Integer.MAX_VALUE
                );
                String value = json.substring(colonIdx + 1, endIdx).trim();
                return Double.parseDouble(value);
            }
        } catch (Exception e) {
            log.debug("Failed to parse confidence: {}", e.getMessage());
        }
        return 0.5;
    }

    private String parseExplanation(String json) {
        try {
            int idx = json.indexOf("\"explanation\"");
            if (idx >= 0) {
                int startQuote = json.indexOf("\"", idx + 13);
                int endQuote = json.indexOf("\"", startQuote + 1);
                if (startQuote > 0 && endQuote > startQuote) {
                    return json.substring(startQuote + 1, endQuote);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse explanation: {}", e.getMessage());
        }
        return "No explanation provided";
    }

    private Map<String, Object> parseFactors(String json) {
        Map<String, Object> factors = new HashMap<>();
        try {
            int idx = json.indexOf("\"factors\"");
            if (idx >= 0) {
                int startBracket = json.indexOf("[", idx);
                int endBracket = json.indexOf("]", startBracket);
                if (startBracket > 0 && endBracket > startBracket) {
                    String factorsStr = json.substring(startBracket + 1, endBracket);
                    String[] parts = factorsStr.split(",");
                    for (int i = 0; i < parts.length; i++) {
                        String factor = parts[i].trim().replace("\"", "");
                        if (!factor.isEmpty()) {
                            factors.put("factor_" + (i + 1), factor);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse factors: {}", e.getMessage());
        }
        return factors;
    }

    /**
     * Get default intent for rule-based fallback.
     */
    private DrivingIntent getDefaultIntent(MicroBehavior behavior) {
        return switch (behavior) {
            case COLLISION_AVOIDANCE -> DrivingIntent.EVASIVE;
            case POTENTIAL_ACCIDENT -> DrivingIntent.UNKNOWN;
            case HARSH_BRAKING, AGGRESSIVE_CORNERING -> DrivingIntent.AGGRESSIVE;
            case SPEEDING, TAILGATING -> DrivingIntent.AGGRESSIVE;
            case DISTRACTED_DRIFTING, ERRATIC_PATTERN -> DrivingIntent.DISTRACTED;
            case SMOOTH_DRIVING -> DrivingIntent.NORMAL;
            default -> DrivingIntent.UNKNOWN;
        };
    }

    /**
     * Check if AI classification is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
