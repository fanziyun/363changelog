package com.github.fanziyun.host

import net.minecraft.client.gui.components.AbstractWidget

internal object HostButtonPlacement {
    private const val BUTTON_HEIGHT = 20
    private const val BUTTON_GAP = 4
    private const val EDGE_MARGIN = 2
    private const val TITLE_FIRST_ROW_OFFSET = 48
    private const val TITLE_ROW_COUNT_FALLBACK = 4
    private const val TITLE_ROW_SPACING = 24

    fun belowExistingColumn(children: List<*>, screenHeight: Int, left: Int, right: Int): Int? {
        val inColumn = children
            .filterIsInstance<AbstractWidget>()
            .filter { it.visible && it.x <= left && it.x + it.width >= right }
        if (inColumn.isEmpty()) return null

        val below = inColumn.maxOf { it.y + it.height } + BUTTON_GAP
        if (below + BUTTON_HEIGHT <= screenHeight - EDGE_MARGIN) return below

        return (inColumn.minOf { it.y } - BUTTON_HEIGHT - BUTTON_GAP).coerceAtLeast(EDGE_MARGIN)
    }

    /** The title screen's main column has three vanilla rows plus Mod Menu's row. */
    fun afterTitleColumn(screenHeight: Int): Int {
        val firstRow = screenHeight / 4 + TITLE_FIRST_ROW_OFFSET
        val afterColumn = firstRow + TITLE_ROW_COUNT_FALLBACK * TITLE_ROW_SPACING
        if (afterColumn + BUTTON_HEIGHT <= screenHeight - EDGE_MARGIN) return afterColumn
        return (firstRow - BUTTON_HEIGHT - BUTTON_GAP).coerceAtLeast(EDGE_MARGIN)
    }
}
