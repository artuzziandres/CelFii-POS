plugins { id("com.android.application") }

// Versión con historial mensual sincronizado entre todos los dispositivos.

android {
    namespace = "com.celfii.ventas"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.celfii.ventas"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "0.9.1"
        buildConfigField("String", "CELFII_API_URL",
            "\"https://script.google.com/macros/s/AKfycbxfd_84OPqT-tTF_ZhO6zBYjGGiGDLsk_XoTffD1NMugaXbhltSUZ-YfncredvgSCBI/exec\"")
    }

    buildFeatures { buildConfig = true }

