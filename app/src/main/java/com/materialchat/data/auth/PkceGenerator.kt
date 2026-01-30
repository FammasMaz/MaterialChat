package com.materialchat.data.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generator for PKCE (Proof Key for Code Exchange) parameters.
 *
 * Implements RFC 7636 for secure OAuth 2.0 authorization code flow
 * on public clients (like mobile apps) that cannot securely store client secrets.
 *
 * PKCE prevents authorization code interception attacks by:
 * 1. Generating a random code_verifier
 * 2. Creating a code_challenge from the verifier (SHA-256 hash)
 * 3. Sending the challenge during authorization
 * 4. Sending the verifier during token exchange
 * 5. Server verifies that hash(verifier) == challenge
 */
@Singleton
class PkceGenerator @Inject constructor() {

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically random code verifier.
     *
     * Per RFC 7636:
     * - Must be between 43-128 characters
     * - Must use unreserved characters: [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~"
     * - Should have at least 256 bits of entropy
     *
     * This implementation uses 64 bytes (512 bits) of random data,
     * base64url encoded without padding.
     *
     * @return A random code verifier string
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(VERIFIER_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    /**
     * Generates a code challenge from a code verifier using SHA-256.
     *
     * Per RFC 7636:
     * - code_challenge = BASE64URL(SHA256(code_verifier))
     * - Uses S256 transformation method (required for security)
     *
     * @param verifier The code verifier to transform
     * @return The code challenge (SHA-256 hash, base64url encoded)
     */
    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return base64UrlEncode(hash)
    }

    /**
     * Generates a random state/nonce parameter for OAuth requests.
     *
     * The state parameter prevents CSRF attacks and is returned
     * unchanged by the authorization server in the callback.
     *
     * @return A random state string
     */
    fun generateState(): String {
        val bytes = ByteArray(STATE_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    /**
     * Encodes bytes to base64url format without padding.
     *
     * Per RFC 7636 and RFC 4648:
     * - Uses URL-safe alphabet (+ → -, / → _)
     * - No padding (= characters removed)
     */
    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    companion object {
        /**
         * Number of random bytes for code verifier (64 bytes = 512 bits entropy).
         * After base64url encoding: ~86 characters (within 43-128 limit).
         */
        private const val VERIFIER_BYTE_LENGTH = 64

        /**
         * Number of random bytes for state parameter (32 bytes = 256 bits).
         */
        private const val STATE_BYTE_LENGTH = 32

        /**
         * The code challenge method to use in authorization requests.
         */
        const val CODE_CHALLENGE_METHOD = "S256"
    }
}
