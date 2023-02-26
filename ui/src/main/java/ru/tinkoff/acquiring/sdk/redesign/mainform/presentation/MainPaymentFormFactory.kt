package ru.tinkoff.acquiring.sdk.redesign.mainform.presentation

import ru.tinkoff.acquiring.sdk.AcquiringSdk
import ru.tinkoff.acquiring.sdk.models.Card
import ru.tinkoff.acquiring.sdk.models.enums.CardStatus
import ru.tinkoff.acquiring.sdk.redesign.common.savedcard.SavedCardsRepository
import ru.tinkoff.acquiring.sdk.redesign.mainform.presentation.MainPaymentFromUtils.getOrNull
import ru.tinkoff.acquiring.sdk.redesign.mainform.presentation.primary.PrimaryButtonConfigurator
import ru.tinkoff.acquiring.sdk.redesign.mainform.presentation.secondary.SecondButtonConfigurator
import ru.tinkoff.acquiring.sdk.requests.performSuspendRequest
import ru.tinkoff.acquiring.sdk.responses.TerminalInfo

/**
 * Created by i.golovachev
 */
internal class MainPaymentFormFactory(
    private val sdk: AcquiringSdk,
    private val savedCardsRepository: SavedCardsRepository,
    private val primaryButtonConfigurator: PrimaryButtonConfigurator,
    private val secondButtonConfigurator: SecondButtonConfigurator,
    private val mergeMethodsStrategy: MergeMethodsStrategy,
    private val _customerKey: String
) {

    suspend fun getState(): MainPaymentForm.State {
        val methods = getMethods()
        val cards = getSavedCards()
        return getUi(methods ?: TerminalInfo(), cards ?: emptyList())
    }

    suspend fun getUi(terminalInfo: TerminalInfo, cards: List<Card>): MainPaymentForm.State {
        val primary = primaryButtonConfigurator.get(terminalInfo, cards)
        val secondaries = secondButtonConfigurator.get(terminalInfo, cards)

        return MainPaymentForm.State(
            ui = mergeMethodsStrategy.merge(primary, secondaries),
            data = MainPaymentForm.Data(
                terminalInfo,
                cards,
                cards.firstOrNull()
            )
        )
    }


    //region getData
    private suspend fun getMethods() = getOrNull {
        sdk.getTerminalPayMethods().performSuspendRequest().getOrThrow().terminalInfo
    }

    private suspend fun getSavedCards() = getOrNull {
        savedCardsRepository.getCards(_customerKey, true)
            .filter { it.status == CardStatus.ACTIVE }
    }
    //endregion
}