package it.drawit.streckbryggan

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import com.izettle.android.commons.ext.state.toLiveData
import com.izettle.payments.android.payment.TransactionReference
import com.izettle.payments.android.payment.refunds.CardPaymentPayload
import com.izettle.payments.android.payment.refunds.RefundsManager
import com.izettle.payments.android.payment.refunds.RetrieveCardPaymentFailureReason
import com.izettle.payments.android.sdk.IZettleSDK.Instance.refundsManager
import com.izettle.payments.android.sdk.IZettleSDK.Instance.user
import com.izettle.payments.android.sdk.User
import com.izettle.payments.android.sdk.User.AuthState.LoggedIn
import com.izettle.payments.android.ui.payment.CardPaymentActivity
import com.izettle.payments.android.ui.payment.CardPaymentResult
import com.izettle.payments.android.ui.readers.CardReadersActivity
import com.izettle.payments.android.ui.refunds.RefundResult
import com.izettle.payments.android.ui.refunds.RefundsActivity
import java.util.*

const val POLL_NOTIFICATION_ID = 1
const val NOTIFICATION_CHANNEL_ID = "streckbryggan-notification-channel"

class MainActivity : AppCompatActivity() {

    private lateinit var pollStatusText: TextView
    private lateinit var enablePollingCheckBox: CheckBox
    private lateinit var pollProgressBar: ProgressBar

    private lateinit var loginStateText: TextView
    private lateinit var loginButton: Button
    private lateinit var logoutButton: Button
    private lateinit var chargeButton: Button
    private lateinit var refundButton: Button
    private lateinit var settingsButton: Button
    private lateinit var amountEditText: EditText
    private lateinit var tippingCheckBox: CheckBox
    private lateinit var installmentsCheckBox: CheckBox
    private lateinit var loginCheckBox: CheckBox
    private lateinit var lastPaymentTraceId: MutableLiveData<String?>

    private lateinit var connection: StrecklistanConnection

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pollStatusText = findViewById(R.id.pollStatusText)
        enablePollingCheckBox = findViewById(R.id.enablePollingCheckBox)
        pollProgressBar = findViewById(R.id.pollProgressBar)

        loginStateText = findViewById(R.id.login_state)
        loginButton = findViewById(R.id.login_btn)
        logoutButton = findViewById(R.id.logout_btn)
        chargeButton = findViewById(R.id.charge_btn)
        refundButton = findViewById(R.id.refund_btn)
        settingsButton = findViewById(R.id.settings_btn)
        amountEditText = findViewById(R.id.amount_input)
        tippingCheckBox = findViewById(R.id.tipping_check_box)
        loginCheckBox = findViewById(R.id.login_check_box)
        installmentsCheckBox = findViewById(R.id.installments_check_box)
        lastPaymentTraceId = MutableLiveData(null)

        val strecklistanBaseUri = getString(R.string.strecklistan_base_uri);
        val strecklistanUser = getString(R.string.strecklistan_http_user);
        val strecklistanPass = getString(R.string.strecklistan_http_pass);
        connection = StrecklistanConnection(
                strecklistanBaseUri,
                strecklistanUser,
                strecklistanPass
        )

        user.state.toLiveData().observe(this, Observer { authState: User.AuthState? ->
            onUserAuthStateChanged(authState is LoggedIn)
        })

        lastPaymentTraceId.observe(this, Observer { value: String? ->
            refundButton.isEnabled = value != null
        })

        loginButton.setOnClickListener { onLoginClicked() }
        logoutButton.setOnClickListener { onLogoutClicked() }
        chargeButton.setOnClickListener { onChargeClicked() }
        refundButton.setOnClickListener { onRefundClicked() }
        settingsButton.setOnClickListener { onSettingsClicked() }
        enablePollingCheckBox.setOnClickListener { onPollingCheckBoxClicked() }

