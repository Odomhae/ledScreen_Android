package com.odom.ledscreen

import android.os.Bundle
import android.util.TypedValue
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.odom.ledscreen.databinding.ActivityResultBinding

class ResultActivity  : AppCompatActivity() {

    private lateinit var resultBackground: ConstraintLayout
    private lateinit var resultText : TextView
    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 화면 꺼짐 방지 + 표시 중 밝기 최대
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val attrs = window.attributes
        attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = attrs

        // 몰입 모드 (상태바/내비바 숨김, 스와이프 시 일시 표시)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        actionBar?.hide();
        supportActionBar?.hide();

        val ledIntent = intent

        val textInput = ledIntent.getStringExtra("TextInput")
        val backColor = ledIntent.getIntExtra("BackColor" , R.color.white)
        val textColor = ledIntent.getIntExtra("TextColor" , R.color.black)
        val fontSize =  ledIntent.getFloatExtra("fontSize",34F)
        val textDirection = ledIntent.getStringExtra("Direction")
        val isBlink = ledIntent.getBooleanExtra("isBlink" , false)
        val isRainbow = ledIntent.getBooleanExtra("isRainbow", false)

        resultBackground = binding.clResult
        resultText = binding.tvResult

        resultText.text = textInput
        resultBackground.setBackgroundColor(ContextCompat.getColor(this, backColor))
        if (isRainbow) {
            TextEffects.applyRainbow(resultText)
        } else {
            resultText.setTextColor(ContextCompat.getColor(this, textColor))
        }
        resultText.setTextSize(TypedValue.COMPLEX_UNIT_DIP , fontSize*2)

        val speed = MarqueeSpeed.fromName(ledIntent.getStringExtra("Speed"))
        when (textDirection) {
            "LEFT", "RIGHT" -> MarqueeController().start(resultText, binding.clResult, textDirection!!, speed)
        }


        if (isBlink){
            val blink: Animation = AnimationUtils.loadAnimation(this, R.anim.blink)
            resultText.startAnimation(blink)
        }

        binding.clResult.setOnClickListener { finish() }
        Toast.makeText(this, R.string.tap_to_exit, Toast.LENGTH_SHORT).show()
    }
}