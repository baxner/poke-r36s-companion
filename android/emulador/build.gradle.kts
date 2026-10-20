plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// El emulador de GBA (mGBA 0.10.5) para el intercambio real y "seguir jugando en el teléfono".
// El código de mGBA lo baja scripts/bajar-mgba.sh a third_party/mgba/ (no se versiona).
android {
    namespace = "com.baxner.r36scompanion.emulador"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // El S24 (arm64) y el emulador de la iMac (x86_64).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DMGBA_DIR=${rootProject.projectDir.parentFile}/third_party/mgba"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
