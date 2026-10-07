plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The computer player. Kept out of `core` on purpose: the web client never runs it, so the
// JS build stays engine-free. Mobile targets can be added here when an offline bot is needed.
kotlin {
    jvmToolchain(21)

    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
