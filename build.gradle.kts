apply(plugin = "io.github.gradle-nexus.publish-plugin")
apply(from = "${rootDir}/publish/publish-root.gradle")

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.12.0")
        classpath("io.github.gradle-nexus:publish-plugin:2.0.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
        classpath("org.jacoco:org.jacoco.core:0.8.13")
    }
}

allprojects {
    group = "com.salesforce.mobilesdk"
    version = "14.0.0"

    // Configure JVM toolchain to ensure consistent Java/Kotlin compilation targets
    plugins.withType<JavaBasePlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
    }

    // Ensure that we do not use newer language features that would make the SDK incompatible with
    // apps that do not target the latest version of Kotlin.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += arrayOf("-Xopt-in=kotlin.RequiresOptIn")
            apiVersion = "1.6"
            languageVersion = "1.6"
        }
    }

    // Set Java compatibility to match Kotlin JVM target
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}
