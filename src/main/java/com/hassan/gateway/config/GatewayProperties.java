package com.hassan.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private Routes routes = new Routes();
    private Cors cors = new Cors();

    @Data
    public static class Routes {
        private String authService;
        private String tripService;
        private String collabService;
        private String aiPlanner;
    }

    @Data
    public static class Cors {
        private java.util.List<String> allowedOrigins = java.util.List.of("http://localhost:5173");
    }
}
