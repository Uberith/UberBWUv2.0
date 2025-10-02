package com.uberith.api.ui

import net.botwithus.imgui.ImGui

/**
 * A reusable API for managing and applying GUI colors using ImGui.
 */
class ColorManager {

    private val colorMap: MutableMap<ColorType, IntArray> = mutableMapOf()

    // Enum mapping color types to ImGui style indices
    enum class ColorType(val index: Int) {
        // Text-related colors
        Text(0), TextDisabled(1), TextSecondary(1), TextSelectedBg(49),

        // Window and Frame background colors
        WindowBg(2), ChildBg(3), childButtonBg(3), PopupBg(4),
        Border(5), BorderShadow(6), FrameBg(7), FrameBgHovered(8), FrameBgActive(9),
        TitleBg(10), TitleBgActive(11), TitleBgCollapsed(12),

        // Scrollbar colors
        ScrollbarBg(13), ScrollbarGrab(14), ScrollbarGrabHovered(15), ScrollbarGrabActive(16),

        // Checkmark and slider colors
        CheckMark(18), SliderGrab(17), SliderGrabActive(19),

        // Button colors
        Button(21), ButtonHovered(22), ButtonActive(23), ButtonText(19),

        // Header colors (collapsing headers, etc.)
        Header(24), HeaderHovered(25), HeaderActive(26),

        // Separator colors
        Separator(27), SeparatorHovered(28), SeparatorActive(29),

        // Resize grip colors
        ResizeGrip(30), ResizeGripHovered(31), ResizeGripActive(32),

        // Tab-related colors
        Tab(33), TabHovered(34), TabActive(35), TabUnfocused(36), TabUnfocusedActive(37),

        // Docking-related colors
        DockingPreview(38), DockingEmptyBg(39),

        // Plot colors
        PlotLines(40), PlotLinesHovered(41), PlotHistogram(42), PlotHistogramHovered(43),

        // Table colors
        TableHeaderBg(44), TableBorderStrong(45), TableBorderLight(46), TableRowBg(47), TableRowBgAlt(48),

        // Miscellaneous
        DragDropTarget(50), NavHighlight(51), NavWindowingHighlight(52), NavWindowingDimBg(53),
        ModalWindowDimBg(54),
    }

    init {
        setDefaultColors()
    }

