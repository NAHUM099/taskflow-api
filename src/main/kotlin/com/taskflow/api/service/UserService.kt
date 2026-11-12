package com.taskflow.api.service

import com.taskflow.api.auth.AuthService
import com.taskflow.api.dto.user.CreateUserRequest
import com.taskflow.api.dto.user.UpdatePasswordRequest
import com.taskflow.api.dto.user.UpdateStatusUserRequest
import com.taskflow.api.dto.user.UpdateUserPasswordRequest
import com.taskflow.api.dto.user.UpdateUserRequest
import com.taskflow.api.dto.user.UserResponse
import com.taskflow.api.entity.UserEntity
import com.taskflow.api.enums.UserRoles
import com.taskflow.api.exception.classException.EmailAlreadyExistsException
import com.taskflow.api.exception.classException.InValidPasswordException
import com.taskflow.api.exception.classException.UserDataException
import com.taskflow.api.exception.classException.UserNotFoundException
import com.taskflow.api.mapper.UserMapper
import com.taskflow.api.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService (
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
){

    private fun findUserById(id : Long) : UserEntity{

        return userRepository.findById(id).orElseThrow {
            UserNotFoundException("usuario no existente")
        }
    }

    private fun validateEmailNotExists(email : String) {

        if (userRepository.findByEmail(email) != null ) {
            throw EmailAlreadyExistsException("email ya existente")
        }
    }

    private fun updateUserName(user : UserEntity, updateUserRequest: UpdateUserRequest) {

        if (updateUserRequest.name != null && updateUserRequest.name.isNotBlank()) {
            user.name = updateUserRequest.name
        }
        else if (updateUserRequest.name != null && updateUserRequest.name.isBlank()) {
            throw UserDataException("el usuario no puede ser vacio")
        }
    }

    private fun updateUserEmail(user : UserEntity, updateUserRequest: UpdateUserRequest) {

        if (
            user.email != updateUserRequest.email &&
            updateUserRequest.email != null
        ) {

            validateEmailNotExists(updateUserRequest.email)
        }

        user.email = updateUserRequest.email ?: user.email
    }



    fun getUsers(pageable : Pageable, search : String?) : Page<UserResponse> {


        val page = if (search.isNullOrBlank()) {
            userRepository.findAll(pageable)
        } else {
            userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search, search, pageable
            )
        }

        return page.map { user ->
            UserMapper.toResponse(user)
        }
    }


    fun getUserById( id : Long) : UserResponse {

        val user = findUserById(id)

        return UserMapper.toResponse(user)
    }


    fun createUser (createUserRequest: CreateUserRequest) : UserResponse {


        validateEmailNotExists(createUserRequest.email)

        val user = UserMapper.toEntity(createUserRequest, passwordEncoder)

        val savedUser = userRepository.save( user)

        return UserMapper.toResponse(savedUser)

    }


    fun patchUser (id : Long, updateUserRequest : UpdateUserRequest ) : UserResponse {

        val user = findUserById(id)

        updateUserName(user, updateUserRequest)

        updateUserEmail(user, updateUserRequest)

         val updatedUser = userRepository.save(user)

        return UserMapper.toResponse(updatedUser)

    }

    fun updateUserStatus (id: Long, request: UpdateStatusUserRequest) : UserResponse {

        val user = userRepository.findById(id).orElseThrow {
            UserNotFoundException("usuario no existe")
        }

        user.active = request.active

        val updateUser = userRepository.save(user)

        return UserMapper.toResponse(updateUser)

    }

    fun updatePassword(authentication: Authentication, request: UpdatePasswordRequest) {

        val user = userRepository.findByEmail(authentication.name) ?: throw UserNotFoundException("Usuario no existente")

        val validPassword = passwordEncoder.matches(request.currentPassword, user.passwordHash)

        if(!validPassword) throw InValidPasswordException("la contraseña no coincide")

        val newPassword = passwordEncoder.encode(request.newPassword)

        user.passwordHash = newPassword

        userRepository.save(user)

    }


    fun updateUserPassword (id : Long, request : UpdateUserPasswordRequest) {

        val user = findUserById(id)

        user.passwordHash = passwordEncoder.encode(request.newPassword)

        userRepository.save(user)

    }


    fun deleteUser (id : Long) {

        val existingUser = findUserById(id)
        userRepository.delete(existingUser)

    }
}