plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.14"
	id("io.spring.dependency-management") version "1.1.7"
	jacoco
}

group = "com.taskflow"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {

    //spring boot
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")

    //kotlin
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

    //database
    runtimeOnly("org.postgresql:postgresql")

    //migraciones del esquema (versión gestionada por Spring Boot -> Flyway 11.7.2)
    implementation("org.flywaydb:flyway-core")
    //desde Flyway 10 el soporte de PostgreSQL vive en un módulo aparte
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    //rate limiting distribuido (opcional: solo se usa con login-rate-limit.store=redis)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    //development
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    //testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// base de datos en memoria para el test de carga de contexto
	testRuntimeOnly("com.h2database:h2")

    //JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.7")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.7")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.7")


}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

jacoco {
	// Versión compatible con Java 21 y con el bytecode generado por Kotlin 1.9.
	toolVersion = "0.8.12"
}

tasks.withType<Test> {
	useJUnitPlatform()
	// Ejecutar `test` (o `build`) deja siempre el reporte de cobertura listo.
	finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)

	reports {
		xml.required.set(true)
		html.required.set(true)
		csv.required.set(false)
	}
}

/**
 * Atajo explícito para la validación local y CI:
 * `./gradlew coverage` ejecuta los tests y genera HTML + XML.
 */
tasks.register("coverage") {
	group = "verification"
	description = "Ejecuta los tests y genera el reporte de cobertura JaCoCo (HTML y XML)."
	dependsOn(tasks.jacocoTestReport)
}
