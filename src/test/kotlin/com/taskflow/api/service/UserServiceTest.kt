package com.taskflow.api.service

import com.taskflow.api.dto.user.UpdateUserPasswordRequest
import com.taskflow.api.entity.UserEntity
import com.taskflow.api.exception.classException.UserNotFoundException
import com.taskflow.api.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional

class UserServiceTest {

    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()
    private val userService = UserService(userRepository, passwordEncoder)

    @Test
    fun `actualiza el hash de la contraseña del usuario indicado`() {
        val user = UserEntity(
            id = 7,
            name = "Ana Torres",
            email = "ana@taskflow.dev",
            passwordHash = passwordEncoder.encode("clave-anterior")
        )
        `when`(userRepository.findById(7)).thenReturn(Optional.of(user))
        `when`(userRepository.save(user)).thenReturn(user)

        userService.updateUserPassword(7, UpdateUserPasswordRequest("nuevaClave123"))

        assertTrue(passwordEncoder.matches("nuevaClave123", user.passwordHash))
        verify(userRepository).save(user)
    }

    @Test
    fun `lanza UserNotFoundException y no guarda cuando el usuario no existe`() {
        `when`(userRepository.findById(99)).thenReturn(Optional.empty())

        assertThrows(UserNotFoundException::class.java) {
            userService.updateUserPassword(99, UpdateUserPasswordRequest("nuevaClave123"))
        }

        verify(userRepository, never()).save(any(UserEntity::class.java))
    }
}
