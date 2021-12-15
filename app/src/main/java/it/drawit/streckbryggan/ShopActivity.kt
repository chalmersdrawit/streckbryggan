package it.drawit.streckbryggan

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.result.Result
import com.izettle.android.commons.ext.state.toLiveData
import com.izettle.payments.android.payment.TransactionReference
import com.izettle.payments.android.ui.payment.CardPaymentActivity
import com.izettle.payments.android.ui.payment.CardPaymentResult
import com.izettle.payments.android.sdk.IZettleSDK.Instance.user
import com.izettle.payments.android.sdk.User
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView


class ShopActivity : AppCompatActivity() {

    private lateinit var webView: GeckoView
    private val geckoSession = GeckoSession()

    private lateinit var enablePollingButton: ToggleButton
    private lateinit var settingsButton: Button
    private lateinit var pulseIndicator: PulseIndicator

    private var poller: StrecklistanPoller? = null

    private lateinit var prefs: SharedPreferences
    private lateinit var prefListener: OnSharedPreferenceChangeListener
    private var reloadSettings = true


    data class GeckoViewAuth(val username: String, val password: String) :
        GeckoSession.PromptDelegate {
        override fun onAuthPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AuthPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
            return GeckoResult.fromValue(prompt.confirm(username, password))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        webView = findViewById(R.id.web_view)
        val runtime = GeckoRuntime.create(this)
        geckoSession.open(runtime)
        webView.setSession(geckoSession)

        enablePollingButton = findViewById(R.id.enable_polling_button)
        settingsButton = findViewById(R.id.settings_button)
        pulseIndicator = PulseIndicator(this)

        enablePollingButton.setOnClickListener { onPollingCheckBoxClicked() }
        settingsButton.setOnClickListener { onSettingsClicked() }

        prefs = getSharedPreferences(fileStrecklistan, MODE_PRIVATE)!!
        // pref listener needs to be stored in a member variable or else it will be garbage collected
        prefListener = OnSharedPreferenceChangeListener { _, _ ->
            reloadSettings = true
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        resetStrecklistanConnection()

        user.state.toLiveData().observe(this) { authState ->
            onUserAuthStateChanged(authState is User.AuthState.LoggedIn)
        }
    }

    override fun onResume() {
        super.onResume()
        if (reloadSettings) {
            reloadSettings = false
            resetStrecklistanConnection()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PAYMENT && data != null) {
            val result: CardPaymentResult =
                data.getParcelableExtra(CardPaymentActivity.RESULT_EXTRA_PAYLOAD)!!
            val connection = this.poller?.connection
            if (connection == null) {
                startActivity(errorIntent(this, getString(R.string.post_error_no_connection)))
            } else {
                connection.postTransactionResult(
                    Integer.parseInt(lastPaymentTraceId.value!!),
                    result
                ) { response ->
                    runOnUiThread {
                        when (response) {
                            is Result.Success -> {
                                if (enablePollingButton.isChecked) {
                                    startPolling()
                                }
                            }
                            is Result.Failure -> {
                                // TODO: since the payment went through, but we were unable to post
                                // the result to the server, we shouldn't just drop this transaction
                                startActivity(
                                    errorIntent(
                                        this,
                                        getString(R.string.post_error_trace, response.error)
                                    )
                                )
                            }
                        }
                    }
                }
            }
            when (result) {
                is CardPaymentResult.Completed -> {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_payment_completed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is CardPaymentResult.Canceled -> {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_payment_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is CardPaymentResult.Failed -> {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_payment_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            runOnUiThread {
                stopPolling()
                startActivity(
                    errorIntent(
                        this,
                        "Internal Error\n\nUnknown iZettle request code: $requestCode"
                    )
                )
            }
        }
    }

    private fun onUserAuthStateChanged(isLoggedIn: Boolean) {
        enablePollingButton.isEnabled = isLoggedIn
        if (!isLoggedIn) {
            stopPolling()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resetStrecklistanConnection() {
        showPollingDisabled()

        poller = null

        val strecklistanBaseUri = prefStrecklistanUrl.get(prefs, this)

        if (strecklistanBaseUri != "") {
            val strecklistanUser = prefStrecklistanUser.get(prefs, this)
            val strecklistanPass = prefStrecklistanPass.get(prefs, this)

            val connection = StrecklistanConnection(
                strecklistanBaseUri,
                strecklistanUser,
                strecklistanPass,
            )

            geckoSession.promptDelegate = GeckoViewAuth(strecklistanUser, strecklistanPass)
            geckoSession.loadUri(strecklistanBaseUri)

            poller = StrecklistanPoller(
                connection,
                { pending -> onPendingTransaction(pending) },
                { error ->
                    stopPolling()
                    startActivity(
                        errorIntent(
                            this,
                            getString(R.string.poll_error_trace, error.message ?: "")
                        )
                    )
                })

            enablePollingButton.isEnabled = true
        } else {
            enablePollingButton.isEnabled = false
        }
    }

    private fun onPendingTransaction(transaction: TransactionPollResponse.Pending) {
        //val internalTraceId = UUID.randomUUID().toString()
        val enableTipping = false
        val enableInstallments = false
        val enableLogin = false
        val reference = TransactionReference.Builder(transaction.id.toString())
            .put("PAYMENT_EXTRA_INFO", "Started from StreckBryggan")
            .build()

        val intent = CardPaymentActivity.IntentBuilder(this)
            .amount(transaction.amount)
            .reference(reference)
            .enableTipping(enableTipping) // Only for markets with tipping support
            .enableInstalments(enableInstallments) // Only for markets with installments support
            .enableLogin(enableLogin) // Mandatory to set
            .build()

        startActivityForResult(intent, REQUEST_CODE_PAYMENT)
        lastPaymentTraceId.value = transaction.id.toString()
    }

    private fun startPolling() {
        pulseIndicator.start()
        poller?.startPolling()
    }

    private fun stopPolling() {
        poller?.stopPolling()
        showPollingDisabled()
    }

    private fun showPollingDisabled() {
        enablePollingButton.isChecked = false
        pulseIndicator.stop()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onPollingCheckBoxClicked() {
        if (enablePollingButton.isChecked) {
            startPolling()
        } else {
            stopPolling()
        }
    }

    private fun onSettingsClicked() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    companion object {
        private const val REQUEST_CODE_PAYMENT = 1001
    }
}

