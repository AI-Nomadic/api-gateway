package com.hassan.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final GatewayProperties gatewayProperties;

    /**
     * WebClient.Builder bean — required so Spring can auto-inject it into
     * ReverseProxy.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * CORS OPTIONS pre-flight — must run at highest priority (before JWT filter).
     */
    @Bean
    @Order(-2)
    public WebFilter corsPreflightFilter() {
        return (exchange, chain) -> {
            String origin = exchange.getRequest().getHeaders().getOrigin();
            List<String> allowedOrigins = gatewayProperties.getCors().getAllowedOrigins();
            
            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                HttpHeaders h = exchange.getResponse().getHeaders();
                if (origin != null && allowedOrigins.contains(origin)) {
                    h.add("Access-Control-Allow-Origin", origin);
                }
                h.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
                h.add("Access-Control-Allow-Headers", "*");
                h.add("Access-Control-Allow-Credentials", "true");
                h.add("Access-Control-Max-Age", "3600");
                exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                return exchange.getResponse().setComplete();
            }
            // Add CORS response headers to all real requests
            if (origin != null && allowedOrigins.contains(origin)) {
                exchange.getResponse().getHeaders().add("Access-Control-Allow-Origin", origin);
                exchange.getResponse().getHeaders().add("Access-Control-Allow-Credentials", "true");
            }
            return chain.filter(exchange);
        };
    }

    /**
     * Proxy filter — runs last, forwards the request to the correct downstream
     * service.
     */
    @Bean
    @Order(1)
    public WebFilter proxyFilter(com.hassan.gateway.proxy.ReverseProxy proxy) {
        return (exchange, chain) -> proxy.forward(exchange);
    }

    /**
     * CorsConfigurationSource bean (used by Spring WebFlux security if needed).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(gatewayProperties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
