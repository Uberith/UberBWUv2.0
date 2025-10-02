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
        val baseColor = if (selected) colorManager.buttonSelectedColor else colorManager.buttonBgColor
        val hoverColor = if (selected) colorManager.buttonSelectedColor else colorManager.buttonHoverColor
        val activeColor = if (selected) colorManager.buttonSelectedColor else colorManager.buttonBgColor
        val textColor = colorManager.whiteTextColor

        val base = colorManager.colorToFloats(baseColor)
        val hover = colorManager.colorToFloats(hoverColor)
        val active = colorManager.colorToFloats(activeColor)
        val text = colorManager.colorToFloats(textColor)

        ImGui.pushStyleColor(ColorManager.ColorType.Button.index, base[0], base[1], base[2], base[3])
        ImGui.pushStyleColor(ColorManager.ColorType.ButtonHovered.index, hover[0], hover[1], hover[2], hover[3])
        ImGui.pushStyleColor(ColorManager.ColorType.ButtonActive.index, active[0], active[1], active[2], active[3])
        ImGui.pushStyleColor(ColorManager.ColorType.ButtonText.index, text[0], text[1], text[2], text[3])

        if (ImGui.button("$label##NavButton", width, height)) {
            onClick()
        }

        ImGui.popStyleColor(4)
    }
}
