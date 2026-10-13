package com.taskflow.api.exception.classException

import java.lang.RuntimeException

class EmailAlreadyExistsException (message : String) : RuntimeException(message)