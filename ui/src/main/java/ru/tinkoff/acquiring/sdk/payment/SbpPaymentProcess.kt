package ru.tinkoff.acquiring.sdk.payment

import android.content.pm.PackageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.tinkoff.acquiring.sdk.AcquiringSdk
import ru.tinkoff.acquiring.sdk.exceptions.AcquiringSdkException
import ru.tinkoff.acquiring.sdk.models.NspkRequest
import ru.tinkoff.acquiring.sdk.models.enums.DataTypeQr
import ru.tinkoff.acquiring.sdk.models.enums.ResponseStatus
import ru.tinkoff.acquiring.sdk.models.options.screen.PaymentOptions
import ru.tinkoff.acquiring.sdk.payment.PaymentProcess.Companion.configure
import ru.tinkoff.acquiring.sdk.redesign.sbp.util.NspkBankProvider
import ru.tinkoff.acquiring.sdk.redesign.sbp.util.SbpBankAppsProvider
import ru.tinkoff.acquiring.sdk.redesign.sbp.util.SbpHelper
import ru.tinkoff.acquiring.sdk.requests.performSuspendRequest

/**
 * Created by i.golovachev
 */
class SbpPaymentProcess internal constructor(
    private val sdk: AcquiringSdk,
    private val bankAppsProvider: SbpBankAppsProvider,
    private val nspkBankProvider: NspkBankProvider,
    private val scope: CoroutineScope
) {
    internal constructor(
        sdk: AcquiringSdk,
        bankAppsProvider: SbpBankAppsProvider,
        nspkBankProvider: NspkBankProvider,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(sdk, bankAppsProvider, nspkBankProvider, CoroutineScope(ioDispatcher))

    val state = MutableStateFlow<SbpPaymentState>(SbpPaymentState.Created)
    private var looperJob: Job = Job()

    fun start(paymentOptions: PaymentOptions) {
        scope.launch {
            runOrCatch {
                val nspkApps =
                    nspkBankProvider.getNspkApps()
                val init = sendInit(paymentOptions)
                state.value = SbpPaymentState.Started(init.paymentId!!)
                val deeplink = sendGetQr(init.paymentId)

                val installedApps =
                    bankAppsProvider.checkInstalledApps(nspkApps, deeplink)
                state.value =
                    SbpPaymentState.NeedChooseOnUi(init.paymentId!!, installedApps, deeplink)
            }
        }
    }

    fun goingToBankApp() {
        val _state = state.value
        when (_state) {
            is SbpPaymentState.NeedChooseOnUi -> {
                state.value = SbpPaymentState.LeaveOnBankApp(_state.paymentId)
            }
            is SbpPaymentState.Stopped -> {
                state.value = SbpPaymentState.LeaveOnBankApp(_state.paymentId!!)
            }
        }
    }

    fun startCheckingStatus(retriesCount: Int = 10) {
        looperJob = scope.launch {
            // выйдем из функции если стейт уже проверяется или вызов некорректен
            val _state = state.value
            if (_state is SbpPaymentState.LeaveOnBankApp || _state is SbpPaymentState.Stopped) {
                StatusLooper(_state.paymentId!!, sdk, state).start(retriesCount)
            }
        }
    }

    fun stop() {
        state.value = SbpPaymentState.Stopped(state.value.paymentId)
        if (looperJob.isActive) {
            looperJob.cancel()
        }
    }

    private suspend fun runOrCatch(block: suspend () -> Unit) = try {
        block()
    } catch (throwable: Throwable) {
        state.update {
            if (throwable is CancellationException) {
                SbpPaymentState.Stopped(it.paymentId)
            } else {
                SbpPaymentState.GetBankListFailed(it.paymentId, throwable)
            }
        }
    }

    private suspend fun sendInit(paymentOptions: PaymentOptions) =
        sdk.init { configure(paymentOptions) }.performSuspendRequest().getOrThrow()

    private suspend fun sendGetQr(paymentId: Long?) = checkNotNull(
        sdk.getQr {
            this.paymentId = paymentId
            this.dataType = DataTypeQr.PAYLOAD
        }.performSuspendRequest().getOrThrow().data,
    ) { "data from NSPK are null" }

    class StatusLooper(
        private val _paymentId: Long,
        private val sdk: AcquiringSdk,
        private val state: MutableStateFlow<SbpPaymentState>,
    ) {
        suspend fun start(retriesCount: Int) {
            var tries = 0
            while (retriesCount > tries) {
                val response =
                    sdk.getState { this.paymentId = _paymentId }.performSuspendRequest()
                        .getOrThrow()
                delay(LOOPER_DELAY_MS)
                val status = response.status
                when (status) {
                    ResponseStatus.AUTHORIZED, ResponseStatus.CONFIRMED -> {
                        state.value = SbpPaymentState.Success(
                            _paymentId, null, null
                        )
                        return
                    }
                    ResponseStatus.REJECTED -> {
                        state.value = SbpPaymentState.PaymentFailed(
                            _paymentId,
                            AcquiringSdkException(IllegalStateException("PaymentState = $status"))
                        )
                        return
                    }
                    else -> {
                        tries += 1
                        state.value =
                            SbpPaymentState.CheckingStatus(_paymentId, response.status)
                    }
                }
            }
            state.value = SbpPaymentState.PaymentFailed(
                _paymentId,
                AcquiringSdkException(IllegalStateException("retriesCount is over"))
            )
        }
    }

    companion object {
        private const val LOOPER_DELAY_MS = 3000L
        private var instance: SbpPaymentProcess? = null

        @Synchronized
        fun init(
            sdk: AcquiringSdk,
            packageManager: PackageManager,
            bankAppsProvider: SbpBankAppsProvider = SbpBankAppsProvider { nspkBanks, dl ->
                SbpHelper.getBankApps(packageManager, dl, nspkBanks)
            },
            nspkBankProvider: NspkBankProvider = NspkBankProvider {
                NspkRequest().execute().banks
            }
        ) {
            instance?.scope?.cancel()
            instance = SbpPaymentProcess(sdk, bankAppsProvider, nspkBankProvider)
        }

        fun get() = instance!!
    }
}

sealed interface SbpPaymentState {
    val paymentId: Long?

    object Created : SbpPaymentState {
        override val paymentId: Long? = null
    }

    class Started(override val paymentId: Long) : SbpPaymentState
    class NeedChooseOnUi(
        override val paymentId: Long,
        val bankList: List<String>,
        val deeplink: String
    ) : SbpPaymentState

    class GetBankListFailed(override val paymentId: Long?, val throwable: Throwable) :
        SbpPaymentState

    class LeaveOnBankApp(override val paymentId: Long) : SbpPaymentState
    class CheckingStatus(
        override val paymentId: Long,
        val status: ResponseStatus?
    ) : SbpPaymentState

    class PaymentFailed(override val paymentId: Long?, val throwable: Throwable) : SbpPaymentState
    class Success(override val paymentId: Long, val cardId: String?, val rebillId: String?) :
        SbpPaymentState

    class Stopped(override val paymentId: Long?) : SbpPaymentState
}