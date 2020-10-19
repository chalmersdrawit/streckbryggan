package it.drawit.streckbryggan

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import com.izettle.payments.android.ui.payment.CardPaymentResult

const val JSON_ENUM_TAG = "type"

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
            /**
             * Type ID, used for deserializing into the proper type
             */
            //val type: String = "TransactionDone",

            var reference: String,

            var amount: Long
    ) : TransactionOver()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("TransactionFailed")
    data class TransactionFailed(
            //val type: String = "TransactionPaid",
            val reason: String
    ) : TransactionOver()

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("TransactionCanceled")
    class TransactionCanceled(
            //val type: String = "TransactionCanceled"
    ) : TransactionOver()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = JSON_ENUM_TAG)
sealed class TransactionPollResponse {
    /**
     * Represents an initiated transaction from strecklistan.
     * This transaction is expected to be processed by iZettle.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("PaymentOk")
    data class Pending(
            var reference: String,
            var amount: Long,
            var paid: Boolean
    ) : TransactionPollResponse()

    /**
     * Represents an initiated transaction from strecklistan.
     * This transaction is expected to be processed by iZettle.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeName("NoPendingTransaction")
    data class NoPending(
            var message: String
    ) : TransactionPollResponse()
}

fun pollTransaction(callback: (s: Result<TransactionPollResponse, FuelError>) -> Unit) {
    val url = "https://" // FIXME

    Fuel.get(url).timeout(10000).response { _, _, result ->
        when (result) {
            is Result.Success -> {
                val json = String(result.value, Charsets.UTF_8)
                val mapper = jacksonObjectMapper()

                val callbackResponse: TransactionPollResponse = mapper.readValue(json)

                callback(Result.success(callbackResponse))
            }
            is Result.Failure -> {
                callback(result)
            }
        }
    }
}

fun postTransactionResult(result: CardPaymentResult, callback: (s: Result<String, FuelError>) -> Unit) {
    val url = "https://" // FIXME

    val data: TransactionOver = when (result) {
        is CardPaymentResult.Completed -> {
            TransactionOver.TransactionPaid(
                    reference = result.payload.reference.toString(),
                    amount = result.payload.amount
            )
        }
        is CardPaymentResult.Canceled -> {
            TransactionOver.TransactionCanceled()
        }
        is CardPaymentResult.Failed -> {
            TransactionOver.TransactionFailed(
                    reason = result.reason.toString()
            )
        }
    }

    val mapper = jacksonObjectMapper()
    val json = mapper.writeValueAsString(data)!!
    Fuel.post(url).body(json, Charsets.UTF_8).response { _, _, response ->
        when (response) {
            is Result.Success -> {
                val json = String(response.value, Charsets.UTF_8)
                callback(Result.success(json))
            }
            is Result.Failure -> {
                callback(response)
                // TODO: decide how to handle this error
            }
        }
    }
}


class PollWorker(appContext: Context, workerParams: WorkerParameters) :
        Worker(appContext, workerParams) {
    override fun doWork(): Result {

        // Do the work here--in this case, upload the images.
        //uploadImages()

        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }
}
