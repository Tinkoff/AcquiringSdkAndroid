package ru.tinkoff.acquiring.sdk.redesign.common.carddatainput

import android.text.Editable
import android.text.TextWatcher
import ru.tinkoff.acquiring.sdk.ui.customview.editcard.CardFormatter
import ru.tinkoff.acquiring.sdk.ui.customview.editcard.CardPaymentSystem

internal class CardNumberFormatter : TextWatcher {

    private var selfChange = false
    private var prev: CharSequence? = null
    var isSingleInsert = false
    private var deleteAt = -1

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        prev = normalize(s?.toString())
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        isSingleInsert = before == 0 && count == 1
        deleteAt = if (before == 1 && count == 0) start else -1
    }

    override fun afterTextChanged(source: Editable) {
        if (selfChange) return

        var cardNumber = normalize(source.toString())
        if (cardNumber == prev && deleteAt != -1) {
            cardNumber = normalize(source.toString().removeRange(deleteAt - 1, deleteAt))
        }

        val paymentSystem = CardPaymentSystem.resolve(cardNumber)

        if (cardNumber.length > paymentSystem.range.last) {
            cardNumber = cardNumber.substring(0, paymentSystem.range.last)
        }

        val mask = CardFormatter.resolveCardNumberMask(cardNumber)
        cardNumber = applyMask(cardNumber, mask)

        if (cardNumber == source.toString()) return

        selfChange = true
        source.replace(0, source.length, cardNumber)
        selfChange = false
    }

    private fun applyMask(source: String, mask: String): String = StringBuilder().apply {
        var maskCounter = 0
        repeat(source.length) {
            append(source[it])
            if (mask.getOrNull(++maskCounter) == ' ') {
                append(' ')
                maskCounter++
            }
        }
    }.toString()

    companion object {

        private val REGEX_NON_DIGITS = "\\D".toRegex()

        fun normalize(source: String?) = source.orEmpty().replace(REGEX_NON_DIGITS, "")
    }
}