package it.drawit.streckbryggan

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var shopButton: Button
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

        shopButton = findViewById(R.id.shopButton)
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

        user.state.toLiveData().observe(this, { authState: User.AuthState? ->
            onUserAuthStateChanged(authState is LoggedIn)
        })

        shopButton.setOnClickListener { onShopClicked() }
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
                            if (enablePollingButton.isChecked) {
                                startPolling()
                            }
                        }
                        is Result.Failure -> {
                            showPollingDisabled()
                            // TODO: since the payment went through, but we were unable to post
                            // the result to the server, we shouldn't just drop this transaction
                            startActivity(errorIntent(this, getString(R.string.post_error_trace, response.error)))
                        }
                    }
                }
            }
            when (result) {
                is CardPaymentResult.Completed -> {
                    Toast.makeText(this, getString(R.string.toast_payment_completed), Toast.LENGTH_SHORT).show()
                }
                is CardPaymentResult.Canceled -> {
                    Toast.makeText(this, getString(R.string.toast_payment_canceled), Toast.LENGTH_SHORT).show()
                }
                is CardPaymentResult.Failed -> {
                    Toast.makeText(this, getString(R.string.toast_payment_failed), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            runOnUiThread {
                showPollingDisabled()
                startActivity(errorIntent(this, "Internal Error\n\nUnknown iZettle request code: $requestCode"))
            }
        }
    }

    private fun onShopClicked() {
        startActivity(Intent(this, ShopActivity::class.java))
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

    private fun handlePollResponse(response: TransactionPollResponse) {
        if (!enablePollingButton.isChecked) {
            return
        }

        when (response) {
            is TransactionPollResponse.Pending -> {
                //val internalTraceId = UUID.randomUUID().toString()
                val enableTipping = false
                val enableInstallments = false
                val enableLogin = false
                val reference = TransactionReference.Builder(response.id.toString())
                        .put("PAYMENT_EXTRA_INFO", "Started from StreckBryggan")
                        .build()

                pollStatusText.text = getString(R.string.start_status_msg, response.id)

                val intent = CardPaymentActivity.IntentBuilder(this)
                        .amount(response.amount)
                        .reference(reference)
                        .enableTipping(enableTipping) // Only for markets with tipping support
                        .enableInstalments(enableInstallments) // Only for markets with installments support
                        .enableLogin(enableLogin) // Mandatory to set
                        .build()

                startActivityForResult(intent, REQUEST_CODE_PAYMENT)
                lastPaymentTraceId.value = response.id.toString()
            }
            is TransactionPollResponse.NoPending -> {
                // Animate ellipsis in a cool-looking way.
                // Must never remove this beautifully disgusting piece of code
                val ellipsis = pollStatusText.text.count { c -> c == '.' } + 1
                pollStatusText.text = getString(R.string.poll_status_msg, ".".repeat(ellipsis % 4))

                val pollJob = GlobalScope.launch {
                    // call poll again after a reasonable delay
                    delay(timeMillis = 1000)
                    poll()
                }
                pollJob.start()
                this.pollJob = pollJob
            }
        }
    }

    private fun poll() {
        connection.pollTransaction { result: Result<TransactionPollResponse, FuelError> ->
            runOnUiThread {
                result.fold(
                        success = {
                            this.handlePollResponse(it)
                        },
                        failure = {
                            showPollingDisabled()
                            startActivity(errorIntent(
                                    this, getString(R.string.poll_error_trace, it.message ?: "")))
                        }
                )
            }
        }
    }

    private fun startPolling() {
        pollProgressBar.visibility = View.VISIBLE
        pollStatusText.text = getString(R.string.poll_status_msg, "")

        poll()
    }

    private fun showPollingDisabled() {
        enablePollingButton.isChecked = false
        pollProgressBar.visibility = View.INVISIBLE
        pollStatusText.text = getString(R.string.idle_status_msg)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onPollingCheckBoxClicked() {
        if (enablePollingButton.isChecked) {
            startPolling()
        } else {
            this.pollJob?.cancel()

            showPollingDisabled()
        }
    }

    companion object {
        private const val REQUEST_CODE_PAYMENT = 1001
    }
}

