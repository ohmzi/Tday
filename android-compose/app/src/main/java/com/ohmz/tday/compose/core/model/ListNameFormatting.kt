package com.ohmz.tday.compose.core.model

fun capitalizeFirstListLetter(value: String): String {
    val firstLetterIndex = value.indexOfFirst { it.isLetter() }
    if (firstLetterIndex < 0) return value

    val current = value[firstLetterIndex]
    val capitalized = current.titlecaseChar()
    if (current == capitalized) return value

    return buildString(value.length) {
        append(value, 0, firstLetterIndex)
        append(capitalized)
        append(value, firstLetterIndex + 1, value.length)
    }
}
