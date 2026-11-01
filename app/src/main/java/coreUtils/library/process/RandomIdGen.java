package coreUtils.library.process;

import java.security.SecureRandom;

/**
 * Utility class for generating cryptographically secure random identifiers.
 * This class uses {@link SecureRandom} to produce random strings suitable for
 * use as video IDs, request tokens, or temporary identifiers where uniqueness
 * and unpredictability are important.
 *
 * <p><strong>Character set:</strong>
 * The generated IDs use a URL-safe character set consisting of:
 * <ul>
 * <li>Uppercase letters A-Z (26 characters).</li>
 * <li>Lowercase letters a-z (26 characters).</li>
 * <li>Digits 0-9 (10 characters).</li>
 * <li>Hyphen (-) and underscore (_) (2 characters).</li>
 * </ul>
 *
 * <p><strong>Total possible combinations:</strong>
 * With 64 characters and 11 positions, there are 64^11 ≈ 2^66 possible IDs,
 * making collisions extremely unlikely for typical usage scenarios.
 *
 * <p>This class cannot be instantiated; all methods are static.
 *
 * @see SecureRandom
 */
public final class RandomIdGen {
    private static final String CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    "abcdefghijklmnopqrstuvwxyz" +
                    "0123456789-_";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a cryptographically secure random video ID of 11 characters.
     * The generated ID uses a URL-safe character set (A-Z, a-z, 0-9, -, _)
     * and is suitable for use in URLs, API requests, or database keys where
     * uniqueness and unpredictability are desired.
     *
     * <p>This method is thread-safe because {@link SecureRandom} is thread-safe.
     *
     * @return A randomly generated 11-character string.
     */
    public static String generateRandomVideoId() {
        StringBuilder sb = new StringBuilder(11);

        for (int i = 0; i < 11; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        return sb.toString();
    }
}