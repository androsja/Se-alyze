package com.sealyze.domain.model

data class SignFrame(
    val leftHand: List<Landmark>? = null,
    val rightHand: List<Landmark>? = null,
    val pose: List<Landmark>? = null,
    val timestamp: Long = System.currentTimeMillis()
)
