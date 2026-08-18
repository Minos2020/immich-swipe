import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.minos2020.immichswipe"
    
    // --- Chargement des secrets ---
    val secretsFile = rootProject.file("secrets.properties")
    val secrets = Properties()
    if (secretsFile.exists()) {
        secrets.load(FileInputStream(secretsFile))
    }
    // ------------------------------

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.minos2020.immichswipe"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "3.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            
            // Valeurs vides ou de test pour la version FOSS
            manifestPlaceholders["admobAppId"] = "unused"
            buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"\"")
        }
        create("play") {
            dimension = "distribution"
            versionNameSuffix = "-play"
            
            // Injection des vrais secrets depuis secrets.properties
            // Fallback sur les IDs de test si le fichier est manquant
            val appId = secrets.getProperty("admob.app.id") ?: "ca-app-pub-3940256099942544~3347511713"
            val adUnitId = secrets.getProperty("admob.native.ad.unit.id") ?: "ca-app-pub-3940256099942544/2247696110"
            
            manifestPlaceholders["admobAppId"] = appId
            buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"$adUnitId\"")
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // Permet de générer la classe BuildConfig contenant nos IDs
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Manually added
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0") // Pour les miniatures vidéo

    // Media3 pour la lecture vidéo
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Publicité (Play Store uniquement)
    "playImplementation"(libs.play.services.ads)
    "playImplementation"(libs.userMessagingPlatform)
}
