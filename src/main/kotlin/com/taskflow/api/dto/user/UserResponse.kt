package com.taskflow.api.dto.user

data class UserResponse (
    val id : Long?,
    val name : String,
    val email : String,
    val active : Boolean
)