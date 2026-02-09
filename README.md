# Simple gradle plugin to publish maven publication to Sonatype Central Portal

This plugin builds and uploads existing maven publication to Maven Central Repository using Portal Publisher API

## Usage:
1. Add and apply plugin
2. Configure extension:
```kotlin
extensions.configure(SonatypeCentralPublishExtension::class) {
        publication = publishing.publications["main"] as MavenPublication // publication to use
        username = System.getenv("MAVEN_CENTRAL_USERNAME")
        password = System.getenv("MAVEN_CENTRAL_PASSWORD")
        publishingType = PublishingType.USER_MANAGED // or AUTOMATIC to publish on ready
    }
```
3. Configure your publication with correct POM and setup signing
4. Run task `publish<publication name>ToSonatype`

## Adding to project:

Setup JitPack
`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        maven("https://jitpack.io")
        gradlePluginPortal()
    }

    // optional resolution strategy to use correct id
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "xyz.bobkinn.sonatype-publisher") {
                useModule("com.github.BoBkiNN:sonatype-maven-central-publisher:${requested.version}")
            }
        }
    }
}
```

`build.gradle.kts`:
```kotlin
plugins {
    id("xyz.bobkinn.sonatype-publisher") version "1.2.5"
}
```

## About fork:
