package com.flashsale.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * Required by the RequestRateLimiter filter configured in application.yml.
     * Rate-limits per client IP. Swap for a per-user-id resolver once auth is added,
     * since IP-based limiting is trivially bypassed and is only a placeholder here.
     *
     * Routes themselves live only in application.yml — do not also define a
     * RouteLocator bean here. A prior version of this file did, using lb://
     * (load-balanced/service-discovery) URIs, which cannot resolve without a
     * discovery client (Eureka/Consul/k8s service discovery) configured. That
     * caused every request through the gateway to fail with 503 Service
     * Unavailable even though the target services were healthy.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown"
        );
    }
}

// postgres: 71e7a0c6-3309-49ac-bc44-562067b7ad1e
//
//reser: ea2d36db-2745-4d64-88b2-d2ccdf87ca25
