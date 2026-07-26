package com.github.fanziyun.util

import net.minecraft.client.gui.components.AbstractWidget

/**
 * 为 mixin 追加到原版界面的按钮挑一个不与现有控件重叠的 Y。
 *
 * 写死坐标挡不住原版布局变动，也挡不住其他模组往同一个界面加按钮，
 * 所以统一按界面里控件的实际位置来算；标题界面和暂停菜单共用这一份逻辑。
 */
object ButtonPlacement {

    const val BUTTON_HEIGHT = 20
    private const val BUTTON_GAP = 4
    private const val EDGE_MARGIN = 2

    /**
     * 把按钮（水平区间 [left] 到 [right]）排在现有控件列的下方；
     * 底部塞不下时（小窗口 / 大 GUI 缩放）退到控件列上方。
     *
     * 只统计水平方向和按钮有交集的控件——像标题界面右下角的版权信息
     * 不在按钮那一列里，不该把按钮往下挤。
     *
     * @return 挑好的 Y；界面里没有可参照的控件时返回 null，兜底位置由调用方决定
     */
    fun belowExistingColumn(children: List<*>, screenHeight: Int, left: Int, right: Int): Int? {
        val inColumn = children
            .filterIsInstance<AbstractWidget>()
            .filter { it.visible && it.x < right && it.x + it.width > left }
        if (inColumn.isEmpty()) return null

        val below = inColumn.maxOf { it.y + it.height } + BUTTON_GAP
        if (below + BUTTON_HEIGHT <= screenHeight - EDGE_MARGIN) return below

        val above = inColumn.minOf { it.y } - BUTTON_HEIGHT - BUTTON_GAP
        return above.coerceAtLeast(EDGE_MARGIN)
    }
}
