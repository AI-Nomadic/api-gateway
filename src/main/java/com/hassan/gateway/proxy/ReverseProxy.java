package com.hassan.gateway.proxy;

import com.hassan.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Reverse proxy that forwards requests to the correct downstream service
 * based on the request path prefix.
 *
 * /auth/** → auth-service
 * /api/** → trip-service
 * /collab/** → collaboration-service
 */
@Slf4j
@Component
public class ReverseProxy {

    private final GatewayProperties gatewayProperties;
    private final WebClient webClient;

    public ReverseProxy(WebClient.Builder builder, GatewayProperties gatewayProperties) {
        this.webClient = builder.build();
        this.gatewayProperties = gatewayProperties;
    }

    public Mono<Void> forward(ServerWebExchange exchange) {
        GatewayProperties.Routes routes = gatewayProperties.getRoutes();
        String authServiceUrl = routes.getAuthService();
        String tripServiceUrl = routes.getTripService();
        String collabServiceUrl = routes.getCollabService();
        String aiPlannerUrl = routes.getAiPlanner();

        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getPath();
        String query = req.getURI().getRawQuery();
        HttpMethod method = req.getMethod();

        // Determine target base URL
        String targetBase;
        if (path.startsWith("/auth")) {
            targetBase = authServiceUrl;
        } else if (path.startsWith("/api/planner")) {
            targetBase = aiPlannerUrl;
        } else if (path.startsWith("/api")) {
            targetBase = tripServiceUrl;
        } else if (path.startsWith("/collab")) {
            targetBase = collabServiceUrl;
        } else {
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        String targetUri = targetBase + path + (query != null ? "?" + query : "");
        log.info("--- [API GATEWAY] Proxying {} to {} ---", method, targetUri);

        // Forward all headers (including the trusted X-User-* ones added by
        // JwtAuthFilter)
        HttpHeaders forwardHeaders = new HttpHeaders();
        forwardHeaders.addAll(req.getHeaders());
        forwardHeaders.remove(HttpHeaders.HOST); // remove browser Host header

        return webClient.method(method)
                .uri(targetUri)
                .headers(h -> h.addAll(forwardHeaders))
                .body(req.getBody(), org.springframework.core.io.buffer.DataBuffer.class)
                .exchangeToMono(clientResponse -> {
                    exchange.getResponse().setStatusCode(clientResponse.statusCode());

                    // Copy downstream response headers, but SKIP CORS headers.
                    // The gateway's corsPreflightFilter already sets Access-Control-*
                    // headers — forwarding them again causes duplicates that browsers reject.
                    HttpHeaders downstreamHeaders = clientResponse.headers().asHttpHeaders();
                    downstreamHeaders.forEach((name, values) -> {
                        if (!name.toLowerCase().startsWith("access-control-")) {
                            exchange.getResponse().getHeaders().addAll(name, values);
                        }
                    });

                    return exchange.getResponse().writeWith(clientResponse.bodyToFlux(
                            org.springframework.core.io.buffer.DataBuffer.class));
                });
    }
}
