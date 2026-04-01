package com.hassan.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT verification WebFilter for the reactive (WebFlux) gateway.
 *
 * - Public paths (/auth/login, /auth/register, /auth/google) are bypassed.
 * - All other paths require a valid Bearer JWT.
 * - On success, adds X-User-Id, X-User-Email, X-User-Name headers for
 * downstream services.
 */
@Slf4j
@Component
@Order(-1) // Runs after corsPreflightFilter(-2) but BEFORE proxyFilter(1)
public class JwtAuthFilter implements WebFilter {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/google",
            "/api/planner");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Pass through public auth routes
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // Require Authorization header for all other routes
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String name = claims.get("name", String.class);

            // Mutate request to add trusted internal headers
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r
                            .header("X-User-Id", userId)
                            .header("X-User-Email", email != null ? email : "")
                            .header("X-User-Name", name != null ? name : ""))
                    .build();

            return chain.filter(mutated);

        } catch (Exception e) {
            log.warn("JWT validation failed for {}: {}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
