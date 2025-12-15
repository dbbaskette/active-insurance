package com.insurancemegacorp.sense.processor;

import com.insurancemegacorp.sense.model.*;
import com.insurancemegacorp.sense.model.BehaviorContext.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

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

    // Metrics
    private final Counter eventsReceived;
    private final Counter eventsProcessed;
    private final Counter behaviorsDetected;
    private final Counter vehicleEventsEmitted;
    private final Counter behaviorContextsEmitted;
    private final Timer processingTimer;

    public TelemetryProcessor(MeterRegistry meterRegistry) {
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

        this.processingTimer = Timer.builder("sense_processing_duration_seconds")
                .description("Time to process each telemetry event")
                .register(meterRegistry);
    }

    /**
     * Main processor function with dual output.
     *
     * <p>Returns a tuple of (VehicleEvent, BehaviorContext) wrapped in a ProcessorOutput.
     * The stream configuration routes output-0 to vehicle_events and output-1 to behavior_context.
     */
    @Bean
    public Function<Message<TelemetryEvent>, ProcessorOutput> sense() {
        return message -> processingTimer.record(() -> {
            eventsReceived.increment();

            TelemetryEvent event = message.getPayload();
            long startTime = System.currentTimeMillis();

            log.debug("Processing telemetry event for driver: {}, vehicle: {}",
                    event.driverId(), event.vehicleId());

            // Detect behaviors
            List<DetectedBehavior> detectedBehaviors = detectBehaviors(event);
            behaviorsDetected.increment(detectedBehaviors.size());

            // Calculate risk score
            double riskScore = calculateRiskScore(detectedBehaviors);
            RiskLevel riskLevel = RiskLevel.fromScore(riskScore);

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
                    log.info("Emitting vehicle event: {} for driver: {}",
                            behavior.type(), event.driverId());
                    break; // One event per telemetry record
                }
            }

            // Build behavior context for Coach Agent
            long processingTimeMs = System.currentTimeMillis() - startTime;
            BehaviorContext behaviorContext = buildBehaviorContext(
                    event, detectedBehaviors, riskScore, riskLevel, processingTimeMs);
            behaviorContextsEmitted.increment();

            eventsProcessed.increment();

            return new ProcessorOutput(vehicleEvent, behaviorContext);
        });
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
     * Calculate risk score from detected behaviors.
     */
    private double calculateRiskScore(List<DetectedBehavior> behaviors) {
        if (behaviors.isEmpty()) {
            return 0.0;
        }

        double totalWeight = 0.0;
        double weightedScore = 0.0;

        for (DetectedBehavior behavior : behaviors) {
            double weight = getBehaviorWeight(behavior.type());
            double score = getSeverityScore(behavior.severity());

            totalWeight += weight;
            weightedScore += weight * score * behavior.confidence();
        }

        return totalWeight > 0 ? weightedScore / totalWeight : 0.0;
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
            double riskScore,
            RiskLevel riskLevel,
            long processingTimeMs) {

        // Build risk assessment
        List<RiskAssessment.RiskFactor> factors = behaviors.stream()
                .filter(b -> b.type() != MicroBehavior.SMOOTH_DRIVING)
                .map(b -> new RiskAssessment.RiskFactor(
                        b.type().name(),
                        getBehaviorWeight(b.type()),
                        getSeverityScore(b.severity())
                ))
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
