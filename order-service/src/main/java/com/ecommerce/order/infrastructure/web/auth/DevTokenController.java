package com.ecommerce.order.infrastructure.web.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only helper that mints RSA-signed JWTs so the secured API can be exercised without an external
 * identity provider (the challenge allows mocked auth). It signs with the same private key whose
 * public half the resource server uses to validate tokens. Disable in production via
 * {@code app.security.dev-token.enabled=false}.
 *
 * <p>Inputs are query parameters (no request body), so a plain {@code POST} with no payload works:
 * {@code POST /api/v1/auth/token?scope=orders:read&scope=orders:write&subject=qa&ttl=1800}.
 */
@RestController
@RequestMapping("/api/v1/auth/token")
@ConditionalOnProperty(name = "app.security.dev-token.enabled", havingValue = "true", matchIfMissing = true)
public class DevTokenController {

    private static final List<String> ALL_SCOPES =
            List.of("orders:read", "orders:write", "payments:read", "payments:write");
    private static final long DEFAULT_TTL_SECONDS = 3600;

    private final RSASSASigner signer;
    private final String issuer;

    public DevTokenController(@Value("classpath:keys/private.pem") Resource privateKeyPem,
                              @Value("${app.security.jwt.issuer:order-service}") String issuer) {
        this.signer = new RSASSASigner(loadPrivateKey(privateKeyPem));
        this.issuer = issuer;
    }

    @PostMapping
    public TokenResponse issue(@RequestParam(name = "subject", required = false) String subjectParam,
                               @RequestParam(name = "scope", required = false) List<String> scopeParam,
                               @RequestParam(name = "ttl", required = false) Long ttlParam) {
        String subject = (subjectParam == null || subjectParam.isBlank()) ? "demo-user" : subjectParam;
        List<String> scopes = (scopeParam == null || scopeParam.isEmpty()) ? ALL_SCOPES : scopeParam;
        long ttl = (ttlParam == null || ttlParam <= 0) ? DEFAULT_TTL_SECONDS : ttlParam;

        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttl)))
                .claim("scope", String.join(" ", scopes))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign development JWT", e);
        }
        return new TokenResponse(jwt.serialize(), "Bearer", ttl, scopes);
    }

    private static RSAPrivateKey loadPrivateKey(Resource pem) {
        try (InputStream in = pem.getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(content);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Unable to load RSA private key for dev token signing", e);
        }
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, List<String> scopes) {
    }
}
