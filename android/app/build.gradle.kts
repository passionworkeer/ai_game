plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Disable KSP incremental processing to avoid Windows incremental-cache corruption
// when generated dirs are locked or partially deleted by clean runs.
ksp {
    arg("ksp.incremental", "false")
    arg("room.schemaLocation", "$projectDir/schemas")
    // Also pass as task arguments as fallback
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += listOf("-Xskip-metadata-version-check")
        }
    }
}

android {
    namespace = "com.aiyougame.companion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aiyougame.companion"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // ── Native build: llama.cpp JNI ────────────────────────
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
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
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ── Ollama engine (OllamaEngineImpl) ─────────────────────────
    // Set to true to use Ollama HTTP API instead of native JNI.
    // When enabled, configure OLLAMA_URL and OLLAMA_MODEL below.
    val OLLAMA_ENABLED = true

    if (OLLAMA_ENABLED) {
        // Android emulator → host Ollama: 10.0.2.2 is emulator's alias for host localhost
        // Real device on same LAN → replace with your PC's local IP (e.g. 192.168.1.x)
        defaultConfig {
            buildConfigField("String", "OLLAMA_URL", "\"http://10.0.2.2:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"gemma-4-e2b-uncensored\"")
            buildConfigField("Boolean", "OLLAMA_ENABLED", "true")
        }
    } else {
        defaultConfig {
            buildConfigField("String", "OLLAMA_URL", "\"http://10.0.2.2:11434\"")
            buildConfigField("String", "OLLAMA_MODEL", "\"gemma-4-e2b-uncensored\"")
            buildConfigField("Boolean", "OLLAMA_ENABLED", "false")
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    val roomVersion = "2.5.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Network & Concurrent
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // Image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Security: EncryptedSharedPreferences for AES key storage
    implementation("androidx.security:security-crypto:1.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("androidx.room:room-testing:2.5.2")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.12.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.hilt:hilt-navigation-testing:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Disable binary result format to avoid Windows file locking issues with Robolectric
tasks.withType<Test> {
    // Uses the standard XML reporter instead of binary to avoid Windows file lock issues
}
