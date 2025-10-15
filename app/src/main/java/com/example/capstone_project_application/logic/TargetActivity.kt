package com.example.capstone_project_application.logic

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.capstone_project_application.R

class TargetActivity : AppCompatActivity() {

    private lateinit var circles: List<View>
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_target)

        circles = listOf(
            findViewById(R.id.circleTopLeft),
            findViewById(R.id.circleTopRight),
            findViewById(R.id.circleBottomLeft),
            findViewById(R.id.circleBottomRight)
        )

        // הצבת מאזין על כל עיגול
        circles.forEach { circle ->
            circle.setOnClickListener {
                updateCircleColors() // מחליף צבעים כשלוחצים על כל עיגול
            }
        }

        // אפשרות: צבעים ראשוניים
        updateCircleColors()
    }

    private fun updateCircleColors() {
        circles.forEach { circle ->
            val colorRes = blues.random() // צבע אקראי מתוך הרשימה
            val color = ContextCompat.getColor(this, colorRes)
            circle.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }
}

