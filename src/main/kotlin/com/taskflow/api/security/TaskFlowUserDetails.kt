package com.taskflow.api.security

import com.taskflow.api.entity.UserEntity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails




class TaskFlowUserDetails (
    private val user : UserEntity
): UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(
            SimpleGrantedAuthority("ROLE_${user.role.name}")
        )


    }

    override fun getPassword(): String {
        return user.passwordHash
    }

    override fun getUsername(): String {
        return user.email
    }

    override fun isAccountNonExpired(): Boolean {
        return true
    }

    override fun isAccountNonLocked(): Boolean {
        return true
    }

    override fun isCredentialsNonExpired(): Boolean {
        return true
    }

    override fun isEnabled(): Boolean {
        return user.active
    }
}