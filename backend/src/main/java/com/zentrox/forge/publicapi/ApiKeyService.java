package com.zentrox.forge.publicapi;

import com.zentrox.forge.aop.Audited;
import com.zentrox.forge.billing.RequiresEntitlement;
import com.zentrox.forge.billing.UsageMetric;
import com.zentrox.forge.entity.ApiKey;
import com.zentrox.forge.exception.ConflictException;
import com.zentrox.forge.exception.NotFoundException;
import com.zentrox.forge.publicapi.dto.ApiKeyCreateRequest;
import com.zentrox.forge.publicapi.dto.ApiKeyCreatedResponse;
import com.zentrox.forge.publicapi.dto.ApiKeyResponse;
import com.zentrox.forge.repository.ApiKeyLookupRepository;
import com.zentrox.forge.repository.tenant.ApiKeyRepository;
import com.zentrox.forge.security.SecurityUtils;
import com.zentrox.forge.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FRD Section 12 - issues, verifies and revokes API keys.
 *
 * <p><b>Key format:</b> {@code forge_<keyId>_<secret>}. The two halves do different jobs:
 * {@code keyId} is a public identifier that turns verification into one indexed lookup, and
 * {@code secret} is 32 bytes of {@link SecureRandom} that is hashed and never stored. Without the
 * embedded id, authenticating a request would mean hashing the presented secret against every key
 * in the table.
 *
 * <p><b>Shown once.</b> {@link #createKey} is the only time the plaintext exists. There is no
 * endpoint that can retrieve it later, because there is nothing to retrieve - which is the property
 * that makes a leaked database useless for authentication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX = "forge";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;
    private static final int KEY_ID_BYTES = 8;

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyLookupRepository apiKeyLookupRepository;
    private final ApiKeyProperties properties;

    // ------------------------------------------------------------------ management (authenticated)

    @Audited(action = "API_KEY_CREATE", entityType = "ApiKey")
    @RequiresEntitlement(UsageMetric.API_KEYS)
    @Transactional
    public ApiKeyCreatedResponse createKey(ApiKeyCreateRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        if (apiKeyRepository.existsByTenantIdAndName(tenantId, request.name())) {
            throw new ConflictException("An API key named '" + request.name() + "' already exists");
        }
        if (request.scopes() == null || request.scopes().isEmpty()) {
            throw new ConflictException("An API key with no scopes cannot do anything; grant at least one");
        }

        String keyId = randomToken(KEY_ID_BYTES);
        String secret = randomToken(SECRET_BYTES);
        String presentedKey = "%s_%s_%s".formatted(KEY_PREFIX, keyId, secret);

        ApiKey apiKey = ApiKey.builder()
                .tenantId(tenantId)
                .name(request.name())
                .keyId(keyId)
                .keyHash(hash(secret))
                .keySuffix(secret.substring(secret.length() - 4))
                .createdBy(SecurityUtils.currentUserId().orElse(null))
                .expiresAt(request.expiresAt())
                .build();
        apiKey.setScopeSet(request.scopes());

        apiKey = apiKeyRepository.save(apiKey);

        // The only time the plaintext is ever returned.
        return ApiKeyCreatedResponse.of(ApiKeyResponse.from(apiKey), presentedKey);
    }

    public List<ApiKeyResponse> listKeys() {
        UUID tenantId = TenantContext.requireTenantId();
        return apiKeyRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    /**
     * Revocation is a timestamp, not a delete: the audit trail and any investigation into how a
     * leaked key was used both need the row to survive.
     */
    @Audited(action = "API_KEY_REVOKE", entityType = "ApiKey")
    @Transactional
    public ApiKeyResponse revokeKey(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("API key not found: " + id));

        if (apiKey.getRevokedAt() == null) {
            apiKey.setRevokedAt(Instant.now());
            apiKey = apiKeyRepository.save(apiKey);
        }
        return ApiKeyResponse.from(apiKey);
    }

    // ------------------------------------------------------------------ verification (unauthenticated)

    /**
     * Resolves a presented key to its tenant and scopes, or empty if it is malformed, unknown,
     * revoked or expired.
     *
     * Returns one undifferentiated empty for every failure mode on purpose: telling a caller
     * "that key exists but is revoked" confirms a valid key id, which is information an attacker
     * probing for live keys would use.
     *
     * Plain {@code REQUIRED} propagation, deliberately not {@code REQUIRES_NEW}: a new transaction
     * cannot see data the caller's transaction has not committed, so REQUIRES_NEW would fail to
     * authenticate a key created earlier in the same transaction. It also bought nothing - the
     * filter that calls this runs before any transaction exists, so REQUIRED opens one anyway.
     */
    @Transactional
    public Optional<ApiKeyPrincipal> authenticate(String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank()) {
            return Optional.empty();
        }

        String[] parts = presentedKey.trim().split("_");
        if (parts.length != 3 || !KEY_PREFIX.equals(parts[0])) {
            return Optional.empty();
        }
        String keyId = parts[1];
        String secret = parts[2];

        Optional<ApiKey> found = apiKeyLookupRepository.findByKeyId(keyId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ApiKey apiKey = found.get();

        // Constant-time comparison: a plain equals on the hash leaks, through timing, how much of a
        // guessed secret was correct.
        if (!java.security.MessageDigest.isEqual(
                hash(secret).getBytes(StandardCharsets.UTF_8),
                apiKey.getKeyHash().getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        if (!apiKey.isUsable(now)) {
            return Optional.empty();
        }

        // Best-effort last-used tracking: useful for spotting unused or compromised keys. Throttled
        // to at most once a minute so a busy integration does not turn every read into a write.
        if (apiKey.getLastUsedAt() == null || apiKey.getLastUsedAt().isBefore(now.minusSeconds(60))) {
            apiKey.setLastUsedAt(now);
            apiKeyLookupRepository.save(apiKey);
        }

        return Optional.of(new ApiKeyPrincipal(
                apiKey.getId(), apiKey.getTenantId(), apiKey.getName(), apiKey.scopeSet()));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Keyed hash, so a stolen database cannot be attacked offline without also stealing the
     * application's pepper. Falls back to the JWT secret only if no dedicated key is configured -
     * see ApiKeyProperties.
     */
    private String hash(String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.hashingKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash API key", e);
        }
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer).replace("_", "").replace("-", "");
    }

}
