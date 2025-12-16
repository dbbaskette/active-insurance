package com.insurancemegacorp.sense;

import com.insurancemegacorp.sense.model.BehaviorContext;
import com.insurancemegacorp.sense.model.MicroBehavior;
import com.insurancemegacorp.sense.model.TelemetryEvent;
import com.insurancemegacorp.sense.model.VehicleEvent;
import com.insurancemegacorp.sense.processor.TelemetryProcessor;
import com.insurancemegacorp.sense.processor.TelemetryProcessor.ProcessorOutput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TelemetryProcessor behavior detection logic.
 * Uses Spring context to properly inject threshold values.
 */
@SpringBootTest(properties = {
    "sense.detection.accident.g-force-threshold=5.0",
    "sense.detection.harsh-braking.g-force-threshold=0.4",
    "sense.detection.speeding.tolerance-mph=5",
    "sense.detection.cornering.lateral-g-threshold=0.3"
})
class TelemetryProcessorTest {

    @Autowired
    private TelemetryProcessor telemetryProcessor;

    private ProcessorOutput process(Message<TelemetryEvent> message) {
        return telemetryProcessor.process(message);
    }

    @Test
    void testNormalDriving_ShouldDetectSmoothDriving() {
        // Given: Normal driving event (g-force 0.2, well below accident threshold of 5.0)
        TelemetryEvent event = TestDataGenerator.normalDrivingEvent();
        assertEquals(0.2, event.gForce(), "Test data should have g-force of 0.2");
        Message<TelemetryEvent> message = MessageBuilder.withPayload(event).build();

        // When: Process the event
        ProcessorOutput output = process(message);

        // Then: Should detect smooth driving
        assertNotNull(output);
        assertNotNull(output.behaviorContext());
        assertNull(output.vehicleEvent(), "Should not emit vehicle event for normal driving (g-force 0.2 < threshold 5.0)");

        BehaviorContext context = output.behaviorContext();
        assertTrue(context.behaviors().stream()
                .anyMatch(b -> b.type() == MicroBehavior.SMOOTH_DRIVING),
                "Should detect smooth driving behavior");
    }

    @Test
    void testHarshBraking_ShouldDetectAndEmitVehicleEvent() {
        // Given: Harsh braking event (g-force 0.6, above harsh braking threshold of 0.4)
        TelemetryEvent event = TestDataGenerator.harshBrakingEvent();
        assertEquals(0.6, event.gForce(), "Test data should have g-force of 0.6");
        Message<TelemetryEvent> message = MessageBuilder.withPayload(event).build();

        // When: Process the event
        ProcessorOutput output = process(message);

        // Then: Should detect harsh braking and emit vehicle event
        assertNotNull(output);
        assertNotNull(output.behaviorContext());
        assertNotNull(output.vehicleEvent(), "Should emit vehicle event for harsh braking");

        BehaviorContext context = output.behaviorContext();
        assertTrue(context.behaviors().stream()
                .anyMatch(b -> b.type() == MicroBehavior.HARSH_BRAKING),
                "Should detect harsh braking behavior");

        VehicleEvent vehicleEvent = output.vehicleEvent();
        assertEquals(MicroBehavior.HARSH_BRAKING.name(), vehicleEvent.eventType());
        assertEquals(event.vehicleId(), vehicleEvent.vehicleId());
        assertEquals(event.policyId(), vehicleEvent.policyId());
    }

    @Test
    void testSpeeding_ShouldDetectSpeedingBehavior() {
        // Given: Speeding event (20 mph over limit, g-force 0.1)
        TelemetryEvent event = TestDataGenerator.speedingEvent();
        assertEquals(75.0, event.speedMph(), "Test data should have speed 75 mph");
        assertEquals(55.0, event.speedLimitMph(), "Test data should have speed limit 55 mph");
        assertEquals(0.1, event.gForce(), "Test data should have g-force of 0.1");
        Message<TelemetryEvent> message = MessageBuilder.withPayload(event).build();

        // When: Process the event
        ProcessorOutput output = process(message);

        // Then: Should detect speeding in behavior context
        // Note: Speeding doesn't emit vehicle event by design - only POTENTIAL_ACCIDENT,
        // COLLISION_AVOIDANCE, HARSH_BRAKING, and AGGRESSIVE_CORNERING record to DB
        assertNotNull(output);
        assertNotNull(output.behaviorContext());
        assertNull(output.vehicleEvent(), "Speeding should not emit vehicle event (by design)");

        BehaviorContext context = output.behaviorContext();
        assertTrue(context.behaviors().stream()
                .anyMatch(b -> b.type() == MicroBehavior.SPEEDING),
                "Should detect speeding behavior");

        // But speeding should trigger coaching
        assertTrue(context.coachingTrigger().shouldTrigger(),
                "Speeding should trigger coaching");
    }

    @Test
    void testAggressiveCornering_ShouldDetectCorneringBehavior() {
        // Given: Aggressive cornering event
        TelemetryEvent event = TestDataGenerator.aggressiveCorneringEvent();
        assertTrue(Math.abs(event.accelerometerY()) > 0.3,
            "Test data should have lateral acceleration > 0.3");
        Message<TelemetryEvent> message = MessageBuilder.withPayload(event).build();

        // When: Process the event
        ProcessorOutput output = process(message);

        // Then: Should detect aggressive cornering
        assertNotNull(output);
        assertNotNull(output.behaviorContext());

        BehaviorContext context = output.behaviorContext();
        assertTrue(context.behaviors().stream()
                .anyMatch(b -> b.type() == MicroBehavior.AGGRESSIVE_CORNERING),
                "Should detect aggressive cornering behavior");
    }

