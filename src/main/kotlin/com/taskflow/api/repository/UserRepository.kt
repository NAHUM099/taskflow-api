package com.taskflow.api.repository



import com.taskflow.api.entity.UserEntity

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {

    fun findByEmail(email: String): UserEntity?

    fun findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
        name: String,
        email: String,
        pageable: Pageable
    ): Page<UserEntity>

}