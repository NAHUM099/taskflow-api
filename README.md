# TaskFlow API

API REST para la gestión de tareas, usuarios y autenticación del proyecto TaskFlow.

## Tecnologías

- Kotlin
- Spring Boot
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- JWT
- Gradle
- Docker y Jenkins para integración continua

## Funcionalidades

- Gestión de usuarios.
- Autenticación mediante JWT.
- Gestión de tareas.
- Aislamiento de tareas por rol: `USER` solo consulta y mueve sus tareas; `ADMIN` y `MODERATOR` ven todas.
- Actualización del estado de las tareas.
- Control de acceso según roles.

## Requisitos

- Java 21
- PostgreSQL
- Gradle Wrapper incluido en el proyecto.

## Configuración

La aplicación utiliza variables de entorno para configurar la conexión a la base de datos y la autenticación.

Variables necesarias:

- `DB_USERNAME`: usuario de PostgreSQL.
- `DB_PASSWORD`: contraseña de PostgreSQL.
- `JWT_SECRET`: secreto utilizado para firmar los tokens JWT.

La configuración de la base de datos se encuentra en `application.yaml`.

## Ejecución local

Configura las variables de entorno y asegúrate de que PostgreSQL esté disponible en la dirección definida en la configuración.

En Windows, ejecuta:

```powershell
.\gradlew.bat bootRun
```

## Pruebas

Para ejecutar las pruebas automatizadas:

```powershell
.\gradlew.bat clean test
```

## Integración continua

El proyecto utiliza Jenkins para ejecutar la compilación y las pruebas automatizadas mediante el archivo `Jenkinsfile`.

El pipeline obtiene el código desde GitHub, ejecuta las pruebas con Gradle y registra sus resultados.

## Estado del proyecto

Proyecto personal de portafolio orientado al desarrollo backend con Kotlin y Spring Boot.
