plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("ru.werelaxe.chess.admin.MainKt")
    applicationName = "chess-admin"
}

// The launcher is chess-admin, the installation keeps the module's name: build/install/admin-cli/bin/chess-admin.
distributions {
    main {
        distributionBaseName.set("admin-cli")
    }
}

dependencies {
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    runtimeOnly(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.client.mock)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
