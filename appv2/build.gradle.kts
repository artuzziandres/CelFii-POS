plugins { id("com.android.application") }

// Versión con historial mensual sincronizado entre todos los dispositivos.

android {
    namespace = "com.celfii.ventas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.celfii.ventas"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "0.6.7"
        buildConfigField("String", "CELFII_API_URL",
            "\"https://script.google.com/macros/s/AKfycbxfd_84OPqT-tTF_ZhO6zBYjGGiGDLsk_XoTffD1NMugaXbhltSUZ-YfncredvgSCBI/exec\"")
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
