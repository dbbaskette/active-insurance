# Demo 1: "Active Insurance" Telemetry & Coaching (Pure Insurance Focus)

## 1. Executive Summary

**Scenario:** A "Usage-Based Insurance" (UBI) platform that uses GenAI to actively coach drivers and dynamically adjust policy terms in real-time.
**Core Value:** Demonstrates **Multi-Agent Collaboration** on Cloud Foundry. It moves beyond simple analytics dashboards by having agents actively "discuss" the risk and agree on incentives without human intervention.

---

## 2. The "Why AI?" Argument

*Why isn't this just a rule engine?*

- **Complex Context:** A rule engine sees "Hard Brake." An AI Agent sees "Hard braking sequence consistent with avoiding an obstacle vs. aggressive tailgating" based on the cluster of data.
- **The "Negotiation":** The core "Agentic" workflow is the tension between the **Coach** (who wants to make the driver happy) and the **Actuary** (who wants to protect the company's risk). They must agree on a reward that satisfies both safety goals and financial margins.

---

## 3. The Agents

### A. The Telemetry Monitor (The "Sense")

- **Role:** Event detection and aggregation.
- **Input:** Streaming Telemetry (Speed, Braking, Cornering, Location).
- **Function:**
    - Ingests high-velocity stream.
    - Identifies "Micro-behaviors" (e.g., "Distracted drifting").
    - Passes structured context to the Coach.

### B. The Driver Coach (The "Advocate")

- **Role:** User interface and behavioral modification.
- **Input:** Driver Persona + Risk Context from Monitor.
- **Function:**
    - **Persona Injection:** Adopts a personality matching the driver (e.g., "Gentle Guide" for nervous drivers, "Performance Instructor" for confident ones).
    - **Gamification Logic:** Generates immediate feedback.
    - **Agentic Action:** When a driver improves, the Coach *contacts the Policy Agent* to demand a reward (lower deductible, premium credit).

### C. The Policy & Actuary Agent (The "Gatekeeper")

- **Role:** Financial risk management.
- **Input:** Customer Policy Data (Deductibles, Limits, Claims History).
- **Function:**
    - Holds the "Authority" to change policy details.
    - **The Negotiation:** The Coach asks: *"Driver improved safety score by 15% this week. I want to give them a $50 credit."*
    - The Actuary Agent evaluates: *"Current risk profile allows for max $30 credit. I will approve $30 if they maintain this score for 24 more hours."*
    - **Output:** Updates the backend policy system.

---

## 4. Architecture

- **Platform:** Cloud Foundry (TAS/Tanzu).
- **Framework:** Spring AI.
- **Data Flow:**
    1. **Ingest:** Python or Java Stream Processor → RabbitMQ.
    2. **Processing:** Spring Boot "Monitor" Service consumes stream.
    3. **Agent Interaction:**
        - Monitor triggers **Coach Agent**.
        - Coach Agent uses `Function Calling` to talk to **Actuary Agent**.
    4. **Storage:** Greenplum (Telemetry History), GemFire (Real-time Session State/Leaderboards).

---

## 5. The Simulation Strategy (The "Virtual Fleet")

Since we lack real drivers, we simulate specific personas to force the Agents to react differently:

1. **The "Aggressive Commuter"**
    - **Simulated Data:** High speed, sudden stops.
    - **Agent Reaction:** Actuary raises "Virtual Premium." Coach switches to "Warning Mode."
2. **The "New Teen Driver"**
    - **Simulated Data:** Erratic turns, low speed.
    - **Agent Reaction:** Coach adopts "Supportive/Teaching" tone. Actuary locks "Rewards" until stability improves.
3. **The "Safe Pro"**
    - **Simulated Data:** Smooth acceleration, anticipating stops.
    - **Agent Reaction:** Coach requests "Gold Status." Actuary approves immediate deductible reduction.
