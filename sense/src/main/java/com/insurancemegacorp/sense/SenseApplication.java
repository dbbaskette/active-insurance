package com.insurancemegacorp.sense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sense Component - Active Insurance Telemetry Monitor Agent
 *
 * <p>This Spring Cloud Stream processor transforms raw vehicle telemetry
 * into structured behavioral context for downstream AI agents (Coach, Actuary).
 *
 * <p>Dual output:
 * <ul>
 *   <li>Output 1: vehicle_events exchange → imc-jdbc-consumer → Greenplum (for ML)</li>
 *   <li>Output 2: behavior_context_exchange → Coach Agent (for AI coaching)</li>
 * </ul>
 *
 * <p>Replaces: imc-telemetry-processor (simple g-force threshold filter)
 */
@SpringBootApplication
public class SenseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SenseApplication.class, args);
    }
}
