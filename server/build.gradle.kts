plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":core"))
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.onevonegames.server.MainKt")
    applicationName = "game-server" // the start scripts in the zip are bin/game-server(.bat)
}
