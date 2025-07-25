import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    val kotlinVersion = "2.2.0"
    kotlin("multiplatform") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("org.jetbrains.dokka") version "2.0.0"
    id("org.hildan.github.changelog") version "2.2.0"
    id("com.vanniktech.maven.publish") version "0.34.0"
    id("ru.vyarus.github-info") version "2.0.0"
}

group = "org.hildan.socketio"
description = "A Kotlin parser for Socket.IO / Engine.IO packet decoding"

github {
    user = "joffrey-bion"
    license = "MIT"
}

changelog {
    githubUser = github.user
    futureVersionTag = project.version.toString()
}

repositories {
    mavenCentral()
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(17)

    jvm()
    js {
        browser()
        nodejs()
    }
    mingwX64()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()
    wasmJs {
        browser()
        nodejs()
        d8()
    }
    wasmWasi {
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                api("org.jetbrains.kotlinx:kotlinx-io-bytestring:0.8.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set(project.name)
        description.set(project.description)

        developers {
            developer {
                id.set("joffrey-bion")
                name.set("Joffrey Bion")
                email.set("joffrey.bion@gmail.com")
            }
        }
    }
}

dokka {
    dokkaSourceSets {
        configureEach {
            sourceRoots.forEach { sourceRootDir ->
                val sourceRootRelativePath = sourceRootDir.relativeTo(rootProject.projectDir).toSlashSeparatedString()
                sourceLink {
                    localDirectory.set(sourceRootDir)
                    // HEAD points to the default branch of the repo.
                    remoteUrl("${github.repositoryUrl}/blob/HEAD/$sourceRootRelativePath")
                }
            }
        }
    }
}

// ensures slash separator even on Windows, useful for URLs creation
private fun File.toSlashSeparatedString(): String = toPath().joinToString("/")
