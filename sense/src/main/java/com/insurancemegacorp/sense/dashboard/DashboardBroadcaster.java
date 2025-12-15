package com.insurancemegacorp.sense.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@EnableScheduling
public class DashboardBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DashboardBroadcaster.class);

    private final DashboardStats stats;
    private final DashboardWebSocketHandler webSocketHandler;

    private final AtomicLong lastEventCount = new AtomicLong(0);
    private volatile long lastCheckTime = System.currentTimeMillis();

    public DashboardBroadcaster(DashboardStats stats, DashboardWebSocketHandler webSocketHandler) {
        this.stats = stats;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedRate = 1000)
    public void broadcastStats() {
        // Calculate events per second
        long currentCount = stats.getSnapshot().eventsReceived();
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastCheckTime;

        if (elapsed > 0) {
            double eps = (currentCount - lastEventCount.get()) * 1000.0 / elapsed;
            stats.setEventsPerSecond(eps);
        }

        lastEventCount.set(currentCount);
        lastCheckTime = currentTime;

        // Broadcast to connected clients
        if (webSocketHandler.getConnectedClients() > 0) {
            webSocketHandler.broadcast(stats.getSnapshot());
        }
    }
}
