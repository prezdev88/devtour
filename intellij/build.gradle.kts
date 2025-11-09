plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.prezdev"
version = "0.2.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.2")
    type.set("IC")
    plugins.set(emptyList())
}

kotlin {
    jvmToolchain(17)
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("")
    }

    runPluginVerifier {
        ideVersions.set(listOf("IC-2024.2"))
    }

    signPlugin {
        enabled = false
    }

    publishPlugin {
        enabled = false
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
