package com.odom.ledscreen

import android.os.Bundle
import android.util.TypedValue
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.odom.ledscreen.databinding.ActivityMainBinding
import com.odom.ledscreen.databinding.ActivityResultBinding

private lateinit var resultBackground : ConstraintLayout

class ResultActivity  : AppCompatActivity() {

    private lateinit var resultText : TextView
    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val ledIntent = intent

        val textInput = ledIntent.getStringExtra("TextInput")
        val backColor = ledIntent.getIntExtra("BackColor" , R.color.white)
        val textColor = ledIntent.getIntExtra("TextColor" , R.color.black)
        val fontSize =  ledIntent.getFloatExtra("fontSize",34F)
        val textDirection = ledIntent.getStringExtra("Direction")
        val isBlink = ledIntent.getBooleanExtra("isBlink" , false)

        resultBackground = binding.clResult
        resultText = binding.tvResult

        resultText.text = textInput
        resultBackground.setBackgroundColor(ContextCompat.getColor(this, backColor))
        resultText.setBackgroundColor(ContextCompat.getColor(this, textColor))
        resultText.setTextSize(TypedValue.COMPLEX_UNIT_DIP , fontSize)

        // TODO: Text Direction


        if (isBlink){
            val blink: Animation = AnimationUtils.loadAnimation(this, R.anim.blink)
            resultText.startAnimation(blink)
        }




    }
}