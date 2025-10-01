package com.uberith.api.ui

import net.botwithus.imgui.ImGui

class Buttons(private val colorManager: ColorManager = ColorManager()) {

    fun drawNavButton(
        label: String,
        selected: Boolean,
        width: Float,
        height: Float,
        onClick: () -> Unit
    ) {
        val accent = colorManager.accentF()
        val accentHover = colorManager.accentHoverF()
        val accentActive = colorManager.accentActiveF()
        val inactive = floats(colorManager.getColor(ColorManager.ColorType.FrameBg))
        val inactiveHover = floats(colorManager.getColor(ColorManager.ColorType.FrameBgHovered))
        val inactiveActive = floats(colorManager.getColor(ColorManager.ColorType.FrameBgActive))
        val activeText = floatArrayOf(1f, 1f, 1f, 1f)
        val inactiveText = floats(colorManager.getColor(ColorManager.ColorType.TextSecondary))

        val buttonColor = if (selected) accent else inactive
        val hoverColor = if (selected) accentHover else inactiveHover
        val activeColor = if (selected) accentActive else inactiveActive
        val textColor = if (selected) activeText else inactiveText

        ImGui.pushStyleColor(ColorManager.ColorType.Button.index, buttonColor[0], buttonColor[1], buttonColor[2], buttonColor[3])
        ImGui.pushStyleColor(ColorManager.ColorType.ButtonHovered.index, hoverColor[0], hoverColor[1], hoverColor[2], hoverColor[3])
        ImGui.pushStyleColor(ColorManager.ColorType.ButtonActive.index, activeColor[0], activeColor[1], activeColor[2], activeColor[3])
        ImGui.pushStyleColor(ColorManager.ColorType.ButtonText.index, textColor[0], textColor[1], textColor[2], textColor[3])

        if (ImGui.button("$label##NavButton", width, height)) {
            onClick()
        }

        ImGui.popStyleColor(4)
    }

    private fun floats(color: IntArray?): FloatArray {
        val fallback = intArrayOf(45, 56, 73, 220)
        return colorManager.colorToFloats(color ?: fallback)
    }
}
