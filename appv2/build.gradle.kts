plugins { id("com.android.application") }

android {
    namespace = "com.celfii.ventas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.celfii.ventas"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.0"
    }

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
