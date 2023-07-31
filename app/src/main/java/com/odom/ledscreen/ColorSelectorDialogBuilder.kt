package com.odom.ledscreen

class ColorSelectorDialogBuilder {
    private var onDialogColorClickListener: ColorSelectorDialog.OnDialogColorClickListener? = null

    private var figureType: FigureType? = null
    private var colorList: List<Int>? = null
    private var selectedColor: Int? = null

    /**
     * Add selection color onDialogColorClickListener
     *
     * @param onDialogColorClickListener ColorSelectorDialog.OnDialogColorClickListener.
     */
    fun setOnDialogColorClickListener(onDialogColorClickListener: ColorSelectorDialog.OnDialogColorClickListener): ColorSelectorDialogBuilder {
        this.onDialogColorClickListener = onDialogColorClickListener
        return this
    }

    /**
     * Set the figure for the colors. The default value is [.CIRCLE].
     *
     * @param figure [FigureType.SQUARE] or [FigureType.CIRCLE].
     */
    fun setFigureType(figureType: FigureType): ColorSelectorDialogBuilder {
        this.figureType = figureType
        return this
    }

    /**
     * Set a list of colors IDs for to display.
     *
     * Example:
     * List<Integer> colorList
     * ...
     * colorList.add(0, R.color.red)
     * colorList.add(1, R.color.pink)
     * colorList.add(2, R.color.purple)
     *
     * @param colorList list of colors in colors.xml
     */
    fun setColorList(colorList: List<Int>): ColorSelectorDialogBuilder {
        this.colorList = colorList
        return this
    }

    /**
     * Set the color selected.
     *
     * @param id Identifier of the color.
     */
    fun setSelectedColor(selectedColor: Int): ColorSelectorDialogBuilder {
        this.selectedColor = selectedColor
        return this
    }

    fun build(): ColorSelectorDialog {
        if (onDialogColorClickListener == null) {
            throw RuntimeException("ColorSelectorDialog.OnDialogColorClickListener must be implemented")
        }

        if (colorList == null) {
            throw RuntimeException("ColorList must be initialized")
        }

        val dialog = ColorSelectorDialog()
        dialog.onDialogColorClickListener = onDialogColorClickListener!!
        dialog.colorList = colorList!!.toMutableList()

        if(figureType != null) dialog.figureType = figureType!!
        if(selectedColor != null) dialog.selectedColor = selectedColor!!

        return dialog
    }
}