    private fun setDefaultColors() {
        // Text-related colors
        colorMap[ColorType.Text] = intArrayOf(255, 255, 200, 255) // Light pastel yellow for readability
        colorMap[ColorType.TextSecondary] = intArrayOf(170, 120, 50, 255) // Dark yellow for secondary text
        colorMap[ColorType.TextDisabled] = intArrayOf(120, 120, 120, 255) // Gray for disabled text
        colorMap[ColorType.TextSelectedBg] = intArrayOf(30, 60, 120, 200) // Dark blue highlight

        // Window and frame background colors
        colorMap[ColorType.WindowBg] = intArrayOf(10, 20, 50, 230) // Dark blue for main background
        colorMap[ColorType.ChildBg] = intArrayOf(15, 25, 60, 0) // Slightly lighter blue
        colorMap[ColorType.childButtonBg] = intArrayOf(15, 25, 60, 0) // Slightly lighter blue
        colorMap[ColorType.PopupBg] = intArrayOf(20, 30, 70, 240) // Popup background
        colorMap[ColorType.Border] = intArrayOf(70, 100, 150, 255) // Neutral blue for contrast
        colorMap[ColorType.BorderShadow] = intArrayOf(30, 30, 30, 100) // Subtle shadow for depth

        // Frame and hover colors
        colorMap[ColorType.FrameBg] = intArrayOf(30, 70, 90, 255) // Teal shade for subtle contrast
        colorMap[ColorType.FrameBgHovered] = intArrayOf(50, 110, 140, 255) // Brighter teal
        colorMap[ColorType.FrameBgActive] = intArrayOf(70, 150, 180, 255) // Vibrant cyan

        // Scrollbar colors
        colorMap[ColorType.ScrollbarBg] = intArrayOf(15, 20, 40, 200) // Darker blue for scrollbar background
        colorMap[ColorType.ScrollbarGrab] = intArrayOf(80, 110, 180, 255) // Neutral blue for grab area
        colorMap[ColorType.ScrollbarGrabHovered] = intArrayOf(100, 140, 200, 255) // Lighter blue when hovered
        colorMap[ColorType.ScrollbarGrabActive] = intArrayOf(120, 160, 220, 255) // Active grab highlight

        // Button colors
        colorMap[ColorType.ButtonText] = intArrayOf(0, 0, 0, 255) // Button text - Doesn't Work
        colorMap[ColorType.Button] = intArrayOf(60, 40, 10, 255) // Dark bronze
        colorMap[ColorType.ButtonHovered] = intArrayOf(80, 50, 20, 255) // Lighter bronze for hover
        colorMap[ColorType.ButtonActive] = intArrayOf(50, 30, 5, 255) // Darker bronze for active

        // Checkmark and slider colors
        colorMap[ColorType.CheckMark] = intArrayOf(255, 240, 160, 255) // Lighter yellow for checkmarks
        colorMap[ColorType.SliderGrab] = intArrayOf(150, 200, 255, 255) // Light blue for slider grab
        colorMap[ColorType.SliderGrabActive] = intArrayOf(180, 220, 255, 255) // Highlighted blue for active grab

        // Header colors (collapsing headers, etc.)
        colorMap[ColorType.Header] = intArrayOf(40, 60, 130, 255) // Blue header
        colorMap[ColorType.HeaderHovered] = intArrayOf(30, 50, 100, 255) // Lighter blue when hovered
        colorMap[ColorType.HeaderActive] = intArrayOf(40, 60, 130, 255) // Slightly darker blue for active headers

        // Separator colors
        colorMap[ColorType.Separator] = intArrayOf(40, 60, 130, 255)
        colorMap[ColorType.SeparatorHovered] = intArrayOf(30, 50, 100, 255)
        colorMap[ColorType.SeparatorActive] = intArrayOf(40, 60, 130, 255)

        // Resize grip colors
        colorMap[ColorType.ResizeGrip] = intArrayOf(30, 70, 90, 255)
        colorMap[ColorType.ResizeGripHovered] = intArrayOf(50, 110, 140, 255)
        colorMap[ColorType.ResizeGripActive] = intArrayOf(70, 150, 180, 255)

        // Tab colors
        colorMap[ColorType.Tab] = intArrayOf(30, 70, 90, 255)
        colorMap[ColorType.TabHovered] = intArrayOf(50, 110, 140, 255)
        colorMap[ColorType.TabActive] = intArrayOf(70, 150, 180, 255)
        colorMap[ColorType.TabUnfocused] = intArrayOf(20, 50, 70, 255)
        colorMap[ColorType.TabUnfocusedActive] = intArrayOf(40, 90, 120, 255)

        // Docking colors
        colorMap[ColorType.DockingPreview] = intArrayOf(60, 40, 10, 200)
        colorMap[ColorType.DockingEmptyBg] = intArrayOf(10, 20, 50, 230)

        // Plot colors
        colorMap[ColorType.PlotLines] = intArrayOf(255, 200, 100, 255)
        colorMap[ColorType.PlotLinesHovered] = intArrayOf(255, 220, 140, 255)
        colorMap[ColorType.PlotHistogram] = intArrayOf(80, 110, 180, 255)
        colorMap[ColorType.PlotHistogramHovered] = intArrayOf(100, 140, 200, 255)

        // Table colors
        colorMap[ColorType.TableHeaderBg] = intArrayOf(40, 60, 130, 255)
        colorMap[ColorType.TableBorderStrong] = intArrayOf(20, 40, 80, 255)
        colorMap[ColorType.TableBorderLight] = intArrayOf(60, 80, 130, 255)
        colorMap[ColorType.TableRowBg] = intArrayOf(15, 25, 60, 255)
        colorMap[ColorType.TableRowBgAlt] = intArrayOf(20, 35, 70, 255)

        // Misc
        colorMap[ColorType.DragDropTarget] = intArrayOf(255, 240, 160, 255)
        colorMap[ColorType.NavHighlight] = intArrayOf(255, 240, 160, 255)
        colorMap[ColorType.NavWindowingHighlight] = intArrayOf(80, 110, 180, 255)
        colorMap[ColorType.NavWindowingDimBg] = intArrayOf(10, 20, 40, 200)
        colorMap[ColorType.ModalWindowDimBg] = intArrayOf(10, 20, 40, 200)
    }

