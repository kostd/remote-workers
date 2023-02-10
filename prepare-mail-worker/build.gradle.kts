import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.7.1"
	id("io.spring.dependency-management") version "1.1.0"
	kotlin("jvm") version "1.7.22"
	kotlin("plugin.spring") version "1.7.22"
}

group = "org.kostd.bpms"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

object Versions {
	const val camunda = "7.17.0"
	const val guava = "31.1-jre"
}


repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")

	implementation("org.camunda.community.rest:camunda-platform-7-rest-client-spring-boot-starter:${Versions.camunda}")
	implementation("org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-external-task-client:${Versions.camunda}")


	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	implementation("com.google.guava:guava:${Versions.guava}")
	// ортодоксальный inject
	implementation("javax.inject:javax.inject:1")

	// #TODO: щас ловим classnotfound на JAXBException. Надо бы нормально поразбираться
	implementation("jakarta.xml.bind:jakarta.xml.bind-api:2.3.3")

}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
