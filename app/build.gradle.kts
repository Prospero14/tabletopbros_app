plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

// Исключаем проблемную библиотеку bcprov с Java 21 классами из всех конфигураций
configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
}

android {
    namespace = "com.fts.ttbros"
    compileSdk = 36
    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.fts.ttbros"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":shared"))
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Используем более старую версию Firebase BOM, которая не тянет Java 21 зависимости
        implementation(platform("com.google.firebase:firebase-bom:31.2.2"))
        implementation("com.google.firebase:firebase-auth-ktx")
        implementation("com.google.firebase:firebase-firestore-ktx")
        implementation("com.google.firebase:firebase-database-ktx")
        implementation("com.google.firebase:firebase-storage-ktx")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
        
        implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
