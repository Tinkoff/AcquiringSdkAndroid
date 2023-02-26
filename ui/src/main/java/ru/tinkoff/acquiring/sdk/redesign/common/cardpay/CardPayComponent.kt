package ru.tinkoff.acquiring.sdk.redesign.common.cardpay

import ru.tinkoff.acquiring.sdk.databinding.AcqCardPayComponentBinding
import ru.tinkoff.acquiring.sdk.redesign.common.emailinput.EmailInputComponent
import ru.tinkoff.acquiring.sdk.redesign.payment.model.CardChosenModel
import ru.tinkoff.acquiring.sdk.redesign.payment.ui.ChosenCardComponent
import ru.tinkoff.acquiring.sdk.ui.customview.LoaderButton

/**
 * Created by i.golovachev
 */
class CardPayComponent(
    private val viewBinding: AcqCardPayComponentBinding,
    private val email: String?,
    private val onCvcCompleted: (String) -> Unit = {},
    private val onEmailInput: (String) -> Unit = {},
    private val onEmailVisibleChange: (Boolean) -> Unit = {},
    private val onChooseCardClick: () -> Unit = {},
    private val onPayClick: () -> Unit = {}
) {
    private val loaderButton: LoaderButton = viewBinding.loaderButton.apply {
        setOnClickListener { onPayClick() }
    }
    private val emailInputComponent = EmailInputComponent(viewBinding.emailInput.root,
        onEmailChange = { onEmailInput(it) },
        onEmailVisibleChange = { onEmailVisibleChange(it) }
    ).apply {
        render(EmailInputComponent.State(email, email != null))
    }
    private val savedCardComponent = ChosenCardComponent(viewBinding.chosenCard.root,
        onCvcCompleted = { cvc, _ -> onCvcCompleted(cvc) },
        onChangeCard = { onChooseCardClick() }
    )

    fun render(state: CardChosenModel, email: String?) {
        emailInputComponent.render(email, email.isNullOrBlank().not())
        savedCardComponent.render(state)
    }

    fun renderEnable(isEnable: Boolean) {
        loaderButton.isEnabled = isEnable
    }

    fun renderLoader(isLoading: Boolean) {
        loaderButton.isLoading = isLoading
    }
}