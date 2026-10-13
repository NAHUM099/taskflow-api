package com.taskflow.api.entity
import com.taskflow.api.enums.UserRoles
import jakarta.persistence.Column

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table


@Entity

@Table(name = "users")
class UserEntity (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id : Long? = null,

    @Column(nullable = false)
    var name : String = "",

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false)
    var passwordHash : String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role : UserRoles = UserRoles.USER,


    //mappedBy indica que la relacion esta definida del otro lado y "user" es el nombre de la variable
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    var tasks : MutableList<TaskEntity> = mutableListOf()

)










