plugins {
    id("locator.android.feature")
}

android {
    namespace = "io.github.nerushkolya.locator.feature.map"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.maplibre.compose)
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(kotlin("test-junit"))
}
