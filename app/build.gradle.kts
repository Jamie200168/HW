plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.project"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    androidResources {
        noCompress += "pmtiles"
    }

    defaultConfig {
        applicationId = "com.example.project"

        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // FastAPI HTTP
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")


    // Fragment
    implementation(
        "androidx.fragment:fragment-ktx:1.9.0"
    )

    // Navigation
    implementation(
        "androidx.navigation:navigation-fragment-ktx:2.10.0"
    )

    implementation(
        "androidx.navigation:navigation-ui-ktx:2.10.0"
    )

    // Lifecycle
    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.11.0"
    )

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0"
    )

    // GPS
    implementation(
        "com.google.android.gms:play-services-location:21.4.0"
    )

    testImplementation(libs.junit)

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )
}