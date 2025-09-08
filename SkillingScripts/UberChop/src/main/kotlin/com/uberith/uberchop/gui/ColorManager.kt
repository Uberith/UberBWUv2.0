package com.uberith.uberchop.gui

import net.botwithus.imgui.ImGui

class ColorManager {

    private val colorMap: MutableMap<ColorType, IntArray> = mutableMapOf()

    enum class ColorType(val index: Int) {
        Text(0), TextDisabled(1), TextSecondary(1), TextSelectedBg(49),
        WindowBg(2), ChildBg(3), childButtonBg(3), PopupBg(4),
        Border(5), BorderShadow(6), FrameBg(7), FrameBgHovered(8), FrameBgActive(9),
        TitleBg(10), TitleBgActive(11), TitleBgCollapsed(12),
        ScrollbarBg(13), ScrollbarGrab(14), ScrollbarGrabHovered(15), ScrollbarGrabActive(16),
        SliderGrab(17), CheckMark(18), SliderGrabActive(19),
        Button(21), ButtonHovered(22), ButtonActive(23), ButtonText(19),
        Header(24), HeaderHovered(25), HeaderActive(26),
        Separator(27), SeparatorHovered(28), SeparatorActive(29),
        ResizeGrip(30), ResizeGripHovered(31), ResizeGripActive(32),
        Tab(33), TabHovered(34), TabActive(35), TabUnfocused(36), TabUnfocusedActive(37),
        DockingPreview(38), DockingEmptyBg(39),
        PlotLines(40), PlotLinesHovered(41), PlotHistogram(42), PlotHistogramHovered(43),
        TableHeaderBg(44), TableBorderStrong(45), TableBorderLight(46), TableRowBg(47), TableRowBgAlt(48),
        DragDropTarget(50), NavHighlight(51), NavWindowingHighlight(52), NavWindowingDimBg(53), ModalWindowDimBg(54),
    }

    // Public accent colors (sleek modern blue palette)
    // Neutral slate base + blue accent (accessible, professional)
    val accent = intArrayOf(59, 130, 246, 255)         // Blue 500 (#3B82F6)
    val accentHover = intArrayOf(96, 165, 250, 255)    // Blue 400 (#60A5FA)
    val accentActive = intArrayOf(37, 99, 235, 255)    // Blue 600 (#2563EB)

    init { setDefaultColors() }

    private fun setDefaultColors() {
        // Base colors for sleek slate-on-dark theme
        colorMap[ColorType.Text] = intArrayOf(229, 231, 235, 255)          // Gray-200: high contrast on dark
        colorMap[ColorType.TextSecondary] = intArrayOf(156, 163, 175, 255) // Gray-400
        colorMap[ColorType.TextDisabled] = intArrayOf(107, 114, 128, 255)  // Gray-500
        colorMap[ColorType.TextSelectedBg] = intArrayOf(30, 58, 138, 160)  // Blue-800 overlay

        // Transparent dark surfaces (charcoal/slate)
        colorMap[ColorType.WindowBg] = intArrayOf(17, 24, 39, 200)         // Gray-900 @ ~78% alpha
        colorMap[ColorType.ChildBg] = intArrayOf(31, 41, 55, 170)          // Gray-800 @ ~67% alpha
        colorMap[ColorType.PopupBg] = intArrayOf(31, 41, 55, 200)
        // Title bar transparency to match window
        colorMap[ColorType.TitleBg] = intArrayOf(17, 24, 39, 205)
        colorMap[ColorType.TitleBgActive] = intArrayOf(17, 24, 39, 220)
        colorMap[ColorType.TitleBgCollapsed] = intArrayOf(17, 24, 39, 190)
        // Subtle border and drop shadow
        colorMap[ColorType.Border] = intArrayOf(55, 65, 81, 220)           // Gray-700
        colorMap[ColorType.BorderShadow] = intArrayOf(3, 7, 18, 160)       // Near-black soft shadow

        // Frames and sliders
        colorMap[ColorType.FrameBg] = intArrayOf(31, 41, 55, 255)          // Gray-800
        // Subtle blue glow for focus affordance on inputs/sliders
        colorMap[ColorType.FrameBgHovered] = intArrayOf(30, 64, 175, 180)  // Blue-700 @ ~70% alpha
        colorMap[ColorType.FrameBgActive] = intArrayOf(37, 99, 235, 200)   // Blue-600 @ ~78% alpha

        // Scrollbars
        colorMap[ColorType.ScrollbarBg] = intArrayOf(17, 24, 39, 200)
        colorMap[ColorType.ScrollbarGrab] = intArrayOf(59, 130, 246, 200)  // Blue 500 translucent
        colorMap[ColorType.ScrollbarGrabHovered] = intArrayOf(96, 165, 250, 220)
        colorMap[ColorType.ScrollbarGrabActive] = intArrayOf(37, 99, 235, 240)

        // Buttons with teal accent and legible text
        colorMap[ColorType.ButtonText] = intArrayOf(255, 255, 255, 255)
        colorMap[ColorType.Button] = accent
        colorMap[ColorType.ButtonHovered] = accentHover
        colorMap[ColorType.ButtonActive] = accentActive

        colorMap[ColorType.CheckMark] = accent
        colorMap[ColorType.SliderGrab] = accentHover
        colorMap[ColorType.SliderGrabActive] = accentActive

        // Accent headers and separators for cohesion
        colorMap[ColorType.Header] = accent
        colorMap[ColorType.HeaderHovered] = accentHover
        colorMap[ColorType.HeaderActive] = accentActive
        colorMap[ColorType.Separator] = accent
        colorMap[ColorType.SeparatorHovered] = accentHover
        colorMap[ColorType.SeparatorActive] = accentActive
    }

    fun pushColors() {
        colorMap.forEach { (type, color) ->
            ImGui.pushStyleColor(type.index, color[0] / 255f, color[1] / 255f, color[2] / 255f, color[3] / 255f)
        }
    }

    fun popColors() {
        ImGui.popStyleColor(colorMap.size)
    }

    fun setColor(colorType: ColorType, color: IntArray) { colorMap[colorType] = color }
    fun getColor(colorType: ColorType): IntArray? = colorMap[colorType]

    fun colorToFloats(color: IntArray): FloatArray = floatArrayOf(
        color[0] / 255f, color[1] / 255f, color[2] / 255f, color[3] / 255f
    )

    fun accentF(): FloatArray = colorToFloats(accent)
    fun accentHoverF(): FloatArray = colorToFloats(accentHover)
    fun accentActiveF(): FloatArray = colorToFloats(accentActive)
}