    @Test
    void testPotentialAccident_ShouldEmitCriticalEvent() {
        // Given: Potential accident event (g-force 8.5, well above threshold of 5.0)
        TelemetryEvent event = TestDataGenerator.potentialAccidentEvent();
        assertEquals(8.5, event.gForce(), "Test data should have g-force of 8.5");
        Message<TelemetryEvent> message = MessageBuilder.withPayload(event).build();

        // When: Process the event
        ProcessorOutput output = process(message);

        // Then: Should detect potential accident and emit critical event
        assertNotNull(output);
        assertNotNull(output.behaviorContext());
        assertNotNull(output.vehicleEvent(), "Should emit vehicle event for potential accident");

        BehaviorContext context = output.behaviorContext();
        assertTrue(context.behaviors().stream()
                .anyMatch(b -> b.type() == MicroBehavior.POTENTIAL_ACCIDENT),
                "Should detect potential accident");

        VehicleEvent vehicleEvent = output.vehicleEvent();
        assertEquals(MicroBehavior.POTENTIAL_ACCIDENT.name(), vehicleEvent.eventType());
        assertEquals("CRITICAL", vehicleEvent.severity());

        // Should trigger immediate coaching
        assertTrue(context.coachingTrigger().shouldTrigger(),
                "Should trigger coaching for potential accident");
    }

    @Test
    void testRiskScoring_HighRiskEvent() {
        // Given: Potential accident (highest risk)
        TelemetryEvent event = TestDataGenerator.potentialAccidentEvent();
        Message<TelemetryEvent> message = MessageBuilder.withPayload(event).build();

        // When: Process the event
        ProcessorOutput output = process(message);

        // Then: Risk score should be high
        BehaviorContext context = output.behaviorContext();
        assertTrue(context.riskAssessment().score() > 0.5,
                "Risk score should be elevated for potential accident");
    }

    @Test
    void testRiskScoring_LowRiskEvent() {
        // Given: Normal driving (lowest risk)
        TelemetryEvent event = TestDataGenerator.normalDrivingEvent();
        Message<TelemetryEvent> message = MessageBuilder.withPayload(event).build();

        // When: Process the event
        ProcessorOutput output = process(message);

        // Then: Risk score should be low
        BehaviorContext context = output.behaviorContext();
        assertTrue(context.riskAssessment().score() < 0.3,
                "Risk score should be low for normal driving");
    }

    @Test
    void testBehaviorContextAlwaysEmitted() {
        // Test that BehaviorContext is always emitted, regardless of event type
        TelemetryEvent[] events = {
            TestDataGenerator.normalDrivingEvent(),
            TestDataGenerator.harshBrakingEvent(),
            TestDataGenerator.speedingEvent(),
            TestDataGenerator.potentialAccidentEvent()
        };

        for (TelemetryEvent event : events) {
            Message<TelemetryEvent> message = MessageBuilder.withPayload(event).build();
            ProcessorOutput output = process(message);

            assertNotNull(output.behaviorContext(),
                    "BehaviorContext should always be emitted");
            assertNotNull(output.behaviorContext().driverId(),
                    "Driver ID should be present");
            assertNotNull(output.behaviorContext().vehicleId(),
                    "Vehicle ID should be present");
            assertFalse(output.behaviorContext().behaviors().isEmpty(),
                    "Should have at least one behavior detected");
        }
    }

    @Test
    void testVehicleEventOnlyForSignificantBehaviors() {
        // Normal driving - no vehicle event
        TelemetryEvent normalEvent = TestDataGenerator.normalDrivingEvent();
        ProcessorOutput normalOutput = process(MessageBuilder.withPayload(normalEvent).build());
        assertNull(normalOutput.vehicleEvent(), "Normal driving should not emit vehicle event");

        // Harsh braking - should emit vehicle event (records to DB)
        TelemetryEvent brakingEvent = TestDataGenerator.harshBrakingEvent();
        ProcessorOutput brakingOutput = process(MessageBuilder.withPayload(brakingEvent).build());
        assertNotNull(brakingOutput.vehicleEvent(), "Harsh braking should emit vehicle event");

        // Speeding - no vehicle event by design (only goes to Coach, not Greenplum)
        TelemetryEvent speedingEvent = TestDataGenerator.speedingEvent();
        ProcessorOutput speedingOutput = process(MessageBuilder.withPayload(speedingEvent).build());
        assertNull(speedingOutput.vehicleEvent(), "Speeding should not emit vehicle event (by design)");

        // Potential accident - should emit vehicle event (records to DB)
        TelemetryEvent accidentEvent = TestDataGenerator.potentialAccidentEvent();
        ProcessorOutput accidentOutput = process(MessageBuilder.withPayload(accidentEvent).build());
        assertNotNull(accidentOutput.vehicleEvent(), "Potential accident should emit vehicle event");
    }

    @Test
    void testCoachingTriggerLogic() {
        // Potential accident should trigger immediate coaching
        TelemetryEvent accidentEvent = TestDataGenerator.potentialAccidentEvent();
        ProcessorOutput accidentOutput = process(MessageBuilder.withPayload(accidentEvent).build());
        assertTrue(accidentOutput.behaviorContext().coachingTrigger().shouldTrigger(),
                "Potential accident should trigger coaching");

        // Normal driving may or may not trigger coaching (depends on implementation)
        TelemetryEvent normalEvent = TestDataGenerator.normalDrivingEvent();
        ProcessorOutput normalOutput = process(MessageBuilder.withPayload(normalEvent).build());
        // Smooth driving doesn't require immediate coaching
        assertNotNull(normalOutput.behaviorContext().coachingTrigger());
    }
}
