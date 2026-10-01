package com.zentrox.forge.sso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts IdP client secrets at rest.
 *
 * <p>These cannot be hashed like passwords or API keys: we have to send the actual value to the
 * provider's token endpoint on every login, so the operation must be reversible. That makes this the
 * one place in the system storing recoverable secret material, hence AES-GCM rather than anything
 * homemade.
 *
 * <p><b>GCM, with a fresh random IV per encryption.</b> GCM is authenticated: tampering with stored
 * ciphertext fails decryption rather than silently yielding different plaintext. The IV must never
 * repeat under the same key - with GCM, IV reuse is catastrophic (it leaks the XOR of plaintexts and
 * can expose the authentication key), so it is 12 fresh random bytes each time and is prepended to
 * the ciphertext rather than being fixed or derived.
 *
 * <p>The configured key is hashed to exactly 256 bits so any passphrase length works without
 * silently truncating to something weaker.
 */
@Component
@RequiredArgsConstructor
public class SecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SsoProperties properties;

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // iv || ciphertext, so decryption needs nothing but the stored value and the key.
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encrypt secret", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[raw.length - IV_LENGTH];
            ByteBuffer.wrap(raw).get(iv).get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Includes the tampering case: GCM authentication failure lands here.
            throw new IllegalStateException("Cannot decrypt secret - wrong key, or the value was altered", e);
        }
    }

    private SecretKeySpec key() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(properties.encryptionKey().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
