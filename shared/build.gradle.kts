plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }
    
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.kotlinx.coroutines.get()}")
                
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.kotlinx.serialization.get()}")
                
                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.kotlinx.datetime.get()}")
                
                // Compose Multiplatform
                implementation(platform("org.jetbrains.compose:compose-bom:${libs.versions.compose.bom.get()}"))
                implementation("org.jetbrains.compose.ui:ui")
                implementation("org.jetbrains.compose.ui:ui-tooling-preview")
                implementation("org.jetbrains.compose.material3:material3")
                implementation("org.jetbrains.compose.runtime:runtime")
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation(platform("com.google.firebase:firebase-bom:31.2.2"))
                implementation("com.google.firebase:firebase-auth-ktx")
                implementation("com.google.firebase:firebase-firestore-ktx")
                implementation("com.google.firebase:firebase-storage-ktx")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${libs.versions.kotlinx.coroutines.get()}")
            }
        }
        
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                // iOS-specific dependencies will be added here
            }
        }
        
        val iosX64Main by getting {
            dependsOn(iosMain)
        }
        
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
    }
}

