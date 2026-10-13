package com.taskflow.api.entity

import com.taskflow.api.enums.TaskStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime


@Entity
@Table(name = "tasks")
class TaskEntity (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // el ID se pone null porque es la DB quien lo genera
    var id : Long? = null,

    @Column(nullable = false)
    var title : String  = "",

    @Column(nullable = false)
    var description : String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TaskStatus = TaskStatus.PENDING,

    @Column(nullable = false)
    var createdAt : LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user : UserEntity = UserEntity()

    )
