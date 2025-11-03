package com.example.capstone_project_application.boundary

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone_project_application.R

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login) // מחבר את ה-XML של המסך

        // מציאת הרכיבים מה-XML לפי ה-id שלהם
        val etIdNumber = findViewById<EditText>(R.id.etIdNumber)
        val rgRegistered = findViewById<RadioGroup>(R.id.rgRegistered)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        // פעולה בעת לחיצה על הכפתור
        btnContinue.setOnClickListener {
            val idNumber = etIdNumber.text.toString().trim()

            // בדיקה שהוזן מספר ת.ז
            if (idNumber.isEmpty()) {
                Toast.makeText(this, "Please enter your ID number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // בדיקה אם נבחרה אחת מהאפשרויות
            val selectedOptionId = rgRegistered.checkedRadioButtonId
            if (selectedOptionId == -1) {
                Toast.makeText(this, "Please select Yes or No", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // קריאת הבחירה שהמשתמש עשה (Yes / No)
            val selectedRadio = findViewById<RadioButton>(selectedOptionId)
            val answer = selectedRadio.text.toString()

            // הצגת הנתונים שהוזנו כהודעה זמנית (Toast)
            val message = "ID: $idNumber\nRegistered: $answer"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
