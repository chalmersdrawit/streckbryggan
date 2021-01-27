package it.drawit.streckbryggan

import androidx.lifecycle.MutableLiveData

/**
 * The ID of the last successful payment. Used to enable refunds
 */
var lastPaymentTraceId: MutableLiveData<String?> = MutableLiveData(null)
