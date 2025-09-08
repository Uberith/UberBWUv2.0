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

    // Public accent colors (modern indigo palette)
    val accent = intArrayOf(99, 102, 241, 255)         // Indigo 500
    val accentHover = intArrayOf(129, 140, 248, 255)   // Indigo 400
    val accentActive = intArrayOf(79, 70, 229, 255)    // Indigo 600

    init { setDefaultColors() }

    private fun setDefaultColors() {
        // Palette aligned with external UberChop ColorManager
        // Slightly lightened primary text for readability
        colorMap[ColorType.Text] = intArrayOf(245, 248, 255, 255)
        colorMap[ColorType.TextSecondary] = intArrayOf(170, 120, 50, 255)
        colorMap[ColorType.TextDisabled] = intArrayOf(120, 120, 120, 255)
        colorMap[ColorType.TextSelectedBg] = intArrayOf(30, 60, 120, 200)

        // Partially transparent surfaces for modern overlay feel
        colorMap[ColorType.WindowBg] = intArrayOf(10, 20, 50, 170)
        // Slightly darker child background for depth behind content panels
        colorMap[ColorType.ChildBg] = intArrayOf(12, 18, 45, 140)
        colorMap[ColorType.PopupBg] = intArrayOf(20, 30, 70, 180)
        // Title bar transparency to match window
        colorMap[ColorType.TitleBg] = intArrayOf(10, 20, 50, 175)
        colorMap[ColorType.TitleBgActive] = intArrayOf(10, 20, 50, 190)
        colorMap[ColorType.TitleBgCollapsed] = intArrayOf(10, 20, 50, 160)
        // More pronounced border and drop shadow for readability on busy backgrounds
        colorMap[ColorType.Border] = intArrayOf(120, 140, 200, 230) // soft indigo-tinted border, higher alpha
        colorMap[ColorType.BorderShadow] = intArrayOf(8, 8, 16, 170) // darker, stronger shadow

        colorMap[ColorType.FrameBg] = intArrayOf(30, 70, 90, 255)
        colorMap[ColorType.FrameBgHovered] = intArrayOf(50, 110, 140, 255)
        colorMap[ColorType.FrameBgActive] = intArrayOf(70, 150, 180, 255)

        colorMap[ColorType.ScrollbarBg] = intArrayOf(15, 20, 40, 200)
        colorMap[ColorType.ScrollbarGrab] = intArrayOf(80, 110, 180, 255)
        colorMap[ColorType.ScrollbarGrabHovered] = intArrayOf(100, 140, 200, 255)
        colorMap[ColorType.ScrollbarGrabActive] = intArrayOf(120, 160, 220, 255)

        // Modern accent for buttons (indigo)
        colorMap[ColorType.ButtonText] = intArrayOf(255, 255, 255, 255)
        colorMap[ColorType.Button] = accent
        colorMap[ColorType.ButtonHovered] = accentHover
        colorMap[ColorType.ButtonActive] = accentActive

        colorMap[ColorType.CheckMark] = intArrayOf(255, 240, 160, 255)
        colorMap[ColorType.SliderGrab] = intArrayOf(150, 200, 255, 255)
        colorMap[ColorType.SliderGrabActive] = intArrayOf(180, 220, 255, 255)

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
