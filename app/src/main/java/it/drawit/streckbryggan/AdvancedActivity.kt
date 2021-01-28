package it.drawit.streckbryggan

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
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

class AdvancedActivity : AppCompatActivity() {

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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced)

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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PAYMENT && data != null) {
            val result: CardPaymentResult = data.getParcelableExtra(CardPaymentActivity.RESULT_EXTRA_PAYLOAD)!!
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
        }
        if (requestCode == REQUEST_CODE_REFUND && data != null) {
            val result: RefundResult? = data.getParcelableExtra(RefundsActivity.RESULT_EXTRA_PAYLOAD)
            when (result) {
                is RefundResult.Completed -> {
                    Toast.makeText(this, getString(R.string.toast_refund_completed), Toast.LENGTH_SHORT).show()
                }
                is RefundResult.Canceled -> {
                    Toast.makeText(this, getString(R.string.toast_refund_canceled), Toast.LENGTH_SHORT).show()
                }
                is RefundResult.Failed -> {
                    Toast.makeText(this, getString(R.string.toast_refund_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    private inner class RefundCallback : RefundsManager.Callback<CardPaymentPayload, RetrieveCardPaymentFailureReason> {

        override fun onFailure(reason: RetrieveCardPaymentFailureReason) {
            Toast.makeText(this@AdvancedActivity, getString(R.string.toast_refund_failed), Toast.LENGTH_SHORT).show()
        }

        override fun onSuccess(payload: CardPaymentPayload) {
            val reference = TransactionReference.Builder(payload.referenceId)
                    .put("REFUND_EXTRA_INFO", "Started from home screen")
                    .build()
            val intent = RefundsActivity.IntentBuilder(this@AdvancedActivity)
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

