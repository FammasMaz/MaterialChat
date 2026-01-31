package com.materialchat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for OAuth domain models.
 *
 * Tests cover:
 * - OAuthState sealed class
 * - OAuthTokens data class
 * - AuthType enum
 */
class OAuthModelsTest {

    // ============================================================================
    // OAuthState Tests
    // ============================================================================

    @Test
    fun `OAuthState Unauthenticated - is distinct state`() {
        val state = OAuthState.Unauthenticated

        assertTrue(state is OAuthState.Unauthenticated)
        assertFalse(state is OAuthState.Authenticated)
    }

    @Test
    fun `OAuthState Authenticating - is distinct state`() {
        val state = OAuthState.Authenticating

        assertTrue(state is OAuthState.Authenticating)
        assertFalse(state is OAuthState.Authenticated)
    }

    @Test
    fun `OAuthState Authenticated - holds email and expiry`() {
        val expiresAt = System.currentTimeMillis() + 3600_000 // 1 hour from now
        val state = OAuthState.Authenticated(
            email = "user@example.com",
            expiresAt = expiresAt
        )

        assertTrue(state is OAuthState.Authenticated)
        assertEquals("user@example.com", state.email)
        assertEquals(expiresAt, state.expiresAt)
    }

    @Test
    fun `OAuthState Error - holds error message`() {
        val state = OAuthState.Error(message = "Authentication failed")

        assertTrue(state is OAuthState.Error)
        assertEquals("Authentication failed", state.message)
    }

    // ============================================================================
    // OAuthTokens Tests
    // ============================================================================

    @Test
    fun `OAuthTokens - isExpired returns true when past expiry`() {
        val tokens = OAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = System.currentTimeMillis() - 1000, // 1 second ago
            email = "user@example.com"
        )

        assertTrue(tokens.isExpired())
    }

    @Test
    fun `OAuthTokens - isExpired returns false when not expired`() {
        val tokens = OAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = System.currentTimeMillis() + 3600_000, // 1 hour from now
            email = "user@example.com"
        )

        assertFalse(tokens.isExpired())
    }

    @Test
    fun `OAuthTokens - needsRefresh returns true when close to expiry`() {
        val tokens = OAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = System.currentTimeMillis() + 60_000, // 1 minute from now
            email = "user@example.com"
        )

        // Default buffer is 5 minutes, so 1 minute left should trigger refresh
        assertTrue(tokens.needsRefresh())
    }

    @Test
    fun `OAuthTokens - needsRefresh returns false when plenty of time left`() {
        val tokens = OAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = System.currentTimeMillis() + 3600_000, // 1 hour from now
            email = "user@example.com"
        )

        assertFalse(tokens.needsRefresh())
    }

    @Test
    fun `OAuthTokens - email can be null`() {
        val tokens = OAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAt = System.currentTimeMillis() + 3600_000,
            email = null
        )

        assertNull(tokens.email)
    }

    // ============================================================================
    // AuthType Tests
    // ============================================================================

    @Test
    fun `AuthType - all values exist`() {
        val values = AuthType.entries

        assertEquals(3, values.size)
        assertTrue(values.contains(AuthType.NONE))
        assertTrue(values.contains(AuthType.API_KEY))
        assertTrue(values.contains(AuthType.OAUTH))
    }

    @Test
    fun `AuthType - valueOf works correctly`() {
        assertEquals(AuthType.NONE, AuthType.valueOf("NONE"))
        assertEquals(AuthType.API_KEY, AuthType.valueOf("API_KEY"))
        assertEquals(AuthType.OAUTH, AuthType.valueOf("OAUTH"))
    }
}
