plugins {
    id("locator.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
}
