package com.github.fanziyun.data

import com.github.fanziyun.util.ChangelogType
import com.github.fanziyun.util.ColorUtil
import com.google.gson.annotations.SerializedName

data class ChangelogEntry(
    @SerializedName("version") private val rawVersion: String? = null,
    @SerializedName("date") private val rawDate: String? = null,
    @SerializedName("title") private val rawTitle: String? = null,
    @SerializedName("type") private val rawType: List<String?>? = null,
    @SerializedName("tags") private val rawTags: List<String?>? = null,
    @SerializedName("color") private val rawColor: String? = null,
    @SerializedName("changes") private val rawChanges: List<String?>? = null,
) {
    val version: String get() = rawVersion?.trim().orEmpty()
    val date: String get() = rawDate?.trim().orEmpty()
    val title: String get() = rawTitle?.trim().orEmpty()
    val tags: List<String> get() = rawTags.normalizedStrings()
    val changes: List<String> get() = rawChanges.normalizedStrings(distinct = false)

    val types: List<String>
        get() = rawType.normalizedStrings()
            .ifEmpty { listOf(ChangelogType.DEFAULT.id) }

    val color: Int
        get() = ColorUtil.parseColor(rawColor.orEmpty(), ColorUtil.typeColor(types.first()))

    val primaryType: ChangelogType get() = ChangelogType.of(types.first()) ?: ChangelogType.DEFAULT

    private fun List<String?>?.normalizedStrings(distinct: Boolean = true): List<String> {
        val normalized = orEmpty().mapNotNull { value ->
            value?.trim()?.takeIf(String::isNotEmpty)
        }
        return if (distinct) normalized.distinct() else normalized
    }
}
