package com.taskflow.api.repository

import com.taskflow.api.dto.task.TaskFilter
import com.taskflow.api.entity.TaskEntity
import com.taskflow.api.entity.UserEntity
import com.taskflow.api.enums.TaskStatus
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object TaskSpecifications {

    /**
     * Construye los filtros de forma dinámica: solo agrega predicados para los
     * valores presentes en la petición, evitando comparar contra parámetros nulos.
     */
    fun withFilters(taskFilter: TaskFilter): Specification<TaskEntity> {

        return Specification { root, _, criteriaBuilder ->

            val predicates = mutableListOf<Predicate>()

            taskFilter.status?.let { status ->
                predicates.add(
                    criteriaBuilder.equal(root.get<TaskStatus>("status"), status)
                )
            }

            taskFilter.userId?.let { userId ->
                predicates.add(
                    criteriaBuilder.equal(
                        root.get<UserEntity>("user").get<Long>("id"),
                        userId
                    )
                )
            }

            taskFilter.search
                ?.trim()
                ?.takeIf { search -> search.isNotEmpty() }
                ?.let { search ->
                    predicates.add(
                        criteriaBuilder.like(
                            criteriaBuilder.lower(root.get<String>("title")),
                            "%${search.lowercase()}%"
                        )
                    )
                }

            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }
}
