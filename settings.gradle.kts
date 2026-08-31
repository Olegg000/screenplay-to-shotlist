// Позволяет Gradle самому скачать JDK 21, если в системе стоит другая версия.
plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "screenplay-to-shotlist"
