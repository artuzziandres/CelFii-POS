plugins { id("com.android.application") }

val webUrl = providers.gradleProperty("CELFII_WEB_URL").orElse("")

android {
    namespace = "com.celfii.ventas.remaster"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.celfii.ventas.remaster"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
        buildConfigField("String", "CELFII_WEB_URL", "\"${webUrl.get()}\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
