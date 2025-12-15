# Migration Notes: imc-telemetry-processor → Sense Component

## Context

The `imc-vehicle-events` directory contains multiple SCDF components:
- **imc-telemetry-processor** - Simple g-force threshold filter (THIS IS WHAT SENSE REPLACES)
- **imc-jdbc-consumer** - Writes to Greenplum (KEEP AS-IS)
- **imc-hdfs-sink** - Long-term storage (KEEP AS-IS)

Sense **only replaces `imc-telemetry-processor`** - the other components continue to operate unchanged.

## Reusable Components from imc-telemetry-processor

The existing `imc-telemetry-processor` provides patterns to leverage:

### Directly Reusable

| Component | Source Location | Reuse Strategy |
|-----------|-----------------|----------------|
| **Telemetry Schema** | 35-field JSON structure | Copy as `TelemetryEvent` record |
| **RabbitMQ Config** | Stream bindings | Adapt input binding pattern |
| **Metrics Pattern** | Micrometer setup | Extend with new metrics |
| **Maven Structure** | Multi-module POM | Use as template |
| **manifest.yml** | CF deployment | Adapt for new service |

### Partially Reusable

| Component | What to Keep | What to Change |
|-----------|--------------|----------------|
| **Message Deserializer** | JSON parsing | Add validation layer |
| **Health Checks** | Actuator pattern | Add custom checks |
| **Error Handling** | Retry logic | Adapt for windowing |

### Keep As-Is (Downstream)

| Component | Current Function | Notes |
|-----------|------------------|-------|
| **imc-jdbc-consumer** | Writes accidents to Greenplum `vehicle_events` table | Keep running - Sense will output to same exchange for ML pipeline |
| **HDFS sink** | Long-term telemetry storage | Keep for analytics/compliance |

### Replace

| Component | Reason | New Approach |
|-----------|--------|--------------|
| **imc-telemetry-processor** | Too simplistic (g_force > 5.0 only) | Sense replaces with multi-behavior detection, dual output |

---

## Key Schema Reference (from imc-vehicle-events)

```java
// Existing 35-field telemetry schema to preserve compatibility
public record TelemetryEvent(
    // === CORE IDENTIFIERS ===
    String policy_id,        // Insurance policy ID
    String vehicle_id,       // Vehicle identifier
    String vin,              // Vehicle Identification Number
    String driver_id,        // Driver identifier
    String event_time,       // ISO 8601 timestamp

    // === SPEED DATA ===
    Double speed_mph,        // Current speed in MPH
    Double speed_limit_mph,  // Posted speed limit
    String current_street,   // Street name

    // === PRIMARY SAFETY METRIC ===
    Double g_force,          // Combined g-force magnitude

    // === GPS POSITION ===
    Double gps_latitude,
    Double gps_longitude,
    Double gps_altitude,
    Double gps_speed,        // GPS-derived speed (m/s)
    Double gps_bearing,      // Direction (degrees)
    Double gps_accuracy,     // Horizontal accuracy (meters)
    Integer gps_satellite_count,
    Long gps_fix_time,

    // === ACCELEROMETER (3-axis) ===
    Double accelerometer_x,  // Longitudinal (braking/accel)
    Double accelerometer_y,  // Lateral (cornering)
    Double accelerometer_z,  // Vertical (bumps)

    // === GYROSCOPE (3-axis) ===
    Double gyroscope_x,      // Pitch rate
    Double gyroscope_y,      // Roll rate
    Double gyroscope_z,      // Yaw rate (turns)

    // === MAGNETOMETER ===
    Double magnetometer_x,
    Double magnetometer_y,
    Double magnetometer_z,
    Double magnetometer_heading,

    // === ENVIRONMENTAL ===
    Double barometric_pressure,

    // === DEVICE STATE ===
    Double device_battery_level,
    Double device_signal_strength,
    String device_orientation,
    Boolean device_screen_on,
    Boolean device_charging
) {}
```

---

## Binding Configuration Migration

### Existing (imc-telemetry-processor)
```yaml
spring:
  cloud:
    stream:
      bindings:
        vehicleEventsOut-in-0:
          destination: flattened_telemetry_exchange
          group: telemetry-processor
        vehicleEventsOut-out-0:
          destination: vehicle_events
```

