package it.drawit.streckbryggan

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.requests.CancellableRequest
import com.github.kittinunf.result.Result
import com.izettle.payments.android.ui.payment.CardPaymentResult

const val JSON_ENUM_TAG = "type"

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GenericStatusResponse(
    var status: Int,
    var description: String
)

// TODO:
// Find some way to avoid @JsonTypeName decoration through some fancy JsonTypeInfo setting.
// JsonTypeInfo.Id.Name includes the name of the sealed class, e.g. TransactionOver$TransactionPaid
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = JSON_ENUM_TAG)
sealed class TransactionOver {
    /**
     * Represents an completed transaction from iZettle.
     * This transaction is expected to be posted to strecklistan.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("TransactionPaid")
    data class TransactionPaid(
        val card_type: String?,
        val card_payment_entry_mode: String?,
        val card_issuing_bank: String?,
        val masked_pan: String?,
    ) : TransactionOver()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("TransactionFailed")
    data class TransactionFailed(
        //val type: String = "TransactionPaid",
        val reason: String
    ) : TransactionOver()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("TransactionCancelled")
    object TransactionCancelled : TransactionOver()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = JSON_ENUM_TAG)
sealed class TransactionPollResponse {
    /**
     * Represents an initiated transaction from strecklistan.
     * This transaction is expected to be processed by iZettle.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("PendingPayment")
    data class Pending(
        var id: Int,
        var amount: Long
    ) : TransactionPollResponse()

    /**
     * Represents an initiated transaction from strecklistan.
     * This transaction is expected to be processed by iZettle.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("NoPendingTransaction")
    object NoPending : TransactionPollResponse()
}

class StrecklistanConnection(
    strecklistanBaseUri: String,
    private val strecklistanUser: String,
    private val strecklistanPass: String
) {
    private val mapper = jacksonObjectMapper()
    private val pollUri = "$strecklistanBaseUri/api/izettle/bridge/poll"
    private val pollUriTimeout = { timeout: Int -> "$pollUri?timeout=$timeout" }
    private val postUri =
        { reference: Int -> "$strecklistanBaseUri/api/izettle/bridge/payment_response/$reference" }

    fun pollTransaction(
        timeout_millis: Int? = null,
        callback: (s: Result<TransactionPollResponse, FuelError>) -> Unit
    ): CancellableRequest {
        var uri = pollUri
        var httpTimeout = 3000
        timeout_millis?.let {
            uri = pollUriTimeout(it)
            httpTimeout += it
        }

        return Fuel.get(uri)
            .authentication().basic(strecklistanUser, strecklistanPass)
            .timeout(httpTimeout)
            .response { _, _, result ->
                when (result) {
                    is Result.Success -> {
                        val callbackResponse: TransactionPollResponse =
                            mapper.readValue(result.value)
                        callback(Result.success(callbackResponse))
                    }
                    is Result.Failure -> {
                        callback(result)
                    }
                }
            }
    }

    fun postTransactionResult(
        reference: Int,
        result: CardPaymentResult,
        callback: (s: Result<String, FuelError>) -> Unit
    ) {
        val data: TransactionOver = when (result) {
            is CardPaymentResult.Completed -> {
                //val reference2 = Integer.parseInt(result.payload.reference.toString())
                //if (BuildConfig.DEBUG && reference != reference2) {
                //    error("Assertion failed: Transaction reference mismatch")
                //}
                TransactionOver.TransactionPaid(
                    card_type = result.payload.cardType,
                    card_issuing_bank = result.payload.cardIssuingBank,
                    card_payment_entry_mode = result.payload.cardPaymentEntryMode,
                    masked_pan = result.payload.maskedPan,
                )
            }
            is CardPaymentResult.Canceled -> {
                TransactionOver.TransactionCancelled
            }
            is CardPaymentResult.Failed -> {
                TransactionOver.TransactionFailed(
                    reason = result.reason.toString()
                )
            }
        }

        val json = mapper.writeValueAsString(data)!!
        Fuel.post(postUri(reference))
            .authentication().basic(strecklistanUser, strecklistanPass)
            .body(json, Charsets.UTF_8).response { _, _, response ->
                when (response) {
                    is Result.Success -> {
                        val body: GenericStatusResponse = mapper.readValue(response.value)
                        callback(Result.success(body.description))
                    }
                    is Result.Failure -> {
                        // TODO: proper error handling
                        callback(response)
                    }
                }
            }
    }
}

