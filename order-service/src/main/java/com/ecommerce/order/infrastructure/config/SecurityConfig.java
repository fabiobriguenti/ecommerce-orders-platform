package com.ecommerce.order.infrastructure.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode;

import com.ecommerce.order.infrastructure.web.security.SecurityProblemSupport;

/**
 * Reactive security (ADR-15). The service is a stateless OAuth2 Resource Server: it validates
 * RSA-signed JWTs against the public key configured in {@code application.yml} and authorizes each
 * route by OAuth2 scope ({@code orders:read/write}, {@code payments:read/write}).
 *
 * <p>Scopes from the {@code scope}/{@code scp} claim are exposed as {@code SCOPE_*} authorities by
 * Spring's default JWT converter. Public surface: health/metrics, OpenAPI/Swagger and the dev-only
 * token endpoint. Errors are rendered as RFC 7807 by {@link SecurityProblemSupport}.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String SCOPE_ORDERS_READ = "SCOPE_orders:read";
    private static final String SCOPE_ORDERS_WRITE = "SCOPE_orders:write";
    private static final String SCOPE_PAYMENTS_READ = "SCOPE_payments:read";
    private static final String SCOPE_PAYMENTS_WRITE = "SCOPE_payments:write";

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**",
            "/api/v1/auth/token"
    };

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, SecurityProblemSupport problemSupport) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "script-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                        + "object-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.mode(Mode.DENY))
                        .hsts(hsts -> hsts.includeSubdomains(true).maxAge(Duration.ofDays(365))))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        // Orders
                        .pathMatchers(HttpMethod.GET, "/api/v1/orders/**").hasAuthority(SCOPE_ORDERS_READ)
                        .pathMatchers(HttpMethod.POST, "/api/v1/orders/**").hasAuthority(SCOPE_ORDERS_WRITE)
                        .pathMatchers(HttpMethod.DELETE, "/api/v1/orders/**").hasAuthority(SCOPE_ORDERS_WRITE)
                        // Payments
                        .pathMatchers(HttpMethod.GET, "/api/v1/payments/**").hasAuthority(SCOPE_PAYMENTS_READ)
                        .pathMatchers(HttpMethod.POST, "/api/v1/payments/**").hasAuthority(SCOPE_PAYMENTS_WRITE)
                        .anyExchange().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport)
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}
