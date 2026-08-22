package com.github.fanziyun.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FeedbackEndpointTest {
    private fun endpoint(baseUrl: String = "https://api.github.com/", repo: String = "owner/repo") = FeedbackEndpoint(
        displayName = "Test",
        baseUrl = baseUrl,
        repo = repo,
        oauthClientId = "client",
        oauthClientSecret = "",
        deviceCodeUrl = "https://github.com/login/device/code",
        authorizationUrl = "https://github.com/login/oauth/authorize",
        tokenUrl = "https://github.com/login/oauth/access_token",
    )

    @Test
    fun `builds issue URL from normalized API root`() {
        assertEquals("https://api.github.com/repos/owner/repo/issues", endpoint().issueUrl())
        assertEquals("https://git.example.com/api/v3/repos/owner/repo/issues", endpoint("https://git.example.com/api/v3").issueUrl())
    }

    @Test
    fun `rejects invalid API roots and repositories`() {
        assertFailsWith<IllegalArgumentException> { endpoint("ftp://example.com").issueUrl() }
        assertFailsWith<IllegalArgumentException> { endpoint(repo = "owner-only").issueUrl() }
    }

    @Test
    fun `storage keys isolate endpoints`() {
        val first = endpoint("https://api.github.com", "owner/one").storageKey()
        val second = endpoint("https://api.github.com", "owner/two").storageKey()
        assert(first != second)
    }
}