        // Initialize notification channel
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "StreckBryggan", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(notificationChannel)
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
                            if(enablePollingCheckBox.isChecked) {
                                poll()
                            }
                        }
                        is Result.Failure -> {
                            enablePollingCheckBox.isChecked = false
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
        }
        if (requestCode == REQUEST_CODE_REFUND && data != null) {
            val result: RefundResult? = data.getParcelableExtra(RefundsActivity.RESULT_EXTRA_PAYLOAD)
            when (result) {
                is RefundResult.Completed -> {
                    Toast.makeText(this, "Refund completed", Toast.LENGTH_SHORT).show()
                }
                is RefundResult.Canceled -> {
                    Toast.makeText(this, "Refund canceled", Toast.LENGTH_SHORT).show()
                }
                is RefundResult.Failed -> {
                    Toast.makeText(this, "Refund failed ", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun onChargeClicked() {
        val amountEditTextContent = amountEditText.text.toString()
        if (amountEditTextContent == "") {
            return
        }
        val internalTraceId = UUID.randomUUID().toString()
        val amount = amountEditTextContent.toLong()
        val enableTipping = tippingCheckBox.isChecked
        val enableInstallments = installmentsCheckBox.isChecked
        val enableLogin = loginCheckBox.isChecked
        val reference = TransactionReference.Builder(internalTraceId)
                .put("PAYMENT_EXTRA_INFO", "Started from home screen")
                .build()

        val intent = CardPaymentActivity.IntentBuilder(this)
                .amount(amount)
                .reference(reference)
                .enableTipping(enableTipping) // Only for markets with tipping support
                .enableInstalments(enableInstallments) // Only for markets with installments support
                .enableLogin(enableLogin) // Mandatory to set
                .build()

        startActivityForResult(intent, REQUEST_CODE_PAYMENT)
        lastPaymentTraceId.value = internalTraceId
    }

    private fun onSettingsClicked() {
        startActivity(CardReadersActivity.newIntent(this))
    }

    private fun onRefundClicked() {
        val internalTraceId = lastPaymentTraceId.value ?: return
        refundsManager.retrieveCardPayment(internalTraceId, RefundCallback())
    }

    private fun poll() {
        connection.pollTransaction { result: Result<TransactionPollResponse, FuelError> ->
            result.fold(
                    success = {
                        if (enablePollingCheckBox.isChecked) {
                            when (it) {
                                is TransactionPollResponse.Pending -> runOnUiThread {
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
                                is TransactionPollResponse.NoPending -> runOnUiThread {
                                    // TODO: Remove this beautifully disgusting piece of code
                                    val dots = pollStatusText.text.count { c -> c == '.' } + 1
                                    pollStatusText.text = "Polling for transaction${".".repeat(dots % 4)}"

                                    // TODO: make sure we sleep for a bit as to not spam the server
                                    poll()
                                }
                            }
                        }
                    }, failure = {
                enablePollingCheckBox.isChecked = false
                pollProgressBar.visibility = View.INVISIBLE
                pollStatusText.text = "Error polling for transaction"
                //pollStatusTextBox.text = "ERROR: \"$it\"\n--------\n${it.message}"
            }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun onPollingCheckBoxClicked() {
        if (enablePollingCheckBox.isChecked) {
            pollProgressBar.visibility = View.VISIBLE
            pollStatusText.text = "Polling for transaction"

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("StreckBryggan")
                    .setContentText("Listening for transactions...")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true)
                    .build()

            notificationManager.notify(POLL_NOTIFICATION_ID, notification)
            poll()
        } else {
            pollProgressBar.visibility = View.INVISIBLE
            pollStatusText.text = "Waiting"
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(POLL_NOTIFICATION_ID)
        }
    }

    private inner class RefundCallback : RefundsManager.Callback<CardPaymentPayload, RetrieveCardPaymentFailureReason> {

        override fun onFailure(reason: RetrieveCardPaymentFailureReason) {
            Toast.makeText(this@MainActivity, "Refund failed", Toast.LENGTH_SHORT).show()
        }

        override fun onSuccess(payload: CardPaymentPayload) {
            val reference = TransactionReference.Builder(payload.referenceId)
                    .put("REFUND_EXTRA_INFO", "Started from home screen")
                    .build()
            val intent = RefundsActivity.IntentBuilder(this@MainActivity)
                    .cardPayment(payload)
                    .receiptNumber("#123456")
                    .taxAmount(payload.amount / 10)
                    .reference(reference)
                    .build()
            startActivityForResult(intent, REQUEST_CODE_REFUND)
        }
    }

    companion object {
        private const val REQUEST_CODE_PAYMENT = 1001
        private const val REQUEST_CODE_REFUND = 1002
    }
}

