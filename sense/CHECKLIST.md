# Sense Component - Implementation Checklist

> Quick reference checklist for tracking implementation progress.
> See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for detailed specifications.

---

## Phase 1: Foundation & Infrastructure

### 1.1 Project Setup
- [ ] Maven multi-module project structure
- [ ] Parent POM with dependency management
- [ ] Spring Boot 3.5.x + Java 21 configuration
- [ ] Spring Cloud Stream + RabbitMQ binder
- [ ] Spring Cloud Dataflow processor setup
- [ ] Micrometer + Prometheus metrics
- [ ] Structured JSON logging

### 1.2 Build & Deployment Config
- [ ] `.gitignore`
- [ ] Maven wrapper (`mvnw`)
- [ ] Maven artifact deployment config (to repo for SCDF)
- [ ] `manifest.yml` (Cloud Foundry)
- [ ] SCDF registration metadata

### 1.3 Domain Model
- [ ] `TelemetryEvent` input record
- [ ] `BehaviorContext` output record
- [ ] `MicroBehavior` enum
- [ ] `RiskLevel` enum
- [ ] JSON serialization config

### 1.4 Basic Message Flow (Dual Output)
- [ ] `TelemetryProcessor` function
- [ ] Input binding (`flattened_telemetry_exchange`)
- [ ] Output 1: `vehicle_events` exchange (→ JDBC consumer → Greenplum ML)
- [ ] Output 2: `behavior_context_exchange` (→ Coach Agent)
- [ ] Pass-through integration test
- [ ] Health endpoint
- [ ] Basic throughput metrics

**Phase 1 Status:** ⬜ Not Started / 🟡 In Progress / ✅ Complete

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

## Phase 3: Micro-Behavior Detection

### 3.1 Behavior Types
- [ ] `HARSH_BRAKING`
- [ ] `HARSH_ACCELERATION`
- [ ] `AGGRESSIVE_CORNERING`
- [ ] `SPEEDING`
- [ ] `DISTRACTED_DRIFTING`
- [ ] `TAILGATING`
- [ ] `SMOOTH_DRIVING`
- [ ] `ERRATIC_PATTERN`
- [ ] `COLLISION_AVOIDANCE`
- [ ] `POTENTIAL_ACCIDENT`

### 3.2 Rule-Based Detectors
- [ ] `HarshBrakingDetector`
- [ ] `SpeedingDetector`
- [ ] `CorneringDetector`
- [ ] `AccelerationDetector`
- [ ] `DriftDetector`
- [ ] `BehaviorDetectorChain`

### 3.3 Pattern Recognition
- [ ] Sequence pattern matching
- [ ] Behavior clustering
- [ ] Temporal correlation
- [ ] Configurable thresholds

### 3.4 Intent Classification
- [ ] Evasive vs aggressive distinction
- [ ] Context-aware classification
- [ ] Confidence scoring
- [ ] Explanation generation

### 3.5 Spring AI 1.1.2 + Spring AI Agents
- [ ] Spring AI 1.1.2 dependencies
- [ ] Spring AI Agents dependencies
- [ ] `BehaviorClassificationAgent` (AgentClient API)
- [ ] Goals for intent classification
- [ ] Detection algorithms as Tools
- [ ] Context management for telemetry windows
- [ ] Judges for classification verification
- [ ] Prompt templates
- [ ] LLM intent disambiguation
- [ ] Embedding anomaly detection
- [ ] Feedback loop

**Phase 3 Status:** ⬜ Not Started / 🟡 In Progress / ✅ Complete

---

## Phase 4: Context Builder & Output

### 4.1 Risk Assessment
- [ ] `RiskScorer` implementation
- [ ] Risk level thresholds
- [ ] Historical baseline integration
- [ ] Trip-level aggregation

### 4.2 Context Structure
- [ ] `BehaviorContext` output schema
- [ ] Confidence scores
- [ ] Environmental context
- [ ] Coaching recommendations
- [ ] Data lineage

### 4.3 Coaching Triggers
- [ ] Trigger conditions
- [ ] Debouncing logic
- [ ] Urgency classification
- [ ] Trigger types (immediate, end-of-trip, milestone)

### 4.4 Output Channels
- [ ] Coach Agent queue output
- [ ] Analytics/storage output
- [ ] Output filtering
- [ ] Rate limiting

**Phase 4 Status:** ⬜ Not Started / 🟡 In Progress / ✅ Complete

---

## Phase 5: Observability

### 5.1 Metrics
- [ ] `sense_events_received_total`
- [ ] `sense_events_processed_total`
- [ ] `sense_behaviors_detected_total`
- [ ] `sense_risk_assessments_total`
- [ ] `sense_coaching_triggers_total`
- [ ] `sense_processing_duration_seconds`
- [ ] `sense_active_sessions`
- [ ] `sense_window_size`
- [ ] `sense_detection_confidence`

### 5.2 Health Checks
- [ ] RabbitMQ connectivity
- [ ] GemFire connectivity
- [ ] Memory pressure
- [ ] Session capacity
- [ ] Processing backlog

### 5.3 Logging & Tracing
- [ ] Structured JSON logging
- [ ] Correlation ID propagation
- [ ] Detection audit logging
- [ ] Performance tracing

### 5.4 Alerting Rules
- [ ] High latency alert
- [ ] Low throughput alert
- [ ] Error rate alert
- [ ] Session capacity warning
- [ ] Memory warning

### 5.5 Operational Endpoints
- [ ] `/actuator/health`
- [ ] `/actuator/metrics`
- [ ] `/actuator/prometheus`
- [ ] `/actuator/info`
- [ ] `/actuator/sessions` (custom)
- [ ] `/actuator/detectors` (custom)

**Phase 5 Status:** ⬜ Not Started / 🟡 In Progress / ✅ Complete

---

## Phase 6: Testing

### 6.1 Unit Tests
- [ ] Detector tests (all types)
- [ ] Window aggregation tests
- [ ] Risk scoring tests
- [ ] Context builder tests
- [ ] Serialization tests

### 6.2 Integration Tests
- [ ] End-to-end message flow
- [ ] RabbitMQ (Testcontainers)
- [ ] GemFire integration
- [ ] SCDF deployment

### 6.3 Simulation Personas
- [ ] `TelemetrySimulator` utility
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
- [ ] Harsh braking accuracy
- [ ] Speeding accuracy
- [ ] Intent distinction accuracy
- [ ] Coaching trigger appropriateness
- [ ] Persona response validation

**Phase 6 Status:** ⬜ Not Started / 🟡 In Progress / ✅ Complete

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
| 1 | Foundation & Infrastructure | ⬜ |
| 2 | Temporal Window Management | ⬜ |
| 3 | Micro-Behavior Detection | ⬜ |
| 4 | Context Builder & Output | ⬜ |
| 5 | Observability | ⬜ |
| 6 | Testing | ⬜ |
| 7 | SCDF Deployment | ⬜ |

**Legend:** ⬜ Not Started | 🟡 In Progress | ✅ Complete

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
