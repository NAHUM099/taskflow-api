package com.taskflow.api.mapper

import com.taskflow.api.dto.user.CreateUserRequest
import com.taskflow.api.dto.user.UserResponse
import com.taskflow.api.entity.UserEntity
import com.taskflow.api.enums.UserRoles
import org.springframework.security.crypto.password.PasswordEncoder

object UserMapper

{
    fun toEntity (request: CreateUserRequest, passwordEncoder: PasswordEncoder) : UserEntity {

        return UserEntity (
            name = request.name,
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            role = UserRoles.USER
        )
    }

    fun toResponse (userEntity: UserEntity) : UserResponse {

        return UserResponse (
            id = userEntity.id,
            name = userEntity.name,
            email = userEntity.email,
            active = userEntity.active
        )
    }
}