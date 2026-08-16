package com.brokenpip3.fatto.data

object ShareIntentParser {
    fun descriptionFrom(extraText: String?): String? = extraText?.trim()?.takeIf { it.isNotBlank() }
}
