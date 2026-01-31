package com.materialchat.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for PkceState.
 *
 * Tests cover:
 * - State creation
 * - Expiration logic
 * - State validation
 *
 * Why PKCE state expiration matters:
 * PKCE sessions should have a limited lifetime to prevent stale authorization
 * requests. If a user starts an OAuth flow but never completes it, the PKCE
 * session should eventually expire to prevent memory leaks and potential
 * security issues with old verifiers.
 */
class PkceStateTest {

    // ============================================================================
    // Creation Tests
    // ============================================================================

    @Test
    fun `PkceState - stores codeVerifier and state correctly`() {
        val state = PkceState(
            codeVerifier = "test-verifier",
            state = "state-123",
            providerId = "provider-1"
        )

        assertEquals("test-verifier", state.codeVerifier)
        assertEquals("state-123", state.state)
        assertEquals("provider-1", state.providerId)
    }

    @Test
    fun `PkceState - optional projectId defaults to null`() {
        val state = PkceState(
            codeVerifier = "verifier",
            state = "state",
            providerId = "provider"
        )

        assertEquals(null, state.projectId)
    }

    @Test
    fun `PkceState - createdAt is set automatically`() {
        val before = System.currentTimeMillis()
        val state = PkceState(
            codeVerifier = "verifier",
            state = "state",
            providerId = "provider"
        )
        val after = System.currentTimeMillis()

        assertTrue(state.createdAt >= before)
        assertTrue(state.createdAt <= after)
    }

    // ============================================================================
    // Expiration Tests
    // ============================================================================

    @Test
    fun `isExpired - returns false when not expired`() {
        val state = PkceState(
            codeVerifier = "verifier",
            state = "state",
            providerId = "provider"
        )

        // Just created, should not be expired
        assertFalse(state.isExpired())
    }

    @Test
    fun `isExpired - returns true when past maxAge`() {
        val state = PkceState(
            codeVerifier = "verifier",
            state = "state",
            providerId = "provider",
            createdAt = System.currentTimeMillis() - 700_000 // 11+ minutes ago
        )

        // Default max age is 10 minutes (600,000 ms)
        assertTrue(state.isExpired())
    }

    @Test
    fun `isExpired - respects custom maxAge`() {
        val state = PkceState(
            codeVerifier = "verifier",
            state = "state",
            providerId = "provider",
            createdAt = System.currentTimeMillis() - 5000 // 5 seconds ago
        )

        // Not expired with default 10 minutes
        assertFalse(state.isExpired())

        // But expired with 1 second max age
        assertTrue(state.isExpired(maxAgeMs = 1000))
    }

    // ============================================================================
    // State Validation Tests
    // ============================================================================

    @Test
    fun `validateState - returns true for matching state`() {
        val pkceState = PkceState(
            codeVerifier = "verifier",
            state = "state-123",
            providerId = "provider"
        )

        assertTrue(pkceState.validateState("state-123"))
    }

    @Test
    fun `validateState - returns false for non-matching state`() {
        val pkceState = PkceState(
            codeVerifier = "verifier",
            state = "state-123",
            providerId = "provider"
        )

        assertFalse(pkceState.validateState("different-state"))
    }

    @Test
    fun `validateState - is case sensitive`() {
        val pkceState = PkceState(
            codeVerifier = "verifier",
            state = "State-123",
            providerId = "provider"
        )

        assertFalse(pkceState.validateState("state-123"))
    }

    // ============================================================================
    // Constants Tests
    // ============================================================================

    @Test
    fun `DEFAULT_MAX_AGE_MS - is 10 minutes`() {
        assertEquals(600_000L, PkceState.DEFAULT_MAX_AGE_MS)
    }
}
