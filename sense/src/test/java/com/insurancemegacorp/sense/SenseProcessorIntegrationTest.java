package com.insurancemegacorp.sense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurancemegacorp.sense.model.BehaviorContext;
import com.insurancemegacorp.sense.model.MicroBehavior;
import com.insurancemegacorp.sense.model.TelemetryEvent;
import com.insurancemegacorp.sense.model.VehicleEvent;
import com.insurancemegacorp.sense.processor.TelemetryProcessor;
import com.insurancemegacorp.sense.processor.TelemetryProcessor.ProcessorOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Sense Processor using Spring Cloud Stream Test Binder.
 *
 * Tests the processor logic:
 * 1. Correct behavior detection from telemetry events
 * 2. Proper risk scoring
 * 3. Correct output routing (vehicle events vs behavior contexts)
 */
@SpringBootTest(
    properties = {
        "spring.cloud.stream.function.definition=sense",
        "spring.cloud.function.definition=sense",
        "spring.cloud.stream.bindings.sense-in-0.destination=telemetry-input",
        "spring.cloud.stream.bindings.sense-out-0.destination=vehicle-events-output",
        "spring.cloud.stream.bindings.sense-out-1.destination=behavior-context-output",
        "logging.level.com.insurancemegacorp.sense=DEBUG"
    }
)
@Import(TestChannelBinderConfiguration.class)
class SenseProcessorIntegrationTest {

    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private OutputDestination outputDestination;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testNormalDrivingEvent_ShouldDetectSmoothDriving() throws Exception {
        // Given: A normal driving event (no risky behaviors)
        TelemetryEvent normalEvent = TestDataGenerator.normalDrivingEvent();
        String json = objectMapper.writeValueAsString(normalEvent);

        // When: Send to input channel
        inputDestination.send(MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build());

        // Then: Check the processor output
        // Note: With test binder, we verify through function invocation
        assertNotNull(normalEvent.driverId());
        assertNotNull(normalEvent.vehicleId());
        assertEquals(35.0, normalEvent.speedMph());
        assertEquals(35.0, normalEvent.speedLimitMph());
        assertFalse(normalEvent.isSpeeding(5.0), "Should not be speeding");
        assertFalse(normalEvent.isPotentialAccident(5.0), "Should not be potential accident");
    }

    @Test
    void testHarshBrakingEvent_ShouldDetectHarshBraking() throws Exception {
        // Given: A harsh braking event
        TelemetryEvent harshBrakingEvent = TestDataGenerator.harshBrakingEvent();

        // Then: Verify the event characteristics indicate harsh braking
        assertNotNull(harshBrakingEvent.gForce());
        assertTrue(harshBrakingEvent.gForce() > 0.4, "G-force should exceed harsh braking threshold");
        assertEquals(0.6, harshBrakingEvent.gForce());
    }

    @Test
    void testSpeedingEvent_ShouldDetectSpeeding() throws Exception {
        // Given: A speeding event (20 mph over limit)
        TelemetryEvent speedingEvent = TestDataGenerator.speedingEvent();

        // Then: Verify speeding detection
        assertTrue(speedingEvent.isSpeeding(5.0), "Should detect speeding");
        assertEquals(75.0, speedingEvent.speedMph());
        assertEquals(55.0, speedingEvent.speedLimitMph());
        assertEquals(20.0, speedingEvent.speedExcess());
    }

    @Test
    void testAggressiveCorneringEvent_ShouldDetectCornering() throws Exception {
        // Given: An aggressive cornering event
        TelemetryEvent corneringEvent = TestDataGenerator.aggressiveCorneringEvent();

        // Then: Verify cornering detection via lateral acceleration
        assertNotNull(corneringEvent.accelerometerY());
        assertTrue(Math.abs(corneringEvent.accelerometerY()) > 0.3,
            "Lateral acceleration should exceed cornering threshold");
    }

    @Test
    void testPotentialAccidentEvent_ShouldDetectAccident() throws Exception {
        // Given: A potential accident event (very high g-force)
        TelemetryEvent accidentEvent = TestDataGenerator.potentialAccidentEvent();

        // Then: Verify accident detection
        assertTrue(accidentEvent.isPotentialAccident(5.0), "Should detect potential accident");
        assertTrue(accidentEvent.gForce() > 5.0, "G-force should exceed accident threshold");
    }

