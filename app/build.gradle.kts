import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.net.URI

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.googleKsp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.grindrplus"
    compileSdk = 35

    defaultConfig {
        val grindrVersionName = listOf("25.15.0")
        val grindrVersionCode = listOf(144041)
        val gitCommitHash = getGitCommitHash() ?: "unknown"

        applicationId = "com.grindrplus"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "4.6.0-${grindrVersionName.let { it.joinToString("_") }}_$gitCommitHash"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String[]",
            "TARGET_GRINDR_VERSION_NAMES",
            grindrVersionName.let { it.joinToString(prefix = "{", separator = ", ", postfix = "}") { version -> "\"$version\"" } }
        )

        buildConfigField(
            "int[]",
            "TARGET_GRINDR_VERSION_CODES",
            grindrVersionCode.let { it.joinToString(prefix = "{", separator = ", ", postfix = "}") { code -> "$code" } }
        )
    }

    buildFeatures {
        buildConfig = true
        aidl = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.configureEach {
        outputs.configureEach {
            val sanitizedVersionName = versionName.trim('_')
            (this as BaseVariantOutputImpl).outputFileName =
                "GPLS46${sanitizedVersionName}-${name}.apk"
        }
    }
}

    dependencies {
        // Core Android dependencies
        compileOnly("de.robv.android.xposed:api:82")


        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.androidx.coordinatorlayout)
        implementation(libs.material)

        // Networking and storage
        implementation(libs.square.okhttp)
        implementation(libs.androidx.datastore.preferences)
        implementation(libs.androidx.room.runtime)
        implementation(libs.androidx.room.ktx)
        ksp(libs.androidx.room.compiler)

        // Compose dependencies
        val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
        implementation(composeBom)
        implementation(libs.androidx.material3)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.navigation.compose)
        implementation(libs.androidx.ui.tooling.preview)
        debugImplementation(libs.androidx.ui.tooling)
        implementation(libs.androidx.material.icons.core)
        implementation(libs.androidx.material.icons.extended)
        implementation(libs.coil.compose)
        implementation(libs.coil.network.okhttp)
        implementation(libs.coil.gif)
        implementation(libs.compose.markdown)

        // Utility libraries
        implementation(libs.plausible.android.sdk)
        implementation(libs.timber)
        implementation(libs.fetch2)
        implementation(libs.fetch2okhttp)
        implementation(libs.rootbeer.lib)
        implementation(libs.zip.android) {
            artifact {
                type = "aar"
            }
        }
        implementation(libs.zipalign.java)
        implementation(libs.arsclib)
        compileOnly(libs.bcprov.jdk18on)

        // Kotlin Coroutines - IMPORTANT: Keep test dependencies separate
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.coroutines.android)

        // Test dependencies - these should NEVER leak into production
        //testImplementation(libs.kotlinx.coroutines.test)
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
        androidTestImplementation("androidx.test:runner:1.5.2")

        // LSPatch for module integration changed from implementation
        compileOnly(fileTree("libs") { include("lspatch.jar") })
    }

    val kotlinxCoroutinesVersion = "1.10.2"
    val kotlinStdlibVersion = "1.9.23" // Align with your Kotlin plugin version

    configurations.all {
        resolutionStrategy {
            // Enforce single version for Kotlin Stdlib
            // Use a version compatible with your project's Kotlin compiler/plugin version
            force("org.jetbrains.kotlin:kotlin-stdlib:$kotlinStdlibVersion")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinStdlibVersion")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinStdlibVersion")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinStdlibVersion")

            // Enforce single version for Coroutines (to fix the 1.5.0 vs 1.10.2 conflict)
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinxCoroutinesVersion")
        }
    }

    tasks.register("setupLSPatch") {
        doLast {
            val jarUrl =
                Regex("https://nightly\\.link/JingMatrix/LSPatch/workflows/main/master/lspatch-debug-[^.]++.zip")
                    .find(
                        URI("https://nightly.link/JingMatrix/LSPatch/workflows/main/master?preview").toURL()
                            .readText()
                    )!!.value

            providers.exec {
                commandLine("mkdir", "-p", "/tmp/lspatch")
            }.result.get()

            providers.exec {
                commandLine("wget", jarUrl, "-O", "/tmp/lspatch/lspatch.zip")
            }.result.get()

            providers.exec {
                commandLine("unzip", "-o", "/tmp/lspatch/lspatch.zip", "-d", "/tmp/lspatch")
            }.result.get()

            val jarPath =
                File("/tmp/lspatch").listFiles()?.find { it.name.contains("jar-") }?.absolutePath

            providers.exec {
                commandLine(
                    "unzip",
                    "-o",
                    jarPath,
                    "assets/lspatch/so*",
                    "-d",
                    "${project.projectDir}/src/main/"
                )
            }.result.get()

            providers.exec {
                commandLine("mv", jarPath, "${project.projectDir}/libs/lspatch.jar")
            }.result.get()

            // Clean up ALL conflicting classes in lspatch.jar
            listOf(
                "com/google/common/util/concurrent/ListenableFuture.class",
                "com/google/errorprone/annotations/*",
                "META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler",
                "META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory",
                "kotlinx/coroutines/test/*"
            ).forEach { pattern ->
                providers.exec {
                    commandLine("zip", "-d", "${project.projectDir}/libs/lspatch.jar", pattern)
                    isIgnoreExitValue = true  // Some patterns might not exist
                }.result.get()
            }
        }
    }

    fun getGitCommitHash(): String? {
        return try {
            val isGitRepo = providers.exec {
                commandLine("git", "rev-parse", "--is-inside-work-tree")
                isIgnoreExitValue = true
            }.result.get().exitValue == 0

            if (isGitRepo) {
                providers.exec {
                    commandLine("git", "rev-parse", "--short", "HEAD")
                }.standardOutput.asText.get().trim()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    tasks.register("printVersionInfo") {
        doLast {
            val versionName = android.defaultConfig.versionName
            println("VERSION_INFO: GrindrPlus v$versionName")
        }
    }