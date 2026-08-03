plugins { id("com.android.application") }

android {
    namespace = "com.celfii.ventas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.celfii.ventas"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.6.5"
        buildConfigField("String", "CELFII_API_URL",
            "\"https://script.google.com/macros/s/AKfycbzCHzQ2OflxGg4iPSZ6yO7VxTAdxLz8iy9FMUsDvy2JhS84N4DhiKFJRCZIKtbK_ilV/exec\"")
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
