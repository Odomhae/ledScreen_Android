package com.odom.ledscreen

import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.odom.ledscreen.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity(), ColorSelectorDialog.OnDialogColorClickListener {
    private lateinit var colorSelectorDialog1: ColorSelectorDialog
    private lateinit var colorSelectorDialog2: ColorSelectorDialog
    private val COLOR_SELECTOR_01 = "colorSelector01"
    private val COLOR_SELECTOR_02 = "colorSelector02"
    private val COLOR_01 = "COLOR_01"
    private val COLOR_02 = "COLOR_02"
    private val DIALOG_01_IS_VISIBLE = "DIALOG_01_IS_VISIBLE"
    private val DIALOG_02_IS_VISIBLE = "DIALOG_02_IS_VISIBLE"
    private var Fontsize = 54f
    private var TextDirection = "STOP"

    private lateinit var ll_background: LinearLayout
    private lateinit var buttonSelector01: Button
    private lateinit var buttonSelector02: Button
    private lateinit var textViewNote : TextView
    private lateinit var editTextInput: EditText
    private lateinit var buttonBlink: Button
    private lateinit var buttonStart: Button
    private lateinit var buttonPlus: ImageButton
    private lateinit var buttonMinus: ImageButton
    private lateinit var buttonLeft : ImageButton
    private lateinit var buttonRight : ImageButton

    var visibleDialog1: Boolean = false

    private lateinit var binding: ActivityMainBinding
    private lateinit var adsManager: AdsManager
    private lateinit var gatekeeper: AdGatekeeper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val store = PrefsAdStateStore(this)
        gatekeeper = AdGatekeeper(store, FirstSession.isFirstSession(store))

        adsManager = AdsManager(this)
        adsManager.start {
            runOnUiThread {
                if (!isDestroyed) {
                    adsManager.attachAdaptiveBanner(binding.adContainer)
                    adsManager.loadInterstitial()
                    adsManager.preloadExitAd()
                }
            }
        }

        ll_background = binding.llBackground
        buttonSelector01 = binding.buttonSelector01
        buttonSelector02 = binding.buttonSelector02
        textViewNote = binding.textViewNote
        editTextInput = binding.etInput
        buttonBlink = binding.buttonBlink
        buttonStart = binding.buttonStart
        buttonPlus = binding.buttonPlus
        buttonMinus = binding.buttonMinus
        buttonLeft = binding.buttonLeft
        buttonRight = binding.buttonRight

        val builder = ColorSelectorDialogBuilder()
        colorSelectorDialog1 = builder.setOnDialogColorClickListener(this)
            .setColorList(getColorsList(true))
            .setSelectedColor(R.color.light_green)
            .setFigureType(FigureType.CIRCLE)
            .build()

        ll_background.setBackgroundColor(ContextCompat.getColor(this, R.color.lime))

        val builder2 = ColorSelectorDialogBuilder()
        colorSelectorDialog2 = builder2.setOnDialogColorClickListener(this)
            .setColorList(getColorsList(true))
            .setFigureType(FigureType.SQUARE)
            .build()


        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(COLOR_01)) {
                colorSelectorDialog1.selectedColor = savedInstanceState.getInt(COLOR_01)
                colorSelectorDialog1.selectedColor?.let {
                    ll_background.setBackgroundColor(ContextCompat.getColor(this, it))
                }
            }
            if (savedInstanceState.containsKey(DIALOG_01_IS_VISIBLE) && savedInstanceState.getBoolean(DIALOG_01_IS_VISIBLE)) {
                showDialog(colorSelectorDialog1, COLOR_SELECTOR_01)
            }

            if (savedInstanceState.containsKey(COLOR_02)) {
                colorSelectorDialog2.selectedColor = savedInstanceState.getInt(COLOR_02)
                colorSelectorDialog2.selectedColor?.let {
                    textViewNote.setTextColor(ContextCompat.getColor(this, it))
                }
            }

            if (savedInstanceState.containsKey(DIALOG_02_IS_VISIBLE) && savedInstanceState.getBoolean(DIALOG_02_IS_VISIBLE)) {
                showDialog(colorSelectorDialog2, COLOR_SELECTOR_02)
            }
        }

        buttonSelector01.setOnClickListener { showDialog(colorSelectorDialog1, COLOR_SELECTOR_01) }
        buttonSelector02.setOnClickListener { showDialog(colorSelectorDialog2, COLOR_SELECTOR_02) }

        val animator =
            ObjectAnimator.ofFloat(textViewNote, "alpha", 0.0f, 1.0f)

        // duration of one color
        animator.duration = 500
        // color will be show in reverse manner
        animator.repeatCount = Animation.REVERSE
        // It will be repeated up to infinite time
        animator.repeatCount = Animation.INFINITE

        buttonBlink.setOnClickListener {
            gatekeeper.onSettingChanged()
            val animBlink: Animation = AnimationUtils.loadAnimation(this, R.anim.blink)
            if (!buttonBlink.isSelected) {
                animator.start()
                //textViewNote.startAnimation(animBlink)
                buttonBlink.isSelected = true
            } else {
                animator.cancel()
         //       textViewNote.clearAnimation()
                buttonBlink.isSelected = false
            }
        }

        buttonLeft.setOnClickListener {
            gatekeeper.onSettingChanged()
            val animMarqueeLeft : Animation = AnimationUtils.loadAnimation(this, R.anim.marquee_rtl)
            if (!buttonLeft.isSelected) {
                textViewNote.startAnimation(animMarqueeLeft)
                buttonLeft.isSelected = true
                buttonRight.isSelected = false
                TextDirection = "LEFT"
            } else {
                textViewNote.clearAnimation()
                buttonLeft.isSelected = false
                TextDirection = "STOP"
            }
        }

        buttonRight.setOnClickListener {
            gatekeeper.onSettingChanged()
            val animMarqueeRight : Animation = AnimationUtils.loadAnimation(this, R.anim.marquee_ltr)
            if (!buttonRight.isSelected) {
                textViewNote.startAnimation(animMarqueeRight)
                TextDirection = "RIGHT"
                buttonRight.isSelected = true
                buttonLeft.isSelected = false
            } else {
                textViewNote.clearAnimation()
                buttonRight.isSelected = false
                TextDirection = "STOP"
            }
        }

        buttonPlus.setOnClickListener {
            Fontsize += 4f
            textViewNote.setTextSize(TypedValue.COMPLEX_UNIT_DIP , Fontsize)
        }

        buttonMinus.setOnClickListener {
            Fontsize -= 4f
            textViewNote.setTextSize(TypedValue.COMPLEX_UNIT_DIP , Fontsize)
        }

        editTextInput.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int,
                                       before: Int, count: Int) {
                textViewNote.text = s
            }
        })

        buttonStart.setOnClickListener {
            if (gatekeeper.shouldShowInterstitialOnStart() && adsManager.isInterstitialReady) {
                adsManager.showInterstitial(
                    onShown = { gatekeeper.onInterstitialShown() },
                    onDismissed = { launchResult() })
            } else {
                launchResult()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })

    }

    private fun showExitDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_exit, null)
        val adContainer = content.findViewById<FrameLayout>(R.id.exitAdContainer)

        val exitAd = adsManager.exitAdView
        if (exitAd != null && adsManager.isExitAdLoaded) {
            (exitAd.parent as? ViewGroup)?.removeView(exitAd)
            adContainer.addView(exitAd)
        } else {
            adContainer.visibility = View.GONE // 광고 없으면 다이얼로그만 (블로킹 금지)
        }

        AlertDialog.Builder(this)
            .setView(content)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun launchResult() {
        val ledIntent = Intent(this, ResultActivity::class.java)
        ledIntent.putExtra("TextInput", textViewNote.text.toString())
        ledIntent.putExtra("BackColor", colorSelectorDialog1.selectedColor)
        ledIntent.putExtra("TextColor", colorSelectorDialog2.selectedColor)
        ledIntent.putExtra("fontSize", Fontsize)
        ledIntent.putExtra("Direction", TextDirection) // STOP / LEFT / RIGHT
        ledIntent.putExtra("isBlink", buttonBlink.isSelected)

        gatekeeper.onResultUsed()
        startActivity(ledIntent)
    }

    override fun onDestroy() {
        adsManager.destroy()
        super.onDestroy()
    }

    private fun getColorsList(useAll: Boolean = false) : List<Int>{
        val colorList = ArrayList<Int>()
        colorList.add(0, R.color.red)
        colorList.add(1, R.color.pink)
        colorList.add(2, R.color.purple)
        colorList.add(3, R.color.deep_purple)
        colorList.add(4, R.color.indigo)
        colorList.add(5, R.color.blue)
        colorList.add(6, R.color.light_blue)
        colorList.add(7, R.color.cyan)
        colorList.add(8, R.color.teal)
        colorList.add(9, R.color.green)
        colorList.add(10, R.color.light_green)
        colorList.add(11, R.color.lime)

        if(useAll){
            colorList.add(12, R.color.yellow)
            colorList.add(13, R.color.amber)
            colorList.add(14, R.color.orange)
            colorList.add(15, R.color.deep_orange)
            colorList.add(16, R.color.grey)
            colorList.add(17, R.color.blue_grey)
            colorList.add(18, R.color.brown)
            colorList.add(19, R.color.black)
            colorList.add(20, R.color.white)
        }

        return colorList
    }

    private fun showDialog(dialog: ColorSelectorDialog, tag: String) {

        try {
            val ft = supportFragmentManager.beginTransaction()
            val prev = supportFragmentManager.findFragmentByTag(tag)
            if (prev != null) {
                ft.remove(prev)
                (prev as ColorSelectorDialog).dismiss()
            }
            ft.addToBackStack(null)
        }catch (e: Exception){ }
        dialog.show(supportFragmentManager, tag)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        visibleDialog1 = colorSelectorDialog1.isVisible
        super.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (colorSelectorDialog1.selectedColor != null)
            outState.putInt(COLOR_01, colorSelectorDialog1.selectedColor!!)

        if (colorSelectorDialog2.selectedColor != null)
            outState.putInt(COLOR_02, colorSelectorDialog2.selectedColor!!)

        outState.putBoolean(DIALOG_01_IS_VISIBLE, colorSelectorDialog1.isVisible)
        outState.putBoolean(DIALOG_02_IS_VISIBLE, colorSelectorDialog2.isVisible)

        super.onSaveInstanceState(outState)
    }

    override fun onColorClick(tagDialog: String, selectedColor: Int?) {
        gatekeeper.onSettingChanged()
        if (tagDialog == COLOR_SELECTOR_01) {
            if (selectedColor != null) {
                ll_background.setBackgroundColor(ContextCompat.getColor(this, colorSelectorDialog1.selectedColor!!))
            } else {
                ll_background.background = null
            }
        }

        if (tagDialog == COLOR_SELECTOR_02) {
            if (selectedColor != null) {
                textViewNote.setTextColor(ContextCompat.getColor(this, selectedColor))

            } else {
                textViewNote.background = null
            }
        }
    }
}