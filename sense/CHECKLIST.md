# Sense Component - Implementation Checklist

> **Quick reference** checklist for tracking implementation progress.
> See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for detailed specifications and architecture decisions.

---

## Phase 1: Foundation & Infrastructure ✅

### 1.1 Project Setup
- [x] Maven multi-module project structure (Spring Modulith)
- [x] Parent POM with dependency management
- [x] Spring Boot 3.5.x + Java 21 configuration
- [x] Spring Cloud Stream + RabbitMQ binder
- [x] Spring Cloud Dataflow processor setup
- [x] Micrometer + Prometheus metrics
- [x] Structured JSON logging

### 1.2 Build & Deployment Config
- [x] `.gitignore`
- [x] Maven wrapper (`mvnw`)
- [ ] Maven artifact deployment config (to repo for SCDF)
- [x] `manifest.yml` (Cloud Foundry)
- [ ] SCDF registration metadata

### 1.3 Domain Model
- [x] `TelemetryEvent` input record (35 fields)
- [x] `BehaviorContext` output record
- [x] `MicroBehavior` enum (10 behavior types)
- [x] `RiskLevel` enum
- [x] JSON serialization config

### 1.4 Basic Message Flow (Dual Output)
- [x] `TelemetryProcessor` function
- [x] Input binding (`flattened_telemetry_exchange`)
- [x] Output 1: `vehicle_events` exchange (→ JDBC consumer → Greenplum ML)
- [x] Output 2: `behavior_context_exchange` (→ Coach Agent)
- [x] Integration tests (19 tests passing)
- [x] Health endpoint
- [x] Basic throughput metrics

**Phase 1 Status:** ✅ Complete

---

## Phase 2: Temporal Window Management

### 2.1 Session State
- [ ] `DriverSession` data structure
- [ ] In-memory session store
- [ ] Session timeout/expiration
- [ ] Session metrics

### 2.2 Sliding Windows
- [ ] Time-based windows (30s default)
- [ ] Event count windows (50 events default)
- [ ] Window overlap config
- [ ] Circular buffer implementation

### 2.3 Sequence Tracking
- [ ] Per-driver event sequences
- [ ] Rolling statistics (mean, std, min, max)
- [ ] Derivative detection (rapid changes)
- [ ] Pattern markers

### 2.4 GemFire Integration (Optional)
- [ ] GemFire client dependency
- [ ] Session state region
- [ ] State externalization
- [ ] Failover/recovery

**Phase 2 Status:** ⬜ Not Started / 🟡 In Progress / ✅ Complete

---

## Phase 3: Micro-Behavior Detection (Partial - Rule-Based Complete)

### 3.1 Behavior Types
- [x] `HARSH_BRAKING`
- [x] `HARSH_ACCELERATION`
- [x] `AGGRESSIVE_CORNERING`
- [x] `SPEEDING`
- [x] `DISTRACTED_DRIFTING`
- [x] `TAILGATING`
- [x] `SMOOTH_DRIVING`
- [x] `ERRATIC_PATTERN`
- [x] `COLLISION_AVOIDANCE`
- [x] `POTENTIAL_ACCIDENT`

### 3.2 Rule-Based Detectors
- [x] Harsh braking detection (in TelemetryProcessor)
- [x] Speeding detection (in TelemetryProcessor)
- [x] Cornering detection (in TelemetryProcessor)
- [x] Accident detection (in TelemetryProcessor)
- [ ] `DriftDetector` (separate class)
- [ ] `BehaviorDetectorChain` (refactor to separate classes)

### 3.3 Pattern Recognition
- [ ] Sequence pattern matching
- [ ] Behavior clustering
- [ ] Temporal correlation
- [ ] Configurable thresholds

### 3.4 Intent Classification
- [x] Evasive vs aggressive distinction
- [x] Context-aware classification
- [x] Confidence scoring
- [x] Explanation generation

### 3.5 Spring AI 1.1.2 + LLM Integration
- [x] Spring AI 1.1.2 dependencies (OpenAI)
- [x] `IntentClassifier` service
- [x] `IntentClassificationResult` model
- [x] `DrivingIntent` enum (EVASIVE, AGGRESSIVE, NORMAL, DISTRACTED, UNKNOWN)
- [x] Hybrid approach: rule-based fast path + LLM for ambiguous events
- [x] Prompt templates for intent classification
- [x] LLM intent disambiguation
- [x] Risk score adjustment based on intent
- [x] Configurable thresholds for AI invocation
- [ ] Spring AI Agents integration (future)
- [ ] Embedding anomaly detection (future)
- [ ] Feedback loop (future)

**Phase 3 Status:** ✅ Complete (Rule-based + AI intent classification)

---

## Phase 4: Context Builder & Output (Partial)

### 4.1 Risk Assessment
- [x] `RiskScorer` implementation (in TelemetryProcessor)
- [x] Risk level thresholds
- [ ] Historical baseline integration
- [ ] Trip-level aggregation

### 4.2 Context Structure
- [x] `BehaviorContext` output schema
- [x] Confidence scores
- [ ] Environmental context
- [x] Coaching recommendations
- [ ] Data lineage

### 4.3 Coaching Triggers
- [x] Trigger conditions
- [ ] Debouncing logic
- [x] Urgency classification
- [x] Trigger types (immediate, end-of-trip, milestone)