### New (sense-processor) - Dual Output
```yaml
spring:
  cloud:
    stream:
      bindings:
        # Input: same as existing processor
        sense-in-0:
          destination: flattened_telemetry_exchange
          group: sense-processor
          consumer:
            concurrency: 3

        # Output 1: Vehicle events (accidents) → existing JDBC consumer → Greenplum ML
        sense-out-0:
          destination: vehicle_events
          producer:
            partition-count: 3

        # Output 2: Behavior context → Coach Agent (new AI pipeline)
        sense-out-1:
          destination: behavior_context_exchange
          producer:
            partition-count: 3
```

---

## Metrics Migration

### Existing Metrics (keep pattern)
```
telemetry_messages_total
telemetry_vehicle_events_total
telemetry_invalid_messages_total
```

### New Metrics (extend)
```
sense_events_received_total
sense_events_processed_total
sense_behaviors_detected_total{type="HARSH_BRAKING"}
sense_behaviors_detected_total{type="SPEEDING"}
sense_risk_assessments_total{level="HIGH"}
sense_coaching_triggers_total{type="IMMEDIATE"}
sense_processing_duration_seconds
sense_active_sessions
```

---

## Development Environment

### Prerequisites
Same as existing:
- Java 21
- Maven 3.9+
- Docker
- RabbitMQ (local or CF service)
- Spring Cloud Dataflow (local or CF)

### Local RabbitMQ
```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```

### Connect to Existing Telemetry Generator
The sense component will consume from the same exchange as the existing processors:
- Exchange: `flattened_telemetry_exchange`
- Type: `fanout`
- No changes needed to upstream telemetry generator

---

## Architecture: Sense Replaces imc-telemetry-processor

Sense replaces the simple threshold filter but maintains the Greenplum output for ML:

```
┌─────────────┐
│  Telemetry  │
│  Generator  │──▶ flattened_telemetry_exchange (fanout)
└─────────────┘                     │
                                    │
                                    ▼
                    ┌───────────────────────────────────────────────────────────────┐
                    │                    SENSE PROCESSOR                            │
                    │              (replaces imc-telemetry-proc)                    │
                    │                                                               │
                    │   ┌─────────────────────────────────────────────────────┐    │
                    │   │  - Micro-behavior detection (10 types)              │    │
                    │   │  - Temporal windowing & pattern recognition         │    │
                    │   │  - Risk scoring & context generation                │    │
                    │   │  - Still detects accidents (g_force > threshold)    │    │
                    │   └─────────────────────────────────────────────────────┘    │
                    │                           │                                   │
                    │           ┌───────────────┴───────────────┐                  │
                    │           │                               │                  │
                    │           ▼                               ▼                  │
                    │   ┌───────────────┐               ┌───────────────┐          │
                    │   │ OUTPUT 1:     │               │ OUTPUT 2:     │          │
                    │   │ vehicle_events│               │ behavior_     │          │
                    │   │ exchange      │               │ context_exch  │          │
                    │   └───────┬───────┘               └───────┬───────┘          │
                    │           │                               │                  │
                    └───────────┼───────────────────────────────┼──────────────────┘
                                │                               │
                                ▼                               ▼
                    ┌───────────────────┐           ┌───────────────────┐
                    │ imc-jdbc-consumer │           │   Coach Agent     │
                    │ (existing - keep) │           │   (Advocate)      │
                    └─────────┬─────────┘           └─────────┬─────────┘
                              │                               │
                              ▼                               ▼
                    ┌───────────────────┐           ┌───────────────────┐
                    │    Greenplum      │           │  Actuary Agent    │
                    │  vehicle_events   │           │  (Gatekeeper)     │
                    │   (for ML)        │           └───────────────────┘
                    └───────────────────┘
```

**Key Points:**
1. **Sense REPLACES imc-telemetry-processor** - single processor with dual output
2. **Greenplum ML preserved** - accidents still written via `vehicle_events` exchange → `imc-jdbc-consumer`
3. **New AI coaching path** - `behavior_context_exchange` feeds Coach Agent
4. **Richer accident detection** - still detects high g-force events, but with more context

---

## Deployment via imc-stream-manager

Use the existing stream management tooling at:
```
/Users/dbbaskette/Projects/insurance-megacorp/imc-stream-manager/
```

**Steps:**
1. Create `sense-streams.yml` in `imc-stream-manager/stream-configs/` (see IMPLEMENTATION_PLAN.md for template)
2. Run `./stream-manager.sh`
3. Select "Deploy Streams" → choose `sense-streams.yml`

**Note:** When deploying Sense, you should first undeploy the existing `telemetry-to-processor` stream from `telemetry-streams.yml` since Sense replaces `imc-telemetry-processor`. The `vehicle-events-to-jdbc` stream remains unchanged.
