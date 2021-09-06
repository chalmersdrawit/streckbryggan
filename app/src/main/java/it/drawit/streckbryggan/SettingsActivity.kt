package it.drawit.streckbryggan

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.izettle.android.commons.ext.state.toLiveData
import com.izettle.payments.android.sdk.IZettleSDK.Instance.user
import com.izettle.payments.android.sdk.User
import com.izettle.payments.android.sdk.User.AuthState.LoggedIn
import com.izettle.payments.android.ui.readers.CardReadersActivity
import java.util.*

class SettingsActivity : AppCompatActivity() {
    private lateinit var loginStateText: TextView
    private lateinit var loginButton: Button
    private lateinit var logoutButton: Button
    private lateinit var advancedButton: Button
    private lateinit var settingsButton: Button

    private lateinit var strecklistanUrlInput: TextView
    private lateinit var strecklistanUserInput: TextView
    private lateinit var strecklistanPassInput: TextView

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        loginStateText = findViewById(R.id.login_state)
        loginButton = findViewById(R.id.login_btn)
        logoutButton = findViewById(R.id.logout_btn)
        advancedButton = findViewById(R.id.advanced_button)
        settingsButton = findViewById(R.id.settings_btn)

        strecklistanUrlInput = findViewById(R.id.sl_url_text_field)
        strecklistanUserInput = findViewById(R.id.sl_user_text_field)
        strecklistanPassInput = findViewById(R.id.sl_pass_text_field)

        val prefs = getSharedPreferences(fileStrecklistan, MODE_PRIVATE)!!

        val setupPrefView = { view: TextView, pref: Pref ->
            view.text = pref.get(prefs, this)
            view.addTextChangedListener(
                    SimpleTextWatcher { s -> pref.set(prefs, s) })
        }

        setupPrefView(strecklistanUrlInput, prefStrecklistanUrl)
        setupPrefView(strecklistanUserInput, prefStrecklistanUser)
        setupPrefView(strecklistanPassInput, prefStrecklistanPass)

        user.state.toLiveData().observe(this, { authState: User.AuthState? ->
            onUserAuthStateChanged(authState is LoggedIn)
        })

        loginButton.setOnClickListener { onLoginClicked() }
        logoutButton.setOnClickListener { onLogoutClicked() }
        advancedButton.setOnClickListener { onAdvancedClicked() }
        settingsButton.setOnClickListener { onSettingsClicked() }
    }

    private fun onUserAuthStateChanged(isLoggedIn: Boolean) {
        loginStateText.text = getString(if (isLoggedIn) R.string.state_authenticated else R.string.state_unauthenticated)
        loginButton.isEnabled = !isLoggedIn
        logoutButton.isEnabled = isLoggedIn
    }

    private fun onLoginClicked() {
        user.login(this)
    }

    private fun onLogoutClicked() {
        user.logout()
    }

    private fun onAdvancedClicked() {
        startActivity(Intent(this, AdvancedActivity::class.java))
    }

    private fun onSettingsClicked() {
        startActivity(CardReadersActivity.newIntent(this))
    }
}