    fun pushColors() {
        colorMap.forEach { (colorType, color) ->
            ImGui.pushStyleColor(
                colorType.index,
                color[0] / 255f,
                color[1] / 255f,
                color[2] / 255f,
                color[3] / 255f
            )
        }
    }

    fun popColors() {
        ImGui.popStyleColor(colorMap.size)
    }

    fun setColor(colorType: ColorType, color: IntArray) {
        require(color.size == 4) { "Color array must have exactly 4 elements (RGBA)." }
        colorMap[colorType] = color
    }

    fun getColor(colorType: ColorType): IntArray? {
        return colorMap[colorType]
    }

    val buttonTextColor = intArrayOf(0, 0, 0, 255)
    val blackTextColor = intArrayOf(0, 0, 0, 255)
    val whiteTextColor = intArrayOf(255, 255, 255, 255)
    val buttonBgColor = intArrayOf(80, 120, 240, 255)
    val buttonHoverColor = intArrayOf(0, 128, 128, 255)
    val buttonSelectedColor = intArrayOf(0, 180, 180, 255)
    val buttonPauseBgColor = intArrayOf(255, 0, 0, 255)
    val buttonPauseHoverColor = intArrayOf(255, 80, 80, 255)
    val buttonPauseSelectedColor = intArrayOf(255, 50, 50, 255)
    val buttonResumeBgColor = intArrayOf(0, 255, 0, 255)
    val buttonResumeHoverColor = intArrayOf(0, 255, 0, 255)
    val buttonResumeSelectedColor = intArrayOf(50, 255, 50, 255)
    val buttonStopBgColor = intArrayOf(255, 0, 0, 255)
    val buttonStopHoverColor = intArrayOf(255, 80, 80, 255)
    val redTextColor = intArrayOf(255, 0, 0, 255)
    val orangeTextColor = intArrayOf(255, 140, 0, 255)
    val greenTextColor = intArrayOf(0, 255, 0, 255)

    val textR = buttonTextColor[0] / 255f
    val textG = buttonTextColor[1] / 255f
    val textB = buttonTextColor[2] / 255f
    val textA = buttonTextColor[3] / 255f

    val whiteTextR = whiteTextColor[0] / 255f
    val whiteTextG = whiteTextColor[1] / 255f
    val whiteTextB = whiteTextColor[2] / 255f
    val whiteTextA = whiteTextColor[3] / 255f

    val bgR = buttonBgColor[0] / 255f
    val bgG = buttonBgColor[1] / 255f
    val bgB = buttonBgColor[2] / 255f
    val bgA = buttonBgColor[3] / 255f

    val hoverR = buttonHoverColor[0] / 255f
    val hoverG = buttonHoverColor[1] / 255f
    val hoverB = buttonHoverColor[2] / 255f
    val hoverA = buttonHoverColor[3] / 255f

    val stopBgR = buttonStopBgColor[0] / 255f
    val stopBgG = buttonStopBgColor[1] / 255f
    val stopBgB = buttonStopBgColor[2] / 255f
    val stopBgA = buttonStopBgColor[3] / 255f

    val stopHoverR = buttonStopHoverColor[0] / 255f
    val stopHoverG = buttonStopHoverColor[1] / 255f
    val stopHoverB = buttonStopHoverColor[2] / 255f
    val stopHoverA = buttonStopHoverColor[3] / 255f

    val orangeTextR = orangeTextColor[0] / 255f
    val orangeTextG = orangeTextColor[1] / 255f
    val orangeTextB = orangeTextColor[2] / 255f
    val orangeTextA = orangeTextColor[3] / 255f

    val redTextR = redTextColor[0] / 255f
    val redTextG = redTextColor[1] / 255f
    val redTextB = redTextColor[2] / 255f
    val redTextA = redTextColor[3] / 255f

    val greenTextR = greenTextColor[0] / 255f
    val greenTextG = greenTextColor[1] / 255f
    val greenTextB = greenTextColor[2] / 255f
    val greenTextA = greenTextColor[3] / 255f

    fun colorToFloats(color: IntArray?): FloatArray {
        val c = color ?: intArrayOf(255, 255, 255, 255)
        return floatArrayOf(c[0] / 255f, c[1] / 255f, c[2] / 255f, c[3] / 255f)
    }
}
