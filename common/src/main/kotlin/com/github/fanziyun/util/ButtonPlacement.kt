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
     * 只统计"和按钮同属一列"的控件——判定规则见 [isInColumn] 与 [isBottomChrome]。
     *
     * @return 挑好的 Y；界面里没有可参照的控件时返回 null，兜底位置由调用方决定
     */
    fun belowExistingColumn(children: List<*>, screenHeight: Int, left: Int, right: Int): Int? {
        val inColumn = children
            .filterIsInstance<AbstractWidget>()
            .filter { it.visible && it.isInColumn(left, right) && !it.isBottomChrome(screenHeight) }
        if (inColumn.isEmpty()) return null

        val below = inColumn.maxOf { it.y + it.height } + BUTTON_GAP
        if (below + BUTTON_HEIGHT <= screenHeight - EDGE_MARGIN) return below

        val above = inColumn.minOf { it.y } - BUTTON_HEIGHT - BUTTON_GAP
        return above.coerceAtLeast(EDGE_MARGIN)
    }

    /**
     * 控件是否和按钮同属一列。
     *
     * 用"控件中心落在按钮的水平区间内"判定，而不是"两者有像素交集"：
     * 标题界面右下角那行版权信息很宽，窗口稍窄时它的左端就会伸进按钮所在的区间，
     * 按交集算会把它误判成同列控件。它又正好贴着屏幕底边（见 [isBottomChrome]），
     * 于是"控件列底部"被算成整个屏幕高度，按钮永远排不到下方。
     *
     * 按中心判定同时保留了另一头的正确性：比按钮更宽、但同样居中的控件
     * （其他模组加的宽按钮）中心仍在区间内，依旧算同列。
     */
    private fun AbstractWidget.isInColumn(left: Int, right: Int): Boolean =
        (x + width / 2) in left..right

    /**
     * 贴着屏幕底边的控件属于界面装饰（版权信息这类），不是菜单列的一部分。
     *
     * 它们的底部等于屏幕高度，一旦被算进控件列，"排在列下方"就必然溢出屏幕。
     */
    private fun AbstractWidget.isBottomChrome(screenHeight: Int): Boolean =
        y + height >= screenHeight - EDGE_MARGIN
}
