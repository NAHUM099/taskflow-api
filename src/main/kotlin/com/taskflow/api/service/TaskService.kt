package com.taskflow.api.service

import com.taskflow.api.dto.task.CreateTaskRequest
import com.taskflow.api.dto.task.StatusRequest
import com.taskflow.api.dto.task.TaskFilter
import com.taskflow.api.dto.task.TaskResponse
import com.taskflow.api.dto.task.TaskSummaryResponse
import com.taskflow.api.entity.TaskEntity
import com.taskflow.api.entity.UserEntity
import com.taskflow.api.enums.TaskStatus
import com.taskflow.api.exception.classException.InactiveUserException
import com.taskflow.api.exception.classException.InvalidStatusTransitionException
import com.taskflow.api.exception.classException.TaskAccessDeniedException
import com.taskflow.api.exception.classException.TaskNotFoundException
import com.taskflow.api.exception.classException.UserNotFoundException
import com.taskflow.api.mapper.TaskMapper
import com.taskflow.api.repository.TaskRepository
import com.taskflow.api.repository.TaskSpecifications
import com.taskflow.api.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
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

    private fun currentUser(authentication: Authentication) : UserEntity {

        return userRepository.findByEmail(authentication.name)
            ?: throw UserNotFoundException("Usuario no existente")
    }

    private fun currentUserId(authentication: Authentication) : Long {

        return currentUser(authentication).id
            ?: throw UserNotFoundException("Usuario no existente")
    }

    // ADMIN y MODERATOR administran el tablero completo; USER solo ve y mueve lo suyo.
    private fun Authentication.canManageAllTasks() : Boolean =
        authorities.any { authority ->
            authority.authority == "ROLE_ADMIN" || authority.authority == "ROLE_MODERATOR"
        }

    /**
     * userId por el que se debe restringir la consulta, o null cuando el usuario
     * autenticado puede ver todas las tareas.
     */
    internal fun scopeUserId(authentication: Authentication) : Long? =
        if (authentication.canManageAllTasks()) null else currentUserId(authentication)

    /**
     * Para un USER el filtro del query string nunca amplía el alcance: el userId
     * se sobrescribe con el del token, mientras status y search se respetan.
     */
    internal fun effectiveFilter(authentication: Authentication, taskFilter: TaskFilter) : TaskFilter =
        if (authentication.canManageAllTasks()) taskFilter
        else taskFilter.copy(userId = currentUserId(authentication))




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


    fun patchStatusTask (id : Long, statusRequest: StatusRequest, authentication: Authentication) : TaskResponse{

        val task = findTaskById(id)

        if (!authentication.canManageAllTasks() && task.user.id != currentUserId(authentication)) {
            throw TaskAccessDeniedException("No puedes cambiar el estado de una tarea asignada a otro usuario")
        }


        val statusNow = task.status

        if (!statusNow.canTransitionTo(statusRequest.status)){
            throw InvalidStatusTransitionException("no se puede cambiar el estado a ${statusRequest.status}")
        }

        task.status = statusRequest.status

        val updatedTask = taskRepository.save(task)

        return TaskMapper.toResponse(updatedTask)
    }


    fun getTasks (taskFilter: TaskFilter, pageable: Pageable, authentication: Authentication) : Page<TaskResponse> {

        // Se fuerza el orden por fecha de creación descendente sin importar
        // el "sort" que envíe el cliente, para mantener un listado estable.
        val pageRequest = PageRequest.of(
            pageable.pageNumber,
            pageable.pageSize,
            Sort.by(Sort.Direction.DESC, "createdAt")
        )

        // El alcance por rol se resuelve aquí, no en el cliente: un USER solo
        // puede consultar sus propias tareas.
        val scopedFilter = effectiveFilter(authentication, taskFilter)

        return taskRepository
            .findAll(TaskSpecifications.withFilters(scopedFilter), pageRequest)
            .map { task -> TaskMapper.toResponse(task) }
    }


    fun getTasksSummary (authentication: Authentication) : TaskSummaryResponse {

        val scopedUserId = scopeUserId(authentication)

        val counts = if (scopedUserId == null) {
            taskRepository.countGroupedByStatus()
        } else {
            taskRepository.countGroupedByStatusByUserId(scopedUserId)
        }

        val countByStatus = counts
            .associate { count -> count.getStatus() to count.getTotal() }

        return TaskSummaryResponse(
            total = countByStatus.values.sum(),
            pending = countByStatus[TaskStatus.PENDING] ?: 0,
            inProgress = countByStatus[TaskStatus.IN_PROGRESS] ?: 0,
            done = countByStatus[TaskStatus.DONE] ?: 0,
            cancelled = countByStatus[TaskStatus.CANCELLED] ?: 0
        )
    }
}