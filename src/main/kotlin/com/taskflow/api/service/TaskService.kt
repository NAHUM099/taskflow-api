package com.taskflow.api.service

import com.taskflow.api.dto.task.CreateTaskRequest
import com.taskflow.api.dto.task.StatusRequest
import com.taskflow.api.dto.task.TaskFilter
import com.taskflow.api.dto.task.TaskResponse
import com.taskflow.api.entity.TaskEntity
import com.taskflow.api.entity.UserEntity
import com.taskflow.api.exception.classException.InactiveUserException
import com.taskflow.api.exception.classException.InvalidStatusTransitionException
import com.taskflow.api.exception.classException.TaskNotFoundException
import com.taskflow.api.exception.classException.UserNotFoundException
import com.taskflow.api.mapper.TaskMapper
import com.taskflow.api.repository.TaskRepository
import com.taskflow.api.repository.UserRepository
import org.springframework.stereotype.Service


@Service
class TaskService (
    private val taskRepository: TaskRepository,
    private val userRepository: UserRepository) {

    private fun findUserById(id: Long): UserEntity {

        return userRepository.findById(id)
            .orElseThrow {
                UserNotFoundException("Usuario no encontrado")
            }

    }

    private fun findTaskById(id: Long) : TaskEntity {

        return taskRepository.findById(id)
            .orElseThrow {
                TaskNotFoundException("tarea no encontrada")
            }
    }




    fun createTask (createTaskRequest: CreateTaskRequest) : TaskResponse {

        val user = findUserById(createTaskRequest.userId)

        if(!user.active) {
            throw InactiveUserException("Usuario inactivo")
        }

        val task = TaskMapper.toEntity(createTaskRequest, user)

        val savedTask = taskRepository.save(task)

        return TaskMapper.toResponse(savedTask)
    }



    fun getByIdTask (id : Long) : List<TaskResponse> {

        findUserById(id)

        val tasks = taskRepository.findAllByUserId(id)

//        if (validtask.isEmpty()) {
//            throw TaskNotFoundException("el usuario no cuenta con tareas")
//        }

        return tasks.map { task ->
            TaskMapper.toResponse(task)
        }
    }


    fun patchStatusTask (id : Long, statusRequest: StatusRequest) : TaskResponse{

        val task = findTaskById(id)


        val statusNow = task.status

        if (!statusNow.canTransitionTo(statusRequest.status)){
            throw InvalidStatusTransitionException("no se puede cambiar el estado a ${statusRequest.status}")
        }

        task.status = statusRequest.status

        val updatedTask = taskRepository.save(task)

        return TaskMapper.toResponse(updatedTask)
    }


    fun getTasks (taskFilter: TaskFilter) : List<TaskResponse> {

        val tasks = when {
            taskFilter.status != null && taskFilter.userId != null -> taskRepository.findByStatusAndUserIdOrderByCreatedAtDesc(taskFilter.status, taskFilter.userId)

            taskFilter.status != null -> taskRepository.findByStatusOrderByCreatedAtDesc(taskFilter.status)

            taskFilter.userId != null -> taskRepository.findAllByUserIdOrderByCreatedAtDesc(taskFilter.userId)

            else -> taskRepository.findAllByOrderByCreatedAtDesc()
        }

        return tasks.map { task ->
            TaskMapper.toResponse(task)
        }
    }
}