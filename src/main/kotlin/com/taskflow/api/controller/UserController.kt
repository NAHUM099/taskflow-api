package com.taskflow.api.controller


import com.taskflow.api.auth.AuthService
import com.taskflow.api.dto.task.TaskResponse
import com.taskflow.api.dto.user.CreateUserRequest
import com.taskflow.api.dto.user.UpdatePasswordRequest
import com.taskflow.api.dto.user.UpdateStatusUserRequest
import com.taskflow.api.dto.user.UpdateUserRequest
import com.taskflow.api.dto.user.UserResponse
import com.taskflow.api.service.TaskService
import com.taskflow.api.service.UserService
import jakarta.validation.Valid

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController (
    private val userService: UserService,
    private val taskService: TaskService
) {

    @GetMapping("/users")
    fun getUsers(
        pageable : Pageable,
        @RequestParam(required = false) search : String?
    ) :  Page<UserResponse> {
        return userService.getUsers(pageable, search)
    }

    @GetMapping("/users/{id}")
    fun getUser(
        @PathVariable id : Long
    ) : UserResponse {
        return userService.getUserById(id)
    }

    @PostMapping("/users")
    fun createUser(
        @Valid @RequestBody request: CreateUserRequest
    ) : UserResponse {

        return userService.createUser(
            request
        )
    }

    @PatchMapping ("/users/{id}")
    fun patchUser (
        @PathVariable id : Long,
        @Valid @RequestBody request : UpdateUserRequest
    ) : UserResponse {
        return userService.patchUser(id, request)
    }

    @PatchMapping("/users/{id}/status")
    fun updateUserStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateStatusUserRequest
    ) : UserResponse {
        return userService.updateUserStatus(id, request)
    }

    @PatchMapping("/users/me/password")
    fun updatePassword(

         authentication: Authentication,
        @Valid @RequestBody request: UpdatePasswordRequest
    ) : ResponseEntity<Void> {
        userService.updatePassword(authentication, request)
        return ResponseEntity.noContent().build()
    }

   @DeleteMapping("/users/{id}")
    fun deleteUser (
        @PathVariable id : Long
    ) : ResponseEntity<Void>{
        userService.deleteUser(id)

    return ResponseEntity.noContent().build()
    }

    // TASKS

    @GetMapping("/users/{id}/tasks")
    fun taskUser (
        @PathVariable id: Long
    ) : List<TaskResponse> {

       return taskService.getByIdTask(id)
    }
}