import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    kotlin("jvm") version "1.9.20"
    id("com.apollographql.apollo3") version "3.8.2"
    id("org.openapi.generator") version "6.6.0"
    id("com.google.cloud.tools.jib") version "3.3.2"
    kotlin("plugin.serialization") version "1.8.21"

    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val kotlinVersion: String by extra("1.9.20")
val kotlinCoroutinesVersion: String by extra("1.7.0-RC")

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://packages.atlassian.com/mvn/maven-atlassian-external/")
}

dependencies {

    implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")
    implementation("com.apollographql.apollo3:apollo-rx3-support:3.8.2")
    implementation("com.apollographql.apollo3:apollo-api:3.8.2")

    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.28.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.28.0")

    implementation("io.opentelemetry:opentelemetry-semconv:1.28.0-alpha")

    implementation("com.newrelic.telemetry:telemetry-core:0.15.0")
    implementation("com.newrelic.telemetry:telemetry-http-okhttp:0.15.0")

    // Required for OpenAPI Generator
    implementation("io.swagger:swagger-annotations:1.6.11")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    // define a BOM and its version
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))

    // define any required OkHttp artifacts without version
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    testCompileOnly("junit:junit:4.11")
    compileOnly("junit:junit:4.11")


}


apollo {
    service("github") {
        packageName.set("com.github")
    }
}


tasks.test {
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    archiveFileName.set("app.jar")

    manifest.attributes.apply {


        put("Main-Class", "com.mns.dora.MainKt")
    }

}


jib {
    from {
        image = "azul/zulu-openjdk-alpine:17"
        platforms {
            platform {
                architecture = "arm64"
                os = "linux"
            }
            platform {
                architecture = "amd64"
                os = "linux"
            }
        }
    }
    to {
        image = "ghcr.io/magnayn-mns/dora"
    }
    container {

        // ??
        //labels = mapOf("org.opencontainers.image.source" to "https://github.com/magnayn-mns/dora")

        jvmFlags = listOf(
            "-Xms512m")

        mainClass = "com.mns.dora.MainKt"


        user = "1000"
        workingDirectory = "/github/workspace"
    }


}

tasks {
    build {
        dependsOn(shadowJar)
    }
}