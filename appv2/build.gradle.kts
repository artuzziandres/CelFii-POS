plugins { id("com.android.application") }

val apiToken = providers.gradleProperty("CELFII_API_TOKEN").orElse("")

android {
    namespace = "com.celfii.ventas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.celfii.ventas"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "0.6.3"
        buildConfigField("String", "CELFII_API_URL", "\"https://script.google.com/macros/s/AKfycbwn9Btk_JoFU6KY-tPmQk2Ks7962bIYJuRLILJw-cj79lW-IZs2NDkku5xuK4xinjD_/exec\"")
        buildConfigField("String", "CELFII_API_TOKEN", "\"${apiToken.get()}\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].res.srcDirs(
        "src/main/res",
        rootProject.file("app/src/main/res")
    )
}

dependencies {
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
