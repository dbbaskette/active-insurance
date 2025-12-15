package com.insurancemegacorp.sense.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

    private final DashboardStats stats;
    private final DashboardWebSocketHandler webSocketHandler;

    public DashboardController(DashboardStats stats, DashboardWebSocketHandler webSocketHandler) {
        this.stats = stats;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public DashboardStats.StatsSnapshot getStats() {
        return stats.getSnapshot();
    }

    @GetMapping("/api/stats/reset")
    @ResponseBody
    public String resetStats() {
        stats.reset();
        return "{\"status\": \"reset\"}";
    }
}
