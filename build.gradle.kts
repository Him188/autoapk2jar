import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    application
}

group = "me.him188"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "MainKt"
}