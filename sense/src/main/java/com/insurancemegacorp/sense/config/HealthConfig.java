package com.insurancemegacorp.sense.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom health indicator for the Sense processor.
 */
@Component
public class HealthConfig implements HealthIndicator {

    private final MeterRegistry meterRegistry;
    private final AtomicLong lastProcessedTime = new AtomicLong(System.currentTimeMillis());

    public HealthConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Health health() {
        long timeSinceLastProcess = System.currentTimeMillis() - lastProcessedTime.get();

        // If no messages processed in 5 minutes, consider unhealthy
        // (This is lenient for demo purposes)
        if (timeSinceLastProcess > 300_000) {
            return Health.down()
                    .withDetail("reason", "No messages processed recently")
                    .withDetail("timeSinceLastProcessMs", timeSinceLastProcess)
                    .build();
        }

        return Health.up()
                .withDetail("status", "Processing telemetry events")
                .withDetail("timeSinceLastProcessMs", timeSinceLastProcess)
                .build();
    }

    /**
     * Update the last processed timestamp.
     */
    public void recordProcessing() {
        lastProcessedTime.set(System.currentTimeMillis());
    }
}