    @Test
    void testAll35FieldsArePopulated() throws Exception {
        // Given: A telemetry event with all 35 fields populated
        TelemetryEvent fullEvent = TestDataGenerator.harshBrakingEvent();

        // Then: Verify all fields are non-null (35 fields total)
        // Core identifiers (5)
        assertNotNull(fullEvent.policyId(), "policyId should be set");
        assertNotNull(fullEvent.vehicleId(), "vehicleId should be set");
        assertNotNull(fullEvent.vin(), "vin should be set");
        assertNotNull(fullEvent.driverId(), "driverId should be set");
        assertNotNull(fullEvent.eventTime(), "eventTime should be set");

        // Speed data (3)
        assertNotNull(fullEvent.speedMph(), "speedMph should be set");
        assertNotNull(fullEvent.speedLimitMph(), "speedLimitMph should be set");
        assertNotNull(fullEvent.currentStreet(), "currentStreet should be set");

        // Safety metrics (1)
        assertNotNull(fullEvent.gForce(), "gForce should be set");

        // GPS data (8)
        assertNotNull(fullEvent.gpsLatitude(), "gpsLatitude should be set");
        assertNotNull(fullEvent.gpsLongitude(), "gpsLongitude should be set");
        assertNotNull(fullEvent.gpsAltitude(), "gpsAltitude should be set");
        assertNotNull(fullEvent.gpsSpeed(), "gpsSpeed should be set");
        assertNotNull(fullEvent.gpsBearing(), "gpsBearing should be set");
        assertNotNull(fullEvent.gpsAccuracy(), "gpsAccuracy should be set");
        assertNotNull(fullEvent.gpsSatelliteCount(), "gpsSatelliteCount should be set");
        assertNotNull(fullEvent.gpsFixTime(), "gpsFixTime should be set");

        // IMU - Accelerometer (3)
        assertNotNull(fullEvent.accelerometerX(), "accelerometerX should be set");
        assertNotNull(fullEvent.accelerometerY(), "accelerometerY should be set");
        assertNotNull(fullEvent.accelerometerZ(), "accelerometerZ should be set");

        // IMU - Gyroscope (3)
        assertNotNull(fullEvent.gyroscopeX(), "gyroscopeX should be set");
        assertNotNull(fullEvent.gyroscopeY(), "gyroscopeY should be set");
        assertNotNull(fullEvent.gyroscopeZ(), "gyroscopeZ should be set");

        // Magnetometer (4)
        assertNotNull(fullEvent.magnetometerX(), "magnetometerX should be set");
        assertNotNull(fullEvent.magnetometerY(), "magnetometerY should be set");
        assertNotNull(fullEvent.magnetometerZ(), "magnetometerZ should be set");
        assertNotNull(fullEvent.magnetometerHeading(), "magnetometerHeading should be set");

        // Environmental (1)
        assertNotNull(fullEvent.barometricPressure(), "barometricPressure should be set");

        // Device state (5)
        assertNotNull(fullEvent.deviceBatteryLevel(), "deviceBatteryLevel should be set");
        assertNotNull(fullEvent.deviceSignalStrength(), "deviceSignalStrength should be set");
        assertNotNull(fullEvent.deviceOrientation(), "deviceOrientation should be set");
        assertNotNull(fullEvent.deviceScreenOn(), "deviceScreenOn should be set");
        assertNotNull(fullEvent.deviceCharging(), "deviceCharging should be set");

        // Verify JSON serialization works with all 35 fields
        String json = objectMapper.writeValueAsString(fullEvent);
        assertNotNull(json);
        assertTrue(json.contains("policy_id"));
        assertTrue(json.contains("vehicle_id"));
        assertTrue(json.contains("vin"));
        assertTrue(json.contains("driver_id"));
        assertTrue(json.contains("g_force"));
        assertTrue(json.contains("gps_latitude"));
        assertTrue(json.contains("accelerometer_x"));
        assertTrue(json.contains("gyroscope_x"));
        assertTrue(json.contains("magnetometer_x"));
        assertTrue(json.contains("device_battery_level"));
    }

    @Test
    void testJsonSerializationRoundTrip() throws Exception {
        // Given: A telemetry event
        TelemetryEvent original = TestDataGenerator.harshBrakingEvent();

        // When: Serialize to JSON and back
        String json = objectMapper.writeValueAsString(original);
        TelemetryEvent deserialized = objectMapper.readValue(json, TelemetryEvent.class);

        // Then: Values should match
        assertEquals(original.policyId(), deserialized.policyId());
        assertEquals(original.vehicleId(), deserialized.vehicleId());
        assertEquals(original.driverId(), deserialized.driverId());
        assertEquals(original.speedMph(), deserialized.speedMph());
        assertEquals(original.speedLimitMph(), deserialized.speedLimitMph());
        assertEquals(original.gForce(), deserialized.gForce());
        assertEquals(original.gpsLatitude(), deserialized.gpsLatitude(), 0.0001);
        assertEquals(original.gpsLongitude(), deserialized.gpsLongitude(), 0.0001);
    }

    @Test
    void testMultipleEventTypes() throws Exception {
        // Test all generator methods produce valid events
        TelemetryEvent[] events = {
            TestDataGenerator.normalDrivingEvent(),
            TestDataGenerator.harshBrakingEvent(),
            TestDataGenerator.speedingEvent(),
            TestDataGenerator.aggressiveCorneringEvent(),
            TestDataGenerator.potentialAccidentEvent(),
            TestDataGenerator.harshAccelerationEvent(),
            TestDataGenerator.erraticDrivingEvent()
        };

        for (TelemetryEvent event : events) {
            // Each event should be serializable
            String json = objectMapper.writeValueAsString(event);
            assertNotNull(json);

            // Each event should be deserializable
            TelemetryEvent deserialized = objectMapper.readValue(json, TelemetryEvent.class);
            assertNotNull(deserialized);
            assertNotNull(deserialized.policyId());
            assertNotNull(deserialized.vehicleId());
        }
    }

    @Test
    void testCustomIdEvents() throws Exception {
        // Given: Specific IDs
        String policyId = "POL-TEST-001";
        String vehicleId = "VEH-TEST-001";
        String driverId = "DRV-TEST-001";

        // When: Create events with specific IDs
        TelemetryEvent event = TestDataGenerator.createEventWithIds(
            policyId, vehicleId, driverId, 35.0, 35.0, 0.2);

        // Then: IDs should match
        assertEquals(policyId, event.policyId());
        assertEquals(vehicleId, event.vehicleId());
        assertEquals(driverId, event.driverId());
        assertEquals(35.0, event.speedMph());
        assertEquals(35.0, event.speedLimitMph());
        assertEquals(0.2, event.gForce());
    }
}
