plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
    id("app.cash.paparazzi") version "1.3.5"
}

android {
    namespace = "io.github.dark1ltg.meridian"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.dark1ltg.meridian"
        minSdk = 26
        targetSdk = 34
        versionCode = 135
        versionName = "1.3.5"
        ndk {
            val abi = (project.findProperty("abi") as String?)?.trim().orEmpty()
            abiFilters += if (abi.isNotEmpty()) {
                listOf(abi)
            } else {
                listOf("arm64-v8a", "x86_64")
            }
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.media3.common.util.UnstableApi")
    }
    buildFeatures {
        compose = true
    }
    ndkVersion = "26.3.11579264"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            install("mutagen")
            install("numpy")
        }
    }
}

val engineRoot = rootProject.projectDir.resolve("../meridian")
val pythonOut = layout.projectDirectory.dir("src/main/python/meridian")

val syncMeridianPython by tasks.registering(Sync::class) {
    from(engineRoot) {
        exclude("ui/**")
        exclude("app.py")
        exclude("player.py")
        exclude("scanner.py")
        exclude("__main__.py")
    }
    into(pythonOut)
}

afterEvaluate {
    tasks.matching { it.name.contains("Python") && it.name != "syncMeridianPython" }.configureEach {
        dependsOn(syncMeridianPython)
    }
    tasks.named("preBuild") { dependsOn(syncMeridianPython) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-decoder:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
