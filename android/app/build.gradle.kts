plugins {
    id("locator.android.application")
    id("locator.android.compose")
    id("locator.hilt")
    id("locator.store-artifact-check")
    id("locator.hardcoded-text-check")
}

android {
    namespace = "io.github.nerushkolya.locator"

    defaultConfig {
        applicationId = "io.github.nerushkolya.locator"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("internal") {
            dimension = "distribution"
            applicationIdSuffix = ".internal"
            // Port of the local server; install-phone.ps1 passes it from infra/.env.
            val port = (project.findProperty("locator.serverPort") as String?) ?: "8080"
            buildConfigField("int", "SERVER_PORT", port)
        }
        create("store") {
            dimension = "distribution"
            // Placeholder until the production stage; no code path changes it.
            buildConfigField("String", "SERVER_URL", "\"https://locator.example.org\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        checkDependencies = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:map"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    // Native map runtime (OpenGL backend); the library itself ships no renderer.
    runtimeOnly(libs.maplibre.compose.runtime.opengl)

    testImplementation(kotlin("test-junit"))
}
