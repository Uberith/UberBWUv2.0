package com.uberith.api.ui

import net.botwithus.imgui.ImGui

class NavigationUI(
    private val selectedTab: String,
    private val availableTabs: List<String>,
    private val onTabSelected: (String) -> Unit,
    private val buttonWidth: Float = 140f,
    private val buttonHeight: Float = 38f,
    private val buttons: Buttons = Buttons()
) {

    fun draw() {
        availableTabs.forEachIndexed { index, tab ->
            val isActive = tab == selectedTab
            buttons.drawNavButton(tab, isActive, buttonWidth, buttonHeight) {
                if (!isActive) {
                    onTabSelected(tab)
                }
            }
            if (index != availableTabs.lastIndex) {
                ImGui.spacing()
            }
        }
    }
}
