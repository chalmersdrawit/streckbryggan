package it.drawit.streckbryggan

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import com.izettle.android.commons.ext.state.toLiveData
import com.izettle.payments.android.payment.TransactionReference
import com.izettle.payments.android.sdk.IZettleSDK.Instance.user
import com.izettle.payments.android.sdk.User
import com.izettle.payments.android.sdk.User.AuthState.LoggedIn
import com.izettle.payments.android.ui.payment.CardPaymentActivity
import com.izettle.payments.android.ui.payment.CardPaymentResult
import com.izettle.payments.android.ui.readers.CardReadersActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var pollStatusText: TextView
    private lateinit var enablePollingButton: ToggleButton
    private lateinit var pollProgressBar: ProgressBar
    private lateinit var loginStateText: TextView
    private lateinit var loginButton: Button
    private lateinit var logoutButton: Button
    private lateinit var advancedButton: Button
    private lateinit var settingsButton: Button

    private lateinit var connection: StrecklistanConnection
    private var pollJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pollStatusText = findViewById(R.id.poll_status_text)
        enablePollingButton = findViewById(R.id.enable_polling_button)
        pollProgressBar = findViewById(R.id.poll_progress_bar)

        loginStateText = findViewById(R.id.login_state)
        loginButton = findViewById(R.id.login_btn)
        logoutButton = findViewById(R.id.logout_btn)
        advancedButton = findViewById(R.id.advanced_button)
        settingsButton = findViewById(R.id.settings_btn)

        val strecklistanBaseUri = getString(R.string.strecklistan_base_uri)
        val strecklistanUser = getString(R.string.strecklistan_http_user)
        val strecklistanPass = getString(R.string.strecklistan_http_pass)
        connection = StrecklistanConnection(
                strecklistanBaseUri,
                strecklistanUser,
                strecklistanPass
        )

        user.state.toLiveData().observe(this, Observer { authState: User.AuthState? ->
            onUserAuthStateChanged(authState is LoggedIn)
        })

        loginButton.setOnClickListener { onLoginClicked() }
        logoutButton.setOnClickListener { onLogoutClicked() }
        advancedButton.setOnClickListener { onAdvancedClicked() }
        settingsButton.setOnClickListener { onSettingsClicked() }
        enablePollingButton.setOnClickListener { onPollingCheckBoxClicked() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PAYMENT && data != null) {
            val result: CardPaymentResult = data.getParcelableExtra(CardPaymentActivity.RESULT_EXTRA_PAYLOAD)!!
            connection.postTransactionResult(Integer.parseInt(lastPaymentTraceId.value!!), result) { response ->
                runOnUiThread {
                    when (response) {
                        is Result.Success -> {
                            pollStatusText.text = response.value
                            if (enablePollingButton.isChecked) {
                                poll()
                            }
                        }
                        is Result.Failure -> {
                            enablePollingButton.isChecked = false
                            pollProgressBar.visibility = View.INVISIBLE
                            pollStatusText.text = "Error: ${response.error}"
                        }
                    }
                }
            }
            when (result) {
                is CardPaymentResult.Completed -> {
                    Toast.makeText(this, "Payment completed", Toast.LENGTH_SHORT).show()
                }
                is CardPaymentResult.Canceled -> {
                    Toast.makeText(this, "Payment canceled", Toast.LENGTH_SHORT).show()
                }
                is CardPaymentResult.Failed -> {
                    Toast.makeText(this, "Payment failed ", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "ERROR: Unknown request code: $requestCode", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun onUserAuthStateChanged(isLoggedIn: Boolean) {
        loginStateText.text = "State: ${if (isLoggedIn) "Authenticated" else "Unauthenticated"}"
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

    private fun poll() {
        connection.pollTransaction { result: Result<TransactionPollResponse, FuelError> ->
            result.fold(
                    success = {
                        runOnUiThread {
                            if (enablePollingButton.isChecked) {
                                when (it) {
                                    is TransactionPollResponse.Pending -> {
                                        //val internalTraceId = UUID.randomUUID().toString()
                                        val enableTipping = false
                                        val enableInstallments = false
                                        val enableLogin = false
                                        val reference = TransactionReference.Builder(it.id.toString())
                                                .put("PAYMENT_EXTRA_INFO", "Started from home screen")
                                                .build()

                                        pollStatusText.text = """Starting transaction ${it.id} for ${it.amount} kr"""

                                        val intent = CardPaymentActivity.IntentBuilder(this)
                                                .amount(it.amount)
                                                .reference(reference)
                                                .enableTipping(enableTipping) // Only for markets with tipping support
                                                .enableInstalments(enableInstallments) // Only for markets with installments support
                                                .enableLogin(enableLogin) // Mandatory to set
                                                .build()

                                        startActivityForResult(intent, REQUEST_CODE_PAYMENT)
                                        lastPaymentTraceId.value = it.id.toString()
                                    }
                                    is TransactionPollResponse.NoPending -> {
                                        // Animate ellipsis in a cool-looking way.
                                        // Must never remove this beautifully disgusting piece of code
                                        val dots = pollStatusText.text.count { c -> c == '.' } + 1
                                        pollStatusText.text = "Polling for transaction${".".repeat(dots % 4)}"

                                        val pollJob = GlobalScope.launch {
                                            delay(timeMillis = 1000)
                                            poll()
                                        }
                                        pollJob.start()
                                        this.pollJob = pollJob
                                    }
                                }
                            }
                        }
                    }, failure = {
                enablePollingButton.isChecked = false
                pollProgressBar.visibility = View.INVISIBLE
                pollStatusText.text = "Error polling for transaction"
            }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onPollingCheckBoxClicked() {
        if (enablePollingButton.isChecked) {
            pollProgressBar.visibility = View.VISIBLE
            pollStatusText.text = "Polling for transaction"

            poll()
        } else {
            this.pollJob?.cancel()

            pollProgressBar.visibility = View.INVISIBLE
            pollStatusText.text = "Idle"
        }
    }

    companion object {
        private const val REQUEST_CODE_PAYMENT = 1001
    }
}

