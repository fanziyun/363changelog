package com.github.fanziyun.feedback

import kotlin.test.Test
import kotlin.test.assertEquals

class IssueBodyTest {
    @Test
    fun `appends version player and contact in order`() {
        assertEquals(
            "Details\n\n---\nVersion: 1.2.3\nForm_Player: Steve\nContact Info: test@example.com",
            IssueBody.build(" Details ", "Steve", "1.2.3", "test@example.com"),
        )
    }

    @Test
    fun `always appends missing contact marker`() {
        assertEquals(
            "Details\n\n---\nForm_Player: Alex\nContact Info: 未提供",
            IssueBody.build("Details", "Alex", "", ""),
        )
    }
}
