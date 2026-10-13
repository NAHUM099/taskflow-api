package com.taskflow.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TaskflowApiApplication

fun main(args: Array<String>) {
	runApplication<TaskflowApiApplication>(*args)
}
