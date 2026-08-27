package com.areina.tenantlab.support;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Signs real JWT Bearer tokens in tests, using the test-only RSA private key that matches the
 * public key the application verifies with. The private key lives exclusively under
 * {@code src/test/resources}; production configuration only knows the public key.
 */
public final class TestJwtFactory {

    private static final RSAPrivateKey PRIVATE_KEY = readPrivateKey();
    private static final KeyPair TAMPERING_KEY = generateFreshKeyPair();

    private TestJwtFactory() {
    }

    /** ana, tenant-a, EDITOR, documents:read, valid for five minutes. */
    public static String anaEditor() {
        return builder().build();
    }

    /** bob, tenant-b, EDITOR, documents:read, valid for five minutes. */
    public static String bobEditor() {
        return builder().subject("bob").tenantId(TestData.TENANT_B).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String subject = "ana";
        private String tenantId = TestData.TENANT_A;
        private List<String> roles = List.of("EDITOR");
        private String scope = "documents:read";
        private String audience = TestData.AUDIENCE;
        private String issuer = TestData.ISSUER;
        private Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        private boolean signWithDifferentKey;
        private boolean includeTenantClaim = true;

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder roles(List<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        /** Sign with a different key so the signature does not verify against the app's public key. */
        public Builder signWithDifferentKey() {
            this.signWithDifferentKey = true;
            return this;
        }

        /** Omit the tenant_id claim to exercise the fail-closed converter. */
        public Builder withoutTenantClaim() {
            this.includeTenantClaim = false;
            return this;
        }

        public String build() {
            try {
                JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString());
                if (roles != null && !roles.isEmpty()) {
                    claims.claim("roles", roles);
                }
                if (scope != null && !scope.isBlank()) {
                    claims.claim("scope", scope);
                }
                if (includeTenantClaim) {
                    claims.claim("tenant_id", tenantId);
                }
                JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("lab-test-key").build();
                SignedJWT signedJwt = new SignedJWT(header, claims.build());
                signedJwt.sign(new RSASSASigner(signWithDifferentKey ? TAMPERING_KEY.getPrivate() : PRIVATE_KEY));
                return signedJwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException("Could not sign test JWT", e);
            }
        }
    }

    private static RSAPrivateKey readPrivateKey() {
        try (InputStream in = TestJwtFactory.class.getResourceAsStream("/keys/jwt-issuer-private.pem")) {
            if (in == null) {
                throw new IllegalStateException("Missing test private key /keys/jwt-issuer-private.pem");
            }
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
            String base64 = pem
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the test private key", e);
        }
    }

    private static KeyPair generateFreshKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate the tampering key", e);
        }
    }
}
