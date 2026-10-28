plugins {
	java
}

group = "ai.alfred"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Same Jackson as the daemon, versions from the Spring Boot BOM.
	implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
	implementation("tools.jackson.core:jackson-databind")
	testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testImplementation("org.assertj:assertj-core")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
	useJUnitPlatform()
}

// A runnable jar with its dependencies, started by bin/alfred.
tasks.jar {
	archiveFileName = "alfred-client.jar"
	manifest {
		attributes("Main-Class" to "ai.alfred.client.AlfredClient")
	}
	from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
	exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/*/module-info.class",
		"module-info.class")
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
