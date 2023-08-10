package com.odom.ledscreen

import android.os.Bundle
import android.os.PersistableBundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.odom.ledscreen.databinding.ActivityMainBinding
import com.odom.ledscreen.databinding.ActivityResultBinding

private lateinit var resultBackground : ConstraintLayout
private lateinit var resultText : TextView

private lateinit var binding: ActivityResultBinding

class ResultActivity  : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resultBackground = binding.clResult
        resultText = binding.tvResult
    }
}