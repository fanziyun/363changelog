package com.github.fanziyun.data

import com.google.gson.annotations.SerializedName

data class ChangelogData(
    @SerializedName("footer") private val rawFooter: String? = null,
    @SerializedName("tagColors") private val rawTagColors: Map<String?, String?>? = null,
    @SerializedName("entries") private val rawEntries: List<ChangelogEntry?>? = null,
) {
    val footer: String get() = rawFooter.orEmpty()
    val tagColors: Map<String, String>
        get() = rawTagColors.orEmpty().mapNotNull { (tag, color) ->
            val normalizedTag = tag?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val normalizedColor = color?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            normalizedTag to normalizedColor
        }.toMap()
    val entries: List<ChangelogEntry> get() = rawEntries.orEmpty().filterNotNull()

    companion object {
        val EMPTY = ChangelogData()
    }
}
