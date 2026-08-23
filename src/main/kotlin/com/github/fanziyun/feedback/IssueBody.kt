package com.github.fanziyun.feedback

object IssueBody {
    fun build(content: String, playerName: String, version: String, contact: String): String = buildString {
        append(content.trim())
        append("\n\n---")
        if (version.isNotBlank()) append("\nVersion: ").append(version)
        append("\nForm_Player: ").append(playerName)
        append("\nContact Info: ").append(contact.ifBlank { "未提供" })
    }
}
