package com.insurancemegacorp.sense.config;

import com.insurancemegacorp.sense.model.BehaviorContext;
import com.insurancemegacorp.sense.model.VehicleEvent;
import com.insurancemegacorp.sense.processor.TelemetryProcessor.ProcessorOutput;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.function.Consumer;

/**
 * Stream configuration for routing processor output to dual destinations.
 */
@Configuration
public class StreamConfig {

    private final StreamBridge streamBridge;

    public StreamConfig(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * Consumer that routes ProcessorOutput to the appropriate channels.
     *
     * <p>VehicleEvent (if present) -> sense-out-0 (vehicle_events exchange)
     * <p>BehaviorContext -> sense-out-1 (behavior_context_exchange)
     */
    @Bean
    public Consumer<ProcessorOutput> routeOutput() {
        return output -> {
            // Route vehicle event to Greenplum pipeline (only if significant event)
            if (output.vehicleEvent() != null) {
                Message<VehicleEvent> vehicleMessage = MessageBuilder
                        .withPayload(output.vehicleEvent())
                        .build();
                streamBridge.send("sense-out-0", vehicleMessage);
            }

            // Always route behavior context to Coach Agent
            if (output.behaviorContext() != null) {
                Message<BehaviorContext> contextMessage = MessageBuilder
                        .withPayload(output.behaviorContext())
                        .build();
                streamBridge.send("sense-out-1", contextMessage);
            }
        };
    }
}
