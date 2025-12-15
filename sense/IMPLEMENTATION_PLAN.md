# Sense Component - Implementation Plan

## Overview

The **Sense** component (Agent 3A - "Telemetry Monitor") is the intelligent sensing layer of the Active Insurance platform. It transforms raw vehicle telemetry into structured behavioral context that enables downstream AI agents (Coach, Actuary) to make informed decisions.

### Mission Statement
> Replace the simple g-force threshold filter with **intelligent micro-behavior detection** that provides rich context for AI-driven driver coaching while maintaining accident event output for Greenplum ML pipeline.

---

## Architecture Summary

```
                                    ┌─────────────────────────────────────────┐
                                    │           SENSE COMPONENT               │
                                    │    (Spring Cloud Dataflow Processor)    │
┌──────────────┐                    │                                         │
│   RabbitMQ   │                    │  ┌─────────────────────────────────┐   │
│  (Telemetry  │───────────────────▶│  │   Telemetry Ingestion Layer    │   │
│   Exchange)  │  Pre-flattened     │  │   - Message deserialization    │   │
└──────────────┘  JSON (35 fields)  │  │   - Validation & normalization │   │
                                    │  └──────────────┬──────────────────┘   │
                                    │                 │                       │
                                    │                 ▼                       │
                                    │  ┌─────────────────────────────────┐   │
                                    │  │   Temporal Window Manager       │   │
                                    │  │   - Sliding window aggregation  │   │
                                    │  │   - Per-driver session tracking │   │
                                    │  │   - Event sequence buffering    │   │
                                    │  └──────────────┬──────────────────┘   │
                                    │                 │                       │
                                    │                 ▼                       │
                                    │  ┌─────────────────────────────────┐   │
                                    │  │   Behavior Detection Engine     │   │
                                    │  │   - Micro-behavior classifiers  │   │
                                    │  │   - Pattern recognition         │   │
                                    │  │   - Anomaly detection           │   │
                                    │  └──────────────┬──────────────────┘   │
                                    │                 │                       │
                                    │                 ▼                       │
                                    │  ┌─────────────────────────────────┐   │
                                    │  │   Context Builder               │   │
                                    │  │   - Risk assessment scoring     │   │
                                    │  │   - Structured output format    │   │
                                    │  │   - Coaching trigger logic      │   │
                                    │  └──────────────┬──────────────────┘   │
                                    │                 │                       │
                                    └─────────────────┼───────────────────────┘
                                                      │
                                        ┌─────────────┴─────────────┐
                                        │                           │
                                        ▼                           ▼
                          ┌─────────────────────────┐ ┌─────────────────────────┐
                          │    OUTPUT 1:            │ │    OUTPUT 2:            │
                          │    vehicle_events       │ │    behavior_context     │
                          │    (accidents)          │ │    (coaching)           │
                          └───────────┬─────────────┘ └───────────┬─────────────┘
                                      │                           │
                                      ▼                           ▼
                          ┌─────────────────────────┐ ┌─────────────────────────┐
                          │  imc-jdbc-consumer      │ │    Coach Agent          │
                          │  → Greenplum ML         │ │    (Advocate)           │
                          └─────────────────────────┘ └─────────────────────────┘
```

---

## Technology Stack

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| Runtime | Java | 21 | LTS with virtual threads |
| Framework | Spring Boot | 3.5.x | Application foundation |
| Streaming | Spring Cloud Stream | 2025.0.x | RabbitMQ integration |
| Orchestration | Spring Cloud Dataflow | 2.11.x | Deployment & scaling |
| State Management | GemFire | 10.x | Real-time session state |
| AI Framework | Spring AI | 1.1.2 | Core AI/LLM integration |
| Agent Framework | Spring AI Agents | Latest | Agent orchestration (Goals, Tools, Context, Judges) |
| Observability | Micrometer + Prometheus | Latest | Metrics & monitoring |
| Build | Maven | 3.9+ | Build & dependency management |

