plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    kotlin("kapt")
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" // 추가
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}

android {
    namespace = "com.robsonmartins.androidmidisynth"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.robsonmartins.androidmidisynth"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    ndkVersion = "29.0.14206865"
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.md,LICENSE-notice.md,NOTICE.md}"
            // JAXB 라이브러리에서 중복되는 META-INF 파일 제외
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
}

// kapt도 Java 21을 사용하도록 설정 (backdrop 라이브러리 호환성)
kapt {
    javacOptions {
        option("-source", "21")
        option("-target", "21")
    }
    useBuildCache = true
    correctErrorTypes = true
}

dependencies {
    implementation("io.github.kyant0:backdrop:1.0.2")
    implementation("io.github.kyant0:capsule:2.1.2")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    // Compose 의존성
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // 디버그용 (선택사항)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // TikXML 의존성 (MusicXML-Android 라이브러리용)
    implementation("com.tickaroo.tikxml:annotation:0.8.13")
    implementation("com.tickaroo.tikxml:core:0.8.13")
    implementation("com.tickaroo.tikxml:retrofit-converter:0.8.13")
    kapt("com.tickaroo.tikxml:processor:0.8.13")

    // Okio (TikXML이 의존)
    implementation("com.squareup.okio:okio:3.2.0")

    // Room (KSP 사용)
    ksp(libs.room.compiler)
}

