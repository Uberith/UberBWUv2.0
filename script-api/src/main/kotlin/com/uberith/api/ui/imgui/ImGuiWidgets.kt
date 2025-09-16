package com.uberith.api.ui.imgui

import net.botwithus.imgui.ImGui

/** Collection of helper widgets to keep script UIs consistent. */
object ImGuiWidgets {

    fun toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val updated = ImGui.checkbox(label, value)
        if (updated != value) {
            onChange(updated)
        }
    }

    fun boundedInt(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
        var updated = value
        if (ImGui.button("-", 24f, 0f)) {
            updated = (updated - step).coerceAtLeast(min)
        }
        ImGui.sameLine(0f, 4f)
        ImGui.text(updated.toString())
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("+", 24f, 0f)) {
            updated = (updated + step).coerceAtMost(max)
        }
        ImGui.sameLine(0f, 8f)
        ImGui.text(label)
        if (updated != value) {
            onChange(updated)
        }
    }

    fun tabs(id: String, labels: List<String>, currentIndex: Int, onSelect: (Int) -> Unit, flags: Int = 0) {
        if (ImGui.beginTabBar(id, flags)) {
            labels.forEachIndexed { index, label ->
                if (ImGui.beginTabItem(label, 0)) {
                    if (index != currentIndex) {
                        onSelect(index)
                    }
                    ImGui.endTabItem()
                }
            }
            ImGui.endTabBar()
        }
    }
}

/** Basic in-memory texture cache for reused assets. */
class TextureCache {
    private val cache = mutableMapOf<String, Any?>()

    fun getOrPut(key: String, loader: () -> Any?): Any? {
        return cache.getOrPut(key) { loader() }
    }

    fun invalidate(key: String? = null) {
        if (key == null) cache.clear() else cache.remove(key)
    }
}
