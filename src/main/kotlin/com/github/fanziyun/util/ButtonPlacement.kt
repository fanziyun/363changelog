package com.github.fanziyun.util

import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.PlainTextButton

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
     * 找一个垂直位置上不与其他控件重叠的 Y：
     * 优先控件列下方，其次上方，都放不下时在控件列区域里找空隙。
     * 贴底的版权文字（[PlainTextButton]）不算控件列成员，但仍参与碰撞检测。
     *
     * @return 挑好的 Y；没有可参照的控件时返回 null，兜底位置由调用方决定
     */
    fun belowExistingColumn(children: List<*>, screenHeight: Int, left: Int, right: Int): Int? {
        val widgets = children.filterIsInstance<AbstractWidget>().filter { it.visible }
        val inColumn = widgets.filter { it.isInColumn(left, right) && !it.isBottomChrome() }
        if (inColumn.isEmpty()) return null

        val below = inColumn.maxOf { it.y + it.height } + BUTTON_GAP
        if (fitsAt(below, widgets, left, right, screenHeight)) return below

        val above = inColumn.minOf { it.y } - BUTTON_HEIGHT - BUTTON_GAP
        if (above >= EDGE_MARGIN && fitsAt(above, widgets, left, right, screenHeight)) return above

        val columnWidgets = inColumn.sortedBy { it.y }
        for (widget in columnWidgets) {
            val candidate = widget.y - BUTTON_HEIGHT - BUTTON_GAP
            if (candidate >= EDGE_MARGIN && fitsAt(candidate, widgets, left, right, screenHeight)) return candidate
        }
        for (widget in columnWidgets) {
            val candidate = widget.y + widget.height + BUTTON_GAP
            if (fitsAt(candidate, widgets, left, right, screenHeight)) return candidate
        }
        return null
    }

    private fun fitsAt(y: Int, widgets: List<AbstractWidget>, left: Int, right: Int, screenHeight: Int): Boolean {
        if (y < EDGE_MARGIN || y + BUTTON_HEIGHT > screenHeight - EDGE_MARGIN) return false
        return widgets.none { widget ->
            widget.x < right && widget.x + widget.width > left && widget.y < y + BUTTON_HEIGHT && widget.y + widget.height > y
        }
    }

    /** 用控件中心是否落在按钮区间内判断同列：版权文字很宽，只伸进一段不算同列。 */
    private fun AbstractWidget.isInColumn(left: Int, right: Int): Boolean =
        (x + width / 2) in left..right

    /** 贴底的版权文字不是菜单列的一部分，不应决定列的底部或顶部。 */
    private fun AbstractWidget.isBottomChrome(): Boolean =
        this is PlainTextButton
}
