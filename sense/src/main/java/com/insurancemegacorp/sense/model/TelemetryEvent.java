package com.insurancemegacorp.sense.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Telemetry event record containing 35 fields from vehicle sensors.
 * This schema matches the existing imc-telemetry-processor input format.
 */
public record TelemetryEvent(
    // Core identifiers
    @JsonProperty("policy_id") String policyId,
    @JsonProperty("vehicle_id") String vehicleId,
    @JsonProperty("vin") String vin,
    @JsonProperty("driver_id") String driverId,
    @JsonProperty("event_time") Instant eventTime,

    // Speed data
    @JsonProperty("speed_mph") Double speedMph,
    @JsonProperty("speed_limit_mph") Double speedLimitMph,
    @JsonProperty("current_street") String currentStreet,

    // Safety metrics
    @JsonProperty("g_force") Double gForce,

    // GPS data
    @JsonProperty("gps_latitude") Double gpsLatitude,
    @JsonProperty("gps_longitude") Double gpsLongitude,
    @JsonProperty("gps_altitude") Double gpsAltitude,
    @JsonProperty("gps_speed") Double gpsSpeed,
    @JsonProperty("gps_bearing") Double gpsBearing,
    @JsonProperty("gps_accuracy") Double gpsAccuracy,
    @JsonProperty("gps_satellite_count") Integer gpsSatelliteCount,
    @JsonProperty("gps_fix_time") Long gpsFixTime,

    // IMU - Accelerometer
    @JsonProperty("accelerometer_x") Double accelerometerX,
    @JsonProperty("accelerometer_y") Double accelerometerY,
    @JsonProperty("accelerometer_z") Double accelerometerZ,

    // IMU - Gyroscope
    @JsonProperty("gyroscope_x") Double gyroscopeX,
    @JsonProperty("gyroscope_y") Double gyroscopeY,
    @JsonProperty("gyroscope_z") Double gyroscopeZ,

    // Magnetometer
    @JsonProperty("magnetometer_x") Double magnetometerX,
    @JsonProperty("magnetometer_y") Double magnetometerY,
    @JsonProperty("magnetometer_z") Double magnetometerZ,
    @JsonProperty("magnetometer_heading") Double magnetometerHeading,

    // Environmental
    @JsonProperty("barometric_pressure") Double barometricPressure,

    // Device state
    @JsonProperty("device_battery_level") Double deviceBatteryLevel,
    @JsonProperty("device_signal_strength") Double deviceSignalStrength,
    @JsonProperty("device_orientation") String deviceOrientation,
    @JsonProperty("device_screen_on") Boolean deviceScreenOn,
    @JsonProperty("device_charging") Boolean deviceCharging
) {
    /**
     * Check if this event represents a potential accident based on g-force threshold.
     */
    public boolean isPotentialAccident(double threshold) {
        return gForce != null && gForce > threshold;
    }

    /**
     * Check if speeding (above speed limit by given tolerance).
     */
    public boolean isSpeeding(double toleranceMph) {
        if (speedMph == null || speedLimitMph == null) {
            return false;
        }
        return speedMph > (speedLimitMph + toleranceMph);
    }

    /**
     * Calculate speed excess over limit.
     */
    public double speedExcess() {
        if (speedMph == null || speedLimitMph == null) {
            return 0.0;
        }
        return Math.max(0.0, speedMph - speedLimitMph);
    }
}
