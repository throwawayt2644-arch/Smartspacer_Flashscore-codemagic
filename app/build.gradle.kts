plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.meilluer.smartspacerFlashScore"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.meilluer.smartspacerFlashScore"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val isCi = System.getenv("CI") == "true"
            if (isCi) {
                storeFile = file(System.getenv("CM_KEYSTORE_PATH"))
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Smartspacer Plugin SDK for creating target/live widgets
    implementation("com.kieronquinn.smartspacer:sdk-plugin:1.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}