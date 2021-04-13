package it.drawit.streckbryggan

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import com.izettle.android.commons.ext.state.toLiveData
import com.izettle.payments.android.payment.TransactionReference
import com.izettle.payments.android.ui.payment.CardPaymentActivity
import com.izettle.payments.android.ui.payment.CardPaymentResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.izettle.payments.android.sdk.IZettleSDK.Instance.user
import com.izettle.payments.android.sdk.User


class ShopActivity : AppCompatActivity() {

    private lateinit var shopView: WebView

    private lateinit var enablePollingButton: ToggleButton
    private lateinit var pollProgressBar: ProgressBar
    private lateinit var settingsButton: Button

    private var connection: StrecklistanConnection? = null
    private lateinit var prefs: SharedPreferences
    private var pollJob: Job? = null

    data class WebViewAuth(val username: String, val password: String): WebViewClient() {
        override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler, host: String?, realm: String?) {
            handler.proceed(username, password)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        shopView = findViewById(R.id.shop_view)
        enablePollingButton = findViewById(R.id.enable_polling_button)
        pollProgressBar = findViewById(R.id.poll_progress_bar)
        settingsButton = findViewById(R.id.settings_button)

        enablePollingButton.setOnClickListener { onPollingCheckBoxClicked() }
        settingsButton.setOnClickListener { onSettingsClicked() }

        prefs = getSharedPreferences(fileStrecklistan, MODE_PRIVATE)!!
        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
            runOnUiThread { resetStrecklistanConnection() }
        }

        resetStrecklistanConnection()

        user.state.toLiveData().observe(this) { authState ->
            onUserAuthStateChanged(authState is User.AuthState.LoggedIn)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PAYMENT && data != null) {
            val result: CardPaymentResult = data.getParcelableExtra(CardPaymentActivity.RESULT_EXTRA_PAYLOAD)!!
            val connection = this.connection
            if(connection == null) {
                startActivity(errorIntent(this, getString(R.string.post_error_no_connection)))
            } else {
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
            }
            when (result) {
                is CardPaymentResult.Completed -> {
                    Toast.makeText(this, getString(R.string.toast_payment_completed), Toast.LENGTH_SHORT).show()
                }
                is CardPaymentResult.Canceled -> {
                    Toast.makeText(this, getString(R.string.toast_payment_cancelled), Toast.LENGTH_SHORT).show()
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

    private fun onUserAuthStateChanged(isLoggedIn: Boolean) {
        enablePollingButton.isEnabled = isLoggedIn
        if (!isLoggedIn) {
            showPollingDisabled()
            pollJob?.cancel()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resetStrecklistanConnection() {
        pollJob?.cancel()
        showPollingDisabled()

        connection = null;
        prefs.getString(prefStrecklistanUrl, null)?.let {
            val strecklistanBaseUri = it
            val strecklistanUser = prefs.getString(prefStrecklistanUser, "")!!
            val strecklistanPass = prefs.getString(prefStrecklistanPass, "")!!
            connection = StrecklistanConnection(
                    strecklistanBaseUri,
                    strecklistanUser,
                    strecklistanPass,
            )
            shopView.webViewClient = WebViewAuth(strecklistanUser, strecklistanPass)
            shopView.settings.javaScriptEnabled = true
            //shopView.requestFocus()
            shopView.loadUrl(strecklistanBaseUri)
        }

        enablePollingButton.isEnabled = connection != null
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

                //pollStatusText.text = getString(R.string.start_status_msg, response.id)

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
                //val ellipsis = pollStatusText.text.count { c -> c == '.' } + 1
                //pollStatusText.text = getString(R.string.poll_status_msg, ".".repeat(ellipsis % 4))

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
        connection?.pollTransaction { result: Result<TransactionPollResponse, FuelError> ->
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
        //pollStatusText.text = getString(R.string.poll_status_msg, "")

        poll()
    }

    private fun showPollingDisabled() {
        enablePollingButton.isChecked = false
        pollProgressBar.visibility = View.INVISIBLE
        //pollStatusText.text = getString(R.string.idle_status_msg)
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

    private fun onSettingsClicked() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    companion object {
        private const val REQUEST_CODE_PAYMENT = 1001
    }
}

