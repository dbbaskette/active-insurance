package com.insurancemegacorp.sense.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Vehicle event record for output to the existing JDBC consumer.
 * This maintains compatibility with the Greenplum vehicle_events table schema.
 */
public record VehicleEvent(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("event_time") Instant eventTime,
    @JsonProperty("policy_id") String policyId,
    @JsonProperty("vehicle_id") String vehicleId,
    @JsonProperty("vin") String vin,
    @JsonProperty("driver_id") String driverId,

    // Event classification
    @JsonProperty("event_type") String eventType,
    @JsonProperty("severity") String severity,
    @JsonProperty("confidence") double confidence,

    // Location
    @JsonProperty("gps_latitude") Double gpsLatitude,
    @JsonProperty("gps_longitude") Double gpsLongitude,
    @JsonProperty("current_street") String currentStreet,

    // Metrics at time of event
    @JsonProperty("speed_mph") Double speedMph,
    @JsonProperty("speed_limit_mph") Double speedLimitMph,
    @JsonProperty("g_force") Double gForce,

    // Sensor data snapshot
    @JsonProperty("accelerometer_x") Double accelerometerX,
    @JsonProperty("accelerometer_y") Double accelerometerY,
    @JsonProperty("accelerometer_z") Double accelerometerZ,
    @JsonProperty("gyroscope_x") Double gyroscopeX,
    @JsonProperty("gyroscope_y") Double gyroscopeY,
    @JsonProperty("gyroscope_z") Double gyroscopeZ,

    // Additional context
    @JsonProperty("behavior_context") String behaviorContext,
    @JsonProperty("risk_score") Double riskScore
) {

    /**
     * Create a VehicleEvent from a TelemetryEvent and detected behavior.
     */
    public static VehicleEvent fromTelemetry(
            TelemetryEvent telemetry,
            MicroBehavior behavior,
            String severity,
            double confidence,
            double riskScore) {
        return new VehicleEvent(
            UUID.randomUUID().toString(),
            telemetry.eventTime(),
            telemetry.policyId(),
            telemetry.vehicleId(),
            telemetry.vin(),
            telemetry.driverId(),
            behavior.name(),
            severity,
            confidence,
            telemetry.gpsLatitude(),
            telemetry.gpsLongitude(),
            telemetry.currentStreet(),
            telemetry.speedMph(),
            telemetry.speedLimitMph(),
            telemetry.gForce(),
            telemetry.accelerometerX(),
            telemetry.accelerometerY(),
            telemetry.accelerometerZ(),
            telemetry.gyroscopeX(),
            telemetry.gyroscopeY(),
            telemetry.gyroscopeZ(),
            behavior.getDescription(),
            riskScore
        );
    }

    /**
     * Create a simple accident event (backward compatible with old processor).
     */
    public static VehicleEvent accident(TelemetryEvent telemetry) {
        return fromTelemetry(
            telemetry,
            MicroBehavior.POTENTIAL_ACCIDENT,
            "HIGH",
            0.95,
            0.9
        );
    }
}
