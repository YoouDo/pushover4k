import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
    kotlin("kapt") version "1.8.22"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    idea
    `java-library`
    `maven-publish`
    signing
    jacoco
    id("org.sonarqube") version "5.0.0.4638"
}

group = "de.kleinkop"
version = "1.3.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    withJavadocJar()
    withSourcesJar()
}

idea {
    module {
        inheritOutputDirs = false
        outputDir = layout.buildDirectory.dir("classes/kotlin/main").get().asFile
        testOutputDir = layout.buildDirectory.dir("classes/kotlin/test").get().asFile
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

val junitVersion: String by project
val kotlinxSerialization: String by project
val resilience4jVersion: String by project

dependencies {
    // kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerialization")

    implementation("io.github.resilience4j:resilience4j-all:$resilience4jVersion")
    // logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // metrics
    implementation("io.micrometer:micrometer-core:1.13.0")

    // testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("io.mockk:mockk:1.13.11")
    testImplementation("com.github.tomakehurst:wiremock-jre8:3.0.1")

    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_17.toString()
        javaParameters = true
    }
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])

            pom {
                name.set("pushover client")
                description.set("A Kotlin client for Pushover messaging")
                url.set("https://github.com/YoouDo/Pushover4K")
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("YoouDo")
                        name.set("Udo Kleinkop")
                        email.set("udo@kleinkop.de")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:YoouDo/Pushover4K.git")
                    developerConnection.set("scm:git:git@github.com:YoouDo/Pushover4K.git")
                    url.set("git@github.com:YoouDo/Pushover4K.git")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USER") ?: return@credentials
                password = System.getenv("OSSRH_PASSWORD") ?: return@credentials
            }
        }
    }
}

signing {
    val key = System.getenv("SIGNING_KEY") ?: return@signing
    val password = System.getenv("SIGNING_PASSWORD") ?: return@signing
    val publishing: PublishingExtension by project

    useInMemoryPgpKeys(key, password)
    sign(publishing.publications["mavenJava"])
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
    }
}

sonar {
    properties {
        property("sonar.projectKey", "YoouDo_pushover4k")
        property("sonar.organization", "yooudo")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}
