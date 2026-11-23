plugins {
    id("locator.jvm.library")
    id("locator.serialization")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
}
