package ru.tinkoff.acquiring.sdk.redesign.cards.list

import app.cash.turbine.test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import ru.tinkoff.acquiring.sdk.AcquiringSdk
import ru.tinkoff.acquiring.sdk.models.enums.CardStatus
import ru.tinkoff.acquiring.sdk.redesign.cards.list.presentation.CardsListViewModel
import ru.tinkoff.acquiring.sdk.redesign.cards.list.ui.CardsListState
import ru.tinkoff.acquiring.sdk.requests.GetCardListRequest
import ru.tinkoff.acquiring.sdk.responses.GetCardListResponse
import ru.tinkoff.acquiring.sdk.utils.ConnectionChecker
import ru.tinkoff.acquiring.sdk.utils.RequestResult
import turbineDelay
import java.lang.Exception

/**
 * Created by Ivan Golovachev
 */
class CardsListViewModelTest {

    @Test
    fun `when customer key are null`() {
        runViewModelCardsLoadTest<CardsListState.Error>(
            null,
            RequestResult.Success(
                GetCardListResponse(
                    emptyArray()
                )
            )
        )
    }

    @Test
    fun `when cards response return error`() {
        runViewModelCardsLoadTest<CardsListState.Error>(
            null,
            RequestResult.Failure(Exception())
        )
    }

    @Test
    fun `when card list empty`() {
        runViewModelCardsLoadTest<CardsListState.Empty>(
            "key",
            RequestResult.Success(
                GetCardListResponse(
                    emptyArray()
                )
            )
        )
    }

    @Test
    fun `when card list full`() {
        runViewModelCardsLoadTest<CardsListState.Content>(
            "key",
            RequestResult.Success(
                GetCardListResponse(
                    arrayOf(mock {
                        on { pan } doReturn "3413413413413414"
                        on { cardId } doReturn "1"
                        on { status } doReturn CardStatus.ACTIVE
                    })
                )
            )
        )
    }

    @Test
    fun `when card list only D status full`() {
        runViewModelCardsLoadTest<CardsListState.Empty>(
            "key",
            RequestResult.Success(
                GetCardListResponse(
                    arrayOf(mock {
                        on { pan } doReturn "3413413413413414"
                        on { cardId } doReturn "1"
                        on { status } doReturn CardStatus.DELETED
                    })
                )
            )
        )
    }

    @Test
    fun `when connection lost`() {
        runViewModelCardsLoadTest<CardsListState.NoNetwork>(
            "key",
            RequestResult.Success(
                GetCardListResponse(
                    arrayOf(mock {
                        on { pan } doReturn "3413413413413414"
                        on { cardId } doReturn "1"
                        on { status } doReturn CardStatus.DELETED
                    })
                )
            ),
            connectionChecker = mock { on { isOnline() } doReturn false }
        )
    }

    private inline fun <reified T : CardsListState> runViewModelCardsLoadTest(
        key: String?,
        requestResult: RequestResult<out GetCardListResponse>,
        recurrentOnly: Boolean = false,
        connectionChecker: ConnectionChecker = mock {
            on { isOnline() } doReturn true
        }
    ) {
        runBlocking {
            val viewModel = createViewModelMock(requestResult, connectionChecker)
            viewModel.loadData(key, recurrentOnly)
            turbineDelay()
            viewModel.stateUiFlow.test {
                val awaitNextEvent = awaitItem()
                val excClass = T::class
                assertTrue(
                    "${awaitNextEvent.javaClass.simpleName} is not ${excClass.simpleName}",
                    awaitNextEvent::class == excClass
                )
            }
        }
    }

    private fun createViewModelMock(
        result: RequestResult<out GetCardListResponse>,
        connectionChecker: ConnectionChecker
    ): CardsListViewModel {
        val request = mock<GetCardListRequest> {
            on { executeFlow() } doReturn MutableStateFlow(result)
        }
        val sdk = mock<AcquiringSdk> {
            on { getCardList(any()) } doReturn request
        }
        return CardsListViewModel(sdk, connectionChecker)
    }

}