plugins {
	kotlin("jvm")
	application
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("tools.jackson.module:jackson-module-kotlin:3.1.5")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
	applicationName = "notesync"
	mainClass = "io.github.mcpejs.notesync.cli.MainKt"
}

tasks.withType<Test> {
	useJUnitPlatform()
}