### 4.4 Output Channels
- [x] Coach Agent queue output
- [x] Vehicle events output (Greenplum)
- [x] Output filtering (significant events only)
- [ ] Rate limiting

**Phase 4 Status:** 🟡 In Progress

---

## Phase 5: Observability (Partial)

### 5.1 Metrics
- [x] `sense_events_received_total`
- [x] `sense_events_processed_total`
- [x] `sense_behaviors_detected_total`
- [ ] `sense_risk_assessments_total`
- [ ] `sense_coaching_triggers_total`
- [x] `sense_processing_duration_seconds`
- [ ] `sense_active_sessions`
- [ ] `sense_window_size`
- [ ] `sense_detection_confidence`

### 5.2 Health Checks
- [x] RabbitMQ connectivity (via Spring Boot)
- [ ] GemFire connectivity
- [ ] Memory pressure
- [ ] Session capacity
- [ ] Processing backlog

### 5.3 Logging & Tracing
- [x] Structured JSON logging
- [ ] Correlation ID propagation
- [x] Detection audit logging
- [ ] Performance tracing

### 5.4 Alerting Rules
- [ ] High latency alert
- [ ] Low throughput alert
- [ ] Error rate alert
- [ ] Session capacity warning
- [ ] Memory warning

### 5.5 Operational Endpoints
- [x] `/actuator/health`
- [x] `/actuator/metrics`
- [x] `/actuator/prometheus`
- [x] `/actuator/info`
- [ ] `/actuator/sessions` (custom)
- [ ] `/actuator/detectors` (custom)

**Phase 5 Status:** 🟡 In Progress

---

## Phase 6: Testing (Partial)

### 6.1 Unit Tests
- [x] Detector tests (TelemetryProcessorTest - 10 tests)
- [ ] Window aggregation tests
- [x] Risk scoring tests
- [x] Context builder tests
- [x] Serialization tests

### 6.2 Integration Tests
- [x] End-to-end message flow (SenseProcessorIntegrationTest - 9 tests)
- [x] Spring Cloud Stream TestChannelBinder
- [ ] RabbitMQ (Testcontainers) - dependencies added
- [ ] GemFire integration
- [ ] SCDF deployment

### 6.3 Simulation Personas
- [x] `TestDataGenerator` utility
- [ ] "Aggressive Commuter" data
- [ ] "New Teen Driver" data
- [ ] "Safe Pro" data
- [ ] Mixed traffic simulation

### 6.4 Performance Tests
- [ ] Throughput (target: 10K/sec)
- [ ] Latency (target: p99 < 100ms)
- [ ] Memory footprint
- [ ] Session scaling (10K drivers)

### 6.5 Validation
- [x] Harsh braking accuracy
- [x] Speeding accuracy
- [ ] Intent distinction accuracy
- [x] Coaching trigger appropriateness
- [ ] Persona response validation

**Phase 6 Status:** 🟡 In Progress (19 tests passing)

---

## Phase 7: SCDF Deployment

### 7.1 Application Registration
- [ ] Properties metadata
- [ ] SCDF processor registration
- [ ] Default binding config
- [ ] Parameter documentation

### 7.2 Stream Definitions (via imc-stream-manager)
- [ ] Create `sense-streams.yml` in `imc-stream-manager/stream-configs/`
- [ ] Define telemetry-to-sense stream (→ vehicle_events)
- [ ] Define sense-to-coach stream (→ behavior_context_exchange)
- [ ] Configure deployment properties (scaling, bindings, thresholds)
- [ ] Test deployment via `./stream-manager.sh`

### 7.3 Cloud Foundry
- [ ] `manifest.yml` resources
- [ ] Service bindings
- [ ] Autoscaling rules
- [ ] Health check config

### 7.4 Operations
- [ ] Deployment procedure doc
- [ ] Rollback procedure doc
- [ ] Issue runbook
- [ ] Config change procedure

**Phase 7 Status:** ⬜ Not Started / 🟡 In Progress / ✅ Complete

---

## Overall Progress

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Foundation & Infrastructure | ✅ Complete |
| 2 | Temporal Window Management | ⬜ Not Started |
| 3 | Micro-Behavior Detection + AI Intent Classification | ✅ Complete |
| 4 | Context Builder & Output | 🟡 Core done, enhancements pending |
| 5 | Observability | 🟡 Basic metrics done |
| 6 | Testing | 🟡 19 tests passing |
| 7 | SCDF Deployment | ⬜ Not Started |

**Legend:** ⬜ Not Started | 🟡 In Progress | ✅ Complete

**Last Updated:** 2025-12-15

---

## Quick Commands

```bash
# Build
./mvnw clean package

# Run locally
./mvnw spring-boot:run

# Run tests
./mvnw test

# Deploy JAR to artifact repository
./mvnw deploy

# Deploy to CF (standalone)
cf push

# Register with SCDF (from Maven repo)
dataflow:> app register --name sense --type processor --uri maven://com.insurancemegacorp:sense-processor:1.0.0

# Register with SCDF (from local JAR)
dataflow:> app register --name sense --type processor --uri file:///path/to/sense-processor-1.0.0.jar
```
