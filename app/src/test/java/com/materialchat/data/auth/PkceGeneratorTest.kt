package com.materialchat.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PkceGenerator constants.
 *
 * Note: The actual generation methods (generateCodeVerifier, generateCodeChallenge, generateState)
 * cannot be unit tested without Android runtime because they use Android's Base64 class.
 * These should be tested in Android instrumented tests instead.
 *
 * Why PKCE matters:
 * PKCE (Proof Key for Code Exchange) protects mobile OAuth flows from
 * authorization code interception attacks. The verifier is a random secret
 * kept on the device; the challenge (hash of verifier) is sent to the
 * authorization server. On callback, the verifier proves we're the same
 * client that initiated the flow.
 */
class PkceGeneratorTest {

    // ============================================================================
    // Constants Tests
    // ============================================================================

    @Test
    fun `CODE_CHALLENGE_METHOD constant - is S256`() {
        assertEquals("S256", PkceGenerator.CODE_CHALLENGE_METHOD)
    }

    @Test
    fun `PkceGenerator - can be instantiated`() {
        val generator = PkceGenerator()
        // Just verify it doesn't throw
        assertTrue(generator != null)
    }
}
