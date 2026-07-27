package com.github.fanziyun.data

import com.github.fanziyun.util.ChangelogType
import com.github.fanziyun.util.ColorUtil
import com.google.gson.annotations.SerializedName

data class ChangelogEntry(
    @SerializedName("version") private val rawVersion: String? = null,
    @SerializedName("date") private val rawDate: String? = null,
    @SerializedName("title") private val rawTitle: String? = null,
    @SerializedName("type") private val rawType: List<String>? = null,
    @SerializedName("tags") private val rawTags: List<String>? = null,
    @SerializedName("color") private val rawColor: String? = null,
    @SerializedName("changes") private val rawChanges: List<String>? = null,
) {
    val version: String get() = rawVersion.orEmpty()
    val date: String get() = rawDate.orEmpty()
    val title: String get() = rawTitle.orEmpty()
    val tags: List<String> get() = rawTags.orEmpty()
    val changes: List<String> get() = rawChanges.orEmpty()

    val types: List<String>
        get() = rawType?.takeIf { it.isNotEmpty() } ?: listOf(ChangelogType.DEFAULT.id)

    val color: Int get() = ColorUtil.parseColor(rawColor.orEmpty())

    val primaryType: ChangelogType get() = ChangelogType.of(types.first()) ?: ChangelogType.DEFAULT
}
