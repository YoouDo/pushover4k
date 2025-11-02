import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ktlint)
    idea
    `java-library`
    `maven-publish`
    signing
    jacoco
    alias(libs.plugins.sonarqube)
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

dependencies {
    // kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization)

    implementation(libs.resilience4j)

    // logging
    implementation(libs.kotlin.logging)

    // metrics
    implementation(libs.micrometer)

    // testing
    testImplementation(libs.junit.api)
    testImplementation(libs.mockk)
    testImplementation(libs.wiremock)
    testImplementation(libs.kotest.assertions)

    testRuntimeOnly(libs.bundles.junit.runtime)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_17)
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

tasks.named("sonar") {
    dependsOn(tasks.jacocoTestReport)
}

sonar {
    properties {
        property("sonar.projectKey", "YoouDo_pushover4k")
        property("sonar.organization", "yooudo")
        property("sonar.host.url", "https://sonarcloud.io")

        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get(),
        )
    }
}
