plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.stib.agent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.stib.agent"
        minSdk = 26
        //noinspection EditedTargetSdkVersion
        targetSdk = 36
        versionCode = 1
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ========== CORE ANDROID ==========
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.core:core:1.13.1")

    // ========== JETPACK COMPOSE ==========
    implementation("androidx.compose.ui:ui:1.10.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.1")
    implementation("androidx.compose.material:material-icons-core:1.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.compose.material:material:1.6.0")

    // ========== NAVIGATION ==========
    implementation("androidx.navigation:navigation-compose:2.9.6")

    // ========== LIFECYCLE & VIEWMODEL ==========
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")

    // ========== CAMERA X ==========
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2")

    // ========== FIREBASE ==========
    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // ========== PDF VIEWING & GENERATION ==========
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.itextpdf:itextpdf:5.5.13.3")


    // ========== FILE OPERATIONS ==========
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ========== PERMISSIONS ==========
    implementation("com.google.accompanist:accompanist-permissions:0.33.2-alpha")

    // ========== ML KIT DOCUMENT SCANNER ==========
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // ========== ML KIT TEXT RECOGNITION ==========
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // ========== IMAGES (COIL) ==========
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ========== SWIPE REFRESH ==========
    implementation("com.google.accompanist:accompanist-swiperefresh:0.32.0")

    // ========== COROUTINES ==========
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.5")

    // Firebase Messaging (FCM)
    implementation("com.google.firebase:firebase-messaging:23.4.1")
    // Compose Runtime
    implementation("androidx.compose.runtime:runtime:1.6.0")
    // Si tu n'as pas déjà Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.datastore:datastore-core:1.0.0")
    //========= Mise a jour =========
    // Pour les requêtes HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Pour parser le JSON
    implementation("com.google.code.gson:gson:2.10.1")
    // ========== TESTING ==========
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.1")
}