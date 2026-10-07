plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    jvm()

    js {
        // The library has no DOM dependencies, so the Node flavour of the output is
        // what the web client consumes (ES modules + TypeScript definitions).
        nodejs {
            testTask {
                useMocha {
                    // Engine tests deliberately use their full thinking budget (up to a few seconds).
                    timeout = "20s"
                }
            }
        }
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
        compilerOptions {
            target.set("es2015")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
