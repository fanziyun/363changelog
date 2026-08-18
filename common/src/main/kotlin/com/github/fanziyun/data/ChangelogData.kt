package com.github.fanziyun.data

import com.google.gson.annotations.SerializedName

/**
 * 更新日志文件的顶层结构。可空私有字段 + 非空属性的原因见 [ChangelogEntry]。
 */
data class ChangelogData(
    @SerializedName("footer") private val rawFooter: String? = null,
    @SerializedName("tagColors") private val rawTagColors: Map<String, String>? = null,
    @SerializedName("entries") private val rawEntries: List<ChangelogEntry>? = null,
) {
    val footer: String get() = rawFooter.orEmpty()
    val tagColors: Map<String, String> get() = rawTagColors.orEmpty()
    val entries: List<ChangelogEntry> get() = rawEntries.orEmpty()

    /**
     * 没有任何可展示的条目。
     *
     * 判空要看条目而不是 `== EMPTY`：`{"footer": "..."}` 这种只有页脚的文档结构上不等于
     * [EMPTY]，但界面上同样什么都没有。加载器用它判断"手上还有没有值得保留的数据"。
     */
    val isEmpty: Boolean get() = entries.isEmpty()

    companion object {
        val EMPTY = ChangelogData()
    }
}
