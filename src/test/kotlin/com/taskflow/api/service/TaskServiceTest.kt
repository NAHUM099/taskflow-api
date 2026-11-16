package com.taskflow.api.service

import com.taskflow.api.dto.task.StatusRequest
import com.taskflow.api.dto.task.TaskFilter
import com.taskflow.api.entity.TaskEntity
import com.taskflow.api.entity.UserEntity
import com.taskflow.api.enums.TaskStatus
import com.taskflow.api.enums.UserRoles
import com.taskflow.api.exception.classException.TaskAccessDeniedException
import com.taskflow.api.repository.TaskRepository
import com.taskflow.api.repository.TaskStatusCount
import com.taskflow.api.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.Optional

class TaskServiceTest {

    private val taskRepository: TaskRepository = mock(TaskRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val taskService = TaskService(taskRepository, userRepository)

    @BeforeEach
    fun setUp() {
        `when`(userRepository.findByEmail(USER_EMAIL)).thenReturn(user(USER_ID))
    }

    @Test
    fun `resume los conteos globales para un ADMIN`() {
        `when`(taskRepository.countGroupedByStatus()).thenReturn(
            listOf(
                countOf(TaskStatus.PENDING, 2),
                countOf(TaskStatus.IN_PROGRESS, 1),
                countOf(TaskStatus.DONE, 4)
            )
        )

        val summary = taskService.getTasksSummary(authentication("ROLE_ADMIN"))

        assertEquals(7, summary.total)
        assertEquals(2, summary.pending)
        assertEquals(1, summary.inProgress)
        assertEquals(4, summary.done)
        assertEquals(0, summary.cancelled)
    }

    @Test
    fun `devuelve ceros cuando no hay tareas registradas`() {
        `when`(taskRepository.countGroupedByStatus()).thenReturn(emptyList())

        val summary = taskService.getTasksSummary(authentication("ROLE_ADMIN"))

        assertEquals(0, summary.total)
        assertEquals(0, summary.pending)
        assertEquals(0, summary.inProgress)
        assertEquals(0, summary.done)
        assertEquals(0, summary.cancelled)
    }

    @Test
    fun `un USER ve el resumen solo de sus tareas`() {
        `when`(taskRepository.countGroupedByStatusByUserId(USER_ID)).thenReturn(
            listOf(
                countOf(TaskStatus.PENDING, 2),
                countOf(TaskStatus.CANCELLED, 1)
            )
        )

        val summary = taskService.getTasksSummary(authentication("ROLE_USER"))

        assertEquals(3, summary.total)
        assertEquals(2, summary.pending)
        assertEquals(0, summary.inProgress)
        assertEquals(0, summary.done)
        assertEquals(1, summary.cancelled)
        verify(taskRepository, never()).countGroupedByStatus()
    }

    @Test
    fun `un USER solo puede consultar su propio userId`() {
        assertEquals(USER_ID, taskService.scopeUserId(authentication("ROLE_USER")))
    }

    @Test
    fun `ADMIN y MODERATOR no se restringen a un usuario`() {
        assertNull(taskService.scopeUserId(authentication("ROLE_ADMIN")))
        assertNull(taskService.scopeUserId(authentication("ROLE_MODERATOR")))
    }

    @Test
    fun `un USER no puede ampliar el alcance con el parametro userId`() {
        val filter = TaskFilter(status = TaskStatus.PENDING, userId = 99L, search = "informe")

        val scoped = taskService.effectiveFilter(authentication("ROLE_USER"), filter)

        assertEquals(USER_ID, scoped.userId)
        assertEquals(TaskStatus.PENDING, scoped.status)
        assertEquals("informe", scoped.search)
    }

    @Test
    fun `ADMIN conserva el filtro por userId solicitado`() {
        val filter = TaskFilter(status = null, userId = 99L)

        assertEquals(filter, taskService.effectiveFilter(authentication("ROLE_ADMIN"), filter))
    }

    @Test
    fun `un USER puede mover el estado de su propia tarea`() {
        val task = task(id = 3L, ownerId = USER_ID)
        `when`(taskRepository.findById(3L)).thenReturn(Optional.of(task))
        `when`(taskRepository.save(task)).thenReturn(task)

        val response = taskService.patchStatusTask(
            3L,
            StatusRequest(TaskStatus.IN_PROGRESS),
            authentication("ROLE_USER")
        )

        assertEquals(TaskStatus.IN_PROGRESS, response.status)
    }

    @Test
    fun `un USER no puede mover el estado de una tarea ajena`() {
        val task = task(id = 3L, ownerId = 8L)
        `when`(taskRepository.findById(3L)).thenReturn(Optional.of(task))

        assertThrows(TaskAccessDeniedException::class.java) {
            taskService.patchStatusTask(
                3L,
                StatusRequest(TaskStatus.IN_PROGRESS),
                authentication("ROLE_USER")
            )
        }

        verify(taskRepository, never()).save(task)
    }

    @Test
    fun `un ADMIN puede mover el estado de cualquier tarea`() {
        val task = task(id = 3L, ownerId = 8L)
        `when`(taskRepository.findById(3L)).thenReturn(Optional.of(task))
        `when`(taskRepository.save(task)).thenReturn(task)

        val response = taskService.patchStatusTask(
            3L,
            StatusRequest(TaskStatus.IN_PROGRESS),
            authentication("ROLE_ADMIN")
        )

        assertEquals(TaskStatus.IN_PROGRESS, response.status)
    }

    private fun authentication(role: String, email: String = USER_EMAIL): Authentication =
        UsernamePasswordAuthenticationToken(email, null, listOf(SimpleGrantedAuthority(role)))

    private fun user(id: Long): UserEntity =
        UserEntity(
            id = id,
            name = "Ana Torres",
            email = USER_EMAIL,
            passwordHash = "hash",
            role = UserRoles.USER
        )

    private fun task(id: Long, ownerId: Long): TaskEntity =
        TaskEntity(
            id = id,
            title = "Tarea $id",
            description = "Descripcion de la tarea $id",
            user = UserEntity(id = ownerId, email = "owner$ownerId@taskflow.dev")
        )

    private fun countOf(status: TaskStatus, total: Long): TaskStatusCount =
        object : TaskStatusCount {
            override fun getStatus(): TaskStatus = status

            override fun getTotal(): Long = total
        }

    private companion object {
        const val USER_ID = 7L
        const val USER_EMAIL = "ana@taskflow.dev"
    }
}