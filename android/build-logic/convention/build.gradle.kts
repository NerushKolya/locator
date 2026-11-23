plugins {
    `kotlin-dsl`
}

group = "io.github.nerushkolya.locator.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.roborazzi.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "locator.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "locator.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "locator.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "locator.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "locator.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("serialization") {
            id = "locator.serialization"
            implementationClass = "SerializationConventionPlugin"
        }
        register("storeArtifactCheck") {
            id = "locator.store-artifact-check"
            implementationClass = "StoreArtifactCheckConventionPlugin"
        }
        register("hardcodedTextCheck") {
            id = "locator.hardcoded-text-check"
            implementationClass = "HardcodedTextCheckConventionPlugin"
        }
        register("hilt") {
            id = "locator.hilt"
            implementationClass = "HiltConventionPlugin"
        }
    }
}
