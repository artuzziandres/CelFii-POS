plugins {
    id("com.android.application")
}

val apiUrl = providers.gradleProperty("CELFII_API_URL").orElse("")
val apiToken = providers.gradleProperty("CELFII_API_TOKEN").orElse("")

android {
    namespace = "com.celfii.pos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.celfii.pos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "CELFII_API_URL", "\"${apiUrl.get()}\"")
        buildConfigField("String", "CELFII_API_TOKEN", "\"${apiToken.get()}\"")
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
