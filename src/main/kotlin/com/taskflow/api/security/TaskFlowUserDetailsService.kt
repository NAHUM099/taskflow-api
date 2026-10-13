package com.taskflow.api.security

import com.taskflow.api.exception.classException.UserNotFoundException
import com.taskflow.api.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class TaskFlowUserDetailsService(
    private val userRepository: UserRepository
) : UserDetailsService{
    override fun loadUserByUsername(username: String): UserDetails {

       val user = userRepository.findByEmail(username) ?:
       throw UsernameNotFoundException("Usuario no existente")

        return TaskFlowUserDetails(user)


    }
}