package com.insurancemegacorp.sense.dashboard;

import com.insurancemegacorp.sense.model.MicroBehavior;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class DashboardStats {

    private final AtomicLong totalEventsReceived = new AtomicLong(0);
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalBehaviorsDetected = new AtomicLong(0);
    private final AtomicLong totalVehicleEventsEmitted = new AtomicLong(0);
    private final AtomicLong potentialAccidents = new AtomicLong(0);
    private final AtomicLong harshBrakingEvents = new AtomicLong(0);
    private final AtomicLong speedingEvents = new AtomicLong(0);
    private final AtomicLong aggressiveCorneringEvents = new AtomicLong(0);

    private final Map<String, DriverStats> driverStats = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<RecentEvent> recentEvents = new CopyOnWriteArrayList<>();

    private volatile Instant startTime = Instant.now();
    private volatile double currentEventsPerSecond = 0.0;

    // Maximum recent events to keep
    private static final int MAX_RECENT_EVENTS = 50;

    public void incrementEventsReceived() {
        totalEventsReceived.incrementAndGet();
    }

    public void incrementEventsProcessed() {
        totalEventsProcessed.incrementAndGet();
    }

    public void incrementBehaviorsDetected(int count) {
        totalBehaviorsDetected.addAndGet(count);
    }

    public void incrementVehicleEventsEmitted() {
        totalVehicleEventsEmitted.incrementAndGet();
    }

    public void recordBehavior(MicroBehavior behavior) {
        switch (behavior) {
            case POTENTIAL_ACCIDENT -> potentialAccidents.incrementAndGet();
            case HARSH_BRAKING -> harshBrakingEvents.incrementAndGet();
            case SPEEDING -> speedingEvents.incrementAndGet();
            case AGGRESSIVE_CORNERING -> aggressiveCorneringEvents.incrementAndGet();
            default -> {}
        }
    }

    public void updateDriverStats(String driverId, MicroBehavior behavior, double riskScore) {
        driverStats.compute(driverId, (id, stats) -> {
            if (stats == null) {
                stats = new DriverStats(id);
            }
            stats.incrementEvents();
            stats.updateRiskScore(riskScore);
            if (behavior != MicroBehavior.SMOOTH_DRIVING) {
                stats.incrementBehaviors();
            }
            return stats;
        });
    }

    public void addRecentEvent(RecentEvent event) {
        recentEvents.addFirst(event);
        // Trim to max size
        while (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.removeLast();
        }
    }

    public void setEventsPerSecond(double eps) {
        this.currentEventsPerSecond = eps;
    }

    public StatsSnapshot getSnapshot() {
        long uptime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        return new StatsSnapshot(
                totalEventsReceived.get(),
                totalEventsProcessed.get(),
                totalBehaviorsDetected.get(),
                totalVehicleEventsEmitted.get(),
                potentialAccidents.get(),
                harshBrakingEvents.get(),
                speedingEvents.get(),
                aggressiveCorneringEvents.get(),
                driverStats.size(),
                currentEventsPerSecond,
                uptime,
                List.copyOf(recentEvents),
                getTopRiskDrivers(5)
        );
    }

    private List<DriverStats> getTopRiskDrivers(int limit) {
        return driverStats.values().stream()
                .sorted((a, b) -> Double.compare(b.getAverageRiskScore(), a.getAverageRiskScore()))
                .limit(limit)
                .toList();
    }

    public void reset() {
        totalEventsReceived.set(0);
        totalEventsProcessed.set(0);
        totalBehaviorsDetected.set(0);
        totalVehicleEventsEmitted.set(0);
        potentialAccidents.set(0);
        harshBrakingEvents.set(0);
        speedingEvents.set(0);
        aggressiveCorneringEvents.set(0);
        driverStats.clear();
        recentEvents.clear();
        startTime = Instant.now();
    }

    public record StatsSnapshot(
            long eventsReceived,
            long eventsProcessed,
            long behaviorsDetected,
            long vehicleEventsEmitted,
            long potentialAccidents,
            long harshBrakingEvents,
            long speedingEvents,
            long aggressiveCorneringEvents,
            int activeDrivers,
            double eventsPerSecond,
            long uptimeMs,
            List<RecentEvent> recentEvents,
            List<DriverStats> topRiskDrivers
    ) {}

    public static class DriverStats {
        private final String driverId;
        private long eventCount = 0;
        private long behaviorCount = 0;
        private double totalRiskScore = 0.0;

        public DriverStats(String driverId) {
            this.driverId = driverId;
        }

        public void incrementEvents() {
            eventCount++;
        }

        public void incrementBehaviors() {
            behaviorCount++;
        }

        public void updateRiskScore(double score) {
            totalRiskScore += score;
        }

        public String getDriverId() {
            return driverId;
        }

        public long getEventCount() {
            return eventCount;
        }

        public long getBehaviorCount() {
            return behaviorCount;
        }

        public double getAverageRiskScore() {
            return eventCount > 0 ? totalRiskScore / eventCount : 0.0;
        }
    }

    public record RecentEvent(
            Instant timestamp,
            String driverId,
            String vehicleId,
            String behaviorType,
            String severity,
            double riskScore
    ) {}
}
