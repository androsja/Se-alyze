package com.sealyze.domain.model

data class TranslationResult(
    val word: String,
    val confidence: Float,
    val iconUrl: String? = null,
    val probabilityDebug: String = ""
)
