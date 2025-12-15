package com.insurancemegacorp.sense.processor;

import com.insurancemegacorp.sense.ai.DrivingIntent;
import com.insurancemegacorp.sense.ai.IntentClassificationResult;
import com.insurancemegacorp.sense.ai.IntentClassifier;
import com.insurancemegacorp.sense.dashboard.DashboardStats;
import com.insurancemegacorp.sense.dashboard.DashboardWebSocketHandler;
import com.insurancemegacorp.sense.model.*;
import com.insurancemegacorp.sense.model.BehaviorContext.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Main telemetry processor with dual output.
 *
 * <p>Input: TelemetryEvent from flattened_telemetry_exchange
 * <p>Output 0: VehicleEvent to vehicle_events exchange (for Greenplum ML)
 * <p>Output 1: BehaviorContext to behavior_context_exchange (for Coach Agent)
 */
@Configuration
public class TelemetryProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProcessor.class);

    // Detection thresholds
    @Value("${sense.detection.accident.g-force-threshold:5.0}")
    private double accidentGForceThreshold;

    @Value("${sense.detection.harsh-braking.g-force-threshold:0.4}")
    private double harshBrakingThreshold;

    @Value("${sense.detection.speeding.tolerance-mph:5}")
    private double speedingToleranceMph;

    @Value("${sense.detection.cornering.lateral-g-threshold:0.3}")
    private double corneringThreshold;

    // AI Intent Classifier
    private final IntentClassifier intentClassifier;

    // Dashboard stats
    private final DashboardStats dashboardStats;

    // Stream bridge for sending to output channels
    private final StreamBridge streamBridge;

    // Metrics
    private final Counter eventsReceived;
    private final Counter eventsProcessed;
    private final Counter behaviorsDetected;
    private final Counter vehicleEventsEmitted;
    private final Counter behaviorContextsEmitted;
    private final Counter intentsClassified;
    private final Timer processingTimer;

    public TelemetryProcessor(MeterRegistry meterRegistry, IntentClassifier intentClassifier,
                              DashboardStats dashboardStats, StreamBridge streamBridge) {
        this.intentClassifier = intentClassifier;
        this.dashboardStats = dashboardStats;
        this.streamBridge = streamBridge;
        this.eventsReceived = Counter.builder("sense_events_received_total")
                .description("Total telemetry events received")
                .register(meterRegistry);

        this.eventsProcessed = Counter.builder("sense_events_processed_total")
                .description("Total telemetry events successfully processed")
                .register(meterRegistry);

        this.behaviorsDetected = Counter.builder("sense_behaviors_detected_total")
                .description("Total behaviors detected")
                .register(meterRegistry);

        this.vehicleEventsEmitted = Counter.builder("sense_vehicle_events_emitted_total")
                .description("Vehicle events emitted to Greenplum pipeline")
                .register(meterRegistry);

        this.behaviorContextsEmitted = Counter.builder("sense_behavior_contexts_emitted_total")
                .description("Behavior contexts emitted to Coach Agent")
                .register(meterRegistry);

        this.intentsClassified = Counter.builder("sense_intents_classified_total")
                .description("Total behaviors classified by AI intent classifier")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("sense_processing_duration_seconds")
                .description("Time to process each telemetry event")
                .register(meterRegistry);
    }

    /**
     * Main processor consumer for telemetry events with dual output.
     *
     * <p>Consumes TelemetryEvent from telematics_exchange and sends:
     * <ul>
     *   <li>VehicleEvent to vehicle_events exchange (for Greenplum ML)</li>
     *   <li>BehaviorContext to behavior_context_exchange (for Coach Agent)</li>
     * </ul>
     */
    @Bean
    public Consumer<Message<TelemetryEvent>> sense() {
        return message -> {
            ProcessorOutput output = process(message);

            // Send VehicleEvent to vehicle_events exchange (if significant)
            if (output.vehicleEvent() != null) {
                Message<VehicleEvent> vehicleMessage = MessageBuilder
                        .withPayload(output.vehicleEvent())
                        .build();
                streamBridge.send("sense-out-0", vehicleMessage);
            }

            // Always send BehaviorContext to behavior_context_exchange
            Message<BehaviorContext> contextMessage = MessageBuilder
                    .withPayload(output.behaviorContext())
                    .build();
            streamBridge.send("sense-out-1", contextMessage);
        };
    }

    /**
     * Core processing logic - exposed for testing.
     * Processes a telemetry event and returns the output without sending to streams.
     */
    public ProcessorOutput process(Message<TelemetryEvent> message) {
        return processingTimer.record(() -> {
            eventsReceived.increment();
            dashboardStats.incrementEventsReceived();

            TelemetryEvent event = message.getPayload();
            long startTime = System.currentTimeMillis();

            log.info("Processing telemetry event for driver: {}, vehicle: {}",
                    event.driverId(), event.vehicleId());

            // Detect behaviors
            List<DetectedBehavior> detectedBehaviors = detectBehaviors(event);
            behaviorsDetected.increment(detectedBehaviors.size());
            dashboardStats.incrementBehaviorsDetected(detectedBehaviors.size());

            // Track individual behaviors for dashboard
            for (DetectedBehavior behavior : detectedBehaviors) {
                dashboardStats.recordBehavior(behavior.type());
            }

            // Classify intent for significant behaviors using AI
            List<IntentClassificationResult> intentResults = classifyIntents(event, detectedBehaviors);

            // Calculate risk score (adjusted by intent classification)
            double riskScore = calculateRiskScore(detectedBehaviors, intentResults);
            RiskLevel riskLevel = RiskLevel.fromScore(riskScore);

            // Update driver stats for dashboard
            MicroBehavior primaryBehavior = detectedBehaviors.isEmpty()
                    ? MicroBehavior.SMOOTH_DRIVING
                    : detectedBehaviors.get(0).type();
            dashboardStats.updateDriverStats(event.driverId(), primaryBehavior, riskScore);

            // Determine if this should go to Greenplum
            VehicleEvent vehicleEvent = null;
            for (DetectedBehavior behavior : detectedBehaviors) {
                if (behavior.type().shouldRecordToDatabase()) {
                    vehicleEvent = VehicleEvent.fromTelemetry(
                            event,
                            behavior.type(),
                            behavior.severity(),
                            behavior.confidence(),
                            riskScore
                    );
                    vehicleEventsEmitted.increment();
                    dashboardStats.incrementVehicleEventsEmitted();
                    log.info("Emitting vehicle event: {} for driver: {}",
                            behavior.type(), event.driverId());

                    // Add to recent events for dashboard (only significant events)
                    String intentStr = intentResults.isEmpty() ? null
                            : intentResults.get(0).intent().name();
                    dashboardStats.addRecentEvent(new DashboardStats.RecentEvent(
                            Instant.now(),
                            event.driverId(),
                            event.vehicleId(),
                            behavior.type().name(),
                            behavior.severity(),
                            riskScore,
                            intentStr
                    ));

                    break; // One event per telemetry record
                }
            }

            // Build behavior context for Coach Agent
            long processingTimeMs = System.currentTimeMillis() - startTime;
            BehaviorContext behaviorContext = buildBehaviorContext(
                    event, detectedBehaviors, intentResults, riskScore, riskLevel, processingTimeMs);
            behaviorContextsEmitted.increment();

            eventsProcessed.increment();
            dashboardStats.incrementEventsProcessed();

            return new ProcessorOutput(vehicleEvent, behaviorContext);
        });
    }

    /**
     * Classify intents for detected behaviors using AI when appropriate.
     * Only significant/ambiguous behaviors are sent to the LLM.
     */
    private List<IntentClassificationResult> classifyIntents(
            TelemetryEvent event,
            List<DetectedBehavior> behaviors) {

        List<IntentClassificationResult> results = new ArrayList<>();

        for (DetectedBehavior behavior : behaviors) {
            // Skip smooth driving - no classification needed
            if (behavior.type() == MicroBehavior.SMOOTH_DRIVING) {
                continue;
            }

            IntentClassificationResult result = intentClassifier.classify(event, behavior.type());
            results.add(result);

            if (result.aiClassified()) {
                intentsClassified.increment();
                dashboardStats.incrementIntentsClassified();
                log.info("AI classified {} as {} (confidence: {}) for driver: {}",
                        behavior.type(), result.intent(), String.format("%.2f", result.confidence()), event.driverId());
            }
        }

        return results;
    }

    /**
     * Detect micro-behaviors from telemetry event.
     */
    private List<DetectedBehavior> detectBehaviors(TelemetryEvent event) {
        List<DetectedBehavior> behaviors = new ArrayList<>();

        // Check for potential accident (high g-force)
        if (event.isPotentialAccident(accidentGForceThreshold)) {
            behaviors.add(DetectedBehavior.withContext(
                    MicroBehavior.POTENTIAL_ACCIDENT,
                    0.95,
                    "CRITICAL",
                    Map.of("g_force", event.gForce(), "threshold", accidentGForceThreshold)
            ));
        }
        // Check for harsh braking
        else if (event.gForce() != null && event.gForce() > harshBrakingThreshold) {
            behaviors.add(DetectedBehavior.withContext(
                    MicroBehavior.HARSH_BRAKING,
                    0.85,
                    "MODERATE",
                    Map.of("g_force", event.gForce(), "threshold", harshBrakingThreshold)
            ));
        }

        // Check for speeding
        if (event.isSpeeding(speedingToleranceMph)) {
            double excess = event.speedExcess();
            String severity = excess > 15 ? "HIGH" : excess > 10 ? "MODERATE" : "LOW";
            behaviors.add(DetectedBehavior.withContext(
                    MicroBehavior.SPEEDING,
                    0.90,
                    severity,
                    Map.of(
                            "speed_mph", event.speedMph(),
                            "limit_mph", event.speedLimitMph(),
                            "excess_mph", excess
                    )
            ));
        }

        // Check for aggressive cornering (using lateral acceleration from accelerometer_y)
        if (event.accelerometerY() != null && Math.abs(event.accelerometerY()) > corneringThreshold) {
            behaviors.add(DetectedBehavior.withContext(
                    MicroBehavior.AGGRESSIVE_CORNERING,
                    0.80,
                    "MODERATE",
                    Map.of("lateral_g", Math.abs(event.accelerometerY()))
            ));
        }

        // If no concerning behaviors, mark as smooth driving
        if (behaviors.isEmpty()) {
            behaviors.add(DetectedBehavior.of(MicroBehavior.SMOOTH_DRIVING, 0.75, "LOW"));
        }

        return behaviors;
    }

    /**
     * Calculate risk score from detected behaviors, adjusted by intent classification.
     *
     * <p>If AI classifies a behavior as EVASIVE (defensive), the risk contribution is reduced.
     * If classified as AGGRESSIVE or DISTRACTED, the risk contribution is increased.
     */
    private double calculateRiskScore(
            List<DetectedBehavior> behaviors,
            List<IntentClassificationResult> intentResults) {

        if (behaviors.isEmpty()) {
            return 0.0;
        }

        // Build a lookup map for intent results by behavior type
        Map<MicroBehavior, IntentClassificationResult> intentMap = new HashMap<>();
        for (IntentClassificationResult result : intentResults) {
            intentMap.put(result.originalBehavior(), result);
        }

        double totalWeight = 0.0;
        double weightedScore = 0.0;

        for (DetectedBehavior behavior : behaviors) {
            double weight = getBehaviorWeight(behavior.type());
            double score = getSeverityScore(behavior.severity());

            // Apply intent adjustment if available
            IntentClassificationResult intent = intentMap.get(behavior.type());
            double intentMultiplier = getIntentMultiplier(intent);

            totalWeight += weight;
            weightedScore += weight * score * behavior.confidence() * intentMultiplier;
        }

        double rawScore = totalWeight > 0 ? weightedScore / totalWeight : 0.0;

        // Clamp to valid range [0, 1]
        return Math.max(0.0, Math.min(1.0, rawScore));
    }

    /**
     * Get risk multiplier based on intent classification.
     * EVASIVE reduces risk (driver was defending), AGGRESSIVE/DISTRACTED increases it.
     */
    private double getIntentMultiplier(IntentClassificationResult intent) {
        if (intent == null) {
            return 1.0; // No adjustment
        }

        // Only apply adjustments for high-confidence classifications
        if (!intent.isHighConfidence()) {
            return 1.0;
        }

        return switch (intent.intent()) {
            case EVASIVE -> 0.5;     // Reduce risk - defensive driving
            case AGGRESSIVE -> 1.3;  // Increase risk - risky behavior
            case DISTRACTED -> 1.2;  // Increase risk - attention issue
            case NORMAL -> 1.0;      // No adjustment
            case UNKNOWN -> 1.0;     // No adjustment
        };
    }

    private double getBehaviorWeight(MicroBehavior behavior) {
        return switch (behavior) {
            case POTENTIAL_ACCIDENT -> 1.0;
            case HARSH_BRAKING, AGGRESSIVE_CORNERING -> 0.7;
            case SPEEDING -> 0.6;
            case DISTRACTED_DRIFTING, TAILGATING -> 0.5;
            case ERRATIC_PATTERN -> 0.4;
            case SMOOTH_DRIVING -> 0.1;
            default -> 0.3;
        };
    }

    private double getSeverityScore(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> 1.0;
            case "HIGH" -> 0.8;
            case "MODERATE" -> 0.5;
            case "LOW" -> 0.2;
            default -> 0.3;
        };
    }

    /**
     * Build the behavior context for the Coach Agent.
     */
    private BehaviorContext buildBehaviorContext(
            TelemetryEvent event,
            List<DetectedBehavior> behaviors,
            List<IntentClassificationResult> intentResults,
            double riskScore,
            RiskLevel riskLevel,
            long processingTimeMs) {

        // Build intent lookup map
        Map<MicroBehavior, IntentClassificationResult> intentMap = new HashMap<>();
        for (IntentClassificationResult result : intentResults) {
            intentMap.put(result.originalBehavior(), result);
        }

        // Build risk assessment with intent information
        List<RiskAssessment.RiskFactor> factors = behaviors.stream()
                .filter(b -> b.type() != MicroBehavior.SMOOTH_DRIVING)
                .map(b -> {
                    IntentClassificationResult intent = intentMap.get(b.type());
                    String factorName = b.type().name();
                    if (intent != null && intent.aiClassified()) {
                        factorName = factorName + " (" + intent.intent().name() + ")";
                    }
                    return new RiskAssessment.RiskFactor(
                            factorName,
                            getBehaviorWeight(b.type()),
                            getSeverityScore(b.severity()) * getIntentMultiplier(intent)
                    );
                })
                .toList();

        RiskAssessment riskAssessment = new RiskAssessment(
                riskLevel,
                riskScore,
                "STABLE", // TODO: Implement trend tracking
                factors
        );

        // Determine coaching trigger
        CoachingTrigger trigger = determineCoachingTrigger(behaviors, riskLevel);

        // Build trip context (simplified - TODO: implement session tracking)
        Map<MicroBehavior, Integer> behaviorCounts = new HashMap<>();
        for (DetectedBehavior b : behaviors) {
            behaviorCounts.merge(b.type(), 1, Integer::sum);
        }
        TripContext tripContext = new TripContext(0, 0.0, behaviorCounts);

        // Processing metadata
        ProcessingMetadata metadata = ProcessingMetadata.of(processingTimeMs, 1);

        return BehaviorContext.create(
                event.driverId(),
                event.vehicleId(),
                event.policyId(),
                UUID.randomUUID().toString(), // Session ID - TODO: implement session tracking
                behaviors,
                tripContext,
                riskAssessment,
                trigger,
                metadata
        );
    }

    /**
     * Determine if and how to trigger coaching.
     */
    private CoachingTrigger determineCoachingTrigger(
            List<DetectedBehavior> behaviors,
            RiskLevel riskLevel) {

        // Check for immediate triggers
        for (DetectedBehavior behavior : behaviors) {
            if (behavior.type().requiresImmediateCoaching()) {
                CoachingTrigger.Urgency urgency = switch (behavior.severity().toUpperCase()) {
                    case "CRITICAL" -> CoachingTrigger.Urgency.CRITICAL;
                    case "HIGH" -> CoachingTrigger.Urgency.HIGH;
                    case "MODERATE" -> CoachingTrigger.Urgency.MEDIUM;
                    default -> CoachingTrigger.Urgency.LOW;
                };

                String topic = switch (behavior.type()) {
                    case HARSH_BRAKING -> "DEFENSIVE_BRAKING";
                    case SPEEDING -> "SPEED_AWARENESS";
                    case AGGRESSIVE_CORNERING -> "SMOOTH_CORNERING";
                    case DISTRACTED_DRIFTING -> "ATTENTION_FOCUS";
                    case TAILGATING -> "SAFE_FOLLOWING";
                    default -> "GENERAL_SAFETY";
                };

                return CoachingTrigger.immediate(topic, urgency);
            }
        }

        // Check risk level triggers
        if (riskLevel.shouldTriggerCoaching()) {
            return new CoachingTrigger(
                    true,
                    CoachingTrigger.TriggerType.END_OF_TRIP,
                    riskLevel.requiresImmediateAttention()
                            ? CoachingTrigger.Urgency.HIGH
                            : CoachingTrigger.Urgency.MEDIUM,
                    "DRIVING_SUMMARY",
                    "SUPPORTIVE"
            );
        }

        return CoachingTrigger.none();
    }

    /**
     * Output wrapper for dual output streams.
     */
    public record ProcessorOutput(
            VehicleEvent vehicleEvent,
            BehaviorContext behaviorContext
    ) {}
}