### Spring AI Agents Integration

The Sense component leverages the [Spring AI Agents](https://spring-ai-community.github.io/spring-ai-agents/) framework for:
- **AgentClient API** - Fluent API pattern (mirrors ChatClient) for agent interactions
- **Goals** - Clear objectives for behavior classification
- **Tools** - Detection algorithms exposed as callable tools
- **Context** - Telemetry window data for decision-making
- **Judges** - Verification of detection accuracy

---

## Phase 1: Foundation & Infrastructure

**Goal:** Establish the project structure, build pipeline, and basic message flow.

**Duration Estimate:** Foundation phase

### Checklist

#### 1.1 Project Setup
- [ ] Create Maven multi-module project structure
- [ ] Configure parent POM with dependency management
- [ ] Set up Spring Boot 3.5.x with Java 21
- [ ] Configure Spring Cloud Stream with RabbitMQ binder
- [ ] Add Spring Cloud Dataflow processor dependencies
- [ ] Configure Micrometer + Prometheus metrics
- [ ] Set up logging with structured JSON output

#### 1.2 Build & CI Configuration
- [ ] Create `.gitignore` for Java/Maven project
- [ ] Add Maven wrapper (`mvnw`)
- [ ] Configure Maven for JAR artifact deployment to repository
- [ ] Create Cloud Foundry `manifest.yml`
- [ ] Add SCDF application registration metadata

#### 1.3 Core Domain Model
- [ ] Define `TelemetryEvent` input record (35 fields from existing schema)
- [ ] Define `BehaviorContext` output record
- [ ] Define `MicroBehavior` enumeration
- [ ] Define `RiskLevel` enumeration
- [ ] Create JSON serialization/deserialization configuration

#### 1.4 Basic Message Flow (Dual Output)
- [ ] Implement `TelemetryProcessor` Spring Cloud Stream function
- [ ] Configure input binding from `flattened_telemetry_exchange`
- [ ] Configure output 1: `vehicle_events` exchange (for existing JDBC consumer → Greenplum ML)
- [ ] Configure output 2: `behavior_context_exchange` (for Coach Agent)
- [ ] Add pass-through mode for integration testing
- [ ] Implement health check endpoint
- [ ] Add basic throughput metrics

### Deliverables
```
sense/
├── pom.xml
├── manifest.yml
├── .gitignore
├── mvnw / mvnw.cmd
├── src/
│   ├── main/
│   │   ├── java/com/insurancemegacorp/sense/
│   │   │   ├── SenseApplication.java
│   │   │   ├── config/
│   │   │   │   ├── StreamConfig.java
│   │   │   │   └── MetricsConfig.java
│   │   │   ├── model/
│   │   │   │   ├── TelemetryEvent.java
│   │   │   │   ├── BehaviorContext.java
│   │   │   │   ├── MicroBehavior.java
│   │   │   │   └── RiskLevel.java
│   │   │   └── processor/
│   │   │       └── TelemetryProcessor.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-cloud.yml
│   └── test/
│       └── java/com/imc/sense/
│           └── processor/
│               └── TelemetryProcessorTest.java
└── docs/
    └── IMPLEMENTATION_PLAN.md (this file)
```

---

## Phase 2: Temporal Window Management

**Goal:** Implement sliding window aggregation for per-driver event sequences.

### Checklist

#### 2.1 Session State Management
- [ ] Design driver session data structure
- [ ] Implement in-memory session store (initial)
- [ ] Add session timeout/expiration logic
- [ ] Implement session metrics (active sessions, memory usage)

#### 2.2 Sliding Window Implementation
- [ ] Implement configurable time-based windows (default: 30 seconds)
- [ ] Implement event count-based windows (default: 50 events)
- [ ] Add window overlap configuration
- [ ] Implement efficient circular buffer for events

#### 2.3 Event Sequence Tracking
- [ ] Track per-driver event sequences
- [ ] Compute rolling statistics (mean, std dev, min, max)
- [ ] Detect rapid changes (derivatives)
- [ ] Implement sequence pattern markers

#### 2.4 GemFire Integration (Optional Enhancement)
- [ ] Add GemFire client dependency
- [ ] Configure region for session state
- [ ] Implement state externalization for HA
- [ ] Add failover/recovery logic

### Key Classes
```java
// Core window management
com.insurancemegacorp.sense.window.DriverSession
com.insurancemegacorp.sense.window.SlidingWindow<T>
com.insurancemegacorp.sense.window.WindowAggregator
com.insurancemegacorp.sense.window.SessionManager

// Statistics computation
com.insurancemegacorp.sense.stats.RollingStatistics
com.insurancemegacorp.sense.stats.DerivativeCalculator
```

### Configuration Properties
```yaml
sense:
  window:
    time-duration: 30s
    event-count: 50
    overlap-ratio: 0.5
  session:
    timeout: 5m
    max-active: 10000
```

---

## Phase 3: Micro-Behavior Detection Engine

**Goal:** Implement rule-based and ML-assisted behavior classification.

### Checklist

#### 3.1 Behavior Taxonomy
- [ ] Define micro-behavior categories:
  - [ ] `HARSH_BRAKING` - Sudden deceleration
  - [ ] `HARSH_ACCELERATION` - Aggressive acceleration
  - [ ] `AGGRESSIVE_CORNERING` - High lateral g-force turns
  - [ ] `SPEEDING` - Exceeding speed limit
  - [ ] `DISTRACTED_DRIFTING` - Lane drift patterns
  - [ ] `TAILGATING` - Unsafe following patterns
  - [ ] `SMOOTH_DRIVING` - Exemplary behavior
  - [ ] `ERRATIC_PATTERN` - Inconsistent behavior
  - [ ] `COLLISION_AVOIDANCE` - Emergency maneuver
  - [ ] `POTENTIAL_ACCIDENT` - High g-force event

#### 3.2 Rule-Based Detectors
- [ ] Implement `HarshBrakingDetector` (g_force threshold + duration)
- [ ] Implement `SpeedingDetector` (speed vs speed_limit comparison)
- [ ] Implement `CorneringDetector` (lateral acceleration analysis)
- [ ] Implement `AccelerationDetector` (longitudinal acceleration)
- [ ] Implement `DriftDetector` (gyroscope yaw analysis)
- [ ] Implement composite `BehaviorDetectorChain`

#### 3.3 Pattern Recognition
- [ ] Implement sequence pattern matching
- [ ] Detect behavior clusters (multiple related events)
- [ ] Implement temporal correlation analysis
- [ ] Add configurable detection thresholds

#### 3.4 Intent Classification (Advanced)
- [ ] Distinguish evasive vs aggressive maneuvers
- [ ] Context-aware classification (road type, traffic)
- [ ] Implement confidence scoring
- [ ] Add explanation generation for classifications

#### 3.5 Spring AI 1.1.2 + Spring AI Agents Integration
- [ ] Add Spring AI 1.1.2 dependencies
- [ ] Add Spring AI Agents dependencies
- [ ] Implement `BehaviorClassificationAgent` using AgentClient API
- [ ] Define Goals for intent classification (evasive vs aggressive)
- [ ] Expose detection algorithms as Tools
- [ ] Configure Context management for telemetry windows
- [ ] Implement Judges for classification verification
- [ ] Design prompt templates for behavior analysis
- [ ] Implement LLM-based intent disambiguation
- [ ] Add embedding-based anomaly detection
- [ ] Create feedback loop for model improvement

### Key Classes
```java
// Behavior detection
com.insurancemegacorp.sense.behavior.MicroBehavior (enum)
com.insurancemegacorp.sense.behavior.BehaviorDetector (interface)
com.insurancemegacorp.sense.behavior.DetectorChain
com.insurancemegacorp.sense.behavior.DetectionResult

// Individual detectors
com.insurancemegacorp.sense.behavior.detectors.HarshBrakingDetector
com.insurancemegacorp.sense.behavior.detectors.SpeedingDetector
com.insurancemegacorp.sense.behavior.detectors.CorneringDetector
com.insurancemegacorp.sense.behavior.detectors.DriftDetector
com.insurancemegacorp.sense.behavior.detectors.ErraticPatternDetector

// Pattern recognition
com.insurancemegacorp.sense.pattern.SequenceMatcher
com.insurancemegacorp.sense.pattern.BehaviorCluster

// Spring AI Agents integration
com.insurancemegacorp.sense.agent.BehaviorClassificationAgent
com.insurancemegacorp.sense.agent.tools.DetectionTools        // Exposes detectors as agent tools
com.insurancemegacorp.sense.agent.context.TelemetryContext    // Window data for agent decisions
com.insurancemegacorp.sense.agent.judges.ClassificationJudge  // Verifies classification accuracy
```

### Configuration Properties
```yaml
sense:
  detection:
    harsh-braking:
      g-force-threshold: 0.4
      duration-ms: 500
    speeding:
      tolerance-mph: 5
      sustained-seconds: 10
    cornering:
      lateral-g-threshold: 0.3
    drift:
      yaw-rate-threshold: 15  # degrees/second
```

---

## Phase 4: Context Builder & Output Generation

**Goal:** Transform detected behaviors into rich, structured context for AI agents.

### Checklist

#### 4.1 Risk Assessment
- [ ] Implement `RiskScorer` with weighted factors
- [ ] Define risk level thresholds (LOW, MODERATE, ELEVATED, HIGH, CRITICAL)
- [ ] Add historical context (driver's baseline)
- [ ] Implement trip-level risk aggregation

#### 4.2 Context Structure Design
- [ ] Define `BehaviorContext` output schema
- [ ] Include detected behaviors with confidence scores
- [ ] Add environmental context (time, location type)
- [ ] Include recommended coaching actions
- [ ] Add data lineage (source events)

#### 4.3 Coaching Trigger Logic
- [ ] Define trigger conditions for Coach Agent
- [ ] Implement trigger debouncing (avoid spam)
- [ ] Add urgency classification
- [ ] Support different trigger types (immediate, end-of-trip, milestone)

#### 4.4 Output Channel Configuration
- [ ] Configure primary output to Coach Agent queue
- [ ] Add secondary output for analytics/storage
- [ ] Implement output filtering (only significant events)
- [ ] Add output rate limiting

### Output Schema
```json
{
  "contextId": "uuid",
  "timestamp": "2024-01-15T10:30:00Z",
  "driverId": "D-12345",
  "vehicleId": "V-67890",
  "policyId": "P-11111",
  "sessionId": "S-22222",

  "behaviors": [
    {
      "type": "HARSH_BRAKING",
      "confidence": 0.92,
      "severity": "MODERATE",
      "context": {
        "gForce": 0.52,
        "durationMs": 850,
        "speedBeforeMph": 45,
        "speedAfterMph": 12
      },
      "interpretation": "COLLISION_AVOIDANCE",
      "interpretationConfidence": 0.78
    }
  ],

  "tripContext": {
    "tripDurationMinutes": 23,
    "distanceMiles": 12.4,
    "behaviorCounts": {
      "HARSH_BRAKING": 2,
      "SPEEDING": 0,
      "SMOOTH_DRIVING": 45
    }
  },

  "riskAssessment": {
    "currentLevel": "MODERATE",
    "score": 0.45,
    "trend": "IMPROVING",
    "factors": [
      {"factor": "BRAKING_EVENTS", "weight": 0.3, "score": 0.6},
      {"factor": "SPEED_COMPLIANCE", "weight": 0.25, "score": 0.95}
    ]
  },

  "coachingTrigger": {
    "shouldTrigger": true,
    "triggerType": "IMMEDIATE",
    "urgency": "MEDIUM",
    "suggestedTopic": "DEFENSIVE_BRAKING",
    "suggestedTone": "SUPPORTIVE"
  },

  "metadata": {
    "processingTimeMs": 12,
    "windowEvents": 47,
    "modelVersion": "1.0.0"
  }
}
```

---

## Phase 5: Observability & Production Readiness

**Goal:** Comprehensive monitoring, alerting, and operational tooling.

### Checklist

#### 5.1 Metrics Implementation
- [ ] `sense_events_received_total` - Input throughput
- [ ] `sense_events_processed_total` - Successfully processed
- [ ] `sense_behaviors_detected_total` - By behavior type
- [ ] `sense_risk_assessments_total` - By risk level
- [ ] `sense_coaching_triggers_total` - Triggers generated
- [ ] `sense_processing_duration_seconds` - Processing latency histogram
- [ ] `sense_active_sessions` - Current driver sessions
- [ ] `sense_window_size` - Events in active windows
- [ ] `sense_detection_confidence` - Confidence score distribution

#### 5.2 Health Checks
- [ ] RabbitMQ connectivity check
- [ ] GemFire connectivity check (if used)
- [ ] Memory pressure check
- [ ] Session capacity check
- [ ] Processing backlog check

#### 5.3 Logging & Tracing
- [ ] Structured JSON logging
- [ ] Correlation ID propagation
- [ ] Behavior detection audit logging
- [ ] Performance trace logging (debug mode)

#### 5.4 Alerting Rules (Prometheus/Grafana)
- [ ] High processing latency alert
- [ ] Low throughput alert
- [ ] Error rate spike alert
- [ ] Session capacity warning
- [ ] Memory pressure warning

#### 5.5 Operational Endpoints
- [ ] `/actuator/health` - Comprehensive health
- [ ] `/actuator/metrics` - All metrics
- [ ] `/actuator/prometheus` - Prometheus scrape
- [ ] `/actuator/info` - Build info, config
- [ ] Custom: `/actuator/sessions` - Active session info
- [ ] Custom: `/actuator/detectors` - Detector status

---

## Phase 6: Testing & Validation

**Goal:** Comprehensive test coverage and simulation capabilities.

### Checklist

#### 6.1 Unit Tests
- [ ] Detector unit tests (each behavior type)
- [ ] Window aggregation tests
- [ ] Risk scoring tests
- [ ] Context builder tests
- [ ] Serialization/deserialization tests

#### 6.2 Integration Tests
- [ ] End-to-end message flow tests
- [ ] RabbitMQ integration tests (Testcontainers)
- [ ] GemFire integration tests (if used)
- [ ] SCDF deployment tests

#### 6.3 Simulation & Personas
- [ ] Implement `TelemetrySimulator` utility
- [ ] Create "Aggressive Commuter" persona data
- [ ] Create "New Teen Driver" persona data
- [ ] Create "Safe Pro" persona data
- [ ] Create mixed traffic simulation

#### 6.4 Performance Testing
- [ ] Throughput benchmarks (target: 10K events/sec)
- [ ] Latency benchmarks (target: p99 < 100ms)
- [ ] Memory footprint analysis
- [ ] Session scaling tests (10K concurrent drivers)

#### 6.5 Validation Scenarios
- [ ] Verify harsh braking detection accuracy
- [ ] Verify speeding detection accuracy
- [ ] Verify collision avoidance vs aggression distinction
- [ ] Verify coaching trigger appropriateness
- [ ] End-to-end persona response validation

---

## Phase 7: SCDF Deployment & Operations

**Goal:** Production deployment configuration and operational procedures.

### Checklist

#### 7.1 SCDF Application Registration
- [ ] Create application properties metadata
- [ ] Register as SCDF processor application
- [ ] Configure default binding properties
- [ ] Document deployment parameters

#### 7.2 Stream Definitions
- [ ] Define development stream pipeline
- [ ] Define production stream pipeline
- [ ] Configure scaling policies
- [ ] Set up stream monitoring

#### 7.3 Cloud Foundry Configuration
- [ ] Create `manifest.yml` with proper resources
- [ ] Configure service bindings (RabbitMQ, GemFire)
- [ ] Set up autoscaling rules
- [ ] Configure health check settings

#### 7.4 Operational Procedures
- [ ] Document deployment procedure
- [ ] Document rollback procedure
- [ ] Create runbook for common issues
- [ ] Document configuration changes procedure

### Stream Management via imc-stream-manager

The Sense component integrates with the existing `imc-stream-manager` tooling at:
```
/Users/dbbaskette/Projects/insurance-megacorp/imc-stream-manager/
```

**Deployment workflow:**
1. Add `sense-streams.yml` to `imc-stream-manager/stream-configs/`
2. Run `./stream-manager.sh`
3. Select "Deploy Streams" → choose `sense-streams.yml`

### Stream Configuration File

Create `imc-stream-manager/stream-configs/sense-streams.yml`:

```yaml
# Sense Component - Active Insurance Telemetry Processing
# Replaces: imc-telemetry-processor (simple g-force threshold)
# Adds: Micro-behavior detection, AI coaching context

apps:
  - name: sense-processor
    type: processor
    github:
      owner: insurance-megacorp
      repo: active-insurance
      artifact: sense-processor
    version: "1.0.0"

streams:
  # Stream 1: Telemetry → Sense → Vehicle Events (for existing JDBC consumer)
  - name: telemetry-to-sense
    definition: ":flattened_telemetry_exchange > sense-processor > :vehicle_events"
    description: "Intelligent behavior detection replacing imc-telemetry-processor"

  # Stream 2: Sense → Behavior Context (for Coach Agent)
  - name: sense-to-coach
    definition: ":flattened_telemetry_exchange > sense-processor > :behavior_context_exchange"
    description: "Rich behavioral context for AI coaching pipeline"

deployment:
  sense-processor:
    count: 3
    memory: 2G

    # Input binding
    spring.cloud.stream.bindings.sense-in-0.destination: flattened_telemetry_exchange
    spring.cloud.stream.bindings.sense-in-0.group: sense-processor
    spring.cloud.stream.bindings.sense-in-0.consumer.concurrency: 3

    # Output 1: Vehicle events (accidents) → existing JDBC consumer → Greenplum
    spring.cloud.stream.bindings.sense-out-0.destination: vehicle_events

    # Output 2: Behavior context → Coach Agent
    spring.cloud.stream.bindings.sense-out-1.destination: behavior_context_exchange

    # Detection thresholds
    sense.detection.harsh-braking.g-force-threshold: 0.4
    sense.detection.speeding.tolerance-mph: 5
    sense.detection.accident.g-force-threshold: 5.0

    # Window configuration
    sense.window.time-duration: 30s
    sense.window.event-count: 50

    # Spring AI configuration
    spring.ai.anthropic.api-key: ${ANTHROPIC_API_KEY}

    # Actuator endpoints
    management.endpoints.web.exposure.include: health,metrics,prometheus
```

### Manual SCDF Commands (Alternative)

```bash
# Register the app (JAR from Maven repository)
dataflow:> app register --name sense-processor --type processor \
  --uri maven://com.insurancemegacorp:sense-processor:1.0.0

# Create the stream (dual output handled by app internally)
dataflow:> stream create --name telemetry-to-sense \
  --definition ":flattened_telemetry_exchange > sense-processor"

# Deploy with scaling
dataflow:> stream deploy telemetry-to-sense \
  --properties "deployer.sense-processor.count=3,deployer.sense-processor.memory=2G"
```

---

## Appendix A: Data Model Reference

### Input: TelemetryEvent (35 fields)

```java
public record TelemetryEvent(
    // Core identifiers
    String policyId,
    String vehicleId,
    String vin,
    String driverId,
    Instant eventTime,

    // Speed data
    Double speedMph,
    Double speedLimitMph,
    String currentStreet,

    // Safety metrics
    Double gForce,

    // GPS data
    Double gpsLatitude,
    Double gpsLongitude,
    Double gpsAltitude,
    Double gpsSpeed,
    Double gpsBearing,
    Double gpsAccuracy,
    Integer gpsSatelliteCount,
    Long gpsFixTime,

    // IMU - Accelerometer
    Double accelerometerX,
    Double accelerometerY,
    Double accelerometerZ,

    // IMU - Gyroscope
    Double gyroscopeX,
    Double gyroscopeY,
    Double gyroscopeZ,

    // Magnetometer
    Double magnetometerX,
    Double magnetometerY,
    Double magnetometerZ,
    Double magnetometerHeading,

    // Environmental
    Double barometricPressure,

    // Device state
    Double deviceBatteryLevel,
    Double deviceSignalStrength,
    String deviceOrientation,
    Boolean deviceScreenOn,
    Boolean deviceCharging
) {}
```

### Output: BehaviorContext

See Phase 4 for complete JSON schema.

---

## Appendix B: Behavior Detection Thresholds

| Behavior | Primary Metric | Threshold | Secondary Conditions |
|----------|---------------|-----------|---------------------|
| HARSH_BRAKING | g_force (longitudinal) | > 0.4g | Duration > 500ms |
| HARSH_ACCELERATION | g_force (longitudinal) | > 0.35g | Duration > 1000ms |
| AGGRESSIVE_CORNERING | g_force (lateral) | > 0.3g | Speed > 25mph |
| SPEEDING | speed - speed_limit | > 5mph | Sustained > 10s |
| DISTRACTED_DRIFTING | gyroscope_z variance | > 15°/s | No turn signal context |
| TAILGATING | Following time estimate | < 2s | Speed > 35mph |
| SMOOTH_DRIVING | All metrics | Within normal | Sustained > 60s |
| POTENTIAL_ACCIDENT | g_force (any axis) | > 4.0g | Sudden onset |

---

## Appendix C: Risk Scoring Model

```
Risk Score = Σ (factor_weight × factor_score) / Σ factor_weight

Factors:
- BRAKING_EVENTS:     weight=0.25, score=f(harsh_brake_count, severity)
- ACCELERATION:       weight=0.15, score=f(harsh_accel_count, severity)
- SPEED_COMPLIANCE:   weight=0.25, score=f(speeding_duration, excess_mph)
- CORNERING:          weight=0.15, score=f(aggressive_turn_count)
- CONSISTENCY:        weight=0.10, score=f(behavior_variance)
- ATTENTION:          weight=0.10, score=f(drift_events, erratic_patterns)

Risk Levels:
- LOW:      score < 0.2
- MODERATE: score 0.2 - 0.4
- ELEVATED: score 0.4 - 0.6
- HIGH:     score 0.6 - 0.8
- CRITICAL: score > 0.8
```

---

## Appendix D: Maven Dependencies

### Core Spring AI Dependencies
```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.5.0</spring-boot.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <spring-cloud-stream.version>4.2.0</spring-cloud-stream.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring AI Core -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-core</artifactId>
    </dependency>

    <!-- Spring AI Agents (community) -->
    <!-- See: https://spring-ai-community.github.io/spring-ai-agents/ -->
    <dependency>
        <groupId>org.springframework.ai.community</groupId>
        <artifactId>spring-ai-agents</artifactId>
        <version>LATEST</version>
    </dependency>

    <!-- LLM Provider (choose one) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
    </dependency>

    <!-- Spring Cloud Stream for RabbitMQ -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
    </repository>
</repositories>
```

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2024-XX-XX | Team | Initial plan |

