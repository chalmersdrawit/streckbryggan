package it.drawit.streckbryggan

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

const val ERROR_MESSAGE_KEY = "error_message"

fun errorIntent(context: Context, message: String): Intent {
    val intent = Intent(context, ErrorActivity::class.java)
    intent.putExtra(ERROR_MESSAGE_KEY, message)
    return intent
}

class ErrorActivity : AppCompatActivity() {

    private lateinit var errorTextBox: TextView

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_error)

        errorTextBox = findViewById(R.id.errorTextBox)
        errorTextBox.text = intent.extras!!.getString(ERROR_MESSAGE_KEY)
    }
}

