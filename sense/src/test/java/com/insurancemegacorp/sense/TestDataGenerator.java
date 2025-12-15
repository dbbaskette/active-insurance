package com.insurancemegacorp.sense;

import com.insurancemegacorp.sense.model.TelemetryEvent;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

/**
 * Generates test telemetry data with all 35 fields for integration testing.
 */
public class TestDataGenerator {

    private static final Random random = new Random();
    private static final String[] STREETS = {
        "Main Street", "Oak Avenue", "Highway 101", "Interstate 95", "Elm Drive"
    };

    /**
     * Create a normal driving telemetry event (no risky behaviors).
     */
    public static TelemetryEvent normalDrivingEvent() {
        return createEvent(
            35.0,   // speed_mph - normal city driving
            35.0,   // speed_limit_mph
            0.2,    // g_force - normal
            0.0,    // accelerometer_x - normal
            0.0,    // accelerometer_y - normal
            9.8,    // accelerometer_z - gravity
            0.0,    // gyroscope_x - stable
            0.0,    // gyroscope_y - stable
            0.0     // gyroscope_z - stable
        );
    }

    /**
     * Create a harsh braking event (high negative longitudinal g-force).
     */
    public static TelemetryEvent harshBrakingEvent() {
        return createEvent(
            45.0,   // speed_mph
            45.0,   // speed_limit_mph
            0.6,    // g_force - harsh braking threshold exceeded
            -6.0,   // accelerometer_x - strong deceleration
            0.0,    // accelerometer_y
            9.8,    // accelerometer_z
            0.0,    // gyroscope_x
            0.0,    // gyroscope_y
            0.0     // gyroscope_z
        );
    }

    /**
     * Create a speeding event.
     */
    public static TelemetryEvent speedingEvent() {
        return createEvent(
            75.0,   // speed_mph - well over limit
            55.0,   // speed_limit_mph
            0.1,    // g_force - normal
            0.0,    // accelerometer_x
            0.0,    // accelerometer_y
            9.8,    // accelerometer_z
            0.0,    // gyroscope_x
            0.0,    // gyroscope_y
            0.0     // gyroscope_z
        );
    }

    /**
     * Create an aggressive cornering event (high lateral g-force).
     */
    public static TelemetryEvent aggressiveCorneringEvent() {
        return createEvent(
            40.0,   // speed_mph
            45.0,   // speed_limit_mph
            0.5,    // g_force - elevated due to cornering
            0.0,    // accelerometer_x
            5.0,    // accelerometer_y - high lateral acceleration
            9.8,    // accelerometer_z
            0.0,    // gyroscope_x
            0.0,    // gyroscope_y
            25.0    // gyroscope_z - high yaw rate
        );
    }

    /**
     * Create a potential accident event (very high g-force).
     */
    public static TelemetryEvent potentialAccidentEvent() {
        return createEvent(
            55.0,   // speed_mph
            55.0,   // speed_limit_mph
            8.5,    // g_force - extremely high, indicates impact
            -15.0,  // accelerometer_x - severe deceleration
            8.0,    // accelerometer_y - lateral impact
            12.0,   // accelerometer_z - vertical displacement
            45.0,   // gyroscope_x - rotation
            30.0,   // gyroscope_y - pitch
            60.0    // gyroscope_z - spin
        );
    }

    /**
     * Create a harsh acceleration event.
     */
    public static TelemetryEvent harshAccelerationEvent() {
        return createEvent(
            25.0,   // speed_mph - accelerating
            45.0,   // speed_limit_mph
            0.45,   // g_force
            5.5,    // accelerometer_x - strong acceleration
            0.0,    // accelerometer_y
            9.8,    // accelerometer_z
            0.0,    // gyroscope_x
            0.0,    // gyroscope_y
            0.0     // gyroscope_z
        );
    }

    /**
     * Create an erratic/drifting driving event (inconsistent yaw).
     */
    public static TelemetryEvent erraticDrivingEvent() {
        return createEvent(
            50.0,   // speed_mph
            55.0,   // speed_limit_mph
            0.25,   // g_force
            0.5,    // accelerometer_x - slight variations
            1.5,    // accelerometer_y - lane drifting
            9.8,    // accelerometer_z
            2.0,    // gyroscope_x
            1.5,    // gyroscope_y
            18.0    // gyroscope_z - above drift threshold
        );
    }

    /**
     * Create a telemetry event with specified sensor values.
     */
    private static TelemetryEvent createEvent(
            double speedMph,
            double speedLimitMph,
            double gForce,
            double accelX,
            double accelY,
            double accelZ,
            double gyroX,
            double gyroY,
            double gyroZ
    ) {
        String policyId = "POL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String vehicleId = "VEH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String driverId = "DRV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String vin = generateVin();

        return new TelemetryEvent(
            policyId,
            vehicleId,
            vin,
            driverId,
            Instant.now(),
            speedMph,
            speedLimitMph,
            STREETS[random.nextInt(STREETS.length)],
            gForce,
            // GPS data - San Francisco area
            37.7749 + (random.nextDouble() - 0.5) * 0.1,
            -122.4194 + (random.nextDouble() - 0.5) * 0.1,
            50.0 + random.nextDouble() * 100,   // altitude
            speedMph * 0.44704,                  // convert mph to m/s for GPS speed
            random.nextDouble() * 360,           // bearing
            5.0 + random.nextDouble() * 10,      // accuracy in meters
            8 + random.nextInt(8),               // satellites
            System.currentTimeMillis(),          // fix time
            // Accelerometer
            accelX,
            accelY,
            accelZ,
            // Gyroscope
            gyroX,
            gyroY,
            gyroZ,
            // Magnetometer
            random.nextDouble() * 50 - 25,
            random.nextDouble() * 50 - 25,
            random.nextDouble() * 50 - 25,
            random.nextDouble() * 360,           // heading
            // Barometric pressure
            1013.25 + random.nextDouble() * 10,
            // Device state
            0.5 + random.nextDouble() * 0.5,     // battery 50-100%
            -60.0 + random.nextDouble() * 30,    // signal strength dBm
            "PORTRAIT",
            random.nextBoolean(),                // screen on
            random.nextBoolean()                 // charging
        );
    }

    /**
     * Generate a realistic VIN number.
     */
    private static String generateVin() {
        String chars = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
        StringBuilder vin = new StringBuilder(17);
        for (int i = 0; i < 17; i++) {
            vin.append(chars.charAt(random.nextInt(chars.length())));
        }
        return vin.toString();
    }

    /**
     * Create an event with specific policy/vehicle IDs (for tracking through pipeline).
     */
    public static TelemetryEvent createEventWithIds(
            String policyId,
            String vehicleId,
            String driverId,
            double speedMph,
            double speedLimitMph,
            double gForce
    ) {
        return new TelemetryEvent(
            policyId,
            vehicleId,
            generateVin(),
            driverId,
            Instant.now(),
            speedMph,
            speedLimitMph,
            STREETS[random.nextInt(STREETS.length)],
            gForce,
            37.7749,
            -122.4194,
            100.0,
            speedMph * 0.44704,
            180.0,
            10.0,
            12,
            System.currentTimeMillis(),
            0.0, 0.0, 9.8,
            0.0, 0.0, 0.0,
            10.0, 10.0, 45.0, 180.0,
            1013.25,
            0.85,
            -45.0,
            "PORTRAIT",
            false,
            true
        );
    }
}
