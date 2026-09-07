plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.wakh.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wakh.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Adresse du serveur de signalisation Wakh (FastAPI derrière l'ASGI
        // Django+FastAPI). À adapter à votre déploiement.
        //
        // IMPORTANT : SIGNALING_HOST doit contenir UNIQUEMENT le nom
        // d'hôte (et éventuellement le port) — ex. "mon-serveur.com" ou
        // "mon-serveur.com:8000" — JAMAIS une URL complète avec un
        // schéma ("https://..."). Le schéma est déjà déterminé
        // séparément par SIGNALING_USE_TLS ; l'inclure ici produirait une
        // URL du type "https://https://mon-serveur.com" et l'erreup6r
        // Android "Unable to resolve host "https"" (ceci reste protégé
        // au runtime par util/ServerConfig.sanitizeSignalingHost, mais
        // autant configurer la bonne valeur dès le départ).
        buildConfigField("String", "SIGNALING_HOST", "\"wakh.djibthiong.com:9090\"")
        buildConfigField("boolean", "SIGNALING_USE_TLS", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose (BOM = versions alignées automatiquement)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // DataStore (préférences utilisateur : nom + numéro)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room (messages + contacts stockés localement)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Réseau : WebSocket de signalisation + upload/download REST (file d'attente hors-ligne)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // WebRTC (P2P DataChannel) — build Maven Central maintenu, remplace
    // l'ancien org.webrtc:google-webrtc retiré du dépôt Google.
    implementation("io.github.webrtc-sdk:android:125.6422.06.1")

    // Permissions runtime simplifiées
    implementation("androidx.core:core:1.13.1")

    // Chargement/mise en cache des vignettes d'images et de vidéos dans les bulles de message
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")

    // Lecture vidéo intégrée dans la visionneuse plein écran (voir ui/chat/components/MediaViewer.kt)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
