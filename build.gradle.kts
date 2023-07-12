plugins {
    id("java")
    kotlin("jvm") version "1.8.21"
    id("com.apollographql.apollo3") version "3.8.2"
    id("org.openapi.generator") version "6.6.0"
    id("com.google.cloud.tools.jib") version "3.3.2"
}

val kotlinVersion: String by extra("1.8.21")
val kotlinCoroutinesVersion: String by extra("1.7.0-RC")

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    implementation("com.apollographql.apollo3:apollo-runtime:3.8.2")
    implementation("com.apollographql.apollo3:apollo-rx3-support:3.8.2")
    implementation("com.apollographql.apollo3:apollo-api:3.8.2")


    implementation("com.newrelic.telemetry:telemetry-core:0.15.0")
    implementation("com.newrelic.telemetry:telemetry-http-okhttp:0.15.0")

    // Required for OpenAPI Generator
    implementation("io.swagger:swagger-annotations:1.6.11")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")

    testCompileOnly("junit:junit:4.11")
    compileOnly("junit:junit:4.11")


}


apollo {
    service("github") {
        packageName.set("com.github")
    }
}

openApiGenerate {
    generatorName.set("java")
    inputSpec.set("src/main/openapi/new_relic_v2.yml")
    outputDir.set(project.layout.buildDirectory.file("generated/$name").map { it.asFile.absolutePath })
  //  ignoreFileOverride.set(project.layout.projectDirectory.file(".openapi-generator-ignore").asFile.absolutePath)
    cleanupOutput.set(true)
    openapiNormalizer.set(mapOf("REF_AS_PARENT_IN_ALLOF" to "true"))
    // see https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/java.md for a description of each configuration option
    generateApiTests.set(false)
    configOptions.set(mapOf(
        "library" to "apache-httpclient",
        "dateLibrary" to "java8",
        "hideGenerationTimestamp" to "true",
        "openApiNullable" to "false",
        "useBeanValidation" to "false",
        "disallowAdditionalPropertiesIfNotPresent" to "false",
        "additionalModelTypeAnnotations" to  "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)",
        "sourceFolder" to "",  // makes IDEs like IntelliJ more reliably interpret the class packages.
        "containerDefaultToNull" to "true",

    ))
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        java {
            srcDir(tasks.openApiGenerate)
        }
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