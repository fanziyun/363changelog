package com.github.fanziyun.util

import net.minecraft.client.gui.components.AbstractWidget

object ButtonPlacement {

    const val BUTTON_HEIGHT = 20
    private const val BUTTON_GAP = 4
    private const val EDGE_MARGIN = 2

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

    private fun AbstractWidget.isInColumn(left: Int, right: Int): Boolean = (x + width / 2) in left..right

    private fun AbstractWidget.isBottomChrome(screenHeight: Int): Boolean = y + height >= screenHeight - EDGE_MARGIN
}
