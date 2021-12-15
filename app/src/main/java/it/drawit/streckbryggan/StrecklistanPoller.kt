package it.drawit.streckbryggan

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.requests.CancellableRequest
import com.github.kittinunf.result.Result
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import java.time.Instant
import java.time.temporal.ChronoUnit

class StrecklistanPoller(
    val connection: StrecklistanConnection,
    val callback: (TransactionPollResponse.Pending) -> Unit,
    val error_callback: (FuelError) -> Unit,
) {
    private var pollDelayJob: Job? = null
    private var pollRequest: CancellableRequest? = null
    private var pollStartedAt: Instant = Instant.now()
    private var sessionId: Long = 0
    private var canceled = false

    fun stopPolling() {
        canceled = true

        pollDelayJob?.cancel()
        pollDelayJob = null

        pollRequest?.cancel()
        pollRequest = null
    }

    fun startPolling() {
        if (this.canceled) {
            this.canceled = false
            sendPollRequest()
        }
    }

    private fun sendPollRequest() {
        if (canceled) {
            return
        }

        this.sessionId += 1

        pollStartedAt = Instant.now()
        val requestId = sessionId
        pollRequest =
            connection.pollTransaction { result: Result<TransactionPollResponse, FuelError> ->
                runOnUiThread {
                    if (requestId == sessionId && !canceled) {
                        pollRequest = null
                        result.fold(
                            success = {
                                handlePollResponse(it)
                            },
                            failure = {
                                error_callback(it)
                            }
                        )
                    }
                }
            }
    }

    private fun handlePollResponse(response: TransactionPollResponse) {
        when (response) {
            is TransactionPollResponse.Pending -> {
                stopPolling()
                callback(response)
            }
            is TransactionPollResponse.NoPending -> {
                val timeTaken = ChronoUnit.MILLIS.between(pollStartedAt, Instant.now())

                val minimumPollDelay = 1000;
                if (timeTaken > minimumPollDelay) {
                    sendPollRequest()
                } else {
                    val pollJob = GlobalScope.launch {
                        // make sure we call poll again after a reasonable delay
                        delay(timeMillis = minimumPollDelay - timeTaken)
                        runOnUiThread {
                            sendPollRequest()
                        }
                    }
                    pollJob.start()
                    this.pollDelayJob = pollJob
                }
            }
        }
    }
}