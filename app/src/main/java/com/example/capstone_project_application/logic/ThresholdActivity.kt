package com.example.capstone_project_application.logic

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.capstone_project_application.databinding.ActivityThresholdBinding
import com.example.capstone_project_application.R


class ThresholdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdBinding
    private val blues = listOf(
        R.color.blue_141,
        R.color.blue_142,
        R.color.blue_143,
        R.color.blue_144,
        R.color.blue_145,
        R.color.blue_150,
        R.color.blue_155,
        R.color.blue_160,
        R.color.blue_165,
        R.color.blue_175
    )

    private var indexA = 0
    private var indexB = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateColors()

        binding.btnA.setOnClickListener {
            indexA = (indexA + 1) % blues.size
            updateColors()
        }

        binding.btnB.setOnClickListener {
            indexB = (indexB + 1) % blues.size
            updateColors()
        }
    }

    private fun updateColors() {
        val colorA = ContextCompat.getColor(this, blues[indexA])
        val colorB = ContextCompat.getColor(this, blues[indexB])

        binding.viewA.setBackgroundColor(colorA)
        binding.viewB.setBackgroundColor(colorB)
    }
}
