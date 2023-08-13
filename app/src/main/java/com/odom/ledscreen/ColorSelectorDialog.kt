package com.odom.ledscreen

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

/**
 * Github: [https://github.com/HeryLopez/ColorSelector](https://github.com/HeryLopez/ColorSelector)
 */
class ColorSelectorDialog : DialogFragment(), View.OnClickListener {
    private lateinit var tagDialog: String
    lateinit var onDialogColorClickListener: OnDialogColorClickListener

    internal var colorList: List<Int> = mutableListOf()
    internal var figureType: FigureType = FigureType.CIRCLE

    var selectedColor: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // NOTE: The example module shows how to handle configuration changes
        return inflater.inflate(R.layout.activity_color_picker_adapter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildView(view)
    }

    private fun buildView(view: View) {
        (view.findViewById(R.id.rootLayoutColorSelector) as LinearLayout).removeAllViews()
        view.setBackgroundResource(androidx.appcompat.R.color.material_blue_grey_800)

        val layoutParamsContainer = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val scale = requireContext().resources.displayMetrics.density
        val pixels = (50 * scale + 0.5f).toInt()

        val layoutParamsCircles = LinearLayout.LayoutParams(pixels, pixels)
        val margin = (4 * scale + 0.5f).toInt()
        layoutParamsCircles.setMargins(margin, margin, margin, margin)

        var rows = colorList.size / 4

        if (colorList.size % 4 != 0) {
            rows += 1
        }

        var index = 0

        for (i in 0 until rows) {
            val lineLayout = LinearLayout(context)
            lineLayout.gravity = Gravity.CENTER
            lineLayout.orientation = LinearLayout.HORIZONTAL
            lineLayout.layoutParams = layoutParamsContainer

            for (y in 0..3) {

                val imageButton = ImageButton(context)
                imageButton.layoutParams = layoutParamsCircles

                if (index < colorList.size) {
                    val colorID = colorList[index]
                    val colorARGB = ContextCompat.getColor(requireContext(), colorID)

                    imageButton.tag = colorID
                    imageButton.contentDescription = "Color $index"
                    imageButton.setOnClickListener(this)

                    // Circle
                    if (figureType == FigureType.CIRCLE) {
                        imageButton.setBackgroundResource(R.drawable.circle)
                        (imageButton.background as GradientDrawable).setColor(colorARGB)

                    } else {
                        imageButton.setBackgroundColor(ContextCompat.getColor(requireContext(), colorID))
                    }

                    if (selectedColor == colorID) {
                        // Set the text color according to the brightness of the color
                        if (Color.red(colorARGB) + Color.green(colorARGB) + Color.blue(colorARGB) < 384) {
                            imageButton.setImageResource(R.drawable.ic_selected_white)
                        } else {
                            imageButton.setImageResource(R.drawable.ic_selected_black)
                        }
                    }

                } else {
                    imageButton.background = null
                }

                lineLayout.addView(imageButton)

                index += 1
            }

            (view.findViewById(R.id.rootLayoutColorSelector) as LinearLayout).addView(lineLayout)
        }

    }

    override fun onClick(v: View) {
        val newColorSelected = v.tag as Int
        selectedColor = when {
            newColorSelected != selectedColor -> newColorSelected
            else -> null // Initial color (Not selected)
        }

        onDialogColorClickListener.onColorClick(tagDialog, selectedColor)
        this.dismiss()
    }

    override fun show(manager: FragmentManager, tag: String?) {
        this.tagDialog = tag!!
        super.show(manager, tag)
    }

    override fun show(transaction: FragmentTransaction, tag: String?): Int {
        this.tagDialog = tag!!
        return super.show(transaction, tag)
    }

    interface OnDialogColorClickListener {
        fun onColorClick(tagDialog: String, selectedColor: Int?)
    }
}