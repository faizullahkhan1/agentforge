package dev.agentforge.prreviewer.github;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * GitHub signs every webhook with the secret you set in the repo settings:
 * header X-Hub-Signature-256 = "sha256=" + hex(HMAC-SHA256(secret, raw request body)).
 * We compute the same value and compare. If they match, the request really came from GitHub
 * and nobody changed the body on the way. Without this, anyone who finds the URL could
 * trigger reviews and spend your OpenAI credit.
 */
public final class WebhookSignature {

    public static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    public static boolean isValid(String secret, byte[] body, String signatureHeader) {
        if (secret == null || secret.isBlank() || signatureHeader == null) {
            return false;
        }
        byte[] expected = sign(secret, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.getBytes(StandardCharsets.UTF_8);
        // Constant-time comparison: equals() would stop at the first wrong byte,
        // and the timing difference can leak how much of a guessed signature was right.
        return MessageDigest.isEqual(expected, actual);
    }

    private WebhookSignature() {
    }
}
