package com.taskflow.api.exception


import java.time.LocalDateTime

data class ErrorResponse (
    val timestamp : LocalDateTime,
    val status : Int,
    val message : String,
    val path : String? = null,
    val details : Map<String, String>? = null
)