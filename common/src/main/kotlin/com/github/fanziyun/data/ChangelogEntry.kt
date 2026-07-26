package com.github.fanziyun.data

import com.github.fanziyun.util.ChangelogType
import com.github.fanziyun.util.ColorUtil
import com.google.gson.annotations.SerializedName

/**
 * 单条更新日志。
 *
 * Gson 反序列化时通过 Unsafe 创建实例并直接写字段，既不会执行 Kotlin 构造函数，
 * 也不会应用默认值，因此非空类型同样可能拿到 null。所以映射字段一律声明为
 * 可空且私有，对外只暴露规范化后的非空属性。
 */
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

    /** 更新类型，未指定时回落到 [ChangelogType.DEFAULT]。 */
    val types: List<String>
        get() = rawType?.takeIf { it.isNotEmpty() } ?: listOf(ChangelogType.DEFAULT.id)

    /** 条目左侧色条的 ARGB 颜色。 */
    val color: Int get() = ColorUtil.parseColor(rawColor.orEmpty())

    val primaryType: ChangelogType get() = ChangelogType.of(types.first()) ?: ChangelogType.DEFAULT
}
